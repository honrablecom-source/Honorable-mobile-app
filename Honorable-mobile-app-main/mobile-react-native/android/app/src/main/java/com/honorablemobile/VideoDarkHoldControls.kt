package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns setup/scan; transfers a bounded frame pack only to an admitted, tracked review. */
internal class VideoDarkHoldControls(private val activity: Activity, private val source: Uri, private val durationMs: Long,
    private val clip: VideoSegment, private val isCurrent: () -> Boolean, private val requireWorkerCurrent: () -> Unit,
    private val ready: (VideoDarkHoldPack) -> Boolean, private val message: (String) -> Unit, private val released: () -> Unit) {
    private val closed = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private var dialog: AlertDialog? = null; private var stage: Any? = null; private var running = false
    private fun valid() = !closed.get() && isCurrent()
    private fun owns(token: Any) = valid() && stage === token
    fun close() { if (!closed.compareAndSet(false, true)) return; stage = null; dialog?.dismiss(); dialog = null; worker.shutdownNow(); released() }
    private fun show(value: AlertDialog, token: Any) {
        if (!valid()) { close(); return }
        stage = null; dialog?.dismiss(); dialog = value; stage = token
        value.setOnCancelListener { if (owns(token)) close() }; value.setOnDismissListener { if (!closed.get() && stage === token) close() }; value.show()
    }
    fun open() {
        if (!valid()) { close(); return }; val token = Any()
        val body = TextView(activity).apply { setTextColor(Color.BLACK); setPadding(20, 12, 20, 12); text = "Scan the WHOLE selected clip ${VideoCaptions.formatTime(clip.startMs)}–${VideoCaptions.formatTime(clip.endMs)} locally. Up to 64 evenly spaced nearest source frames, roughly every0.5s for short clips and coarser for long clips; each at most256px SDR. A suggestion needs at least3consecutive opaque samples with EVERY decoded channel≤12, between bright sampled neighbors. Only the interval between the first/last dark requested centers is proposed. Leading/trailing/unknown-adjacent dark runs stay.\n\nThis is experimental sampling, NOT proof of empty/black footage. Repeated nearest frames, downsampling, missed bright flashes and intentional dark scenes can mislead. Actual decoded timestamps are unavailable. Source frames exclude edited titles, captions, grades, crops, motion and fades, so meaningful overlays can disappear if you remove source-dark footage. Review EVERY candidate sample. Every removal starts unchecked and requires a separate full edit review.\n\nChoosing intervals removes both picture and original audio and ripples later clips earlier. Music settings remain; synchronization changes. Edge fades repeat on kept pieces. Apply is one Undo. Original files remain unchanged; no upload.2.5–120source seconds,96MiB initial heap headroom, checked60sdecoder/65stotal deadlines. Blocked providers can delay physical cancellation." }
        show(AlertDialog.Builder(activity).setTitle("Review sampled dark holds").setView(ScrollView(activity).apply { setBackgroundColor(Color.WHITE); addView(body) })
            .setNegativeButton("Cancel") { _, _ -> if (owns(token)) close() }
            .setPositiveButton("Scan original frames") { _, _ -> if (owns(token)) scan() }.create(), token)
    }
    private fun scan() {
        if (!valid() || running) return
        running = true; val token = Any()
        show(AlertDialog.Builder(activity).setTitle("Scanning source frames").setMessage("Local bounded frame scan. Original video and edit kept. Stop refuses late output; blocked decoders can delay physical cancellation.")
            .setNegativeButton("Stop") { _, _ -> if (stage === token) close() }.create(), token)
        if (closed.get()) return
        try { worker.execute {
            val began = System.nanoTime(); var lastOwner = began - 100_000_000L
            fun current() {
                check(!closed.get() && !Thread.currentThread().isInterrupted)
                val now = System.nanoTime(); check(now - began < 65_000_000_000L) { "Frame scan timed out; choose a shorter clip" }
                if (now - lastOwner >= 100_000_000L) { requireWorkerCurrent(); lastOwner = now }
            }
            var produced: VideoDarkHoldPack? = null
            val result = runCatching { current(); VideoDarkHoldFrames.scan(activity.applicationContext, source, durationMs, clip, ::current) { index, total ->
                activity.runOnUiThread { if (owns(token)) dialog?.setMessage("Reading original source frame $index/$total; no timeline changes. Nearest frames can repeat; unknowns cannot join a dark hold.") }
            }.also { produced = it; current() } }
            if (result.isFailure) produced?.dispose()
            if (closed.get()) { result.getOrNull()?.dispose(); return@execute }
            activity.runOnUiThread {
                if (!owns(token)) { result.getOrNull()?.dispose(); close(); return@runOnUiThread }
                close()
                result.onSuccess { value ->
                    val accepted = runCatching { ready(value) }.getOrElse { message("Could not show dark-hold review. Original video and edits were kept."); false }
                    if (!accepted) value.dispose()
                }.onFailure { message(it.message ?: "Dark-hold scan failed. Original video and edits were kept.") }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { close() }
    }
}
