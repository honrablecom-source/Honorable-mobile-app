package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns only the analysis stage; reviewed edit approval uses the existing batch controller. */
internal class VideoAudioOnsetControls(private val activity: Activity, private val source: Uri,
    private val clip: VideoSegment, private val isCurrent: () -> Boolean,
    private val requireWorkerCurrent: () -> Unit, private val ready: (VideoAudioOnsets.Analysis) -> Unit,
    private val message: (String) -> Unit, private val released: () -> Unit) {
    private val closed = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private var dialog: AlertDialog? = null
    private var stage: Any? = null
    private var running = false
    private fun valid() = !closed.get() && isCurrent()
    private fun owns(token: Any) = valid() && stage === token
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        stage = null; dialog?.dismiss(); dialog = null; worker.shutdownNow(); released()
    }
    private fun show(value: AlertDialog, token: Any) {
        if (!valid()) { close(); return }
        stage = null; dialog?.dismiss(); dialog = value; stage = token
        value.setOnCancelListener { if (owns(token)) close() }
        value.setOnDismissListener { if (!closed.get() && stage === token) close() }
        value.show()
    }
    fun open() {
        if (!valid()) { close(); return }; val token = Any()
        val body = TextView(activity).apply { setTextColor(Color.BLACK); setPadding(20, 12, 20, 12); text = "Suggest source-audio energy-rise cuts in ${VideoCaptions.formatTime(clip.startMs)}–${VideoCaptions.formatTime(clip.endMs)}. Reads the original audio locally before volume, mute, speed, fades and soundtrack. No upload. At most two source minutes and a checked 30-second decoder deadline; blocked providers can delay cancellation.\n\nUses 20 ms RMS windows, a 200 ms known-history median, a peak at least 0.02 RMS and twice the baseline, and a rise of at least 0.01 RMS. At least 95% known source-time coverage is required. Nearby alternatives keep the strongest relative rise, with at least 160 playback milliseconds between potential cuts/edges; the first 200 source milliseconds are history, not candidates. Up to 200 suggestions.\n\nThis is experimental energy-rise analysis, NOT beat/speech/semantic detection or a frame/sample-exact guarantee. Speech/noise/music can all trigger it and real onsets can be missed. Review and uncheck suggestions before a separate complete edit review. Analysis alone never changes your timeline. New pieces retain source coverage, settings and intersecting captions; video/audio edge fades repeat at new cuts. Original files stay; Apply is one Undo." }
        val value = AlertDialog.Builder(activity).setTitle("Suggest audio-onset cuts").setView(ScrollView(activity).apply { setBackgroundColor(Color.WHITE); addView(body) })
            .setNegativeButton("Cancel") { _, _ -> if (owns(token)) close() }
            .setPositiveButton("Analyse original audio") { _, _ -> if (owns(token)) analyse() }.create()
        show(value, token)
    }
    private fun analyse() {
        if (!valid() || running) return
        running = true; val token = Any()
        show(AlertDialog.Builder(activity).setTitle("Reading source audio")
            .setMessage("Local 20 ms energy analysis; original video and timeline kept. Cancelled or stale results cannot become edits. Blocked decoders may delay physical cancellation.")
            .setNegativeButton("Stop") { _, _ -> if (stage === token) close() }.create(), token)
        if (closed.get()) return
        try { worker.execute {
            val began = System.nanoTime(); var lastOwner = began - 100_000_000L
            fun current() {
                check(!closed.get() && !Thread.currentThread().isInterrupted)
                val now = System.nanoTime(); check(now - began < 35_000_000_000L) { "Onset analysis timed out; choose a shorter clip" }
                if (now - lastOwner >= 100_000_000L) { requireWorkerCurrent(); lastOwner = now }
            }
            val result = runCatching {
                current()
                val windows = VideoQuietAudio.scan(activity.applicationContext, source, clip.startMs, clip.endMs) { current(); false }
                VideoAudioOnsets.detect(clip, windows, ::current).also { current() }
            }
            if (closed.get()) return@execute
            activity.runOnUiThread {
                if (!owns(token)) { close(); return@runOnUiThread }
                close() // Release import busy before current batch-review admission; no media payload retained.
                result.onSuccess { value -> runCatching { ready(value) }.onFailure { message("Could not review onsets. Original video and timeline edits were kept.") } }
                    .onFailure { message(it.message ?: "Audio-onset analysis failed. Original video and edits were kept.") }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { close() }
    }
}
