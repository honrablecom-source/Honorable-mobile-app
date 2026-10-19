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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import android.graphics.drawable.GradientDrawable
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
    private var draftBusy = false
    private val draftWorker = Executors.newSingleThreadExecutor()
    private var pendingRange: Pair<Long, Long>? = null
    private var pendingSegments: List<VideoSegment>? = null
    private var project: VideoEditProject? = null
    private var selectedClip = 0
    private lateinit var timeline: LinearLayout
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
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= 26) Color.WHITE else Color.BLACK
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(20, 24, 20, 24)
        }
        fun label(value: String, size: Float = 15f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.BLACK); setPadding(0, 8, 0, 8)
        }
        root.addView(label("Video editor", 28f))

        val previewFrame = FrameLayout(this)
        video = VideoView(this).apply { setVideoURI(source); setBackgroundColor(Color.WHITE) }
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
        controls.addView(label("Timeline · tap a clip", 12f))
        timeline = LinearLayout(this)
        controls.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(timeline) })
        controls.addView(clipStatus)
        controls.addView(Button(this).apply { text = "Suggest scene (experimental)"; setOnClickListener { suggestScene() } })
        controls.addView(label("Uses on-device labels from sampled frames. Preview the suggestion; it may miss the action.", 12f))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(Button(this).apply {
            text = "Preview clip"
            setOnClickListener {
                if (saving || draftBusy || pendingRange != null || durationMs <= 0) return@setOnClickListener
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
            clipActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving && !draftBusy) action() } },
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
        val extraActions = LinearLayout(this)
        extraActions.addView(Button(this).apply { text = "Apply trim"; setOnClickListener {
            if (saving || draftBusy || pendingRange != null) return@setOnClickListener
            val clip = project?.segments?.getOrNull(selectedClip) ?: return@setOnClickListener
            runCatching { project?.trim(selectedClip, maxOf(start.progress * 1_000L, clip.startMs), minOf(end.progress * 1_000L, clip.endMs)) }
                .onSuccess { showClip() }.onFailure { status.text = "Select a valid range inside this clip." }
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        extraActions.addView(Button(this).apply { text = "Duplicate"; setOnClickListener {
            if (saving || draftBusy || pendingRange != null) return@setOnClickListener
            runCatching { requireNotNull(project).duplicate(selectedClip) }
                .onSuccess { selectedClip++; showClip() }.onFailure { status.text = "Cannot duplicate this clip. Check the project clip limit." }
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        extraActions.addView(Button(this).apply { text = "Mute"; setOnClickListener {
            if (saving || draftBusy || pendingRange != null) return@setOnClickListener
            project?.setVolume(selectedClip, 0f); showClip()
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(extraActions)
        val historyActions = LinearLayout(this)
        fun historyButton(title: String, action: () -> Unit) {
            historyActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving && !draftBusy) action() } },
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
            audioActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving && !draftBusy) action() } },
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
        val drafts = LinearLayout(this)
        drafts.addView(Button(this).apply { text = "Save draft"; setOnClickListener { draftOperation(false) } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        drafts.addView(Button(this).apply { text = "Restore draft"; setOnClickListener { draftOperation(true) } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(drafts)
        controls.addView(label("Drafts stay on this device. Reopen this video to restore its saved edits.", 12f))
        styleEditorControls(root, previewFrame)
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
        renderTimeline()
    }

    private fun previewProject() {
        if (saving || analyzing || draftBusy || pendingRange != null) return
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

    private fun styleEditorControls(view: View, player: View) {
        if (view === player) return // Media/player overlay colors are independent of UI chrome.
        if (view is TextView) view.setTextColor(Color.BLACK)
        if (view is Button) {
            view.isAllCaps = false; view.textSize = 12f
            view.minHeight = (48 * resources.displayMetrics.density).toInt()
            view.background = GradientDrawable().apply {
                setColor(Color.WHITE); setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), Color.BLACK)
                cornerRadius = 6 * resources.displayMetrics.density
            }
        }
        if (view is SeekBar) {
            view.progressTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
            view.thumbTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) styleEditorControls(view.getChildAt(i), player)
    }

    private fun renderTimeline() {
        timeline.removeAllViews()
        project?.segments?.forEachIndexed { index, clip ->
            timeline.addView(Button(this).apply {
                text = "${index + 1} · ${clip.durationMs / 1_000f}s\n${clip.startMs / 1_000f}–${clip.endMs / 1_000f}s"
                contentDescription = "Select clip ${index + 1}"
                setOnClickListener { if (!saving && !draftBusy && pendingRange == null) { selectedClip = index; showClip() } }
                styleEditorControls(this, projectPreviewView)
                if (index == selectedClip) background = GradientDrawable().apply {
                    setColor(Color.rgb(240,240,240)); setStroke((2 * resources.displayMetrics.density).toInt(), Color.BLACK)
                    cornerRadius = 6 * resources.displayMetrics.density
                }
            }, LinearLayout.LayoutParams((116 * resources.displayMetrics.density).toInt(), (64 * resources.displayMetrics.density).toInt()))
        }
    }

    private fun suggestScene() {
        if (analyzing || saving || draftBusy || pendingRange != null || durationMs <= 0) return
        val selection = runCatching { VideoSceneSelection.capture(project?.segments ?: emptyList(), selectedClip,
            start.progress * 1_000L, end.progress * 1_000L) }.getOrElse {
            status.text = "Choose a valid range before suggesting a scene."; return
        }
        analyzing = true
        status.text = "Finding a scene on this device..."
        Thread {
            val result = runCatching {
                val retriever = MediaMetadataRetriever()
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                try {
                    retriever.setDataSource(this, source)
                    var bestTime = selection.startMs
                    var bestScore = -1f
                    for (time in selection.sampleTimes()) {
                        if (analysisCancellation.get()) break
                        val frame = if (Build.VERSION.SDK_INT >= 27) {
                            retriever.getScaledFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 384, 384)
                        } else {
                            val full = retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                            full?.let { android.graphics.Bitmap.createScaledBitmap(it, 384, 384, true).also { scaled -> if (scaled !== it) it.recycle() } }
                        } ?: continue
                        val task = try { labeler.process(InputImage.fromBitmap(frame, 0)) } catch (error: Throwable) { frame.recycle(); throw error }
                        try {
                            val labels = Tasks.await(task, 10, TimeUnit.SECONDS)
                            val score = labels.filterNot { it.text.lowercase() in setOf("photography", "video", "image", "snapshot") }
                                .sumOf { it.confidence.toDouble().coerceAtMost(.85) }.toFloat()
                            if (score > bestScore) { bestScore = score; bestTime = time }
                        } finally {
                            // A timed-out task may still own the bitmap; release only after completion.
                            if (task.isComplete) frame.recycle() else task.addOnCompleteListener { frame.recycle() }
                        }
                    }
                    require(bestScore >= 0) { "No frames could be analyzed" }
                    bestTime
                } finally { labeler.close(); retriever.release() }
            }
            runOnUiThread {
                analyzing = false
                if (isDestroyed || isFinishing || analysisCancellation.get()) return@runOnUiThread
                if (!selection.stillMatches(project?.segments ?: emptyList(), selectedClip,
                        start.progress * 1_000L, end.progress * 1_000L)) {
                    status.text = "Your edits changed. Run scene suggestion again for this selection."; return@runOnUiThread
                }
                result.onSuccess { center ->
                    val range = selection.suggestedRange(center)
                    start.progress = (range.first / 1_000).toInt()
                    end.progress = ((range.second + 999) / 1_000).toInt().coerceAtMost(end.max)
                    status.text = "Suggested scene: ${start.progress}s - ${end.progress}s. Preview and adjust before saving."
                }.onFailure { status.text = "Scene suggestion unavailable. Set the trim range manually." }
            }
        }.start()
    }

    private fun saveCopy() {
        if (saving || analyzing || draftBusy || pendingRange != null || durationMs <= 0) return
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

    private fun draftOperation(restore: Boolean) {
        if (saving || analyzing || draftBusy || pendingRange != null || durationMs <= 0) return
        val current = project ?: return
        val sourceDuration = durationMs.toLong()
        val snapshot = if (restore) null else runCatching {
            val clip = current.segments[selectedClip]
            VideoDraft(durationMs.toLong(), current.segments.toList(), selectedClip,
                video.currentPosition.toLong().coerceIn(0L, durationMs.toLong() - 1),
                maxOf(start.progress * 1000L, clip.startMs), minOf(end.progress * 1000L, clip.endMs))
        }.getOrElse { status.text = "Choose a valid range before saving a draft (up to 1,000 clips)."; return }
        draftBusy = true
        start.isEnabled = false; end.isEnabled = false
        stopProjectPreview()
        video.pause()
        previewHandler.removeCallbacks(stopPreview)
        status.text = if (restore) "Restoring local draft..." else "Saving local draft..."
        draftWorker.execute {
            var persistence: VideoDraftPersistence? = null
            val result = runCatching {
                VideoDraftPersistence(this, source.toString()).also { persistence = it }.let { storage ->
                    if (restore) storage.load(sourceDuration)
                    else { storage.save(requireNotNull(snapshot)); null }
                }
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                draftBusy = false
                start.isEnabled = true; end.isEnabled = true
                if (result.isFailure || runCatching { requireNotNull(persistence).requireCurrentOwner() }.isFailure) {
                    status.text = "Draft unavailable. Check your account and try again."
                    return@runOnUiThread
                }
                if (project !== current || durationMs.toLong() != sourceDuration) {
                    status.text = "Video changed. Try the draft action again."
                    return@runOnUiThread
                }
                val saved = result.getOrNull()
                if (restore && saved != null) {
                    current.applyDraft(saved.segments)
                    selectedClip = saved.selectedClip
                    showClip()
                    start.progress = (saved.trimStartMs / 1000).toInt()
                    end.progress = ((saved.trimEndMs + 999) / 1000).toInt().coerceAtMost(end.max)
                    video.seekTo(saved.previewPositionMs.toInt())
                    status.text = "Draft restored. Preview before saving a copy. Undo restores prior clip edits."
                } else status.text = if (restore) "No compatible draft is saved for this video and account."
                    else "Draft saved on this device. Your original is unchanged."
            }
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
        draftWorker.shutdownNow()
        exportCancellation.cancel()
        exporter?.cancel()
        super.onDestroy()
    }
}
