package com.honorablemobile

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.PlayerView
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.atomic.AtomicBoolean
import androidx.media3.common.util.UnstableApi

@UnstableApi
class HonorableVideoEditorActivity : Activity() {
    private lateinit var source: Uri
    private lateinit var video: VideoView
    private lateinit var projectPreviewView: PlayerView
    private var previewPlayer: CompositionPlayer? = null
    private lateinit var start: SeekBar
    private lateinit var end: SeekBar
    private lateinit var status: TextView
    private var durationMs = 0
    private var saving = false
    private var analyzing = false
    private var pendingRange: Pair<Long, Long>? = null
    private var pendingSegments: List<VideoSegment>? = null
    private var project: VideoEditProject? = null
    private var selectedClip = 0
    private lateinit var clipStatus: TextView
    private var exporter: VideoCompositionExporter? = null
    private val exportCancellation = CancellationSignal()
    private val analysisCancellation = AtomicBoolean(false)
    private val previewHandler = Handler(Looper.getMainLooper())
    private val stopPreview = object : Runnable {
        override fun run() {
            if (!::video.isInitialized || !video.isPlaying) return
            if (video.currentPosition >= end.progress * 1000) video.pause()
            else previewHandler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = Uri.parse(intent.getStringExtra("uri") ?: run { finish(); return })
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(20, 24, 20, 24)
        }
        fun label(value: String, size: Float = 15f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE); setPadding(0, 8, 0, 8)
        }
        root.addView(label("Video editor", 28f))
        root.addView(label("Edit clips locally and save a separate copy. Your original stays untouched."))
        val previewFrame = FrameLayout(this)
        video = VideoView(this).apply { setVideoURI(source); setBackgroundColor(Color.BLACK) }
        previewFrame.addView(video, FrameLayout.LayoutParams(-1, -1))
        projectPreviewView = PlayerView(this).apply { visibility = View.GONE; useController = true }
        previewFrame.addView(projectPreviewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        video.setMediaController(MediaController(this).apply { setAnchorView(video) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val controlsHeight = (300 * resources.displayMetrics.density).toInt()
        root.addView(ScrollView(this).apply { addView(controls) }, LinearLayout.LayoutParams(-1, controlsHeight))
        start = SeekBar(this)
        end = SeekBar(this)
        controls.addView(label("Start")); controls.addView(start)
        controls.addView(label("End")); controls.addView(end)
        status = label("Loading video...")
        controls.addView(status)
        clipStatus = label("Clip 1", 13f)
        controls.addView(clipStatus)
        controls.addView(Button(this).apply { text = "Suggest scene (experimental)"; setOnClickListener { suggestScene() } })
        controls.addView(label("Uses on-device labels from sampled frames. Preview the suggestion; it may miss the action.", 12f))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(Button(this).apply {
            text = "Preview clip"
            setOnClickListener {
                if (saving || pendingRange != null || durationMs <= 0) return@setOnClickListener
                stopProjectPreview()
                video.seekTo(start.progress * 1000)
                video.start()
                previewHandler.removeCallbacks(stopPreview)
                previewHandler.post(stopPreview)
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(this).apply {
            text = "Preview project"
            setOnClickListener { previewProject() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(this).apply { text = "Save copy"; setOnClickListener { saveCopy() } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(actions)
        val clipActions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun clipButton(title: String, action: () -> Unit) {
            clipActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving) action() } },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        clipButton("Previous") { if (selectedClip > 0) { selectedClip--; showClip() } }
        clipButton("Next") { if (selectedClip + 1 < (project?.segments?.size ?: 0)) { selectedClip++; showClip() } }
        clipButton("Split") {
            runCatching { requireNotNull(project).split(selectedClip, video.currentPosition.toLong()) }
                .onSuccess { selectedClip++; showClip() }
                .onFailure { status.text = "Play or seek inside this clip, then split." }
        }
        clipButton("Delete") {
            runCatching { requireNotNull(project).delete(selectedClip) }
                .onSuccess { selectedClip = selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex); showClip() }
                .onFailure { status.text = "Keep at least one clip." }
        }
        controls.addView(clipActions)
        val historyActions = LinearLayout(this)
        fun historyButton(title: String, action: () -> Unit) {
            historyActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving) action() } },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        historyButton("Move left") { moveClip(-1) }
        historyButton("Move right") { moveClip(1) }
        historyButton("Undo") { if (project?.undo() == true) { selectedClip = selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex); showClip() } }
        historyButton("Redo") { if (project?.redo() == true) { selectedClip = selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex); showClip() } }
        historyButton("Reset") { project?.reset(); selectedClip = 0; showClip() }
        controls.addView(historyActions)
        val audioActions = LinearLayout(this)
        fun audioButton(title: String, action: () -> Unit) {
            audioActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving) action() } },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        audioButton("Speed") {
            val clip = project?.segments?.getOrNull(selectedClip) ?: return@audioButton
            val speeds = listOf(.5f, 1f, 1.5f, 2f)
            project?.setSpeed(selectedClip, speeds[(speeds.indexOf(clip.speed) + 1) % speeds.size])
            showClip()
        }
        audioButton("Volume") {
            val clip = project?.segments?.getOrNull(selectedClip) ?: return@audioButton
            val volumes = listOf(1f, .5f, 0f)
            project?.setVolume(selectedClip, volumes[(volumes.indexOf(clip.volume) + 1) % volumes.size])
            showClip()
        }
        controls.addView(audioActions)
        setContentView(root)
        video.setOnPreparedListener { player ->
            durationMs = player.duration.coerceAtLeast(0)
            if (durationMs > 0) {
                project = VideoEditProject(durationMs.toLong())
                readSegments(savedInstanceState, "project")?.let { saved ->
                    runCatching { project?.restore(saved) }
                }
                selectedClip = (savedInstanceState?.getInt("selectedClip") ?: 0)
                    .coerceIn(0, requireNotNull(project).segments.lastIndex)
            }
            val seconds = ((durationMs + 999) / 1000).coerceAtLeast(1)
            start.max = seconds
            end.max = seconds
            start.progress = savedInstanceState?.getInt("startProgress", 0) ?: 0
            end.progress = savedInstanceState?.getInt("endProgress", seconds) ?: seconds
            showRange()
            showClip()
            val previewPosition = savedInstanceState?.getLong("previewPositionMs")
                ?: intent.getLongExtra("timestampMs", 0L)
            video.seekTo(previewPosition.coerceIn(0L, (durationMs.toLong() - 1L).coerceAtLeast(0L)).toInt())
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) showRange() }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        }
        start.setOnSeekBarChangeListener(listener)
        end.setOnSeekBarChangeListener(listener)
        if (savedInstanceState?.containsKey("pendingStartMs") == true) {
            pendingRange = savedInstanceState.getLong("pendingStartMs") to savedInstanceState.getLong("pendingEndMs")
            pendingSegments = readSegments(savedInstanceState, "pending")
            start.isEnabled = false; end.isEnabled = false
        }
    }

    private fun showRange() {
        status.text = "${start.progress}s - ${end.progress}s; ${(end.progress - start.progress).coerceAtLeast(0)}s selected"
    }

    private fun showClip() {
        stopProjectPreview()
        val clips = project?.segments ?: return
        val clip = clips[selectedClip]
        start.progress = (clip.startMs / 1000).toInt()
        end.progress = ((clip.endMs + 999) / 1000).toInt().coerceAtMost(end.max)
        clipStatus.text = "Clip ${selectedClip + 1}/${clips.size} · ${clip.speed}x · volume ${(clip.volume * 100).toInt()}% · ${project?.totalDurationMs?.div(1000)}s project"
        video.seekTo(clip.startMs.toInt())
        showRange()
    }

    private fun previewProject() {
        if (saving || analyzing || pendingRange != null) return
        val clips = project?.segments ?: return
        stopProjectPreview()
        video.pause()
        previewHandler.removeCallbacks(stopPreview)
        runCatching {
            CompositionPlayer.Builder(this).build().also { player ->
                previewPlayer = player
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        status.text = "Project preview unavailable: ${error.message ?: "Playback error"}"
                        stopProjectPreview()
                    }
                })
                projectPreviewView.player = player
                projectPreviewView.visibility = View.VISIBLE
                video.visibility = View.GONE
                player.setComposition(VideoCompositionExporter.composition(source, clips))
                player.prepare()
                player.play()
                status.text = "Previewing ${clips.size} clips in project order"
            }
        }.onFailure {
            stopProjectPreview()
            status.text = "Project preview unavailable: ${it.message ?: "Playback error"}"
        }
    }

    private fun stopProjectPreview() {
        previewPlayer?.release()
        previewPlayer = null
        if (::projectPreviewView.isInitialized) {
            projectPreviewView.player = null
            projectPreviewView.visibility = View.GONE
        }
        if (::video.isInitialized) video.visibility = View.VISIBLE
    }

    private fun moveClip(direction: Int) {
        val target = selectedClip + direction
        val clips = project?.segments ?: return
        if (target !in clips.indices) return
        project?.move(selectedClip, target)
        selectedClip = target
        showClip()
    }

    private fun suggestScene() {
        if (analyzing || saving || pendingRange != null || durationMs <= 0) return
        analyzing = true
        status.text = "Finding a scene on this device..."
        Thread {
            val result = runCatching {
                val retriever = MediaMetadataRetriever()
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                try {
                    retriever.setDataSource(this, source)
                    val count = minOf(12, maxOf(1, durationMs / 4000))
                    var bestTime = durationMs / 2L
                    var bestScore = -1f
                    for (index in 0 until count) {
                        if (analysisCancellation.get()) break
                        val time = ((index + .5) * durationMs / count).toLong()
                        val frame = if (Build.VERSION.SDK_INT >= 27) {
                            retriever.getScaledFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 384, 384)
                        } else {
                            val full = retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            full?.let { android.graphics.Bitmap.createScaledBitmap(it, 384, 384, true).also { scaled -> if (scaled !== it) it.recycle() } }
                        } ?: continue
                        try {
                            val labels = Tasks.await(labeler.process(InputImage.fromBitmap(frame, 0)))
                            val score = labels.filterNot { it.text.lowercase() in setOf("photography", "video", "image", "snapshot") }
                                .sumOf { it.confidence.toDouble().coerceAtMost(.85) }.toFloat()
                            if (score > bestScore) { bestScore = score; bestTime = time }
                        } finally { frame.recycle() }
                    }
                    require(bestScore >= 0) { "No frames could be analyzed" }
                    bestTime
                } finally { labeler.close(); retriever.release() }
            }
            runOnUiThread {
                analyzing = false
                if (isDestroyed || isFinishing || analysisCancellation.get()) return@runOnUiThread
                result.onSuccess { center ->
                    val centerSeconds = (center / 1000).toInt()
                    start.progress = (centerSeconds - 4).coerceAtLeast(0)
                    end.progress = (centerSeconds + 4).coerceAtMost(end.max)
                    status.text = "Suggested scene: ${start.progress}s - ${end.progress}s. Preview and adjust before saving."
                }.onFailure { status.text = "Scene suggestion unavailable. Set the trim range manually." }
            }
        }.start()
    }

    private fun saveCopy() {
        if (saving || analyzing || pendingRange != null || durationMs <= 0) return
        val current = project?.segments?.getOrNull(selectedClip) ?: return
        val from = maxOf(start.progress * 1000L, current.startMs)
        val to = minOf(end.progress * 1000L, current.endMs)
        if (to <= from) { status.text = "End must be after start"; return }
        project?.trim(selectedClip, from, to)
        pendingSegments = project?.segments?.toList()
        pendingRange = from to to
        start.isEnabled = false; end.isEnabled = false
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/mp4"
                putExtra(Intent.EXTRA_TITLE, "Honorable-video-copy.mp4")
            }, 22)
        } catch (error: Exception) {
            pendingRange = null
            pendingSegments = null
            start.isEnabled = true; end.isEnabled = true
            status.text = "No app is available to save a copy"
        }
    }

    @Deprecated("Uses the system document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 22) return
        val range = pendingRange
        val clips = pendingSegments
        pendingRange = null
        pendingSegments = null
        start.isEnabled = true; end.isEnabled = true
        if (resultCode != RESULT_OK || data?.data == null || saving || range == null || clips == null || isFinishing || isDestroyed) return
        val destination = data.data!!
        saving = true
        start.isEnabled = false; end.isEnabled = false
        status.text = "Saving copy..."
        exporter = VideoCompositionExporter(this)
        runCatching {
            exporter!!.export(source, destination, clips) { result ->
                saving = false
                exporter = null
                if (isDestroyed || isFinishing) return@export
                start.isEnabled = true; end.isEnabled = true
                status.text = result.fold(
                    onSuccess = { "Copy saved. Original preserved. Check playback and audio." },
                    onFailure = { "Export failed: ${it.message ?: "Unknown error"}. Check Files for a partial copy." },
                )
            }
        }.onFailure {
            saving = false; exporter = null
            start.isEnabled = true; end.isEnabled = true
            status.text = "Export could not start: ${it.message ?: "Unknown error"}"
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::start.isInitialized) outState.putInt("startProgress", start.progress)
        if (::end.isInitialized) outState.putInt("endProgress", end.progress)
        if (::video.isInitialized) outState.putLong("previewPositionMs", video.currentPosition.toLong())
        outState.putInt("selectedClip", selectedClip)
        project?.segments?.let { writeSegments(outState, "project", it) }
        pendingRange?.let { outState.putLong("pendingStartMs", it.first); outState.putLong("pendingEndMs", it.second) }
        pendingSegments?.let { writeSegments(outState, "pending", it) }
    }

    private fun writeSegments(state: Bundle, key: String, clips: List<VideoSegment>) {
        state.putLongArray("${key}Times", clips.flatMap { listOf(it.startMs, it.endMs) }.toLongArray())
        state.putFloatArray("${key}Audio", clips.flatMap { listOf(it.speed, it.volume) }.toFloatArray())
    }

    private fun readSegments(state: Bundle?, key: String): List<VideoSegment>? {
        val times = state?.getLongArray("${key}Times") ?: return null
        val audio = state.getFloatArray("${key}Audio") ?: return null
        if (times.isEmpty() || times.size != audio.size || times.size % 2 != 0) return null
        return runCatching {
            times.indices.step(2).map { VideoSegment(times[it], times[it + 1], audio[it], audio[it + 1]) }
        }.getOrNull()
    }

    override fun onPause() { if (::video.isInitialized) video.pause(); stopProjectPreview(); super.onPause() }
    override fun onDestroy() {
        previewHandler.removeCallbacks(stopPreview)
        stopProjectPreview()
        analysisCancellation.set(true)
        exportCancellation.cancel()
        exporter?.cancel()
        super.onDestroy()
    }
}
