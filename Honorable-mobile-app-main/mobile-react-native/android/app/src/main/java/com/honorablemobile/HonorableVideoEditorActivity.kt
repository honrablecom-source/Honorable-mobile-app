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
import app.honorable.editor.PhotoLook
import app.honorable.editor.PhotoLightStats
import app.honorable.editor.EditorAccessMode

@UnstableApi
class HonorableVideoEditorActivity : Activity() {
    private var accessMode = EditorAccessMode.ACCOUNT
    @Volatile private var guestProjectRequest = 0L
    private lateinit var source: Uri
    private lateinit var video: VideoView
    private lateinit var projectPreviewView: PlayerView
    private var previewPlayer: CompositionPlayer? = null
    private var previewSegments: List<VideoSegment>? = null
    private var previewSelectedIndex: Int? = null
    private lateinit var start: SeekBar
    private lateinit var end: SeekBar
    private lateinit var status: TextView
    private var durationMs = 0
    private var primaryDurationMs = 0L
    private val importedSources = linkedMapOf<String, VideoSource>()
    private var videoSourceId = "primary"
    private var pendingSeekMs = 0L
    private var importing = false
    private var sourceReady = false
    private var recoveryBytes: ByteArray? = null
    private var saving = false
    private var analyzing = false
    private var draftBusy = false
    private val draftWorker = Executors.newSingleThreadExecutor()
    private var pendingRange: Pair<Long, Long>? = null
    private var pendingSegments: List<VideoSegment>? = null
    private var captionImportTarget: Pair<Int, VideoSegment>? = null
    private var captionImportProject: VideoEditProject? = null
    private var pendingSubtitleBytes: ByteArray? = null
    private var pendingSubtitleOriginals: Set<Uri>? = null
    private var project: VideoEditProject? = null
    private var selectedClip = 0
    private lateinit var timeline: VideoFilmstripView
    private lateinit var timelineScroll: HorizontalScrollView
    private lateinit var timecode: TextView
    private lateinit var playControl: ImageButton
    private var playheadUs=0L
    private var workspaceActive=false
    private val workspaceTick=object:Runnable{override fun run(){if(!workspaceActive)return;updateWorkspace();previewHandler.postDelayed(this,100)}}
    private lateinit var clipStatus: TextView
    private var exporter: VideoCompositionExporter? = null
    private data class PendingAudio(val clips:List<VideoSegment>,val sources:Map<String,Uri>,val durations:Map<String,Long>,val soundtrack:VideoSoundtrack?)
    private var pendingAudio:PendingAudio?=null
    private var audioExporter:VideoAudioExporter?=null
    private val exportCancellation = CancellationSignal()
    private val analysisCancellation = AtomicBoolean(false)
    private val previewHandler = Handler(Looper.getMainLooper())
    private val stopPreview = object : Runnable {
        override fun run() {
            if (!::video.isInitialized || !video.isPlaying) return
            if (video.currentPosition.toLong() >= end.progress * 1000L) video.pause()
            else previewHandler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accessMode = runCatching { EditorAccessMode.fromExtra(intent.getStringExtra(EditorAccessMode.EXTRA)) }.getOrElse { finish(); return }
        val restoreMode=savedInstanceState?.getString(EditorAccessMode.EXTRA)
        val sameMode=restoreMode==accessMode.name||(restoreMode==null&&accessMode==EditorAccessMode.ACCOUNT)
        recoveryBytes = if(sameMode)savedInstanceState?.getByteArray("multiSourceProject")else null
        source = Uri.parse(intent.getStringExtra("uri") ?: run { finish(); return })
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= 26) Color.WHITE else Color.BLACK
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        fun label(value: String, size: Float = 15f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.BLACK); setPadding(0, 8, 0, 8)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(Button(this).apply { text = "‹"; contentDescription = "Close video editor"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(label("Video editor", 16f).apply{typeface=android.graphics.Typeface.create("sans-serif-medium",0)}, LinearLayout.LayoutParams(0, -2, 1f))
        fun icon(parent:LinearLayout,label:String,kind:String,action:()->Unit):ImageButton {
            val b=ImageButton(this).apply{contentDescription=label;setImageDrawable(app.honorable.editor.EditorChrome.ToolIcon(kind));setPadding(dp(12),dp(12),dp(12),dp(12));setBackgroundColor(Color.WHITE);setOnClickListener{action()}}
            parent.addView(b,LinearLayout.LayoutParams(dp(44),dp(48)));return b
        }
        icon(header,"Undo video edit","Undo"){if(editorIdle()&&project?.undo()==true){selectedClip=selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex);showClip()}}
        icon(header,"Redo video edit","Redo"){if(editorIdle()&&project?.redo()==true){selectedClip=selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex);showClip()}}
        val exportButton = Button(this).apply { text = "Export"; contentDescription = "Export video as a new copy"; setOnClickListener { saveCopy() } }
        header.addView(exportButton, LinearLayout.LayoutParams(dp(88), dp(48)))
        root.addView(header)
        fun divider() = View(this).apply { setBackgroundColor(Color.rgb(225,225,225)) }
        root.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)))

        val previewFrame = FrameLayout(this)
        video = VideoView(this).apply { setVideoURI(source); setBackgroundColor(Color.rgb(250,250,250)) }
        previewFrame.addView(video, FrameLayout.LayoutParams(-1, -1))
        projectPreviewView = PlayerView(this).apply { visibility = View.GONE; useController = true }
        previewFrame.addView(projectPreviewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        // Preview and timeline stay visible; only the chosen tool group occupies the tray.
        val transport = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        timecode=label("00:00 / 00:00",11f).apply{typeface=android.graphics.Typeface.MONOSPACE}
        transport.addView(timecode,LinearLayout.LayoutParams(0,dp(48),1f))
        icon(transport,"Back one second","Previous"){seekTimeline((playheadUs-1_000_000).coerceAtLeast(0))}
        playControl=icon(transport,"Play project","Play"){toggleProjectPlayback()}
        icon(transport,"Forward one second","Next"){seekTimeline(playheadUs+1_000_000)}
        root.addView(transport)
        clipStatus=label("Clip 1",11f).apply{maxLines=1}
        val compactWorkspace=resources.configuration.screenHeightDp<760
        val timelineHeight=if(compactWorkspace)104 else 138
        timeline=VideoFilmstripView(this).apply{onSeek={seekTimeline(it)}}
        timelineScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;contentDescription="Video clip timeline";addView(timeline,FrameLayout.LayoutParams(-2,dp(timelineHeight)))}
        root.addView(timelineScroll,LinearLayout.LayoutParams(-1,dp(timelineHeight)))
        status = label("Loading video...", 11f).apply { maxLines = 1; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        root.addView(status)
        root.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)))
        val tray = FrameLayout(this)
        root.addView(tray, LinearLayout.LayoutParams(-1, dp(if(compactWorkspace)96 else 160)))
        val groups = linkedMapOf<String, LinearLayout>()
        fun group(name: String): LinearLayout {
            val tools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            groups[name] = tools
            tray.addView(ScrollView(this).apply {
                visibility = View.GONE; isFillViewport = true; addView(tools)
            }, FrameLayout.LayoutParams(-1,-1))
            return tools
        }
        val controls = group("Edit")
        val pictureTools=group("Picture")
        val speedTools=group("Speed")
        speedTools.addView(label("Playback speed",14f))
        val speeds=LinearLayout(this)
        listOf(.5f,1f,1.5f,2f).forEach{rate->speeds.addView(Button(this).apply{text="${rate}x";setOnClickListener{if(editorIdle()){project?.setSpeed(selectedClip,rate);showClip();status.text="Playback speed ${rate}x"}}},LinearLayout.LayoutParams(0,dp(48),1f))};speedTools.addView(speeds)
        val audioTools = group("Audio")
        val creativeTools = group("Titles")
        val projectTools = group("Project")
        val assistTools = group("Assist")
        start = SeekBar(this)
        end = SeekBar(this)
        controls.addView(label("In point · source seconds",11f)); controls.addView(start,LinearLayout.LayoutParams(-1,dp(36)))
        controls.addView(label("Out point · source seconds",11f)); controls.addView(end,LinearLayout.LayoutParams(-1,dp(36)))
        pictureTools.addView(Button(this).apply{text="Open inspector";setOnClickListener{openInspector()}})
        pictureTools.addView(Button(this).apply{text="Color controls";setOnClickListener{editColor()}})
        pictureTools.addView(Button(this).apply{text="White balance";setOnClickListener{editColor(true)}})
        pictureTools.addView(Button(this).apply{text="Rotate / mirror";setOnClickListener{transformVideo()}})
        projectTools.addView(Button(this).apply{text="Output format";setOnClickListener{outputFormat()}})
        projectTools.addView(Button(this).apply{text="Apply clip color to all";setOnClickListener{copyGradeToAll()}})
        projectTools.addView(Button(this).apply { text = "+ Add video"; setOnClickListener { importVideo() } })
        assistTools.addView(Button(this).apply { text = "AI rough cut"; isEnabled=accessMode.allowsOnDeviceAi; setOnClickListener { roughCutOptions() } })
        assistTools.addView(Button(this).apply { text = "AI video looks"; isEnabled=accessMode.allowsOnDeviceAi; setOnClickListener { videoLookOptions() } })
        assistTools.addView(Button(this).apply { text = "Cancel analysis"; setOnClickListener { if(analyzing){analysisCancellation.set(true);status.text="Cancelling analysis..."} } })
        assistTools.addView(Button(this).apply { text = "Suggest scene"; isEnabled=accessMode.allowsOnDeviceAi; setOnClickListener { suggestScene() } })
        assistTools.addView(label("Experimental · on-device sampled-frame labels. Preview every suggestion.", 12f))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(Button(this).apply {
            text = "Preview clip"
            setOnClickListener {
                if (saving || draftBusy || importing || pendingRange != null || durationMs <= 0 || !sourceReady) return@setOnClickListener
                val clip = project?.segments?.getOrNull(selectedClip) ?: return@setOnClickListener
                runCatching {
                    val trimmed = clip.copy(startMs = maxOf(start.progress*1000L,clip.startMs), endMs = minOf(end.progress*1000L,clip.endMs))
                    previewComposition(listOf(trimmed), selectedClip)
                }.onFailure { status.text = "Choose a valid clip range to preview." }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(this).apply {
            text = "Preview project"
            setOnClickListener { previewProject() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        projectTools.addView(actions, LinearLayout.LayoutParams(-1, -2))
        val clipActions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun clipButton(title: String, action: () -> Unit) {
            clipActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving && !draftBusy && !importing && pendingRange == null) action() } },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        clipButton("Split") {
            if(!sourceReady && previewPlayer == null) return@clipButton
            runCatching { requireNotNull(project).split(selectedClip, splitPosition()) }
                .onSuccess { selectedClip++; showClip() }
                .onFailure { status.text = "Select the playing clip or seek inside this clip, then split." }
        }
        clipButton("Delete") {
            runCatching { requireNotNull(project).delete(selectedClip) }
                .onSuccess { selectedClip = selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex); showClip() }
                .onFailure { status.text = "Keep at least one clip." }
        }
        val extraActions = LinearLayout(this)
        extraActions.addView(Button(this).apply { text = "Apply trim"; setOnClickListener {
            if (saving || draftBusy || importing || pendingRange != null) return@setOnClickListener
            val clip = project?.segments?.getOrNull(selectedClip) ?: return@setOnClickListener
            runCatching { project?.trim(selectedClip, maxOf(start.progress * 1_000L, clip.startMs), minOf(end.progress * 1_000L, clip.endMs)) }
                .onSuccess { showClip() }.onFailure { status.text = "Select a valid range inside this clip." }
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        extraActions.addView(Button(this).apply { text = "Duplicate"; setOnClickListener {
            if (saving || draftBusy || importing || pendingRange != null) return@setOnClickListener
            runCatching { requireNotNull(project).duplicate(selectedClip) }
                .onSuccess { selectedClip++; showClip() }.onFailure { status.text = "Cannot duplicate this clip. Check the project clip limit." }
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        extraActions.addView(Button(this).apply { text = "Mute"; setOnClickListener {
            if (saving || draftBusy || importing || pendingRange != null) return@setOnClickListener
            project?.setVolume(selectedClip, 0f); showClip()
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(extraActions)
        controls.addView(Button(this).apply { text="Precise trim";setOnClickListener{preciseTrim()} })
        controls.addView(clipActions)
        val historyActions = LinearLayout(this)
        fun historyButton(title: String, action: () -> Unit) {
            historyActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving && !draftBusy && !importing && pendingRange == null) action() } },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        historyButton("Move left") { moveClip(-1) }
        historyButton("Move right") { moveClip(1) }
        historyButton("Undo") { if (project?.undo() == true) { selectedClip = selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex); showClip() } }
        historyButton("Redo") { if (project?.redo() == true) { selectedClip = selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex); showClip() } }
        historyButton("Reset") { project?.reset(); selectedClip = 0; showClip() }
        projectTools.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(historyActions) })
        for (i in 0 until historyActions.childCount) historyActions.getChildAt(i).layoutParams = LinearLayout.LayoutParams(dp(88), dp(48))
        val audioActions = LinearLayout(this)
        fun audioButton(title: String, action: () -> Unit) {
            audioActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving && !draftBusy && !importing && pendingRange == null) action() } },
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
        audioTools.addView(audioActions)
        audioTools.addView(Button(this).apply{text="Export audio copy";setOnClickListener{saveAudioCopy()}})
        audioTools.addView(Button(this).apply{text="Cancel audio export";setOnClickListener{audioExporter?.cancel()}})
        audioTools.addView(Button(this).apply{text="Project audio fade";setOnClickListener{toggleAudioFade()}})
        val creative = LinearLayout(this)
        creative.addView(Button(this).apply { text = "Title"; setOnClickListener { editTitle() } },LinearLayout.LayoutParams(0,-2,1f))
        creative.addView(Button(this).apply { text = "Video fade"; setOnClickListener {
            if(saving || draftBusy || importing || pendingRange != null) return@setOnClickListener
            val current = project ?: return@setOnClickListener
            val clip = current.segments[selectedClip]
            current.setFadeEdges(selectedClip,!clip.fadeEdges); showClip()
            status.text = if(clip.fadeEdges) "Video fade removed." else "Fade through black added. Preview the project to check it."
        } },LinearLayout.LayoutParams(0,-2,1f))
        creativeTools.addView(creative)
        creativeTools.addView(Button(this).apply{text="Title style";setOnClickListener{titleStyle()}})
        creativeTools.addView(Button(this).apply{text="Captions";setOnClickListener{captionOptions()}})
        val music = LinearLayout(this)
        fun musicButton(title: String, action: () -> Unit) {
            music.addView(Button(this).apply { text = title; setOnClickListener {
                if(!saving && !draftBusy && !importing && pendingRange == null) action()
            } },LinearLayout.LayoutParams(0,-2,1f))
        }
        musicButton("Add music") { importSoundtrack() }
        musicButton("Music volume") {
            val current = project ?: return@musicButton
            val track = current.soundtrack ?: run { status.text = "Add a soundtrack first."; return@musicButton }
            val levels = listOf(.25f,.5f,1f,0f)
            current.setSoundtrack(track.copy(volume=levels[(levels.indexOf(track.volume)+1)%levels.size]))
            showClip(); status.text = "Soundtrack volume ${(requireNotNull(current.soundtrack).volume*100).toInt()}%. Preview project to check the mix."
        }
        musicButton("Remove music") { project?.setSoundtrack(null);showClip();status.text = "Soundtrack removed. Undo restores it." }
        audioTools.addView(music)
        audioTools.addView(label("Soundtrack loops to the video length. Use Preview project to check the mix.",12f))
        creativeTools.addView(label("Titles appear throughout the clip. Video fades are up to 0.5s at each edge; audio is unchanged.",12f))
        val drafts = LinearLayout(this)
        val guestProject = accessMode == EditorAccessMode.GUEST_BASIC
        drafts.addView(Button(this).apply { text = if(guestProject)"Save project" else "Save draft"; isEnabled=guestProject||accessMode.allowsAccountDrafts; setOnClickListener { if(guestProject)guestProjectOperation(false)else draftOperation(false) } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        drafts.addView(Button(this).apply { text = if(guestProject)"Restore project" else "Restore draft"; isEnabled=guestProject||accessMode.allowsAccountDrafts; setOnClickListener { if(guestProject)guestProjectOperation(true)else draftOperation(true) } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        projectTools.addView(drafts)
        if(guestProject)projectTools.addView(Button(this).apply{text="Delete saved project";setOnClickListener{deleteGuestProject()}})
        projectTools.addView(label(if(guestProject)"One local guest save per original video, up to 20. Save replaces its earlier save. Reopen the same original in Files, then Restore project. Media must remain available; account drafts stay separate." else "Drafts stay on this device. Reopen this video to restore its saved edits.", 12f))
        val tabs = LinearLayout(this)
        val tabButtons = mutableListOf<Button>()
        fun selectTools(name: String) {
            groups.forEach { (key, tools) -> (tools.parent as View).visibility = if(key == name) View.VISIBLE else View.GONE }
            tabButtons.forEach { button ->
                val selected = button.text.toString() == name
                button.isSelected = selected
                button.setTextColor(Color.BLACK)
                button.background = GradientDrawable().apply {
                    setColor(if(selected) Color.rgb(241,241,241) else Color.WHITE)
                    cornerRadius = dp(14).toFloat()
                }
            }
        }
        fun findAction(parent:View,label:String):Button?{if(parent is Button&&parent.text.toString()==label)return parent;if(parent is ViewGroup)for(i in 0 until parent.childCount)findAction(parent.getChildAt(i),label)?.let{return it};return null}
        val split=findAction(controls,"Split")
        val titles=findAction(creativeTools,"Title")
        val options=listOf("Trim" to "Edit","Split" to "Edit","Speed" to "Speed","Color" to "Picture","Title" to "Titles","Music" to "Audio","AI" to "Assist","More" to "Project")
        options.forEach{(name,group)->val tab=Button(this).apply{text=name;contentDescription="$name tools";isEnabled=name!="AI"||accessMode.allowsOnDeviceAi;setOnClickListener{val collapse=isSelected&&tray.visibility==View.VISIBLE;selectTools(group);tray.visibility=if(collapse)View.GONE else View.VISIBLE;tabButtons.forEach{it.isSelected=!collapse&&it===this;it.setBackgroundColor(if(it.isSelected)Color.rgb(241,241,241)else Color.WHITE)};when(name){"Split"->split?.performClick();"Title"->titles?.performClick();"Color"->editColor()}}};tabButtons.add(tab);tabs.addView(tab,LinearLayout.LayoutParams(dp(68),dp(if(compactWorkspace)64 else 72)))}
        root.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(tabs)},root.indexOfChild(tray))
        styleEditorControls(root, previewFrame)
        tabButtons.forEach { app.honorable.editor.EditorChrome.style(it,true) }
        exportButton.setTextColor(Color.WHITE)
        exportButton.background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(8).toFloat() }
        selectTools("Edit")
        tray.visibility=View.GONE
        setContentView(root)
        app.honorable.editor.NativeWorkspaceInsets.install(this,root)
        video.setOnErrorListener { _, _, _ -> status.text = "Video unavailable. Check access to the selected source."; true }
        video.setOnPreparedListener { player ->
            sourceReady = true
            if(project != null) {
                video.seekTo(pendingSeekMs.coerceIn(0L, (durationMs.toLong()-1).coerceAtLeast(0L)).toInt())
                return@setOnPreparedListener
            }
            durationMs = player.duration.coerceAtLeast(0)
            if(durationMs <= 0) { status.text = "This video has no supported duration."; return@setOnPreparedListener }
            primaryDurationMs = durationMs.toLong()
            val current = VideoEditProject(primaryDurationMs)
            project = current
            showClip()
            val saved = recoveryBytes?.let { VideoDraftCodec.decode(it, primaryDurationMs) }
            if(saved != null) {
                draftBusy = true
                status.text = "Checking restored video sources..."
                draftWorker.execute {
                    val checked = runCatching { validateSavedMedia(saved) }
                    runOnUiThread {
                        draftBusy = false
                        if(isDestroyed || isFinishing || project !== current) return@runOnUiThread
                        recoveryBytes = null
                        checked.onSuccess { runCatching { applySavedDraft(current, saved, false) }
                            .onFailure { status.text = "Project recovery unavailable. The original video remains open." } }
                            .onFailure { status.text = "A restored video is unavailable or changed. Reopen its document and try the saved draft." }
                    }
                }
            } else {
                recoveryBytes = null
                // Preserve lifecycle compatibility with the earlier single-source editor.
                readSegments(if(sameMode)savedInstanceState else null, "project")?.let { runCatching { current.restore(it) } }
                selectedClip = (savedInstanceState?.getInt("selectedClip") ?: 0).coerceIn(0, current.segments.lastIndex)
                showClip()
                pendingSeekMs = savedInstanceState?.getLong("previewPositionMs") ?: intent.getLongExtra("timestampMs", 0L)
                video.seekTo(pendingSeekMs.coerceIn(0L, primaryDurationMs-1).toInt())
                syncSourcePlayhead()
            }
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) showRange() }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        }
        start.setOnSeekBarChangeListener(listener)
        end.setOnSeekBarChangeListener(listener)
        // A system save picker is intentionally cancelled across recreation; no frozen export is replayed.
    }

    private fun showRange() {
        status.text = "${start.progress}s - ${end.progress}s; ${(end.progress - start.progress).coerceAtLeast(0)}s selected" + if(project?.segments?.getOrNull(selectedClip)?.grade?.isIdentity==false)" · source view; Preview clip shows color" else ""
    }

    private fun showClip() {
        stopProjectPreview()
        val clips = project?.segments ?: return
        val clip = clips[selectedClip]
        syncClipControls(clips, clip)
        pendingSeekMs = clip.startMs
        if(videoSourceId != clip.sourceId) {
            video.pause(); previewHandler.removeCallbacks(stopPreview)
            sourceReady = false
            videoSourceId = clip.sourceId
            video.setVideoURI(sourceUri(clip.sourceId))
        } else video.seekTo(clip.startMs.toInt())
        showRange()
        playheadUs=VideoFilmstripScale.outputTime(clips,selectedClip,clip.startMs)
        renderTimeline()
        updateWorkspace()
    }

    private fun syncClipControls(clips: List<VideoSegment>, clip: VideoSegment) {
        durationMs = sourceDuration(clip.sourceId).toInt()
        val seconds = ((durationMs + 999L) / 1000).toInt().coerceAtLeast(1)
        start.max = seconds; end.max = seconds
        start.progress = (clip.startMs / 1000).toInt()
        end.progress = ((clip.endMs + 999) / 1000).toInt().coerceAtMost(end.max)
        clipStatus.text = "Clip ${selectedClip + 1}/${clips.size} · source ${sourceNumber(clip.sourceId)} · ${clip.speed}x · volume ${(clip.volume * 100).toInt()}% · ${project?.totalDurationMs?.div(1000)}s project${if(clip.title.isNotBlank()) " · title" else ""}${if(clip.fadeEdges) " · fade" else ""}"
        showRange()
    }

    private fun previewProject() {
        if (saving || analyzing || draftBusy || importing || pendingRange != null) return
        val clips = project?.segments ?: return
        previewComposition(clips, null)
    }

    private fun previewComposition(clips: List<VideoSegment>, selectedIndex: Int?) {
        stopProjectPreview()
        video.pause()
        previewHandler.removeCallbacks(stopPreview)
        runCatching {
            CompositionPlayer.Builder(this).build().also { player ->
                previewPlayer = player
                previewSegments = clips.toList(); previewSelectedIndex = selectedIndex
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        status.text = "Project preview unavailable: ${error.message ?: "Playback error"}"
                        stopProjectPreview()
                    }
                })
                projectPreviewView.player = player
                projectPreviewView.visibility = View.VISIBLE
                video.visibility = View.GONE
                player.setComposition(VideoCompositionExporter.composition(sourceUris(), clips, sourceDurations(), if(selectedIndex == null) project?.soundtrack else null))
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
        previewPlayer = null; previewSegments = null; previewSelectedIndex = null
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun styleEditorControls(view: View, player: View) {
        if (view === player) return // Media/player overlay colors are independent of UI chrome.
        if (view is TextView) view.setTextColor(Color.BLACK)
        if (view is Button) app.honorable.editor.EditorChrome.style(view)
        if (view is SeekBar) {
            view.progressTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
            view.thumbTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) styleEditorControls(view.getChildAt(i), player)
    }

    private fun renderTimeline() {
        val clips=project?.segments?:return
        val viewport=(resources.displayMetrics.widthPixels-dp(32)).toFloat().coerceAtLeast(1f)
        timeline.submit(clips,selectedClip,viewport,sourceUris());timeline.playhead(playheadUs)
    }
    private fun seekTimeline(timeUs:Long){
        if(!editorIdle())return
        val clips=project?.segments?:return
        val bounded=timeUs.coerceIn(0,clips.sumOf{it.durationUs}-1)
        val position=VideoTimeline.locate(clips,bounded)
        val player=previewPlayer
        if(player!=null&&previewSelectedIndex==null&&previewSegments==clips){player.seekTo(bounded/1000);selectedClip=position.clipIndex;syncClipControls(clips,clips[selectedClip]);renderTimeline()}
        else{selectedClip=position.clipIndex;showClip();pendingSeekMs=position.sourceTimeMs;if(sourceReady)video.seekTo(position.sourceTimeMs.toInt())}
        playheadUs=bounded;timeline.playhead(bounded);updateTimecode()
    }
    private fun toggleProjectPlayback(){
        if(!editorIdle())return
        previewPlayer?.let{if(it.isPlaying)it.pause()else it.play();updateWorkspace();return}
        val position=playheadUs;previewProject();previewPlayer?.seekTo(position/1000);updateWorkspace()
    }
    private fun syncSourcePlayhead() {
        val clips=project?.segments?:return
        playheadUs=VideoFilmstripScale.outputTime(clips,selectedClip,pendingSeekMs)
        timeline.playhead(playheadUs)
        updateTimecode()
    }
    private fun updateTimecode(){
        if(!::timecode.isInitialized)return
        fun stamp(us:Long):String {val seconds=us.coerceAtLeast(0)/1_000_000;return "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}.${us.coerceAtLeast(0)%1_000_000/100_000}"}
        val text="${stamp(playheadUs)} / ${stamp(project?.segments?.sumOf{it.durationUs}?:0)}"
        if(timecode.text.toString()!=text)timecode.text=text
    }
    private fun updateWorkspace(){
        val clips=project?.segments?:return
        val player=previewPlayer
        val playing=player?.isPlaying==true
        if(player!=null){
            val preview=previewSegments?:return
            val pos=VideoTimeline.locate(preview,player.currentPosition.coerceAtLeast(0)*1000)
            val index=(previewSelectedIndex?:pos.clipIndex).coerceIn(clips.indices)
            playheadUs=VideoFilmstripScale.outputTime(clips,index,pos.sourceTimeMs)
        }
        timeline.playhead(playheadUs);updateTimecode()
        playControl.contentDescription=if(playing)"Pause project"else "Play project"
        if(playControl.tag!=playing){playControl.tag=playing;playControl.setImageDrawable(app.honorable.editor.EditorChrome.ToolIcon(if(playing)"Pause"else "Play"))}
        if(playing)timelineScroll.smoothScrollTo((timeline.positionX()-timelineScroll.width/2).toInt().coerceAtLeast(0),0)
    }

    private fun suggestScene() {
        if(!allowOnDeviceAi())return
        if (analyzing || saving || draftBusy || importing || pendingRange != null || durationMs <= 0) return
        val selection = runCatching { VideoSceneSelection.capture(project?.segments ?: emptyList(), selectedClip,
            start.progress * 1_000L, end.progress * 1_000L) }.getOrElse {
            status.text = "Choose a valid range before suggesting a scene."; return
        }
        val selectedSource = sourceUri(selection.clips[selection.selectedIndex].sourceId)
        analysisCancellation.set(false)
        analyzing = true
        status.text = "Finding a scene on this device..."
        Thread {
            val result = runCatching {
                val retriever = MediaMetadataRetriever()
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                try {
                    retriever.setDataSource(this, selectedSource)
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
                if (isDestroyed || isFinishing) return@runOnUiThread
                if(analysisCancellation.get()){status.text="Analysis cancelled. Your project is unchanged.";return@runOnUiThread}
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

    private fun toggleAudioFade(){if(editorIdle()){val current=project?:return;current.setProjectAudioFade(!current.segments.first().audioFade);showClip();status.text="Project audio fade ${if(current.segments.first().audioFade)"on" else "off"}. Preview project to check the mix."}}
    private fun openInspector(){
        if(!editorIdle())return;val current=project?:return
        fun t(label:String,run:()->Unit)=app.honorable.editor.NativeInspectorPanel.Tool(label,run)
        app.honorable.editor.NativeInspectorPanel.show(this,"Video inspector","Clip ${selectedClip+1}/${current.segments.size} · ${current.totalDurationMs/1000f}s project",listOf(
            "Picture" to listOf(t("Precise trim"){preciseTrim()},t("Color controls"){editColor()},t("White balance"){editColor(true)},t("Rotate / mirror"){transformVideo()},t("Copy color to all"){copyGradeToAll()}),
            "Titles & audio" to listOf(t("Edit title"){editTitle()},t("Title style"){titleStyle()},t("Timed captions"){captionOptions()},t("Project audio fade"){toggleAudioFade()}),
            "Project" to (listOf(t("Output format"){outputFormat()},t("Preview project"){previewProject()}) + if(accessMode==EditorAccessMode.GUEST_BASIC)listOf(t("Save project"){guestProjectOperation(false)},t("Restore project"){guestProjectOperation(true)},t("Delete saved project"){deleteGuestProject()})else emptyList()),
            "On-device AI" to (if(accessMode.allowsOnDeviceAi)listOf(t("Video looks"){videoLookOptions()},t("Rough cut"){roughCutOptions()})else emptyList())
        ).filter{it.second.isNotEmpty()})
    }
    private fun editorIdle()=!saving&&!analyzing&&!draftBusy&&!importing&&pendingRange==null
    private fun preciseTrim() {
        if(!editorIdle()||isDestroyed||isFinishing)return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val originalDuration=sourceDuration(clip.sourceId);val mode=accessMode
        val accountOwner=if(mode==EditorAccessMode.ACCOUNT)runCatching{VideoDraftPersistence(this,source.toString())}.getOrElse{status.text="Account changed. Reopen this video before trimming.";return}else null
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply{text="Original-source seconds, up to 3 decimal places. This trims inside the current clip and is undoable. Codec output is not guaranteed frame-exact.";textSize=12f;setTextColor(Color.DKGRAY)})
        fun field(name:String,value:Long)=EditText(this).apply{hint=name;contentDescription=name;inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(VideoTrimRange.format(value));setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(16));panel.addView(this)}
        val from=field("Trim start in source seconds",clip.startMs);val to=field("Trim end in source seconds",clip.endMs)
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Precise trim").setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!editorIdle()||isDestroyed||isFinishing||accessMode!=mode||project!==current||selectedClip!=index||current.segments.getOrNull(index)!=clip||runCatching{accountOwner?.requireCurrentOwner()}.isFailure){status.text="Clip or session changed. Open Precise trim again.";dialog.dismiss();return@setOnClickListener}
            val range=runCatching{VideoTrimRange.parse(from.text.toString(),to.text.toString(),clip,originalDuration)}.getOrElse{to.error=it.message;return@setOnClickListener}
            runCatching{current.trim(index,range.startMs,range.endMs)}.onSuccess{showClip();status.text="Trim applied: ${VideoTrimRange.format(range.startMs)}–${VideoTrimRange.format(range.endMs)} source seconds. Undo restores the clip.";dialog.dismiss()}.onFailure{to.error="Trim could not be applied; existing edits are unchanged."}
        }};dialog.show()
    }
    private fun allowOnDeviceAi():Boolean {
        if(accessMode.allowsOnDeviceAi)return true
        status.text="On-device editing tools are unavailable in this editor session."
        return false
    }
    private fun outputFormat(){
        if(!editorIdle())return;val current=project?:return;val snapshot=current.segments
        VideoInspectorControls.format(this,snapshot.first().layout){ratio,crop->if(editorIdle()&&project===current&&current.segments==snapshot){current.setFormat(ratio,crop);showClip();previewProject()}}
    }
    private fun transformVideo(){
        if(!editorIdle())return;val current=project?:return;val index=selectedClip;val clip=current.segments[index]
        android.app.AlertDialog.Builder(this).setTitle("Video transform").setItems(arrayOf("Rotate 90° clockwise","Flip horizontally","Flip vertically","Reset transform")){_,i->
            if(editorIdle()&&project===current&&selectedClip==index&&current.segments[index]==clip){
                val l=clip.layout;current.setLayout(index,when(i){0->l.copy(rotation=(l.rotation+90)%360);1->l.copy(mirrorX=!l.mirrorX);2->l.copy(mirrorY=!l.mirrorY);else->l.copy(rotation=0,mirrorX=false,mirrorY=false)})
                showClip();previewComposition(listOf(current.segments[index]),index)
            }
        }.setNegativeButton("Cancel",null).show()
    }
    private fun titleStyle(){
        if(!editorIdle())return;val current=project?:return;val index=selectedClip;val clip=current.segments[index]
        VideoInspectorControls.title(this,clip.titleStyle){style->if(editorIdle()&&project===current&&selectedClip==index&&current.segments[index]==clip){current.setTitleStyle(index,style);showClip();previewComposition(listOf(current.segments[index]),index)}}
    }
    private fun copyGradeToAll(){
        if(!editorIdle())return;val current=project?:return;val snapshot=current.segments;val index=selectedClip
        android.app.AlertDialog.Builder(this).setTitle("Apply color to all clips?").setMessage("Copies this clip's exposure, contrast, saturation and white balance to ${snapshot.size} clips as one undoable edit.")
            .setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->if(editorIdle()&&project===current&&current.segments==snapshot&&selectedClip==index){current.applyGradeToAll(index);showClip();previewProject()}}.show()
    }

    private fun editColor(balanceOnly:Boolean=false) {
        if(saving||analyzing||draftBusy||importing||pendingRange!=null)return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        VideoGradeControls.show(this,clip.grade,balanceOnly){grade->
            if(project!==current||selectedClip!=index||current.segments.getOrNull(index)!=clip||saving||analyzing||draftBusy||importing||pendingRange!=null){status.text="Clip changed. Open color controls again.";return@show}
            current.setGrade(index,grade);showClip();previewComposition(listOf(current.segments[index]),index)
        }
    }

    private fun videoLookOptions() {
        if(!allowOnDeviceAi())return
        if(saving||analyzing||draftBusy||importing||pendingRange!=null||!sourceReady)return
        val names=arrayOf("Auto scene look","Cinematic","Soft portrait","Vivid","Monochrome")
        android.app.AlertDialog.Builder(this).setTitle("AI video look").setItems(names){_,which->suggestVideoLook(PhotoLook.values()[which])}.setNegativeButton("Cancel",null).show()
    }

    private fun suggestVideoLook(look:PhotoLook) {
        if(!allowOnDeviceAi())return
        if(saving||analyzing||draftBusy||importing||pendingRange!=null||!sourceReady)return
        val current=project?:return
        val selection=runCatching{VideoSceneSelection.capture(current.segments,selectedClip,start.progress*1000L,end.progress*1000L)}.getOrElse{status.text="Choose a valid clip range first.";return}
        val clip=selection.clips[selection.selectedIndex];val uri=sourceUri(clip.sourceId)
        analysisCancellation.set(false);analyzing=true
        video.pause();stopProjectPreview();status.text="Analyzing video color on this device..."
        Thread {
            var samplesRead=0
            val result=runCatching {
                val labeler=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);val retriever=MediaMetadataRetriever()
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20)
                try {
                    retriever.setDataSource(this,uri)
                    val samples=mutableListOf<PhotoLightStats>();val subjects=mutableSetOf<String>()
                    for(time in VideoAutoGrade.sampleTimes(selection.startMs,selection.endMs)) {
                        check(!analysisCancellation.get()&&System.nanoTime()<deadline){"Cancelled or timed out"}
                        val frame=if(Build.VERSION.SDK_INT>=27)retriever.getScaledFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST,384,384)
                            else retriever.getFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST)?.let{original->
                                android.graphics.Bitmap.createScaledBitmap(original,384,384,true).also{if(it!==original)original.recycle()}}
                        if(frame==null)continue
                        val task=try {
                            val proxy=android.graphics.Bitmap.createScaledBitmap(frame,64,64,true)
                            try {val pixels=IntArray(4096);proxy.getPixels(pixels,0,64,0,0,64,64);samples.add(PhotoLightStats.measure(pixels))}finally{if(proxy!==frame)proxy.recycle()}
                            labeler.process(InputImage.fromBitmap(frame,0))
                        }catch(error:Throwable){frame.recycle();throw error}
                        try {
                            val remaining=TimeUnit.NANOSECONDS.toMillis((deadline-System.nanoTime()).coerceAtLeast(1)).coerceIn(1,5000)
                            subjects.addAll(Tasks.await(task,remaining,TimeUnit.MILLISECONDS).filter{it.confidence>=.65f}.map{it.text})
                        }finally{if(task.isComplete)frame.recycle()else task.addOnCompleteListener{frame.recycle()}}
                    }
                    check(!analysisCancellation.get()&&System.nanoTime()<deadline){"Cancelled or timed out"}
                    samplesRead=samples.size
                    VideoAutoGrade.suggest(samples,subjects,look)
                }finally{labeler.close();retriever.release()}
            }
            runOnUiThread {
                analyzing=false
                if(isDestroyed||isFinishing)return@runOnUiThread
                if(analysisCancellation.get()){status.text="Analysis cancelled. Your color is unchanged.";return@runOnUiThread}
                fun matches()=project===current&&selection.stillMatches(current.segments,selectedClip,start.progress*1000L,end.progress*1000L)
                if(!matches()){status.text="Your clip changed. Analyze video color again.";return@runOnUiThread}
                result.onSuccess{grade->
                    android.app.AlertDialog.Builder(this).setTitle("Video look ready")
                        .setMessage("Based on $samplesRead sampled frames. Replaces this entire clip's color with exposure ${grade.exposure} EV, contrast ${(grade.contrast*100).toInt()}% and saturation ${(grade.saturation*100).toInt()}%. Preview before exporting.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Apply look"){_,_->
                            if(!matches()||saving||analyzing||draftBusy||importing||pendingRange!=null){status.text="Clip changed. Analyze video color again.";return@setPositiveButton}
                            current.setGrade(selection.selectedIndex,grade);showClip();previewComposition(listOf(current.segments[selectedClip]),selectedClip)
                        }.show()
                }.onFailure{status.text="Video look unavailable or timed out. Your color is unchanged."}
            }
        }.start()
    }

    private fun roughCutOptions() {
        if(!allowOnDeviceAi())return
        if(isDestroyed||isFinishing||analyzing||saving||draftBusy||importing||pendingRange!=null)return
        val clips=project?.segments?:return
        if(clips.size>VideoRoughCut.MAX_CLIPS){status.text="AI rough cut supports up to 8 timeline clips. Keep a smaller selection first.";return}
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        content.addView(TextView(this).apply{text="Create a shorter draft from the timeline. Local scene labels choose moments from four sampled frames per clip. This does not analyze speech or musical beats.";setTextColor(Color.DKGRAY)})
        val length=Spinner(this).apply{adapter=ArrayAdapter(this@HonorableVideoEditorActivity,android.R.layout.simple_spinner_dropdown_item,listOf("15 seconds","30 seconds","60 seconds"));setSelection(1);contentDescription="Rough cut duration"}
        val focus=Spinner(this).apply{adapter=ArrayAdapter(this@HonorableVideoEditorActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Balanced","People","Scenery"));contentDescription="Rough cut focus"}
        content.addView(length,LinearLayout.LayoutParams(-1,dp(48)));content.addView(focus,LinearLayout.LayoutParams(-1,dp(48)))
        android.app.AlertDialog.Builder(this).setTitle("AI-assisted rough cut").setView(content).setNegativeButton("Cancel",null)
            .setPositiveButton("Create draft"){_,_->createRoughCut(listOf(15_000L,30_000L,60_000L)[length.selectedItemPosition],RoughCutFocus.values()[focus.selectedItemPosition])}.show()
    }

    private fun createRoughCut(target:Long,focus:RoughCutFocus) {
        if(!allowOnDeviceAi())return
        if(isDestroyed||isFinishing||analyzing||saving||draftBusy||importing||pendingRange!=null||!sourceReady)return
        val current=project?:return
        val selection=runCatching{VideoSceneSelection.capture(current.segments,selectedClip,start.progress*1000L,end.progress*1000L)}.getOrElse{status.text="Choose a valid clip range first.";return}
        val track=current.soundtrack
        val effective=selection.clips.toMutableList().apply{set(selectedClip,get(selectedClip).copy(startMs=selection.startMs,endMs=selection.endMs))}.toList()
        if(effective.size>VideoRoughCut.MAX_CLIPS){status.text="AI rough cut supports up to 8 clips.";return}
        if(effective.sumOf{it.durationMs}<=target){status.text="The timeline is already shorter than this target. No edits were changed.";return}
        val uris=sourceUris()
        analysisCancellation.set(false);analyzing=true
        status.text="Choosing moments on this device..."
        Thread {
            val result=runCatching {
                val labeler=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60)
                try {
                    val centers=effective.mapIndexed{index,clip->
                        val retriever=MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(this,requireNotNull(uris[clip.sourceId]))
                            var bestScore=-1f;var bestTime=clip.startMs
                            for(time in VideoRoughCut.sampleTimes(clip)) {
                                check(!analysisCancellation.get() && System.nanoTime()<deadline){"Analysis cancelled or timed out"}
                                val frame=if(Build.VERSION.SDK_INT>=27)retriever.getScaledFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST,384,384)
                                    else retriever.getFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST)?.let{original->
                                        android.graphics.Bitmap.createScaledBitmap(original,384,384,true).also{if(it!==original)original.recycle()}}
                                if(frame==null)continue
                                val task=try{labeler.process(InputImage.fromBitmap(frame,0))}catch(error:Throwable){frame.recycle();throw error}
                                try {
                                    val secondsLeft=TimeUnit.NANOSECONDS.toMillis((deadline-System.nanoTime()).coerceAtLeast(1)).coerceIn(1,5_000)
                                    val labels=Tasks.await(task,secondsLeft,TimeUnit.MILLISECONDS)
                                    val score=VideoRoughCut.score(labels.map{it.text to it.confidence},focus)
                                    if(score>bestScore){bestScore=score;bestTime=time}
                                } finally {if(task.isComplete)frame.recycle()else task.addOnCompleteListener{frame.recycle()}}
                            }
                            require(bestScore>0){"No recognized subjects in one of the clips"}
                            runOnUiThread{if(!isDestroyed&&!isFinishing&&analyzing)status.text="Analyzed clip ${index+1}/${effective.size}"}
                            bestTime
                        }finally{retriever.release()}
                    }
                    check(!analysisCancellation.get() && System.nanoTime()<deadline)
                    VideoRoughCut.assemble(effective,centers,target)
                }finally{labeler.close()}
            }
            runOnUiThread {
                analyzing=false
                if(isDestroyed||isFinishing)return@runOnUiThread
                if(analysisCancellation.get()){status.text="Analysis cancelled. Your project is unchanged.";return@runOnUiThread}
                fun stillMatches()=project===current && current.soundtrack==track && selection.stillMatches(current.segments,selectedClip,start.progress*1000L,end.progress*1000L)
                if(!stillMatches()){status.text="Your edits changed. Create the rough cut again.";return@runOnUiThread}
                result.onSuccess{clips->
                    android.app.AlertDialog.Builder(this).setTitle("Rough cut ready")
                        .setMessage("${clips.size} clips, ${clips.sumOf{it.durationMs}/1000f} seconds. Replaces the timeline as one undoable edit. Sampled labels may miss important action; preview before exporting.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Apply draft"){_,_->
                            if(!stillMatches()||analyzing||saving||draftBusy||importing||pendingRange!=null){status.text="Project changed. Create the rough cut again.";return@setPositiveButton}
                            runCatching{current.applyDraft(clips,soundtrack=track)}.onSuccess{selectedClip=0;showClip();status.text="AI-assisted rough cut applied. Preview project; Undo restores your timeline."}
                                .onFailure{status.text="Could not apply this draft. Your project is unchanged."}
                        }.show()
                }.onFailure{status.text="Rough cut unavailable or timed out. No project edits were changed."}
            }
        }.start()
    }

    private fun saveAudioCopy() {
        if(!editorIdle() || durationMs<=0)return
        val current=project?:return
        val clip=current.segments.getOrNull(selectedClip)?:return
        val from=maxOf(start.progress*1000L,clip.startMs)
        val to=minOf(end.progress*1000L,clip.endMs)
        if(to<=from){status.text="End must be after start";return}
        current.trim(selectedClip,from,to)
        pendingAudio=PendingAudio(current.segments.toList(),sourceUris().toMap(),sourceDurations().toMap(),current.soundtrack)
        importing=true;video.pause();stopProjectPreview()
        start.isEnabled=false;end.isEnabled=false
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type="audio/mp4"
                putExtra(Intent.EXTRA_TITLE,"Honorable-audio-copy.m4a")
            },27)
        }catch(error:Exception){pendingAudio=null;importing=false;start.isEnabled=true;end.isEnabled=true;status.text="No app is available to save audio"}
    }
    private fun finishAudioCopy(resultCode:Int,data:Intent?) {
        val snapshot=pendingAudio;pendingAudio=null;importing=false
        start.isEnabled=true;end.isEnabled=true
        if(resultCode!=RESULT_OK||data?.data==null)return
        if(snapshot==null){status.text="Audio export was interrupted. Choose Export audio copy again.";return}
        if(isDestroyed||isFinishing)return
        saving=true;start.isEnabled=false;end.isEnabled=false;status.text="Saving audio copy..."
        val worker=VideoAudioExporter(this);audioExporter=worker
        runCatching {
            worker.export(snapshot.sources,requireNotNull(data.data),snapshot.clips,snapshot.durations,snapshot.soundtrack){result->
                saving=false;audioExporter=null
                if(!isDestroyed&&!isFinishing){
                    start.isEnabled=true;end.isEnabled=true
                    status.text=result.fold({"Audio copy saved. Original preserved. Check playback in Files."},{"Audio export stopped: ${it.message?:"Unknown error"}. Check Files for a partial copy."})
                }
            }
        }.onFailure{saving=false;audioExporter=null;start.isEnabled=true;end.isEnabled=true;status.text="Audio export could not start: ${it.message}"}
    }

    private fun saveCopy() {
        if (saving || analyzing || draftBusy || importing || pendingRange != null || durationMs <= 0) return
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
        if(requestCode == 27) { finishAudioCopy(resultCode,data); return }
        if(requestCode == 25) { finishCaptionImport(resultCode,data); return }
        if(requestCode == 26) { finishSubtitleExport(resultCode,data); return }
        if(requestCode == 24) { finishSoundtrackImport(resultCode,data); return }
        if(requestCode == 23) { finishImport(resultCode, data); return }
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
            exporter!!.export(sourceUris(), destination, clips, sourceDurations(), project?.soundtrack) { result ->
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

    private enum class GuestProjectAction { SAVE, RESTORE, DELETE, DELETE_ALL }
    private fun guestProjectOperation(restore:Boolean)=runGuestProjectAction(if(restore)GuestProjectAction.RESTORE else GuestProjectAction.SAVE)
    private fun deleteGuestProject() {
        if(accessMode!=EditorAccessMode.GUEST_BASIC||!editorIdle()||isDestroyed||isFinishing)return
        android.app.AlertDialog.Builder(this).setTitle("Delete saved guest project?")
            .setMessage("Delete only the local saved edits for this original video. Current edits, source media and account drafts stay unchanged.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete this save"){_,_->runGuestProjectAction(GuestProjectAction.DELETE)}
            .setNeutralButton("All guest saves"){_,_->android.app.AlertDialog.Builder(this).setTitle("Delete all saved guest projects?")
                .setMessage("Remove every local guest video save, including saves with unavailable originals. Source media, current edits and account drafts stay unchanged.")
                .setNegativeButton("Cancel",null).setPositiveButton("Delete all guest saves"){_,_->runGuestProjectAction(GuestProjectAction.DELETE_ALL)}.show()}.show()
    }
    private fun validateGuestMedia(primary:String,saved:VideoDraft) {
        fun hasRead(value:String)=contentResolver.persistedUriPermissions.any{it.uri==Uri.parse(value)&&it.isReadPermission}
        GuestVideoProjectMedia.validate(primary,saved,::hasRead,{inspectSource(Uri.parse(it))},{inspectSoundtrack(Uri.parse(it))}){value,duration->
            val uri=Uri.parse(value)
            contentResolver.openFileDescriptor(uri,"r").use{input->requireNotNull(input);require(android.system.Os.fstat(input.fileDescriptor).st_ino!=0L){"Subtitle original is unavailable"}}
            val bytes=contentResolver.openInputStream(uri)?.use{VideoSubtitleDocuments.readBounded(it)}?:error("Subtitle original is unavailable")
            VideoCaptions.parseSrt(bytes,duration)
        }
    }
    private fun runGuestProjectAction(action:GuestProjectAction) {
        if(accessMode!=EditorAccessMode.GUEST_BASIC||!editorIdle()||durationMs<=0||isDestroyed||isFinishing)return
        val current=project?:return;val primary=source.toString();val duration=primaryDurationMs
        val before=current.segments;val beforeMusic=current.soundtrack;val beforeIndex=selectedClip
        val snapshot=if(action==GuestProjectAction.SAVE)runCatching{currentDraft()}.getOrElse{status.text="Choose a valid clip range before saving the guest project.";return}else null
        val request=++guestProjectRequest
        fun requireCurrent(){check(request==guestProjectRequest&&!isDestroyed&&!isFinishing&&accessMode==EditorAccessMode.GUEST_BASIC&&source.toString()==primary){"Guest editor session changed"}}
        draftBusy=true;start.isEnabled=false;end.isEnabled=false;stopProjectPreview();video.pause();previewHandler.removeCallbacks(stopPreview)
        status.text=when(action){GuestProjectAction.SAVE->"Saving local guest project...";GuestProjectAction.RESTORE->"Checking saved guest project and original files...";else->"Deleting guest save..."}
        draftWorker.execute{
            val result=runCatching{
                requireCurrent()
                val directory=java.io.File(noBackupFilesDir,"guest-video-projects")
                val store=GuestVideoProjectStore(directory,primary)
                when(action){
                    GuestProjectAction.SAVE->{
                        val saved=requireNotNull(snapshot)
                        GuestVideoProjectMedia.withDurableAccess(GuestVideoProjectMedia.originals(primary,saved),
                            {value->contentResolver.persistedUriPermissions.any{it.uri==Uri.parse(value)&&it.isReadPermission}},
                            {value->contentResolver.takePersistableUriPermission(Uri.parse(value),Intent.FLAG_GRANT_READ_URI_PERMISSION)},
                            {value->contentResolver.releasePersistableUriPermission(Uri.parse(value),Intent.FLAG_GRANT_READ_URI_PERMISSION)},::requireCurrent){
                            validateGuestMedia(primary,saved);store.save(saved,::requireCurrent)
                        };null
                    }
                    GuestProjectAction.RESTORE->store.load(duration)?.also{validateGuestMedia(primary,it);requireCurrent()}
                    GuestProjectAction.DELETE->{store.delete(::requireCurrent);null}
                    GuestProjectAction.DELETE_ALL->{GuestVideoProjectStore.deleteAll(directory,::requireCurrent);null}
                }
            }
            runOnUiThread{
                if(runCatching{requireCurrent()}.isFailure)return@runOnUiThread
                draftBusy=false;start.isEnabled=true;end.isEnabled=true
                if(project!==current||primaryDurationMs!=duration||current.segments!=before||current.soundtrack!=beforeMusic||selectedClip!=beforeIndex){status.text="Video changed. Run the guest project action again.";return@runOnUiThread}
                val saved=result.getOrElse{status.text="Guest project unavailable: ${it.message?:"Check document access and storage"}. Current edits are unchanged.";return@runOnUiThread}
                if(action==GuestProjectAction.RESTORE&&saved!=null){
                    val applied=runCatching{
                        require(GuestVideoProjectMedia.originals(primary,saved).all{value->contentResolver.persistedUriPermissions.any{it.uri==Uri.parse(value)&&it.isReadPermission}}){"Original document access changed"}
                        applySavedDraft(current,saved,true)
                    }
                    status.text=if(applied.isSuccess)"Guest project restored. Preview before exporting. Undo restores prior clip edits." else "Guest project sources conflict or access changed. Current edits are preserved."
                }else status.text=when(action){GuestProjectAction.SAVE->"Guest project saved locally. Reopen this same original, then Restore project. Media is not copied.";GuestProjectAction.RESTORE->"No guest project is saved for this original video.";else->"Guest save deleted. Source media and account drafts are unchanged."}
            }
        }
    }

    private fun draftOperation(restore: Boolean) {
        if(!accessMode.allowsAccountDrafts){status.text="Sign in to save or restore account drafts. Export a copy to keep your guest edit.";return}
        if (saving || analyzing || draftBusy || importing || pendingRange != null || durationMs <= 0) return
        val current = project ?: return
        val sourceDuration = primaryDurationMs
        val snapshot = if (restore) null else runCatching { currentDraft() }
            .getOrElse { status.text = "Choose a valid range before saving a draft (up to 1,000 clips)."; return }
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
                    if (restore) storage.load(sourceDuration)?.also { validateSavedMedia(it) }
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
                if (project !== current || primaryDurationMs != sourceDuration) {
                    status.text = "Video changed. Try the draft action again."
                    return@runOnUiThread
                }
                val saved = result.getOrNull()
                if (restore && saved != null) {
                    val applied = runCatching { applySavedDraft(current, saved, true) }
                    if(applied.isFailure) { status.text = "Draft sources conflict with the current project. Current edits preserved."; return@runOnUiThread }
                    status.text = "Draft restored. Preview before saving a copy. Undo restores prior clip edits."
                } else status.text = if (restore) "No compatible draft is saved for this video and account."
                    else "Draft saved on this device. Your original is unchanged."
            }
        }
    }

    private fun splitPosition(): Long {
        val player = previewPlayer ?: return video.currentPosition.toLong()
        val clips = requireNotNull(previewSegments)
        val position = VideoTimeline.locate(clips,player.currentPosition.coerceAtLeast(0)*1000)
        require((previewSelectedIndex ?: position.clipIndex) == selectedClip) { "Select the playing clip" }
        return position.sourceTimeMs
    }
    private fun editTitle() {
        if(saving || draftBusy || importing || pendingRange != null) return
        val current = project ?: return
        val index = selectedClip; val clip = current.segments[index]
        val input = EditText(this).apply {
            setText(clip.title); setTextColor(Color.BLACK); setBackgroundColor(Color.WHITE)
            hint = "Title (leave blank to remove)"; maxLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(120))
        }
        val dialog = android.app.AlertDialog.Builder(this,android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Clip title").setView(input)
            .setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(project !== current || selectedClip != index || current.segments.getOrNull(index) != clip || saving || draftBusy || importing || pendingRange != null) { dialog.dismiss(); return@setOnClickListener }
            runCatching { current.setTitle(index,input.text.toString().trim()) }.onSuccess {
                dialog.dismiss();showClip();status.text = "Title updated. Preview the project before saving a copy."
            }.onFailure { input.error = "Use up to 120 characters and three lines; project title text is limited." }
        } }
        dialog.show()
    }
    private fun sourceDurations(): Map<String, Long> = mapOf("primary" to primaryDurationMs) + importedSources.mapValues { it.value.durationMs }

    private fun sourceUri(id: String): Uri = if(id == "primary") source else Uri.parse(requireNotNull(importedSources[id]).uri)
    private fun sourceDuration(id: String): Long = if(id == "primary") primaryDurationMs else requireNotNull(importedSources[id]).durationMs
    private fun sourceNumber(id: String): Int = if(id == "primary") 1 else importedSources.keys.indexOf(id) + 2
    private fun sourceUris(): Map<String, Uri> = mapOf("primary" to source) + importedSources.mapValues { Uri.parse(it.value.uri) } +
        (project?.soundtrackUris()?.mapIndexed { index, uri -> "__audio_$index" to Uri.parse(uri) }?.toMap() ?: emptyMap()) +
        (project?.captionSourceUris()?.mapIndexed { index, uri -> "__caption_$index" to Uri.parse(uri) }?.toMap() ?: emptyMap())

    private fun captionOptions() {
        if(!editorIdle()) return
        val current=project?:return;val index=selectedClip;val clip=current.segments[index]
        val choices=listOf("Add caption at playhead","Import SRT for this clip","Export project SRT","Clear this clip's captions") + clip.captions.map { "${VideoCaptions.formatTime(it.startMs)}  ${it.text.replace('\n',' ')}" }
        android.app.AlertDialog.Builder(this).setTitle("Timed captions · clip ${index+1}")
            .setItems(choices.toTypedArray()){_,choice->
                if(!editorIdle()||project!==current||selectedClip!=index||current.segments[index]!=clip)return@setItems
                when(choice){
                    0->editCaption(current,index,clip,null)
                    1->importCaptions(current,index,clip)
                    2->exportSubtitles()
                    3->android.app.AlertDialog.Builder(this).setTitle("Clear clip captions?").setMessage("Removes ${clip.captions.size} captions from this clip. Undo restores them.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Clear"){_,_->if(editorIdle()&&project===current&&selectedClip==index&&current.segments[index]==clip){current.setCaptions(index,emptyList());showClip();previewProject()}}.show()
                    else->editCaption(current,index,clip,choice-4)
                }
            }.setNegativeButton("Close",null).show()
    }

    private fun editCaption(current:VideoEditProject,index:Int,clip:VideoSegment,cueIndex:Int?) {
        val cue=cueIndex?.let{clip.captions[it]}
        val from=cue?.startMs ?: runCatching{splitPosition()}.getOrDefault(clip.startMs).coerceIn(clip.startMs,clip.endMs-1)
        val to=cue?.endMs ?: minOf(from+3000,clip.endMs)
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        body.addView(TextView(this).apply{text="Use original video timestamps in seconds. Captions follow this clip's trims and speed. Up to 200 cues per clip; plain text only.";setTextColor(Color.DKGRAY)})
        fun timeField(label:String,value:Long)=EditText(this).apply{hint=label;contentDescription=label;setText(String.format(java.util.Locale.ROOT,"%.3f",value/1000.0));inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL}.also{body.addView(it)}
        val a=timeField("Caption start, source seconds",from);val b=timeField("Caption end, source seconds",to)
        val text=EditText(this).apply{hint="Caption text";contentDescription="Caption text";setText(cue?.text.orEmpty());maxLines=3;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(240))}
        body.addView(text)
        val builder=android.app.AlertDialog.Builder(this).setTitle(if(cue==null)"Add caption" else "Edit caption").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Apply",null)
        if(cueIndex!=null)builder.setNeutralButton("Delete"){_,_->if(editorIdle()&&project===current&&selectedClip==index&&current.segments[index]==clip){current.setCaptions(index,clip.captions.filterIndexed{i,_->i!=cueIndex});showClip();previewProject()}}
        val dialog=builder.create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!editorIdle()||project!==current||selectedClip!=index||current.segments[index]!=clip){dialog.dismiss();return@setOnClickListener}
            runCatching{
                val next=VideoCaption(VideoCaptions.seconds(a.text.toString()),VideoCaptions.seconds(b.text.toString()),text.text.toString().trim())
                val cues=clip.captions.toMutableList().apply{if(cueIndex==null)add(next)else set(cueIndex,next)}.sortedBy{it.startMs}
                current.setCaptions(index,cues)
            }.onSuccess{dialog.dismiss();showClip();previewProject();status.text="Captions updated. Preview the project before exporting."}.onFailure{text.error=it.message?:"Check caption timing and text"}
        }}
        dialog.show()
    }

    private fun importCaptions(current:VideoEditProject,index:Int,clip:VideoSegment) {
        android.app.AlertDialog.Builder(this).setTitle("Import SRT")
            .setMessage("Replaces captions on this clip as one undoable edit. SRT times refer to the original video before trimming or speed changes. UTF-8, up to 128 KB / 200 non-overlapping cues.")
            .setNegativeButton("Cancel",null).setPositiveButton("Choose SRT"){_,_->
                if(!editorIdle()||project!==current||selectedClip!=index||current.segments[index]!=clip)return@setPositiveButton
                importing=true;captionImportTarget=index to clip;captionImportProject=current;video.pause();stopProjectPreview()
                try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},25)}
                catch(error:Exception){importing=false;captionImportTarget=null;captionImportProject=null;status.text="No subtitle document picker is available."}
            }.show()
    }

    private fun finishCaptionImport(resultCode:Int,data:Intent?) {
        val target=captionImportTarget;val current=captionImportProject;val uri=data?.data
        captionImportTarget=null;captionImportProject=null
        if(resultCode!=RESULT_OK||uri==null||target==null||current==null||isDestroyed||isFinishing){importing=false;return}
        val sourceDuration=sourceDuration(target.second.sourceId)
        status.text="Reading subtitles on this device..."
        draftWorker.execute{
            val result=runCatching{
                require(uri.scheme=="content")
                contentResolver.openFileDescriptor(uri,"r").use{input->requireNotNull(input);require(android.system.Os.fstat(input.fileDescriptor).st_ino!=0L){"Cannot protect this subtitle original"}}
                val bytes=contentResolver.openInputStream(uri)?.use{VideoSubtitleDocuments.readBounded(it)}?:error("Cannot read SRT")
                VideoCaptions.parseSrt(bytes,sourceDuration)
            }
            runOnUiThread{
                importing=false
                if(isDestroyed||isFinishing)return@runOnUiThread
                if(project!==current||selectedClip!=target.first||current.segments.getOrNull(target.first)!=target.second){status.text="Clip changed. Import subtitles again.";return@runOnUiThread}
                result.onSuccess{cues->runCatching{
                    VideoCaptionImportPermissions.commit(
                        hasReadGrant={contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission}},
                        takeReadGrant={contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)},
                        releaseReadGrant={contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)},
                        applyCaptions={current.setCaptions(target.first,cues,uri.toString())},
                    )
                }
                    .onSuccess{showClip();previewProject();status.text="Imported ${cues.size} captions. Preview before exporting."}
                    .onFailure{status.text="Could not keep subtitles: ${it.message?:"Check document access and project limits"}. Existing captions are unchanged."}}
                    .onFailure{status.text="SRT import failed: ${it.message?:"Unreadable file"}. Existing captions are unchanged."}
            }
        }
    }

    private fun exportSubtitles() {
        if(!editorIdle())return
        val current=project?:return
        val clip=current.segments.getOrNull(selectedClip)?:return
        val from=maxOf(start.progress*1000L,clip.startMs);val to=minOf(end.progress*1000L,clip.endMs)
        if(to<=from){status.text="Choose a valid clip range first.";return}
        val effective=current.segments.toMutableList().apply{set(selectedClip,clip.copy(startMs=from,endMs=to))}
        val bytes=runCatching{VideoCaptions.exportSrt(effective)}.getOrElse{status.text=it.message?:"Add captions first";return}
        current.trim(selectedClip,from,to);showClip()
        pendingSubtitleBytes=bytes;pendingSubtitleOriginals=sourceUris().values.toSet();importing=true
        try{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/x-subrip";putExtra(Intent.EXTRA_TITLE,"Honorable-captions-copy.srt")},26)}
        catch(error:Exception){importing=false;pendingSubtitleBytes=null;pendingSubtitleOriginals=null;status.text="No app is available to save subtitles."}
    }

    private fun finishSubtitleExport(resultCode:Int,data:Intent?) {
        val bytes=pendingSubtitleBytes;val originals=pendingSubtitleOriginals;val destination=data?.data
        pendingSubtitleBytes=null;pendingSubtitleOriginals=null
        if(resultCode!=RESULT_OK||bytes==null||originals==null||destination==null||isDestroyed||isFinishing){importing=false;return}
        status.text="Saving subtitle copy..."
        draftWorker.execute{
            val result=runCatching{VideoSubtitleDocuments.writeCopy(this,destination,bytes,originals)}
            runOnUiThread{importing=false;if(!isDestroyed&&!isFinishing)status.text=result.fold({"Subtitle copy saved. Timings match the edited project."},{"Subtitle export failed: ${it.message}"})}
        }
    }

    private fun currentDraft(): VideoDraft {
        val clips = requireNotNull(project).segments
        val clip = clips[selectedClip]
        return VideoDraft(primaryDurationMs, clips.toList(), selectedClip,
            (if(previewPlayer != null) runCatching { splitPosition() }.getOrDefault(clip.startMs) else if(sourceReady) video.currentPosition.toLong() else pendingSeekMs).coerceIn(0L, sourceDuration(clip.sourceId)-1),
            maxOf(start.progress*1000L, clip.startMs), minOf(end.progress*1000L, clip.endMs), importedSources.values.toList(), project?.soundtrack)
    }
    private fun inspectSource(uri: Uri): Long {
        require(uri.scheme == "content") { "Choose a video document" }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            require(duration in 1..Int.MAX_VALUE.toLong())
            duration
        } finally { retriever.release() }
    }
    private fun inspectSoundtrack(uri: Uri): Long {
        require(uri.scheme == "content")
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this,uri)
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            require(duration in 1..Int.MAX_VALUE.toLong()); duration
        } finally { retriever.release() }
    }
    private fun validateSavedMedia(saved: VideoDraft) {
        validateSources(saved.sources)
        saved.soundtrack?.let { require(inspectSoundtrack(Uri.parse(it.uri)) == it.durationMs) { "Soundtrack changed or unavailable" } }
    }
    private fun importSoundtrack() {
        if(project == null || saving || analyzing || draftBusy || importing || pendingRange != null) return
        importing = true;video.pause();stopProjectPreview()
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type="audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            },24)
        } catch(error: Exception) { importing=false;status.text="No audio document picker is available." }
    }
    private fun finishSoundtrackImport(resultCode: Int, data: Intent?) {
        val uri = data?.data
        if(resultCode != RESULT_OK || uri == null || isDestroyed || isFinishing) { importing=false;return }
        val current=project ?: run { importing=false;return }
        status.text="Checking soundtrack on this device..."
        draftWorker.execute {
            val result=runCatching {
                contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                require(contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission })
                VideoSoundtrack(uri.toString(),inspectSoundtrack(uri))
            }
            runOnUiThread {
                importing=false
                if(isDestroyed || isFinishing || project !== current) return@runOnUiThread
                result.onSuccess { track ->
                    runCatching { VideoTimeline.soundtrackRepeatCount(current.segments,track);current.setSoundtrack(track) }
                        .onSuccess { showClip();status.text="Soundtrack added at 25%. Preview project to check the mix." }
                        .onFailure { status.text="Choose longer audio; this project supports up to 1000 soundtrack repeats." }
                }
                    .onFailure { status.text="Choose readable audio from a provider that keeps document access. Nothing was added." }
            }
        }
    }

    private fun validateSources(sources: List<VideoSource>) {
        sources.forEach { require(inspectSource(Uri.parse(it.uri)) == it.durationMs) { "Imported source changed or unavailable" } }
    }
    private fun applySavedDraft(current: VideoEditProject, saved: VideoDraft, undoable: Boolean) {
        val merged = importedSources + saved.sources.associateBy { it.id }
        require(saved.sources.all { importedSources[it.id]?.let { old -> old == it } ?: true })
        require(merged.size <= 31 && merged.values.map { it.uri }.distinct().size == merged.size)
        val durations = merged.mapValues { it.value.durationMs }
        if(undoable) current.applyDraft(saved.segments, durations, saved.soundtrack) else current.restore(saved.segments, durations, saved.soundtrack)
        importedSources.putAll(merged)
        selectedClip = saved.selectedClip
        showClip()
        start.progress = (saved.trimStartMs/1000).toInt()
        end.progress = ((saved.trimEndMs+999)/1000).toInt().coerceAtMost(end.max)
        pendingSeekMs = saved.previewPositionMs
        video.seekTo(saved.previewPositionMs.toInt())
        syncSourcePlayhead()
    }
    private fun importVideo() {
        if(project == null || saving || analyzing || draftBusy || importing || pendingRange != null) return
        if(importedSources.size >= 31) { status.text = "A project supports up to 32 source videos."; return }
        importing = true
        video.pause(); stopProjectPreview(); previewHandler.removeCallbacks(stopPreview)
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "video/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, 23)
        } catch(error: Exception) { importing = false; status.text = "No document picker is available." }
    }
    private fun finishImport(resultCode: Int, data: Intent?) {
        val uri = data?.data
        if(resultCode != RESULT_OK || uri == null || isDestroyed || isFinishing) { importing = false; return }
        val current = project ?: run { importing = false; return }
        status.text = "Checking imported video on this device..."
        draftWorker.execute {
            val result = runCatching {
                require(uri.scheme == "content")
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                require(contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission })
                inspectSource(uri)
            }
            runOnUiThread {
                importing = false
                if(isDestroyed || isFinishing || project !== current) return@runOnUiThread
                result.onSuccess { duration ->
                    runCatching {
                        val existing = if(uri == source) "primary" else importedSources.values.firstOrNull { it.uri == uri.toString() }?.id
                        val id = existing ?: java.util.UUID.randomUUID().toString()
                        if(existing != null) require(sourceDuration(id) == duration) { "Source changed" }
                        val metadata = if(id != "primary") VideoSource(id, uri.toString(), duration) else null
                        current.appendSource(id, duration)
                        metadata?.let { importedSources[id] = it }
                        selectedClip = current.segments.lastIndex; showClip()
                        status.text = "Video added. Preview the project before saving a copy."
                    }.onFailure { status.text = "Video could not be added. Check source duration and project limits." }
                }.onFailure { status.text = "Choose a readable video from a provider that keeps document access. Nothing was added." }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EditorAccessMode.EXTRA,accessMode.name)
        if (::start.isInitialized) outState.putInt("startProgress", start.progress)
        if (::end.isInitialized) outState.putInt("endProgress", end.progress)
        if (::video.isInitialized) outState.putLong("previewPositionMs", video.currentPosition.toLong())
        outState.putInt("selectedClip", selectedClip)
        (recoveryBytes ?: runCatching { VideoDraftCodec.encode(currentDraft()) }.getOrNull())?.let { outState.putByteArray("multiSourceProject", it) }
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

    override fun onResume(){super.onResume();workspaceActive=true;previewHandler.post(workspaceTick)}
    override fun onPause() { workspaceActive=false;previewHandler.removeCallbacks(workspaceTick); if (::video.isInitialized) video.pause(); stopProjectPreview(); super.onPause() }
    override fun onDestroy() {
        guestProjectRequest++
        previewHandler.removeCallbacks(stopPreview)
        previewHandler.removeCallbacks(workspaceTick)
        if(::timeline.isInitialized)timeline.close()
        stopProjectPreview()
        analysisCancellation.set(true)
        draftWorker.shutdownNow()
        exportCancellation.cancel()
        exporter?.cancel()
        audioExporter?.cancel()
        super.onDestroy()
    }
}
