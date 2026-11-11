package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.RadioGroup
import android.widget.RadioButton
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one stage, worker request and prepared image; UI checks remain on the UI thread. */
internal class VideoFrameArchiveControls(private val activity: Activity, private val source: android.net.Uri,
    private val sourceDuration: Long, private val fromMs: Long, private val toMs: Long,
    private val isCurrent: () -> Boolean, private val requireWorkerCurrent: () -> Unit,
    private val message: (String) -> Unit, private val shareArchive: (File) -> Unit, private val released: () -> Unit) {
    private val closed = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private val lease = PhotoDifferenceLease<VideoFrameArchiveReview>({ it.token }, { it.dispose() })
    private var prepared: VideoFrameArchiveReview? = null
    private var dialog: AlertDialog? = null; private var stage: Any? = null
    private val images = mutableListOf<ImageView>(); private var request: AtomicBoolean? = null
    private var errorText = ""
    private var count = 8
    private fun valid() = !closed.get() && isCurrent()
    private fun owns(token: Any) = valid() && stage === token
    private fun clearImage() { images.forEach { it.setImageDrawable(null) }; images.clear() }
    private fun clearPrepared() { clearImage(); prepared = null; lease.clear() }
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        request?.set(true); request = null; stage = null; clearPrepared()
        dialog?.dismiss(); dialog = null; worker.shutdownNow(); released()
    }
    private fun show(value: AlertDialog, token: Any) {
        if (!valid()) { close(); return }
        stage = null; clearImage(); dialog?.dismiss(); dialog = value; stage = token
        value.setOnCancelListener { if (owns(token)) close() }
        value.setOnDismissListener { if (!closed.get() && stage === token) close() }
        value.show()
    }
    fun open() = setup()
    private fun setup() {
        if (!valid()) { close(); return }; if (request != null) return
        clearPrepared(); val token = Any()
        val labels = arrayOf("4 separate frames", "8 separate frames", "16 separate frames")
        val selected = listOf(4, 8, 16).indexOf(count)
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12); setBackgroundColor(Color.WHITE) }
        panel.addView(TextView(activity).apply { setTextColor(Color.BLACK); text = (if (errorText.isEmpty()) "" else errorText + "\n\n") + "Source selection ${VideoCaptions.formatTime(fromMs)}–${VideoCaptions.formatTime(toMs)}. Exports evenly spaced nearest decoded source frames as separate SDR PNGs up to 512 pixels per edge, plus a manifest of requested source times, dimensions, byte counts and SHA-256 checksums. No HDR/profile preservation. Actual decoded timestamps are unavailable and frames may repeat. Video effects, captions, grade, crop and motion are excluded. Review every complete image before explicitly sharing a ZIP to another app. The archive contains image content and selected source times, but no original URI, filename, account or project metadata. Other apps can keep shared copies. Original video and timeline stay unchanged. Limits: 2 MiB per PNG, 16 MiB combined images, 20 MiB ZIP." })
        val ids = IntArray(labels.size) { android.view.View.generateViewId() }
        val options = RadioGroup(activity)
        labels.forEachIndexed { index, label -> options.addView(RadioButton(activity).apply { id = ids[index]; text = label; setTextColor(Color.BLACK); buttonTintList = android.content.res.ColorStateList.valueOf(Color.BLACK); minHeight = (48 * activity.resources.displayMetrics.density).toInt() }) }
        options.check(ids[selected]); options.setOnCheckedChangeListener { _, id -> if (owns(token)) { val index = ids.indexOf(id); if (index >= 0) { count = listOf(4, 8, 16)[index] } } }
        panel.addView(options)
        val value = AlertDialog.Builder(activity).setTitle("Video frame archive")
            .setView(ScrollView(activity).apply { addView(panel) })
            .setNegativeButton("Cancel") { _, _ -> if (owns(token)) close() }
            .setPositiveButton("Prepare review", null).create()
        show(value, token)
        if (!closed.get()) value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!owns(token) || request != null) return@setOnClickListener
            val plan = runCatching { VideoFrameArchive.Plan(fromMs, toMs, count) }.getOrElse { errorText = it.message ?: "Choose a longer source selection"; value.setTitle(errorText); message(errorText); return@setOnClickListener }
            prepare(token, plan)
        }
    }
    private fun busy(title: String, cancellation: AtomicBoolean) {
        val token = Any()
        show(AlertDialog.Builder(activity).setTitle(title).setMessage("Local frame work has a checked 60-second deadline. Blocked providers or decoders may delay physical cancellation; cancelled output never opens.")
            .setNegativeButton("Stop") { _, _ -> if (stage === token) { cancellation.set(true); close() } }.create(), token)
    }
    private fun <T> job(title: String, action: (() -> Unit) -> T, done: (T) -> Unit, abandon: (T) -> Unit) {
        if (!valid() || request != null) return
        val cancellation = AtomicBoolean(false); request = cancellation; busy(title, cancellation)
        if (closed.get()) return
        try { worker.execute {
            val began = System.nanoTime()
            var lastOwnerCheck = began - 100_000_000L
            fun current() {
                val now = System.nanoTime()
                check(!closed.get() && !cancellation.get()); check(now - began < 60_000_000_000L)
                if (now - lastOwnerCheck >= 100_000_000L) { requireWorkerCurrent(); lastOwnerCheck = now }
            }
            var produced: T? = null
            val result = runCatching { current(); action(::current).also { produced = it; current() } }
            if (result.isFailure) produced?.let(abandon)
            if (closed.get() || cancellation.get()) { result.getOrNull()?.let(abandon); return@execute }
            activity.runOnUiThread {
                if (request !== cancellation || !valid()) { result.getOrNull()?.let(abandon); close(); return@runOnUiThread }
                request = null
                result.onSuccess { value -> runCatching { done(value) }.onFailure { abandon(value); clearPrepared(); message("Could not show the frame archive review. Originals and edits were kept."); close() } }
                    .onFailure { clearPrepared(); errorText = "Frame archive work failed. Try fewer samples or a source with consistent frame dimensions. Originals and edits were kept."; message(errorText); setup() }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { request = null; close() }
    }
    private fun prepare(token: Any, plan: VideoFrameArchive.Plan) {
        if (!owns(token)) return
        errorText = ""; clearPrepared()
        job("Preparing frame archive", { current ->
            val archive = VideoFrameArchiveImage.prepare(activity.applicationContext, source, sourceDuration, plan, current) { index, total ->
                activity.runOnUiThread { if (request != null && valid()) message("Reading nearest source frame $index/$total · original kept") }
            }
            try { VideoFrameArchiveReview(archive, VideoFrameArchiveImage.previews(archive, current)) }
            catch (error: Throwable) { archive.dispose(); throw error }
        }, { result -> lease.offer(result); prepared = result; review(result) }, { it.dispose() })
    }
    private fun review(value: VideoFrameArchiveReview) {
        if (!valid()) { close(); return }; val token = Any()
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12); setBackgroundColor(Color.WHITE) }
        val pictures = mutableListOf<ImageView>()
        value.archive.entries.forEachIndexed { index, entry ->
            panel.addView(TextView(activity).apply { setTextColor(Color.BLACK); text = "${entry.name} · requested ${VideoCaptions.formatTime(entry.requestedMs)}\n${entry.width} × ${entry.height} pixels · ${entry.bytes} bytes\nSHA-256 ${entry.sha256}" })
            val picture = ImageView(activity).apply { setImageBitmap(value.previews[index]); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Complete PNG frame ${index + 1} of ${value.archive.plan.count}" }
            panel.addView(picture, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 512)); pictures += picture
        }
        panel.addView(TextView(activity).apply { setTextColor(Color.BLACK); text = "Complete manifest.json:\n" + VideoFrameArchive.manifest(value.archive.plan, value.archive.entries).toString(Charsets.UTF_8) + "\nZIP ${value.archive.file.length()} bytes\nSHA-256 ${value.archive.digest}\n\nNearest decoded frames may repeat; requested times are not exact decoded timestamps. These previews came from the verified ZIP. Sharing verifies the whole archive again and grants read access only to this prepared archive, not the original video. The chosen app can retain image content and timing. This is a temporary export: save it in the chosen app to keep it. Original video and timeline edits remain unchanged." })
        val valueDialog = AlertDialog.Builder(activity).setTitle("Review all archived frames").setView(ScrollView(activity).apply { addView(panel) })
            .setNegativeButton("Change sample count") { _, _ -> if (owns(token)) setup() }
            .setNeutralButton("Cancel") { _, _ -> if (owns(token)) close() }
            .setPositiveButton("Share reviewed ZIP") { _, _ -> if (owns(token) && prepared === value && request == null) handoff(value) }.create()
        show(valueDialog, token); images += pictures
    }
    private fun handoff(value: VideoFrameArchiveReview) {
        job("Verifying reviewed ZIP", { current -> VideoFrameArchive.verify(value.archive, current); value }, { checked ->
            if (prepared !== checked) { close(); return@job }
            val taken = lease.take(checked.token) ?: run { close(); return@job }
            prepared = null; clearImage(); taken.clearPreview()
            runCatching { shareArchive(taken.archive.file) }.onSuccess { message("Frame archive share opened. Save the ZIP in the chosen app to keep it; original video and edits were kept.") }
                .onFailure { taken.dispose(); message("Could not share the prepared archive. Original video and edits were kept.") }
            close()
        }, { /* close() owns and disposes this existing lease. */ })
    }
}
