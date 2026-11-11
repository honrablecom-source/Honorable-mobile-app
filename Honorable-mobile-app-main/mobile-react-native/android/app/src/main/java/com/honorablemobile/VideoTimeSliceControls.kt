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
internal class VideoTimeSliceControls(private val activity: Activity, private val source: android.net.Uri,
    private val sourceDuration: Long, private val fromMs: Long, private val toMs: Long,
    private val isCurrent: () -> Boolean, private val requireWorkerCurrent: () -> Unit,
    private val message: (String) -> Unit, private val openImage: (File) -> Unit, private val released: () -> Unit) {
    private val closed = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private val lease = PhotoDifferenceLease<VideoTimeSlicePrepared>({ it.token }, { it.dispose() })
    private var prepared: VideoTimeSlicePrepared? = null
    private var dialog: AlertDialog? = null; private var stage: Any? = null
    private var image: ImageView? = null; private var request: AtomicBoolean? = null
    private var errorText = ""
    private var count = 8; private var axis = VideoTimeSlice.Axis.COLUMNS
    private fun valid() = !closed.get() && isCurrent()
    private fun owns(token: Any) = valid() && stage === token
    private fun clearImage() { image?.setImageDrawable(null); image = null }
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
        val labels = arrayOf("4 columns · time left to right", "8 columns · time left to right", "16 columns · time left to right", "4 rows · time top to bottom", "8 rows · time top to bottom", "16 rows · time top to bottom")
        val selected = listOf(4, 8, 16).indexOf(count) + if (axis == VideoTimeSlice.Axis.ROWS) 3 else 0
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12); setBackgroundColor(Color.WHITE) }
        panel.addView(TextView(activity).apply { setTextColor(Color.BLACK); text = (if (errorText.isEmpty()) "" else errorText + "\n\n") + "Source selection ${VideoCaptions.formatTime(fromMs)}–${VideoCaptions.formatTime(toMs)}. Combines bands at their original spatial positions from evenly sampled nearest decoded frames. Original video effects, captions, grade, crop and motion are excluded. Output is a separate SDR PNG up to 512 pixels per edge; no HDR/profile preservation. Seams can be abrupt and nearby samples can decode the same frame. Review the completed image before opening a local guest photo workspace. Original video and timeline stay unchanged." })
        val ids = IntArray(labels.size) { android.view.View.generateViewId() }
        val options = RadioGroup(activity)
        labels.forEachIndexed { index, label -> options.addView(RadioButton(activity).apply { id = ids[index]; text = label; setTextColor(Color.BLACK); buttonTintList = android.content.res.ColorStateList.valueOf(Color.BLACK); minHeight = (48 * activity.resources.displayMetrics.density).toInt() }) }
        options.check(ids[selected]); options.setOnCheckedChangeListener { _, id -> if (owns(token)) { val index = ids.indexOf(id); if (index >= 0) { count = listOf(4, 8, 16)[index % 3]; axis = if (index < 3) VideoTimeSlice.Axis.COLUMNS else VideoTimeSlice.Axis.ROWS } } }
        panel.addView(options)
        val value = AlertDialog.Builder(activity).setTitle("Video time-slice photo")
            .setView(ScrollView(activity).apply { addView(panel) })
            .setNegativeButton("Cancel") { _, _ -> if (owns(token)) close() }
            .setPositiveButton("Prepare review", null).create()
        show(value, token)
        if (!closed.get()) value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!owns(token) || request != null) return@setOnClickListener
            val plan = runCatching { VideoTimeSlice.Plan(fromMs, toMs, count, axis) }.getOrElse { errorText = it.message ?: "Choose a longer source selection"; value.setTitle(errorText); message(errorText); return@setOnClickListener }
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
                result.onSuccess { value -> runCatching { done(value) }.onFailure { abandon(value); clearPrepared(); message("Could not show the time-slice review. Originals and edits were kept."); close() } }
                    .onFailure { clearPrepared(); errorText = "Time-slice work failed. Try fewer bands, another direction, or a source with consistent frame dimensions. Originals and edits were kept."; message(errorText); setup() }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { request = null; close() }
    }
    private fun prepare(token: Any, plan: VideoTimeSlice.Plan) {
        if (!owns(token)) return
        errorText = ""
        clearPrepared()
        job("Preparing time-slice image", { current -> VideoTimeSliceImage.prepare(activity.applicationContext, source, sourceDuration, plan, current) { index, total ->
            activity.runOnUiThread { if (request != null && valid()) message("Reading nearest source frame $index/$total · original kept") }
        } }, { result -> lease.offer(result); prepared = result; review(result) }, { it.dispose() })
    }
    private fun review(value: VideoTimeSlicePrepared) {
        if (!valid()) { close(); return }; val token = Any()
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12); setBackgroundColor(Color.WHITE) }
        val picture = ImageView(activity).apply { setImageBitmap(value.preview); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Complete prepared time-slice PNG" }
        panel.addView(picture, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 512))
        panel.addView(TextView(activity).apply { setTextColor(Color.BLACK); text = "${value.width} × ${value.height} pixels · ${value.file.length()} bytes. ${value.plan.count} bands; ${if (value.plan.axis == VideoTimeSlice.Axis.COLUMNS) "time left to right" else "time top to bottom"}.\nRequested source samples:\n${value.plan.times.joinToString("\n") { VideoCaptions.formatTime(it) }}\n\nNearest decoded frames can repeat; no alignment, seam blending or frame-exact timing. This is the complete PNG preview. Opening verifies its SHA-256 and gives only this temporary image to a separate local guest photo workspace; no account draft or privileges transfer. Save a project/export there to keep it. The original video remains protected and unchanged." })
        val valueDialog = AlertDialog.Builder(activity).setTitle("Review time-slice photo").setView(ScrollView(activity).apply { addView(panel) })
            .setNegativeButton("Try different bands") { _, _ -> if (owns(token)) setup() }
            .setNeutralButton("Cancel") { _, _ -> if (owns(token)) close() }
            .setPositiveButton("Open separate photo") { _, _ -> if (owns(token) && prepared === value && request == null) handoff(value) }.create()
        show(valueDialog, token); image = picture
    }
    private fun handoff(value: VideoTimeSlicePrepared) {
        job("Verifying reviewed PNG", { current -> VideoTimeSliceImage.bytes(value, current); value }, { checked ->
            if (prepared !== checked) { close(); return@job }
            val taken = lease.take(checked.token) ?: run { close(); return@job }
            prepared = null; clearImage(); taken.clearPreview()
            runCatching { openImage(taken.file) }.onSuccess { message("Time-slice photo opened separately. Video and timeline edits were kept.") }
                .onFailure { taken.dispose(); message("Could not open the prepared photo. Original video and edits were kept.") }
            close()
        }, { /* close() owns and disposes this existing lease. */ })
    }
}
