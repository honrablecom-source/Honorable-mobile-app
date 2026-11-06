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
    private var restoreReviewDialog:android.app.AlertDialog?=null
    private var closeDialog:android.app.AlertDialog?=null
    private var releaseEditorBack:(()->Unit)?=null
    private var accessMode = EditorAccessMode.ACCOUNT
    private data class VoiceoverTarget(val project:VideoEditProject,val clips:List<VideoSegment>,val soundtrack:VideoSoundtrack?,val selected:Int,val mode:EditorAccessMode,val owner:String)
    private var voiceoverTarget:VoiceoverTarget?=null
    private var framePhotoCancellation:java.util.concurrent.atomic.AtomicBoolean?=null
    // Shared ownership for original-source audio analysis jobs and their dialogs.
    private var quietAudioCancellation:AtomicBoolean?=null
    private var quietAudioGeneration=0L
    private var versionShelf:VideoProjectVersionShelf?=null
    private var titleStyleShelf:VideoTitleStyleShelf?=null
    private var quietAudioDialog:android.app.AlertDialog?=null
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
    private var mediaImportTarget:VideoMediaImportTarget?=null
    private var importing = false
    private var sourceReady = false
    private var recoveryBytes: ByteArray? = null
    private var recoveryBlocked=false
    private var recoveryChecking=false
    private var recoveryRequest=0L
    private lateinit var recoveryPanel:LinearLayout
    private var recoveryDiscardDialog:android.app.AlertDialog?=null
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
        recoveryBytes = if(sameMode)savedInstanceState?.getByteArray("multiSourceProject")?.copyOf()else null
        source = Uri.parse(intent.getStringExtra("uri") ?: run { finish(); return })
        if(savedInstanceState==null&&intent.hasExtra(PhotoVideoProjectsActivity.PROJECT_EXTRA)) {
            val owned=runCatching {
                require(accessMode==EditorAccessMode.GUEST_BASIC)
                val id=requireNotNull(intent.getStringExtra(PhotoVideoProjectsActivity.PROJECT_EXTRA))
                val store=PhotoVideoProjectStore(this)
                require(PhotoVideoProjectCodec.validId(id)&&source==store.sourceUri(id,0)&&store.ownsReadableSource(source))
                requireNotNull(intent.getByteArrayExtra(PhotoVideoProjectsActivity.DRAFT_EXTRA)).also{require(it.size<=GuestVideoProjectCodec.MAX_BYTES)}
            }.getOrElse{finish();return}
            recoveryBytes=owned.copyOf()
        }
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
        header.addView(Button(this).apply { text = "‹"; contentDescription = "Close video editor"; setOnClickListener { requestClose() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
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
        recoveryPanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;visibility=View.GONE
            addView(label("Session recovery is kept. Retry after restoring document access, or start from the original.",12f))
            val actions=LinearLayout(this@HonorableVideoEditorActivity)
            actions.addView(Button(this@HonorableVideoEditorActivity).apply{text="Retry recovery";setOnClickListener{retryRecovery()}},LinearLayout.LayoutParams(0,dp(48),1f))
            actions.addView(Button(this@HonorableVideoEditorActivity).apply{text="Start from original";setOnClickListener{confirmDiscardRecovery()}},LinearLayout.LayoutParams(0,dp(48),1f))
            addView(actions)
        }
        root.addView(recoveryPanel)
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
        pictureTools.addView(Button(this).apply{text="Motion pan and zoom";setOnClickListener{editMotion()}})
        pictureTools.addView(Button(this).apply{text="Color controls";setOnClickListener{editColor()}})
        pictureTools.addView(Button(this).apply{text="Saved color looks";setOnClickListener{savedColorLooks()}})
        pictureTools.addView(Button(this).apply{text="White balance";setOnClickListener{editColor(true)}})
        pictureTools.addView(Button(this).apply{text="Rotate / mirror";setOnClickListener{transformVideo()}})
        projectTools.addView(Button(this).apply{text="Output format";setOnClickListener{outputFormat()}})
        projectTools.addView(Button(this).apply{text="Apply clip color to all";setOnClickListener{copyGradeToAll()}})
        projectTools.addView(Button(this).apply { text = "+ Add video"; setOnClickListener { importVideo() } })
        projectTools.addView(Button(this).apply { text = "Build a montage"; setOnClickListener { montageOptions() } })
        projectTools.addView(Button(this).apply { text = "Batch editing"; setOnClickListener { batchOptions() } })
        projectTools.addView(Button(this).apply{text="Moment notes";setOnClickListener{momentNotes()}})
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
        historyButton("Reset") { confirmProjectReset() }
        projectTools.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(historyActions) })
        for (i in 0 until historyActions.childCount) historyActions.getChildAt(i).layoutParams = LinearLayout.LayoutParams(dp(88), dp(48))
        val audioActions = LinearLayout(this)
        fun audioButton(title: String, action: () -> Unit) {
            audioActions.addView(Button(this).apply { text = title; setOnClickListener { if (!saving && !draftBusy && !importing && pendingRange == null) action() } },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        audioButton("Speed") { chooseClipPlayback(speed=true) }
        audioButton("Volume") { chooseClipPlayback(speed=false) }
        audioTools.addView(audioActions)
        audioTools.addView(Button(this).apply{text="Record voiceover";setOnClickListener{recordVoiceover()}})
        audioTools.addView(Button(this).apply{text="Remove quiet pauses";setOnClickListener{quietPauseOptions()}})
        audioTools.addView(Button(this).apply{text="Inspect source audio levels";setOnClickListener{sourceAudioLevels()}})
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
        creativeTools.addView(Button(this).apply{text="Saved title styles";setOnClickListener{savedTitleStyles()}})
        creativeTools.addView(Button(this).apply{text="Captions";setOnClickListener{captionOptions()}})
        creativeTools.addView(Button(this).apply { text = "Script to captions"; setOnClickListener { scriptCaptionOptions() } })
        val music = LinearLayout(this)
        fun musicButton(title: String, action: () -> Unit) {
            music.addView(Button(this).apply { text = title; setOnClickListener {
                if(!saving && !draftBusy && !importing && pendingRange == null) action()
            } },LinearLayout.LayoutParams(0,-2,1f))
        }
        musicButton("Add music") { importSoundtrack() }
        musicButton("Soundtrack volume") { chooseSoundtrackAudio(remove=false) }
        musicButton("Remove soundtrack") { chooseSoundtrackAudio(remove=true) }
        audioTools.addView(music)
        audioTools.addView(Button(this).apply{text="Choose soundtrack excerpt";setOnClickListener{soundtrackExcerpt()}})
        audioTools.addView(label("Music initially loops; voiceovers initially play once. Choose soundtrack excerpt to change the range or looping. Preview project to check the mix.",12f))
        creativeTools.addView(label("Titles appear throughout the clip. Video fades are up to 0.5s at each edge; audio is unchanged.",12f))
        val drafts = LinearLayout(this)
        val guestProject = accessMode == EditorAccessMode.GUEST_BASIC
        drafts.addView(Button(this).apply { text = if(guestProject)"Save project" else "Save draft"; isEnabled=guestProject||accessMode.allowsAccountDrafts; setOnClickListener { if(guestProject)guestProjectOperation(false)else draftOperation(false) } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        drafts.addView(Button(this).apply { text = if(guestProject)"Restore project" else "Restore draft"; isEnabled=guestProject||accessMode.allowsAccountDrafts; setOnClickListener { if(guestProject)guestProjectOperation(true)else draftOperation(true) } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        projectTools.addView(drafts)
        projectTools.addView(Button(this).apply{text="Saved video versions";setOnClickListener{savedVideoVersions()}})
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
        runCatching { VideoEditorBack.install(this){requestClose()} }
            .onSuccess { releaseEditorBack=it }
            .onFailure { status.text="System Back protection is unavailable. Use the editor's Close button to leave." }
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
            if(recoveryBytes!=null){
                draftBusy=true
                runCatching{showClip()}
                retryRecovery()
                return@setOnPreparedListener
            }
            // Preserve lifecycle compatibility with the earlier single-source editor.
            readSegments(if(sameMode)savedInstanceState else null, "project")?.let { runCatching { current.restore(it) } }
            selectedClip = (savedInstanceState?.getInt("selectedClip") ?: 0).coerceIn(0, current.segments.lastIndex)
            showClip()
            pendingSeekMs = savedInstanceState?.getLong("previewPositionMs") ?: intent.getLongExtra("timestampMs", 0L)
            video.seekTo(pendingSeekMs.coerceIn(0L, primaryDurationMs-1).toInt())
            syncSourcePlayhead()
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
            "Picture" to listOf(t("Precise trim"){preciseTrim()},t("Slip source window"){slipSourceWindow()},t("Roll cut with next clip"){rollCut()},t("Color controls"){editColor()},t("Motion pan and zoom"){editMotion()},t("White balance"){editColor(true)},t("Rotate / mirror"){transformVideo()},t("Copy color to all"){copyGradeToAll()},t("Saved color looks"){savedColorLooks()}),
            "Titles & audio" to listOf(t("Edit title"){editTitle()},t("Title style"){titleStyle()},t("Saved title styles"){savedTitleStyles()},t("Timed captions"){captionOptions()},t("Find caption text"){findCaptionText()},t("Copy caption text"){copyCaptionText()},t("Script to captions"){scriptCaptionOptions()},t("Project audio fade"){toggleAudioFade()},t("Remove quiet pauses"){quietPauseOptions()},t("Inspect source audio levels"){sourceAudioLevels()},t("Record voiceover"){recordVoiceover()},t("Soundtrack excerpt"){soundtrackExcerpt()}),
            "Project" to (listOf(t("Build a montage"){montageOptions()},t("Timeline report"){timelineReport()},t("Saved video versions"){savedVideoVersions()},t("Clip chapter list"){chapterList()},t("Open source frame as photo"){sourceFramePhoto()},t("Create visual storyboard"){visualStoryboard()},t("Moment notes"){momentNotes()},t("Highlights from notes"){momentHighlights()},t("Output format"){outputFormat()},t("Preview project"){previewProject()}) + if(accessMode==EditorAccessMode.GUEST_BASIC)listOf(t("Save project"){guestProjectOperation(false)},t("Restore project"){guestProjectOperation(true)},t("Delete saved project"){deleteGuestProject()})else emptyList()),
            "Batch editing" to VideoBatchTool.values().map{kind->t(kind.title){batchEdit(kind)}},
            "On-device AI" to (if(accessMode.allowsOnDeviceAi)listOf(t("Video looks"){videoLookOptions()},t("Rough cut"){roughCutOptions()})else emptyList())
        ).filter{it.second.isNotEmpty()})
    }
    private fun chooseSoundtrackAudio(remove:Boolean){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val track=current.soundtrack?:run{status.text="Add a soundtrack first.";return}
        val clips=current.segments.toList();val mode=accessMode
        val owner=videoLookNamespace()?:run{status.text="Session changed. Reopen the video.";return}
        fun valid()=editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&current.soundtrack==track&&accessMode==mode&&videoLookNamespace()==owner
        fun applyVolume(value:Float?){
            if(!valid()){if(!isDestroyed&&!isFinishing)status.text="Video or soundtrack changed. Open soundtrack controls again.";return}
            if(value==track.volume)return
            runCatching{
                current.setSoundtrack(value?.let{track.copy(volume=it)})
                showClip()
                if(value==null)status.text="Soundtrack removed from this edit. Undo restores it."
                else previewComposition(current.segments,null)
            }.onFailure{status.text="Could not change the soundtrack. Check project limits and try again."}
        }
        if(remove){
            android.app.AlertDialog.Builder(this).setTitle("Remove soundtrack?")
                .setMessage("Removes the separate music or voiceover from this edit. Clip source audio and the original audio file are kept. Undo restores the soundtrack and its settings.")
                .setNegativeButton("Keep soundtrack",null).setPositiveButton("Remove"){_,_->applyVolume(null)}.show()
            return
        }
        val choices=mutableListOf(1f,.5f,.25f,0f)
        val labels=mutableListOf("100% · source level","50% · half level","25% · quarter level","0% · mute")
        if(track.volume !in choices){choices.add(track.volume);labels.add(String.format(java.util.Locale.ROOT,"Keep current volume (%.1f%%)",track.volume*100))}
        val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(8));setBackgroundColor(Color.WHITE)}
        title.addView(TextView(this).apply{text="Soundtrack volume";textSize=18f;setTextColor(Color.BLACK)})
        title.addView(TextView(this).apply{text="Changes separate music or voiceover only. Clip source audio is kept. Review the project preview to check the mix.";textSize=13f;setTextColor(Color.DKGRAY);setPadding(0,dp(8),0,0)})
        android.app.AlertDialog.Builder(this).setCustomTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(),choices.indexOf(track.volume)){dialog,which->applyVolume(choices[which]);dialog.dismiss()}
            .setNegativeButton("Cancel",null).show()
    }
    private fun confirmProjectReset(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList();val soundtrack=current.soundtrack
        val mode=accessMode;val owner=videoLookNamespace()?:run{status.text="Session changed. Reopen the video.";return}
        android.app.AlertDialog.Builder(this).setTitle("Reset video edits?")
            .setMessage("Returns to the full original first video at normal speed and source volume. Removes added clips, titles, captions, color, framing, motion, fades and the separate soundtrack from this edit. Original media and saved projects stay on your device. Undo restores the previous edit.")
            .setNegativeButton("Keep editing",null).setPositiveButton("Reset edits"){_,_->
                if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing||project!==current||current.segments!=clips||current.soundtrack!=soundtrack||accessMode!=mode||videoLookNamespace()!=owner){
                    if(!isDestroyed&&!isFinishing)status.text="Video or session changed. Open Reset again."
                    return@setPositiveButton
                }
                runCatching{current.reset();selectedClip=0;showClip()}
                    .onSuccess{status.text="Video edits reset. Undo restores the previous edit."}
                    .onFailure{status.text="Could not reset this edit. Try reopening the video."}
            }.show()
    }
    private fun chooseClipPlayback(speed:Boolean){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip;val clip=clips.getOrNull(index)?:return
        val mode=accessMode;val owner=videoLookNamespace()?:run{status.text="Session changed. Reopen the video.";return}
        val before=if(speed)clip.speed else clip.volume
        val choices=if(speed)mutableListOf(.5f,1f,1.5f,2f)else mutableListOf(1f,.5f,0f)
        val labels=if(speed)mutableListOf("0.5× · half speed","1× · normal speed","1.5× · faster","2× · double speed")else mutableListOf("100% · source level","50% · half level","0% · mute")
        if(before !in choices){choices.add(before);labels.add(String.format(java.util.Locale.ROOT,"Keep current volume (%.1f%%)",before*100))}
        val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(8));setBackgroundColor(Color.WHITE)}
        title.addView(TextView(this).apply{text="Clip ${index+1} ${if(speed)"speed"else "source volume"}";textSize=18f;setTextColor(Color.BLACK)})
        title.addView(TextView(this).apply{text=if(speed)"Changes this clip's playback duration. Captions follow edited timing. Review the rendered preview after choosing."else "Changes this clip's source audio if present. Separate soundtrack volume is kept. Review the preview after choosing.";textSize=13f;setTextColor(Color.DKGRAY);setPadding(0,dp(8),0,0)})
        fun unchanged()=editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&selectedClip==index&&accessMode==mode&&videoLookNamespace()==owner
        android.app.AlertDialog.Builder(this).setCustomTitle(title).setSingleChoiceItems(labels.toTypedArray(),choices.indexOf(before)){dialog,which->
            if(!unchanged()){if(!isDestroyed&&!isFinishing)status.text="Video or session changed. Open these clip controls again.";dialog.dismiss();return@setSingleChoiceItems}
            val value=choices[which]
            if(value==before){dialog.dismiss();return@setSingleChoiceItems}
            runCatching{
                if(speed)current.setSpeed(index,value)else current.setVolume(index,value)
                showClip();previewComposition(listOf(current.segments[index]),index)
            }.onFailure{status.text="Could not change this clip. Check the project limits and try again."}
            dialog.dismiss()
        }.setNegativeButton("Cancel",null).show()
    }
    private fun editMotion(){
        if(!editorIdle()||isDestroyed||isFinishing)return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        VideoMotionControls.show(this,clip){motion->
            require(!isDestroyed&&!isFinishing&&editorIdle()&&project===current&&selectedClip==index&&current.segments.getOrNull(index)==clip){"Video changed. Open motion again."}
            current.setMotion(index,motion);showClip();previewComposition(listOf(current.segments[index]),index)
        }
    }
    private fun editorIdle()=!saving&&!analyzing&&!draftBusy&&!importing&&pendingRange==null

    private fun timelineReport(){
        if(!editorIdle())return
        val clips=project?.segments?:return
        runCatching{VideoRefinementTools.report(clips).text()}.onSuccess{text->
            android.app.AlertDialog.Builder(this).setTitle("Timeline report").setMessage(text).setPositiveButton("Done",null).show()
        }.onFailure{status.text=it.message?:"Timeline report is unavailable."}
    }

    private fun savedVideoVersions() {
        if(!workspaceActive||!editorIdle()||isDestroyed||isFinishing||recoveryBytes!=null)return
        val current=project?:return;val captured=closeState();val mode=accessMode;val primary=source.toString()
        val draft=runCatching{VideoProjectVersion.snapshot(currentDraft())}.getOrElse{status.text="Choose a valid range before using saved versions.";return}
        versionShelf?.close()
        runCatching{video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        lateinit var shelf:VideoProjectVersionShelf
        shelf=VideoProjectVersionShelf(this,mode,primary,draft,
            isCurrent={workspaceActive&&!isDestroyed&&!isFinishing&&editorIdle()&&accessMode==mode&&captured.stillMatches(closeState())},
            busy={value->if(versionShelf===shelf)draftBusy=value},message={status.text=it},
            withSaveAccess={saved,requireCurrent,commit->
                GuestVideoProjectMedia.originals(primary,saved).forEach { value ->
                    requireCurrent()
                    if(!hasVersionRead(value))contentResolver.takePersistableUriPermission(Uri.parse(value),Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    require(hasVersionRead(value)){"An original needs lasting document access. Reopen it through Files."}
                }
                // Read grants are shared by other projects; cancellation must not revoke their access.
                requireCurrent();validateVersionMedia(primary,saved);requireCurrent();commit()
            },
            validateOpen={saved,requireCurrent->requireCurrent();validateVersionMedia(primary,saved);requireCurrent()},
            apply={saved->
                runCatching{applySavedDraft(current,VideoVersionSources.reconcile(primary,importedSources,saved),true)}
                    .onSuccess{ready->status.text=if(ready)"Saved video version opened. Timeline Undo restores prior edits." else "Saved version opened; preview is unavailable. Timeline Undo restores prior edits."}
                    .onFailure{status.text="Saved version conflicts with the current project. Existing edits were kept."}
            })
        versionShelf=shelf;shelf.open()
    }

    private fun hasVersionRead(value:String):Boolean=VideoVersionReadPolicy.existingAccess(value,hasDurableGuestRead(value),
        app.honorable.search.MediaCapabilityManager(this).current().canReadVideos)
    private fun validateVersionMedia(primary:String,saved:VideoDraft) {
        GuestVideoProjectMedia.validate(primary,saved,::hasVersionRead,{inspectSource(Uri.parse(it))},{inspectSoundtrack(Uri.parse(it))}){value,duration->
            val bytes=contentResolver.openInputStream(Uri.parse(value))?.use{VideoSubtitleDocuments.readBounded(it)}?:error("Subtitle original is unavailable")
            VideoCaptions.parseSrt(bytes,duration)
        }
    }

    private fun videoLookNamespace():String?=VideoPrivateOwner.namespace(this,accessMode)

    private fun savedColorLooks(){
        if(!editorIdle()||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return
        val music=current.soundtrack;val mode=accessMode
        val namespace=videoLookNamespace()?:run{status.text="Session changed. Reopen the video before using saved looks.";return}
        val store=VideoColorLookStore(java.io.File(noBackupFilesDir,"video-color-looks"),namespace)
        fun valid()=editorIdle()&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&current.soundtrack==music&&selectedClip==index&&accessMode==mode&&videoLookNamespace()==namespace
        fun requireCurrent(){require(valid()){"Video or account changed. Open saved looks again."}}
        fun selectLook(delete:Boolean){
            val catalog=runCatching{requireCurrent();store.load().also{requireCurrent()}}.getOrElse{status.text=it.message?:"Saved video looks are unavailable.";return}
            if(catalog.looks.isEmpty()){status.text="No saved video color looks yet. Save this clip's color first.";return}
            android.app.AlertDialog.Builder(this).setTitle(if(delete)"Delete saved color look" else "Apply saved color look")
                .setItems(catalog.looks.map{it.name}.toTypedArray()){_,chosen->
                    if(!valid()){status.text="Video or account changed. Open saved looks again.";return@setItems}
                    val look=catalog.looks[chosen]
                    android.app.AlertDialog.Builder(this).setTitle(if(delete)"Delete ${look.name}?" else "Apply ${look.name}?")
                        .setMessage(if(delete)"Removes this local color recipe. Current edits and original media stay unchanged." else "Replaces only the selected clip's color and white balance. Timing, captions, motion, framing and audio stay unchanged. Undo restores the previous color.")
                        .setNegativeButton("Cancel",null).setPositiveButton(if(delete)"Delete look" else "Apply and preview"){_,_->
                            runCatching{
                                requireCurrent()
                                if(delete)store.remove(look){requireCurrent()}
                                else {
                                    val proposal=clips.mapIndexed{i,value->if(i==index)look.applyTo(value)else value}
                                    current.applyDraft(proposal,soundtrack=music);showClip();previewComposition(listOf(current.segments[index]),index)
                                }
                            }.onSuccess{status.text=if(delete)"Saved color look deleted. Current edits are unchanged." else "Saved color look applied. Undo restores the previous color."}
                                .onFailure{status.text=it.message?:"Saved color look could not be used. Existing edits were kept."}
                        }.show()
                }.setNegativeButton("Cancel",null).show()
        }
        android.app.AlertDialog.Builder(this).setTitle("Saved color looks")
            .setItems(arrayOf("Save this clip's color","Apply a saved look","Delete a saved look")){_,action->
                if(!valid()){status.text="Video or account changed. Open saved looks again.";return@setItems}
                when(action){
                    0->{
                        val name=EditText(this).apply{hint="Look name";contentDescription="Video color look name";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(40))}
                        val dialog=android.app.AlertDialog.Builder(this).setTitle("Save video color look").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save on this device",null).create()
                        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                            if(!valid()){status.text="Video or account changed. Open saved looks again.";dialog.dismiss();return@setOnClickListener}
                            val result=runCatching{store.add(NamedVideoColorLook(name.text.toString().trim(),clip.grade)){requireCurrent()}}
                            if(result.isFailure){name.error=result.exceptionOrNull()?.message?:"Could not save this look";return@setOnClickListener}
                            dialog.dismiss();status.text="Video color look saved on this device."
                        }};dialog.show()
                    }
                    1->selectLook(false)
                    2->selectLook(true)
                }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun momentNotes(){
        if(!editorIdle()||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return
        val mode=accessMode;val music=current.soundtrack;val uri=sourceUris()[clip.sourceId]?:return
        val namespace=videoLookNamespace()?:run{status.text="Session changed. Reopen this video before using notes.";return}
        val duration=sourceDuration(clip.sourceId)
        val at=runCatching{if(previewPlayer!=null)splitPosition()else if(sourceReady)video.currentPosition.toLong()else pendingSeekMs}
            .getOrElse{status.text=it.message?:"Select the playing clip first.";return}.coerceIn(clip.startMs,clip.endMs-1)
        video.pause();previewPlayer?.pause()
        val store=runCatching{VideoMomentNoteStore(java.io.File(noBackupFilesDir,"video-moment-notes"),namespace,uri.toString(),duration)}
            .getOrElse{status.text="Moment notes need a supported local source video.";return}
        fun valid()=editorIdle()&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&current.soundtrack==music&&selectedClip==index&&accessMode==mode&&videoLookNamespace()==namespace&&sourceUris()[clip.sourceId]==uri
        fun requireCurrent(){require(valid()){"Video or account changed. Open moment notes again."}}
        val catalog=runCatching{requireCurrent();store.load().also{requireCurrent()}}.getOrElse{status.text=it.message?:"Moment notes could not be read. Existing notes were kept.";return}
        fun editNote(before:VideoMomentNote?){
            val position=before?.sourceMs?:at
            val label=EditText(this).apply{hint="Moment label";contentDescription="Moment note label";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(80));setText(before?.label?:"Moment "+VideoCaptions.formatTime(position))}
            val dialog=android.app.AlertDialog.Builder(this).setTitle(if(before==null)"Note this source moment" else "Rename moment note").setView(label).setNegativeButton("Cancel",null).setPositiveButton("Save on this device",null).create()
            dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(!valid()){dialog.dismiss();status.text="Video or account changed. Open notes again.";return@setOnClickListener}
                val result=runCatching{
                    val text=label.text.toString().trim()
                    if(before==null)store.add(VideoMomentNote(position,text)){requireCurrent()}else store.rename(before,text){requireCurrent()}
                }
                if(result.isFailure){label.error=result.exceptionOrNull()?.message?:"Could not save this note";return@setOnClickListener}
                dialog.dismiss();status.text="Moment note saved locally. Video and project edits are unchanged."
            }};dialog.show()
        }
        fun chooseNote(note:VideoMomentNote){
            if(!valid()){status.text="Video or account changed. Open notes again.";return}
            val canJump=note.sourceMs>=clip.startMs&&note.sourceMs<clip.endMs
            val actions=(if(canJump)listOf("Jump to this moment")else emptyList())+listOf("Rename note","Delete note")
            android.app.AlertDialog.Builder(this).setTitle(note.label).setItems(actions.toTypedArray()){_,action->
                if(!valid()){status.text="Video or account changed. Open notes again.";return@setItems}
                when(action-(if(canJump)1 else 0)){
                    -1->{seekTimeline(VideoFilmstripScale.outputTime(clips,index,note.sourceMs));status.text="Jumped to saved source moment. Project edits are unchanged."}
                    0->editNote(note)
                    1->android.app.AlertDialog.Builder(this).setTitle("Delete moment note?").setMessage("Removes this local note only. Original video and project edits stay unchanged.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Delete note"){_,_->
                            runCatching{requireCurrent();store.remove(note){requireCurrent()}}
                                .onSuccess{status.text="Moment note deleted. Video and project edits are unchanged."}
                                .onFailure{status.text=it.message?:"Note could not be deleted."}
                        }.show()
                }
            }.setNegativeButton("Cancel",null).show()
        }
        val visible=catalog.visible(clip.startMs,clip.endMs)
        val choices=listOf("Add note at current source moment","Manage all source notes","Cut highlights from saved notes")+visible.map{VideoCaptions.formatTime(it.sourceMs)+" · "+it.label}
        android.app.AlertDialog.Builder(this).setTitle("Moment notes · this clip")
            .setItems(choices.toTypedArray()){_,choice->
                if(!valid()){status.text="Video or account changed. Open notes again.";return@setItems}
                when(choice){
                    0->editNote(null)
                    1->{
                        if(catalog.notes.isEmpty()){status.text="No moment notes saved for this source yet.";return@setItems}
                        android.app.AlertDialog.Builder(this).setTitle("All notes for source video")
                            .setItems(catalog.notes.map{VideoCaptions.formatTime(it.sourceMs)+" · "+it.label}.toTypedArray()){_,selected->chooseNote(catalog.notes[selected])}
                            .setNegativeButton("Close",null).show()
                    }
                    2->momentHighlights()
                    else->chooseNote(visible[choice-3])
                }
            }.setNeutralButton("About notes"){_,_->
                android.app.AlertDialog.Builder(this).setTitle("Private source moments").setMessage("Up to 100 labeled moments per source video, saved privately on this device. Times follow the original source, so trims and speed changes do not move notes. The clip list shows in-range moments; Manage all source notes also renames or deletes out-of-range notes. Notes are not rendered into exports or included in project transfer.").setPositiveButton("Done",null).show()
            }.setNegativeButton("Close",null).show()
    }

    private fun momentHighlights(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip;val clip=clips.getOrNull(index)?:return
        val music=current.soundtrack;val mode=accessMode;val namespace=videoLookNamespace()?:return;val uri=sourceUris()[clip.sourceId]?:return
        fun valid()=editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&current.soundtrack==music&&selectedClip==index&&accessMode==mode&&videoLookNamespace()==namespace&&sourceUris()[clip.sourceId]==uri
        val store=runCatching{VideoMomentNoteStore(java.io.File(noBackupFilesDir,"video-moment-notes"),namespace,uri.toString(),sourceDuration(clip.sourceId))}
            .getOrElse{status.text="Highlights need saved notes for a supported local video.";return}
        val catalog=runCatching{check(valid());store.load()}.getOrElse{status.text="Saved moments could not be read. Original video and notes were kept.";return}
        val notes=catalog.visible(clip.startMs,clip.endMs)
        if(notes.isEmpty()){status.text="Save moment notes inside this clip first, then open Highlights from notes.";return}
        video.pause();previewPlayer?.pause()
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Choose saved moments from this clip. Source-time handles keep footage around each note;overlapping or touching excerpts merge. The selected clip is replaced by these forward-playing excerpts. Later clips move earlier;the existing soundtrack keeps its settings. Titles,looks,audio and source-anchored motion stay;only captions intersecting retained footage stay visible. Notes and originals are unchanged. Preview after applying;codec cuts are not promised frame-exact.";setTextColor(Color.DKGRAY)})
        fun field(label:String,initial:String)=EditText(this).apply{hint=label;contentDescription=label;setText(initial);setTextColor(Color.BLACK);setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(8));body.addView(this)}
        val before=field("Seconds before each source moment","1.000");val after=field("Seconds after each source moment","2.000")
        val checks=notes.map{note->android.widget.CheckBox(this).apply{text=VideoCaptions.formatTime(note.sourceMs)+" · "+note.label;setTextColor(Color.BLACK);isChecked=true;body.addView(this)}}
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Highlights from saved notes").setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Cancel",null).setPositiveButton("Review excerpts",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!valid()){dialog.dismiss();status.text="Project or session changed. Open highlights again.";return@setOnClickListener}
            val proposal=runCatching{VideoMomentHighlights.propose(clips,index,notes.filterIndexed{i,_->checks[i].isChecked}.map{it.sourceMs},VideoCaptions.seconds(before.text.toString()),VideoCaptions.seconds(after.text.toString()))}
                .getOrElse{after.error=it.message?:"Choose saved moments and valid source handles.";return@setOnClickListener}
            if(proposal==clips){status.text="These excerpts retain the whole clip. Choose smaller handles to shorten it.";return@setOnClickListener}
            val excerpts=proposal.subList(index,proposal.size-(clips.size-index-1))
            val summary=excerpts.joinToString("\n"){VideoCaptions.formatTime(it.startMs)+" → "+VideoCaptions.formatTime(it.endMs)}
            val oldSeconds=String.format(java.util.Locale.ROOT,"%.3f",VideoPlaybackTime.durationUs(clips)/1_000_000.0)
            val newSeconds=String.format(java.util.Locale.ROOT,"%.3f",VideoPlaybackTime.durationUs(proposal)/1_000_000.0)
            android.app.AlertDialog.Builder(this).setTitle("Review ${excerpts.size} highlight excerpts")
                .setMessage("Original-source ranges:\n$summary\n\nProject $oldSeconds → $newSeconds seconds. Replace clip ${index+1} as one Undo step,then preview before export. Saved notes and original media stay unchanged.")
                .setNegativeButton("Back",null).setPositiveButton("Apply and preview"){_,_->
                    if(!valid()){dialog.dismiss();status.text="Project or session changed. Open highlights again.";return@setPositiveButton}
                    runCatching{
                        check(store.load()==catalog){"Saved notes changed. Open highlights again."}
                        check(valid()){"Project or session changed. Open highlights again."}
                        current.applyDraft(proposal,soundtrack=music)
                    }.onSuccess{dialog.dismiss();selectedClip=index;showClip();previewProject();status.text="Saved-note highlights applied. Undo restores the previous timeline;notes and originals are unchanged."}
                        .onFailure{status.text=it.message?:"Highlights could not be applied. Existing edits were kept."}
                }.show()
        }};dialog.show()
    }

    private fun chapterList(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList();val mode=accessMode
        val owner=videoLookNamespace()?:run{status.text="Session changed. Reopen the video before copying chapters.";return}
        fun valid()=editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&accessMode==mode&&videoLookNamespace()==owner
        runCatching{VideoChapters.text(clips)}.onSuccess{text->
            android.app.AlertDialog.Builder(this).setTitle("Clip chapter list").setMessage(text)
                .setPositiveButton("Copy list"){_,_->
                    if(!valid()){status.text="Project or session changed. Reopen the chapter list before copying.";return@setPositiveButton}
                    runCatching{
                        val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            ?: error("Clipboard is unavailable")
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Video clip chapters",text))
                    }.onSuccess{status.text="Chapter list copied. Times refer to edited playback."}
                        .onFailure{status.text="Could not copy the chapter list. Try again."}
                }.setNegativeButton("Close",null).show()
        }.onFailure{status.text=it.message?:"Chapter list is unavailable."}
    }

    private fun batchOptions(){
        if(!editorIdle())return
        val tools=VideoBatchTool.values()
        android.app.AlertDialog.Builder(this).setTitle("Batch editing").setItems(tools.map{it.title}.toTypedArray()){_,index->batchEdit(tools[index])}.setNegativeButton("Cancel",null).show()
    }

    private fun batchEdit(kind:VideoBatchTool){
        if(!editorIdle()||isDestroyed||isFinishing)return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack;val index=selectedClip;val mode=accessMode
        val owner=if(mode==EditorAccessMode.ACCOUNT)runCatching{VideoDraftPersistence(this,source.toString())}.getOrElse{status.text="Account changed. Reopen this video.";return}else null
        fun valid()=editorIdle()&&!isDestroyed&&!isFinishing&&project===current&&current.segments==snapshot&&current.soundtrack==music&&selectedClip==index&&accessMode==mode&&runCatching{owner?.requireCurrentOwner()}.isSuccess
        fun review(proposal:()->List<VideoSegment>){
            if(!valid())return
            val proposed=runCatching(proposal).getOrElse{status.text=it.message?:"This batch could not be prepared.";return}
            if(proposed==snapshot){status.text="These settings leave the project unchanged.";return}
            val duration=String.format(java.util.Locale.ROOT,"%.2f",proposed.sumOf{it.durationUs}/1_000_000.0)
            val explanation=when(kind){
                VideoBatchTool.DIVIDE->"Divides the selected clip into equal source ranges. Captions crossing a cut remain visible on both pieces."
                VideoBatchTool.LIMIT->"Keeps centered excerpts no longer than the chosen playback duration. Short clips stay unchanged. Captions outside the retained ranges are removed."
                VideoBatchTool.REVERSE->"Reverses clip order only. Footage still plays forward within each clip."
                VideoBatchTool.SHUFFLE->"Reorders the existing clips without changing their contents. This proposal stays fixed until you apply or cancel."
                VideoBatchTool.REPEAT->"Repeats the whole sequence, including its titles and captions. Music remains the existing project soundtrack."
                VideoBatchTool.SPEED->"Replaces every clip's playback speed. Source-relative titles and captions follow the existing timing pipeline."
                VideoBatchTool.CAPTION_SHIFT->"Moves the selected clip's caption timestamps by the chosen source-time offset. No captions are silently dropped."
                VideoBatchTool.CAPTION_WRAP->"Reflows the selected clip's caption text without changing timing. No words are removed; all cues remain editable."
                VideoBatchTool.REMOVE_INTERVAL->"Removes the chosen source interval from the selected clip and closes the timeline gap. Remaining captions follow the retained pieces."
                VideoBatchTool.CAPTION_CUT->"Keeps ranges covered by existing captions, with optional source-time handles. This does not detect speech. Uncaptioned ranges are removed."
                VideoBatchTool.BEAT_GRID->"Cuts the project on a manual tempo grid. Cuts within 0.1 playback second of clip edges are skipped. This does not analyze soundtrack beats."
                VideoBatchTool.SPEED_RAMP->"Creates four editable pieces at 0.5x, 1x, 1.5x and 2x, in the chosen direction. This is a stepped ramp, not optical-flow interpolation."
                VideoBatchTool.INTERLEAVE->"Alternates between source videos while preserving each source's existing clip order. It keeps every clip."
                VideoBatchTool.WORD_CAPTIONS->"Replaces the selected clip's captions with word groups. Timing is estimated inside each existing cue, not aligned to speech."
                VideoBatchTool.MERGE_CAPTIONS->"Joins nearby caption phrases up to 160 characters / eight source seconds. Merged text remains visible across the joined gaps."
                VideoBatchTool.TIMECODES->"Replaces the selected clip's captions with original-source timecode labels. They are visible in preview and burned into exported video."
                VideoBatchTool.CAPTION_DUCK->"Splits the selected clip at caption boundaries and lowers its original audio while captions are active. The project soundtrack is unchanged."
                VideoBatchTool.SORT->"Sorts whole clips by playback duration or groups them by source video and original source time. Every clip keeps its edits. The soundtrack follows the reordered timeline."
                VideoBatchTool.DEDUPLICATE->"Keeps the first of each exactly identical clip, including its source, timing and all edits. Different looks, audio, layout, titles or captions are preserved. This shortens repeated timelines."
                VideoBatchTool.JOIN_CUTS->"Joins neighboring source-contiguous clips only when their render settings agree and neither has edge or audio fades. Captions and subtitle provenance are retained."
                VideoBatchTool.CAPTION_HOLDS->"Extends the selected clip's visible captions into nearby gaps, bounded by the clip and gap midpoints. Values use source milliseconds; speech is not analyzed."
                VideoBatchTool.CAPTION_CASE->"Changes the selected clip's caption text to upper or lower case. Timing, layout and source provenance stay unchanged. Unicode expansion may exceed caption limits and is then refused."
                VideoBatchTool.CAPTION_REPLACE->"Replaces literal text in captions in the chosen scope. Timing, source provenance, styles and other edits stay unchanged. Case matching is optional. Empty or over-limit cue results are refused before applying."
                VideoBatchTool.STORYBOARD_TITLES->"Replaces clip titles using one pasted line per clip. Titles remain editable and follow the existing preview/export renderer."

            }
            android.app.AlertDialog.Builder(this).setTitle("Review ${kind.title.lowercase()}")
                .setMessage("$explanation\n\n${snapshot.size} → ${proposed.size} clips · $duration seconds. Existing source files are unchanged. Apply as one undoable edit, then preview before export.")
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->
                    if(!valid()){status.text="Project or session changed. Open the batch tool again.";return@setPositiveButton}
                    runCatching{current.applyDraft(proposed,soundtrack=music)}
                        .onSuccess{selectedClip=if(kind in setOf(VideoBatchTool.CAPTION_SHIFT,VideoBatchTool.CAPTION_WRAP,VideoBatchTool.WORD_CAPTIONS,VideoBatchTool.MERGE_CAPTIONS,VideoBatchTool.TIMECODES,VideoBatchTool.STORYBOARD_TITLES,VideoBatchTool.CAPTION_HOLDS,VideoBatchTool.CAPTION_CASE,VideoBatchTool.CAPTION_REPLACE))index else 0;showClip();previewProject();status.text="${kind.title} applied. Undo restores the previous project."}
                        .onFailure{status.text=it.message?:"Project limits reached. Existing edits are unchanged."}
                }.show()
        }
        fun options(labels:Array<String>,choose:(Int)->List<VideoSegment>){android.app.AlertDialog.Builder(this).setTitle(kind.title).setItems(labels){_,i->review{choose(i)}}.setNegativeButton("Cancel",null).show()}
        fun textInput(hint:String,initial:String,choose:(String)->List<VideoSegment>){
            val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
            panel.addView(TextView(this).apply{text=hint;setTextColor(Color.BLACK)})
            val field=EditText(this).apply{setText(initial);setTextColor(Color.BLACK);minLines=3;maxLines=8;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(16000));contentDescription=kind.title};panel.addView(field)
            val dialog=android.app.AlertDialog.Builder(this).setTitle(kind.title).setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
            dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(!valid()){dialog.dismiss();return@setOnClickListener}
                val proposed=runCatching{choose(field.text.toString())}
                if(proposed.isFailure){field.error=proposed.exceptionOrNull()?.message?:"Check these values";return@setOnClickListener}
                dialog.dismiss();review{proposed.getOrThrow()}
            }};dialog.show()
        }

        when(kind){
            VideoBatchTool.SORT->options(arrayOf("Group by source, then original time","Shortest playback first","Longest playback first")){VideoRefinementTools.sort(snapshot,VideoSortOrder.entries[it])}
            VideoBatchTool.DEDUPLICATE->review{VideoRefinementTools.removeExactDuplicates(snapshot)}
            VideoBatchTool.JOIN_CUTS->review{VideoRefinementTools.joinMatchingCuts(snapshot)}
            VideoBatchTool.CAPTION_HOLDS->{val values=listOf(100L,250L,500L,1000L,2000L);options(values.map{"$it source ms before and after"}.toTypedArray()){VideoRefinementTools.extendCaptionHolds(snapshot,index,values[it],values[it])}}
            VideoBatchTool.CAPTION_CASE->options(arrayOf("UPPER CASE","lower case")){VideoRefinementTools.captionCase(snapshot,index,CaptionLetterCase.entries[it])}
            VideoBatchTool.CAPTION_REPLACE->{
                val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
                panel.addView(TextView(this).apply{text="Replace literal text. Leave replacement empty to remove a match; a caption cannot become blank. Review before applying.";setTextColor(Color.BLACK)})
                fun field(label:String)=EditText(this).apply{hint=label;contentDescription=label;setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(240));panel.addView(this)}
                val find=field("Find caption text");val replacement=field("Replacement caption text")
                val all=android.widget.CheckBox(this).apply{text="All clips in this project";setTextColor(Color.BLACK);panel.addView(this)}
                val matchCase=android.widget.CheckBox(this).apply{text="Match case";isChecked=true;setTextColor(Color.BLACK);panel.addView(this)}
                val dialog=android.app.AlertDialog.Builder(this).setTitle(kind.title).setView(android.widget.ScrollView(this).apply{addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
                dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                    if(!valid()){dialog.dismiss();return@setOnClickListener}
                    val proposal=runCatching{VideoCaptionReplace.propose(snapshot,find.text.toString(),replacement.text.toString(),if(all.isChecked)null else index,!matchCase.isChecked)}
                    if(proposal.isFailure){find.error=proposal.exceptionOrNull()?.message?:"Check this replacement";return@setOnClickListener}
                    dialog.dismiss();review{proposal.getOrThrow()}
                }};dialog.show()
            }
            VideoBatchTool.DIVIDE->{val values=listOf(2,3,4,5,10);options(values.map{"$it pieces"}.toTypedArray()){VideoBatchTools.divide(snapshot,index,values[it])}}
            VideoBatchTool.LIMIT->{val values=listOf(1_000L,2_000L,3_000L,5_000L,10_000L);options(values.map{"${it/1000} seconds per clip"}.toTypedArray()){VideoBatchTools.limitLengths(snapshot,values[it])}}
            VideoBatchTool.REVERSE->review{VideoBatchTools.reverseOrder(snapshot)}
            VideoBatchTool.SHUFFLE->{val seed=System.nanoTime().toInt();review{VideoBatchTools.shuffleOrder(snapshot,seed)}}
            VideoBatchTool.REPEAT->{val values=listOf(2,3,4);options(values.map{"$it sequence copies"}.toTypedArray()){VideoBatchTools.repeatSequence(snapshot,values[it])}}
            VideoBatchTool.SPEED->{val values=listOf(.5f,1f,1.5f,2f);options(values.map{"All clips at ${it}x"}.toTypedArray()){VideoBatchTools.allSpeeds(snapshot,values[it])}}
            VideoBatchTool.CAPTION_SHIFT->{val values=listOf(-2000L,-1000L,-500L,-250L,250L,500L,1000L,2000L);options(values.map{String.format(java.util.Locale.ROOT,"%+.2f source seconds",it/1000.0)}.toTypedArray()){VideoBatchTools.shiftCaptions(snapshot,index,values[it])}}
            VideoBatchTool.CAPTION_WRAP->{val values=listOf(20,30,40,60,80);options(values.map{"Up to $it characters per line"}.toTypedArray()){VideoBatchTools.reflowCaptions(snapshot,index,values[it])}}
            VideoBatchTool.REMOVE_INTERVAL->{
                val clip=snapshot[index];val span=clip.endMs-clip.startMs
                val initial=String.format(java.util.Locale.ROOT,"%.3f\n%.3f",(span/3)/1000.0,(span-span/3)/1000.0)
                textInput("Two lines: start and end source-time seconds from this clip's current start, before playback speed changes.",initial){text->
                    val lines=text.trim().lines();require(lines.size==2){"Write start and end on separate lines"}
                    val from=VideoCaptions.seconds(lines[0]);val to=VideoCaptions.seconds(lines[1]);require(from<=span&&to<=span){"Use offsets inside this clip"}
                    VideoAuthoringTools.removeInterval(snapshot,index,clip.startMs+from,clip.startMs+to)
                }
            }
            VideoBatchTool.CAPTION_CUT->{val values=listOf(0L,150L,300L,500L,1000L);options(values.map{"$it ms source-time handles"}.toTypedArray()){VideoAuthoringTools.captionCut(snapshot,index,values[it])}}
            VideoBatchTool.BEAT_GRID->{val tempos=listOf(60,90,120,150,180)
                android.app.AlertDialog.Builder(this).setTitle("Manual tempo").setItems(tempos.map{"$it BPM"}.toTypedArray()){_,tempo->
                    val beats=listOf(1,2,4);options(beats.map{"$it beat(s) per cut"}.toTypedArray()){VideoAuthoringTools.beatGrid(snapshot,tempos[tempo],beats[it])}
                }.setNegativeButton("Cancel",null).show()
            }
            VideoBatchTool.SPEED_RAMP->options(arrayOf("Accelerate: 0.5x → 2x","Decelerate: 2x → 0.5x")){VideoAuthoringTools.speedRamp(snapshot,index,it==0)}
            VideoBatchTool.INTERLEAVE->review{VideoAuthoringTools.interleaveSources(snapshot)}
            VideoBatchTool.WORD_CAPTIONS->{val values=listOf(1,2,3,4);options(values.map{"$it word(s) per cue"}.toTypedArray()){VideoAuthoringTools.wordCaptions(snapshot,index,values[it])}}
            VideoBatchTool.MERGE_CAPTIONS->{val values=listOf(0L,150L,300L,500L,1000L);options(values.map{"Join gaps up to $it source ms"}.toTypedArray()){VideoAuthoringTools.mergeCaptions(snapshot,index,values[it])}}
            VideoBatchTool.TIMECODES->{val values=listOf(1000L,2000L,5000L,10000L);options(values.map{"Timecode every ${it/1000} playback second(s)"}.toTypedArray()){VideoAuthoringTools.sourceTimecodes(snapshot,index,values[it])}}
            VideoBatchTool.CAPTION_DUCK->{val values=listOf(0f,.25f,.5f,.75f);options(values.map{"Captioned audio: ${(it*100).toInt()}% of current volume"}.toTypedArray()){VideoAuthoringTools.captionDucking(snapshot,index,values[it])}}
            VideoBatchTool.STORYBOARD_TITLES->textInput("Exactly one nonempty title line for each of the ${snapshot.size} clips. This replaces their current titles.",snapshot.joinToString("\n"){it.title.replace('\n',' ')}){VideoAuthoringTools.storyboardTitles(snapshot,it)}

        }
    }

    private fun montageOptions() {
        if (!editorIdle() || isDestroyed || isFinishing) return
        val current = project ?: return
        val snapshot = current.segments
        val music = current.soundtrack
        val mode = accessMode
        val owner = if (mode == EditorAccessMode.ACCOUNT) runCatching { VideoDraftPersistence(this, source.toString()) }
            .getOrElse { status.text = "Account changed. Reopen this video."; return } else null
        val targets = listOf(15_000L, 30_000L, 60_000L)
        android.app.AlertDialog.Builder(this).setTitle("Build a montage")
            .setItems(arrayOf("15 seconds", "30 seconds", "60 seconds")) { _, choice ->
                if (!editorIdle() || isDestroyed || isFinishing || project !== current || current.segments != snapshot || current.soundtrack != music || accessMode != mode || runCatching { owner?.requireCurrentOwner() }.isFailure) return@setItems
                val proposed = runCatching { VideoStoryTools.montage(snapshot, targets[choice]) }.getOrElse { status.text = "Cannot build a montage from this project."; return@setItems }
                val seconds = String.format(java.util.Locale.ROOT, "%.2f", proposed.sumOf { it.durationUs } / 1_000_000.0)
                android.app.AlertDialog.Builder(this).setTitle("Review montage")
                    .setMessage("$seconds seconds · ${proposed.size} clips. Uses balanced centre excerpts in your current order. Short footage stays shorter; nothing is repeated or generated. Existing color, speed, titles, visible captions and music are kept. Replaces the timeline as one undoable edit. Preview before exporting.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Apply and preview") { _, _ ->
                        if (!editorIdle() || isDestroyed || isFinishing || project !== current || current.segments != snapshot || current.soundtrack != music || accessMode != mode || runCatching { owner?.requireCurrentOwner() }.isFailure) { status.text = "Project or session changed. Build the montage again."; return@setPositiveButton }
                        runCatching { current.applyDraft(proposed, soundtrack = music) }
                            .onSuccess { selectedClip = 0; showClip(); previewProject(); status.text = "Montage applied. Undo restores the full timeline." }
                            .onFailure { status.text = "Montage could not be applied; existing edits are unchanged." }
                    }.show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun scriptCaptionOptions() {
        if (!editorIdle() || isDestroyed || isFinishing) return
        val current = project ?: return
        val index = selectedClip
        val clip = current.segments.getOrNull(index) ?: return
        val mode = accessMode
        val owner = if (mode == EditorAccessMode.ACCOUNT) runCatching { VideoDraftPersistence(this, source.toString()) }
            .getOrElse { status.text = "Account changed. Reopen this video."; return } else null
        fun valid() = editorIdle() && !isDestroyed && !isFinishing && project === current && selectedClip == index && current.segments.getOrNull(index) == clip && accessMode == mode && runCatching { owner?.requireCurrentOwner() }.isSuccess
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        panel.addView(TextView(this).apply { text = "Paste one caption per line, up to 100 lines and 240 characters per line. Timing is estimated from text length across this clip, not speech recognition. Review and adjust in Captions. Applying replaces this clip's captions; Undo restores them."; setTextColor(Color.DKGRAY) })
        val script = EditText(this).apply { hint = "Write or paste your script"; contentDescription = "Caption script, one caption per line"; minLines = 4; maxLines = 7; gravity = Gravity.TOP; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE; filters = arrayOf(android.text.InputFilter.LengthFilter(16_000)) }
        panel.addView(script)
        val dialog = android.app.AlertDialog.Builder(this).setTitle("Script to captions").setView(panel).setNegativeButton("Cancel", null).setPositiveButton("Review timing", null).create()
        dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!valid()) { status.text = "Clip or session changed. Open Script to captions again."; dialog.dismiss(); return@setOnClickListener }
            val proposed = runCatching { VideoStoryTools.scriptCaptions(clip, script.text.toString()) }.getOrElse { script.error = it.message; return@setOnClickListener }
            val overview = proposed.take(4).joinToString("\n\n") { "${VideoTrimRange.format(it.startMs)}–${VideoTrimRange.format(it.endMs)} source seconds\n${it.text}" }
            android.app.AlertDialog.Builder(this).setTitle("Review ${proposed.size} captions")
                .setMessage("$overview${if (proposed.size > 4) "\n\nPlus ${proposed.size - 4} more. All captions remain editable." else ""}\n\nEstimated timing, not synchronized to speech. Replaces ${clip.captions.size} existing captions on this clip.")
                .setNegativeButton("Back", null).setPositiveButton("Apply and preview") { _, _ ->
                    if (!valid()) { status.text = "Clip or session changed. Open Script to captions again."; dialog.dismiss(); return@setPositiveButton }
                    runCatching { current.setCaptions(index, proposed, null) }
                        .onSuccess { dialog.dismiss(); showClip(); previewProject(); status.text = "Script captions applied. Review timing in Captions; Undo restores previous captions." }
                        .onFailure { script.error = "Caption limits reached. Existing edits are unchanged." }
                }.show()
        } }
        dialog.show()
    }

    private fun rollCut(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        video.pause();previewPlayer?.pause();updateWorkspace()
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip
        if(index>=clips.lastIndex){status.text="Select a clip with a following neighbor to roll their cut.";return}
        val mode=accessMode;val owner=videoLookNamespace()?:return;val music=current.soundtrack;val sources=sourceUris();val durations=sourceDurations()
        val limits=runCatching{VideoRollCut.bounds(clips,index,durations)}.getOrElse{status.text="Source metadata changed. Reopen this video.";return}
        if(limits.first==0L&&limits.second==0L){status.text="Trim a boundary first to leave original source footage for rolling this cut.";return}
        val initial=if(limits.second>0)minOf(500L,limits.second)else maxOf(-500L,limits.first)
        fun valid()=editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&current.soundtrack==music&&selectedClip==index&&accessMode==mode&&videoLookNamespace()==owner&&sourceUris()==sources
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Move the cut between clips ${index+1} and ${index+2} without changing their combined playback length or later clip positions. Positive offsets extend the first clip and shorten the next; negative offsets do the reverse. Each source needs enough original footage at the boundary. Available offset: ${String.format(java.util.Locale.ROOT,"%+.3f",limits.first/1000.0)} to ${String.format(java.util.Locale.ROOT,"%+.3f",limits.second/1000.0)} playback seconds.";setTextColor(Color.DKGRAY)})
        val offset=EditText(this).apply{hint="Signed cut offset, playback seconds";contentDescription="Signed cut offset, playback seconds";setSingleLine(true);setTextColor(Color.BLACK);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;filters=arrayOf(android.text.InputFilter.LengthFilter(16));setText(String.format(java.util.Locale.ROOT,"%.3f",initial/1000.0));body.addView(this)}
        body.addView(TextView(this).apply{text="Use 0.002-second steps, for example 0.500 or -0.250. Each side follows its own speed; different speeds may leave a gap or overlap in same-source footage. Clip settings and source-anchored captions/motion remain, so visible cues may change. Video/audio move together. Preview after applying; codec output is not guaranteed frame-exact.";setTextColor(Color.DKGRAY)})
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Roll cut").setView(android.widget.ScrollView(this).apply{addView(body)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!valid()){status.text="Project or session changed. Reopen Roll cut.";dialog.dismiss();return@setOnClickListener}
            runCatching{val delta=VideoRollCut.offset(offset.text.toString());delta to VideoRollCut.propose(clips,index,delta,durations)}.onSuccess{(delta,proposal)->
                val left=proposal[index];val right=proposal[index+1]
                android.app.AlertDialog.Builder(this).setTitle("Move this cut?")
                    .setMessage("Clip ${index+1}: ${VideoTrimRange.format(left.startMs)}–${VideoTrimRange.format(left.endMs)} source seconds.\nClip ${index+2}: ${VideoTrimRange.format(right.startMs)}–${VideoTrimRange.format(right.endMs)} source seconds.\n\nTheir combined playback length, later clips and project soundtrack stay in place. Existing caption/motion source timestamps stay anchored. Undo restores both ranges.")
                    .setNegativeButton("Keep editing",null).setPositiveButton("Apply roll cut"){_,_->
                        if(!valid()){status.text="Project or session changed. Reopen Roll cut.";dialog.dismiss();return@setPositiveButton}
                        runCatching{current.rollCut(index,delta)}.onSuccess{dialog.dismiss();showClip();status.text="Cut moved with combined playback length kept. Preview project; Undo restores both ranges."}
                            .onFailure{offset.error="This cut could not be saved. Existing edits were kept."}
                    }.show()
            }.onFailure{offset.error=it.message?:"Choose an offset with available source footage"}
        }};dialog.show()
    }

    private fun slipSourceWindow(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        video.pause();previewPlayer?.pause();updateWorkspace()
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip;val clip=clips[index]
        val duration=sourceDuration(clip.sourceId);val uri=sourceUris()[clip.sourceId]?:return
        val latest=runCatching{VideoClipWindow.latestStart(clip,duration)}.getOrElse{status.text="Original source metadata changed. Reopen the video.";return}
        if(latest==0L){status.text="Trim a shorter clip first to leave alternative source footage.";return}
        val mode=accessMode;val owner=videoLookNamespace()?:return;val music=current.soundtrack
        fun valid()=editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&current.soundtrack==music&&selectedClip==index&&accessMode==mode&&videoLookNamespace()==owner&&sourceUris()[clip.sourceId]==uri
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Choose different footage from this same original while keeping this clip's length and neighboring playback positions. Current source range ${VideoTrimRange.format(clip.startMs)}–${VideoTrimRange.format(clip.endMs)} seconds. New start: 0–${VideoTrimRange.format(latest)} source seconds.";setTextColor(Color.DKGRAY)})
        val from=EditText(this).apply{hint="New window start, source seconds";contentDescription="New window start, source seconds";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(16));setText(VideoTrimRange.format(clip.startMs));setTextColor(Color.BLACK);body.addView(this)}
        body.addView(TextView(this).apply{text="Source video and its audio move together. Grade, framing, title, volume and fades remain. Captions and motion keep their original-source timestamps, so what is visible may change. Removed cues are not regenerated. Preview after applying; this does not promise frame-exact seeking.";setTextColor(Color.DKGRAY)})
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Slip source window").setView(android.widget.ScrollView(this).apply{addView(body)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!valid()){dialog.dismiss();return@setOnClickListener}
            runCatching{VideoClipWindow.move(clip,VideoCaptions.seconds(from.text.toString()),duration)}.onSuccess{proposal->
                android.app.AlertDialog.Builder(this).setTitle("Use this source window?")
                    .setMessage("${VideoTrimRange.format(proposal.startMs)}–${VideoTrimRange.format(proposal.endMs)} source seconds, same ${String.format(java.util.Locale.ROOT,"%.3f",clip.durationUs/1000000.0)} seconds of playback. Other clips and the project soundtrack stay in place. Existing captions/motion retain source timestamps. Undo restores this window.")
                    .setNegativeButton("Keep editing",null).setPositiveButton("Apply window"){_,_->
                        if(!valid()){dialog.dismiss();return@setPositiveButton}
                        runCatching{current.slipSourceWindow(index,proposal.startMs)}
                            .onSuccess{dialog.dismiss();showClip();status.text="Source window changed with playback length kept. Preview project; Undo restores the old window."}
                            .onFailure{from.error="This window could not be saved. Existing edits were kept."}
                    }.show()
            }.onFailure{from.error=it.message?:"Choose a start inside the original source"}
        }};dialog.show()
    }

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
    private fun savedTitleStyles(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments;val index=selectedClip
        val clip=clips.getOrNull(index)?:return;val music=current.soundtrack;val mode=accessMode
        titleStyleShelf?.close()
        video.pause();previewPlayer?.pause()
        lateinit var shelf:VideoTitleStyleShelf
        shelf=VideoTitleStyleShelf(this,mode,clip.titleStyle,
            isCurrent={editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&current.soundtrack==music&&selectedClip==index&&accessMode==mode},
            busy={value->if(titleStyleShelf===shelf)draftBusy=value},
            message={text->status.text=text},
            apply={style,all->
                val proposed=clips.mapIndexed{i,value->if(all||i==index)style.applyTo(value)else value}
                val result=runCatching{current.applyDraft(proposed,soundtrack=music)}
                if(result.isFailure)status.text=result.exceptionOrNull()?.message?:"Title appearance could not be applied."
                else {
                    status.text="Saved title appearance applied. Project Undo restores the previous style."
                    runCatching{showClip();previewComposition(listOf(current.segments[index]),index)}
                        .onFailure{status.text="Title appearance applied; preview unavailable. Project Undo is available."}
                }
            })
        titleStyleShelf=shelf;shelf.open()
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
        val exportClips=runCatching{VideoExportSnapshot.clips(current.segments,selectedClip,from,to)}
            .getOrElse{status.text=it.message?:"Choose a valid audio range";return}
        pendingAudio=PendingAudio(exportClips,sourceUris().toMap(),sourceDurations().toMap(),current.soundtrack)
        importing=true;video.pause();stopProjectPreview()
        start.isEnabled=false;end.isEnabled=false
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type="audio/mp4"
                putExtra(Intent.EXTRA_TITLE,"Noble-audio-copy.m4a")
            },27)
        }catch(error:Exception){pendingAudio=null;importing=false;start.isEnabled=true;end.isEnabled=true;status.text="No app is available to save audio"}
    }
    private fun finishAudioCopy(resultCode:Int,data:Intent?) {
        val snapshot=pendingAudio;pendingAudio=null;importing=false
        start.isEnabled=true;end.isEnabled=true
        if(isDestroyed||isFinishing||saving)return
        if(resultCode!=RESULT_OK||data?.data==null){
            if(snapshot!=null)status.text="Audio save cancelled. Current edits are unchanged."
            return
        }
        if(snapshot==null){status.text="Audio save was interrupted. Choose Export audio copy again. Check Files for an empty or partial copy.";return}
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
        pendingSegments = runCatching{VideoExportSnapshot.clips(requireNotNull(project).segments,selectedClip,from,to)}
            .getOrElse{status.text=it.message?:"Choose a valid video range";return}
        pendingRange = from to to
        start.isEnabled = false; end.isEnabled = false
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/mp4"
                putExtra(Intent.EXTRA_TITLE, "Noble-video-copy.mp4")
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
        if(holdRecoveryResult(requestCode)||!allowsMediaImportResult(requestCode))return
        if(requestCode == 28) { finishVoiceover(resultCode,data); return }
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
        if (saving || isFinishing || isDestroyed) return
        if (resultCode != RESULT_OK || data?.data == null) {
            if(range!=null && clips!=null)status.text="Video save cancelled. Current edits are unchanged."
            return
        }
        if(range==null || clips==null){status.text="Video save was interrupted. Choose Export again. Check Files for an empty or partial copy.";return}
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
    private fun guestProjectOperation(restore:Boolean) {
        if(restore)requestSavedRestore(true){runGuestProjectAction(GuestProjectAction.RESTORE)}
        else runGuestProjectAction(GuestProjectAction.SAVE)
    }
    private fun deleteGuestProject() {
        if(accessMode!=EditorAccessMode.GUEST_BASIC||!editorIdle()||isDestroyed||isFinishing)return
        android.app.AlertDialog.Builder(this).setTitle("Delete saved guest project?")
            .setMessage("Delete only the local saved edits for this original video. Current edits, source media and account drafts stay unchanged.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete this save"){_,_->runGuestProjectAction(GuestProjectAction.DELETE)}
            .setNeutralButton("All guest saves"){_,_->android.app.AlertDialog.Builder(this).setTitle("Delete all saved guest projects?")
                .setMessage("Remove every local guest video save, including saves with unavailable originals. Source media, current edits and account drafts stay unchanged.")
                .setNegativeButton("Cancel",null).setPositiveButton("Delete all guest saves"){_,_->runGuestProjectAction(GuestProjectAction.DELETE_ALL)}.show()}.show()
    }
    private fun hasDurableGuestRead(value:String):Boolean {
        val uri=Uri.parse(value)
        return VoiceoverProvider.ownsReadable(this,uri)||PhotoVideoProjectStore(this).ownsReadableSource(uri)||contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission}
    }
    private fun validateGuestMedia(primary:String,saved:VideoDraft) {
        fun hasRead(value:String)=hasDurableGuestRead(value)
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
                            {value->hasDurableGuestRead(value)},
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
                        require(GuestVideoProjectMedia.originals(primary,saved).all{value->hasDurableGuestRead(value)}){"Original document access changed"}
                        applySavedDraft(current,saved,true)
                    }
                    status.text=when {
                        applied.isFailure -> "Guest project sources conflict or access changed. Current edits are preserved."
                        applied.getOrDefault(false) -> "Guest project restored. Preview before exporting. Undo restores prior clip edits."
                        else -> "Guest project restored; preview is unavailable. Undo restores prior clip edits."
                    }
                }else status.text=when(action){GuestProjectAction.SAVE->"Guest project saved locally. Reopen this same original, then Restore project. Media is not copied.";GuestProjectAction.RESTORE->"No guest project is saved for this original video.";else->"Guest save deleted. Source media and account drafts are unchanged."}
            }
        }
    }

    private fun draftOperation(restore:Boolean) {
        if(restore){
            if(!accessMode.allowsAccountDrafts){status.text="Sign in to save or restore account drafts. Export a copy to keep your guest edit.";return}
            requestSavedRestore(false){performDraftOperation(true)}
        }else performDraftOperation(false)
    }
    private fun performDraftOperation(restore: Boolean) {
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
                val ownerCurrent=runCatching { requireNotNull(persistence).requireCurrentOwner() }.isSuccess
                if (result.isFailure || !ownerCurrent) {
                    status.text = if(ownerCurrent && result.exceptionOrNull() is VideoDraftRecordException)
                        "Saved draft could not be opened or is incompatible. Saved data and current edits were kept. Try reopening the original document."
                    else "Draft unavailable. Check your account, storage and original document access."
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
                    status.text = if(applied.getOrDefault(false)) "Draft restored. Preview before saving a copy. Undo restores prior clip edits."
                        else "Draft restored; preview is unavailable. Undo restores prior clip edits."
                } else status.text = if (restore) "No draft is saved for this video and account on this device."
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

    private fun quietPauseOptions(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing||quietAudioDialog?.isShowing==true)return
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip;val clip=clips[index]
        if(clip.endMs-clip.startMs>120_000||clip.endMs>Int.MAX_VALUE){status.text="Select a clip range of at most two source minutes before analysing quiet pauses.";return}
        val sources=sourceUris().toMap();val uri=sources[clip.sourceId]?:return
        val mode=accessMode;val owner=videoLookNamespace()?:return;val music=current.soundtrack;val generation=quietAudioGeneration
        fun sameSnapshot()=workspaceActive&&!isDestroyed&&!isFinishing&&quietAudioGeneration==generation&&project===current&&current.segments==clips&&current.soundtrack==music&&selectedClip==index&&accessMode==mode&&videoLookNamespace()==owner&&sourceUris()==sources
        fun editable()=editorIdle()&&sameSnapshot()
        fun track(dialog:android.app.AlertDialog){
            quietAudioDialog=dialog
            dialog.setOnDismissListener{if(quietAudioDialog===dialog)quietAudioDialog=null}
            dialog.show()
        }
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(12))}
        val information=TextView(this).apply{text="Find interior quiet pauses in this clip's original audio, before volume, mute, music or effects. Leading/trailing quiet is kept. Low energy is not speech recognition: quiet words or music may be selected. Review source-time cuts before Apply. Originals stay unchanged; Undo restores the edit. Analysis is local, limited to two source minutes.";setTextColor(Color.DKGRAY)}
        body.addView(information)
        fun field(label:String,value:String,numberType:Int,max:Int):EditText{
            body.addView(TextView(this).apply{text=label;setTextColor(Color.BLACK);setPadding(0,dp(12),0,0)})
            return EditText(this).apply{setText(value);contentDescription=label;setSingleLine(true);setTextColor(Color.BLACK);inputType=numberType;filters=arrayOf(android.text.InputFilter.LengthFilter(max));body.addView(this)}
        }
        val number=android.text.InputType.TYPE_CLASS_NUMBER
        val thresholdInput=field("Quiet threshold (dBFS, -60 to -15)","-38",number or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED,4)
        val minimumInput=field("Minimum quiet pause (0.2–5 playback seconds)","0.5",number or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL,5)
        val paddingInput=field("Keep at each pause edge (0–0.5 playback seconds)","0.1",number or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL,5)
        val optionsDialog=android.app.AlertDialog.Builder(this).setTitle("Remove quiet pauses · clip ${index+1}")
            .setView(ScrollView(this).apply{addView(body)}).setNegativeButton("Cancel",null).setPositiveButton("Analyse selected clip",null).create()
        optionsDialog.setOnShowListener{optionsDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener analyseClip@{
            if(!editable()){optionsDialog.dismiss();status.text="Video or session changed. Open quiet-pause analysis again.";return@analyseClip}
            val settings=runCatching{VideoQuietCuts.Options(thresholdInput.text.toString().trim().toIntOrNull()?:error("Use a whole-number threshold from -60 to -15 dBFS."),VideoCaptions.seconds(minimumInput.text.toString()),VideoCaptions.seconds(paddingInput.text.toString()))}
                .getOrElse{information.text=it.message?:"Check the threshold, minimum pause and padding.";return@analyseClip}
            optionsDialog.dismiss();stopProjectPreview();video.pause()
            val cancellation=AtomicBoolean(false);quietAudioCancellation=cancellation;importing=true
            status.text="Analysing original audio on this device. Current edits are unchanged."
            val progress=android.app.AlertDialog.Builder(this).setTitle("Analysing quiet pauses")
                .setMessage("Reading this selected source range only. No media is uploaded. Current edits are unchanged.")
                .setNegativeButton("Cancel analysis"){_,_->cancellation.set(true);if(!isDestroyed)status.text="Cancelling audio analysis; edits are unchanged."}.create()
            progress.setOnCancelListener{cancellation.set(true)};track(progress)
            val appContext=applicationContext
            Thread({
                val result=runCatching{
                    val windows=VideoQuietAudio.scan(appContext,uri,clip.startMs,clip.endMs){cancellation.get()}
                    if(cancellation.get())throw java.util.concurrent.CancellationException("Audio analysis cancelled")
                    val plan=VideoQuietCuts.plan(clip.startMs,clip.endMs,clip.speed,windows,settings)
                    plan to VideoQuietCuts.apply(clips,index,plan)
                }
                runOnUiThread{
                    if(quietAudioCancellation===cancellation){quietAudioCancellation=null;importing=false}
                    runCatching{progress.dismiss()}
                    if(isDestroyed||isFinishing)return@runOnUiThread
                    if(cancellation.get()||!sameSnapshot()||!editorIdle()){
                        if(workspaceActive&&quietAudioGeneration==generation)status.text="Audio analysis cancelled or the video changed. Current edits are unchanged."
                        return@runOnUiThread
                    }
                    result.onSuccess{(plan,proposed)->
                        val removedUs=clips.sumOf{it.durationUs}-proposed.sumOf{it.durationUs}
                        val message=buildString{
                            append("${plan.removed.size} interior quiet pause(s), ")
                            append(String.format(java.util.Locale.ROOT,"%.3f",removedUs/1_000_000.0));append(" playback seconds removed.\n")
                            append("${plan.knownWindows*100/plan.totalWindows}% of selected source audio covered. Threshold ${settings.thresholdDb} dBFS.\n\n")
                            plan.removed.forEachIndexed{i,range->append("${i+1}. ${VideoCaptions.formatTime(range.startMs)} → ${VideoCaptions.formatTime(range.endMs)} source time\n")}
                            append("\nApply replaces this clip with ${plan.retained.size} retained pieces and moves later clips earlier. Original audio/settings and source-relative captions stay with each piece. Edge fades repeat on new pieces; the separate soundtrack keeps its settings. Review the resulting mix. Nothing changes until Apply; Undo restores the previous timeline.")
                        }
                        val review=android.app.AlertDialog.Builder(this).setTitle("Review quiet-pause cuts").setMessage(message).setNegativeButton("Keep current edit",null).setPositiveButton("Apply cuts",null).create()
                        review.setOnShowListener{review.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener applyCuts@{
                            if(!editable()||cancellation.get()){review.dismiss();status.text="Video or session changed. Analyse again before applying.";return@applyCuts}
                            runCatching{current.applyDraft(proposed,soundtrack=music)}.onSuccess{
                                review.dismiss();selectedClip=index
                                runCatching{showClip();previewProject()}.onSuccess{status.text="Quiet-pause cuts applied. Review audio and captions; Undo restores the old timeline."}
                                    .onFailure{status.text="Cuts applied, but preview could not open. Use Undo to restore the old timeline."}
                            }.onFailure{review.setMessage(message+"\n\nCould not apply: "+(it.message?:"Check project limits")+". Current edits are unchanged.")}
                        }};track(review)
                    }.onFailure{status.text=(it.message?:"Audio could not be analysed")+". Current edits are unchanged."}
                }
            },"noble-quiet-audio").start()
        }};track(optionsDialog)
    }

    private fun sourceAudioLevels(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing||quietAudioDialog?.isShowing==true)return
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip;val clip=clips[index]
        if(clip.endMs-clip.startMs>120_000||clip.endMs>Int.MAX_VALUE){status.text="Select at most two source minutes to inspect audio levels.";return}
        val sources=sourceUris().toMap();val uri=sources[clip.sourceId]?:return
        val mode=accessMode;val owner=videoLookNamespace()?:return;val music=current.soundtrack;val generation=quietAudioGeneration
        fun sameSnapshot()=workspaceActive&&!isDestroyed&&!isFinishing&&quietAudioGeneration==generation&&project===current&&current.segments==clips&&current.soundtrack==music&&selectedClip==index&&accessMode==mode&&videoLookNamespace()==owner&&sourceUris()==sources
        fun track(dialog:android.app.AlertDialog){quietAudioDialog=dialog;dialog.setOnDismissListener{if(quietAudioDialog===dialog)quietAudioDialog=null};dialog.show()}
        stopProjectPreview();video.pause();val cancellation=AtomicBoolean(false);quietAudioCancellation=cancellation;importing=true
        status.text="Measuring original audio levels locally. Timeline edits are unchanged."
        val progress=android.app.AlertDialog.Builder(this).setTitle("Reading source audio levels").setMessage("This uses original audio before clip volume, mute, music and effects. No media is uploaded and no timeline edit is made.")
            .setNegativeButton("Cancel"){_,_->cancellation.set(true)}.create()
        progress.setOnCancelListener{cancellation.set(true)};track(progress)
        val appContext=applicationContext
        Thread({
            val result=runCatching{
                val windows=VideoQuietAudio.scan(appContext,uri,clip.startMs,clip.endMs){cancellation.get()}
                if(cancellation.get())throw java.util.concurrent.CancellationException("Audio inspection cancelled")
                VideoAudioLevels.inspect(windows).also{require(it.knownMs>0){"No reliable source audio could be measured"}}
            }
            runOnUiThread{
                if(quietAudioCancellation===cancellation){quietAudioCancellation=null;importing=false}
                runCatching{progress.dismiss()};if(isDestroyed||isFinishing)return@runOnUiThread
                if(cancellation.get()||!sameSnapshot()||!editorIdle()){
                    if(workspaceActive&&quietAudioGeneration==generation)status.text="Audio inspection cancelled or the source changed. Timeline edits are unchanged."
                    return@runOnUiThread
                }
                result.onSuccess{overview->
                    val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(12))}
                    body.addView(TextView(this).apply{text="Original source RMS energy before volume, mute, music or effects. Black bars show energy on a -60 to 0 dBFS scale; grey regions have unknown coverage. This is an approximate level overview. Select on the graph, use a region below, or enter a source time. Jump changes only the playhead.";setTextColor(Color.DKGRAY)})
                    body.addView(TextView(this).apply{text="${overview.knownMs*100/(overview.endMs-overview.startMs)}% source coverage · known-audio RMS ${VideoAudioLevels.levelText(overview.averageRms)}";setTextColor(Color.BLACK)})
                    val selectedInfo=TextView(this).apply{setTextColor(Color.BLACK);accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
                    val time=EditText(this).apply{contentDescription="Selected audio source time in seconds";setSingleLine(true);setTextColor(Color.BLACK);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(12))}
                    lateinit var graph:VideoAudioLevelsView
                    fun select(at:Long){
                        VideoAudioLevels.fraction(overview,at);graph.position(at);time.setText(VideoTrimRange.format(at));time.error=null
                        selectedInfo.text="Overview region RMS: ${VideoAudioLevels.levelText(VideoAudioLevels.barAt(overview,at).rms)}"
                    }
                    graph=VideoAudioLevelsView(this,overview){select(it)};body.addView(graph,LinearLayout.LayoutParams(-1,dp(156)))
                    body.addView(TextView(this).apply{text="Source time in seconds (${VideoTrimRange.format(clip.startMs)} to before ${VideoTrimRange.format(clip.endMs)})";setTextColor(Color.BLACK)})
                    body.addView(time);body.addView(selectedInfo)
                    body.addView(Button(this).apply{text="Review this time";setOnClickListener{runCatching{select(VideoCaptions.seconds(time.text.toString()))}.onFailure{time.error=it.message?:"Choose a time inside this range"}}})
                    val regions=VideoAudioLevels.topRegions(overview)
                    body.addView(TextView(this).apply{text=if(regions.isEmpty())"No reliable positive-energy regions are available in this overview."else"Largest-energy regions (averages)";setTextColor(Color.BLACK)})
                    for(region in regions)body.addView(Button(this).apply{text="${VideoCaptions.formatTime(region.startMs)} · ${VideoAudioLevels.levelText(region.rms)}";contentDescription="Select source region at ${VideoCaptions.formatTime(region.startMs)}, ${VideoAudioLevels.levelText(region.rms)}";setOnClickListener{select(region.startMs)}})
                    select(clip.startMs)
                    val review=android.app.AlertDialog.Builder(this).setTitle("Source audio levels · clip ${index+1}").setView(ScrollView(this).apply{addView(body)}).setNegativeButton("Close",null).setPositiveButton("Jump to selected time",null).create()
                    review.setOnShowListener{review.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener jumpAudio@{
                        if(!editorIdle()||!sameSnapshot()||cancellation.get()){review.dismiss();status.text="Video or session changed. Inspect audio again before jumping.";return@jumpAudio}
                        val at=runCatching{VideoCaptions.seconds(time.text.toString()).also{VideoAudioLevels.fraction(overview,it)}}.getOrElse{time.error=it.message?:"Choose a time inside this range";return@jumpAudio}
                        review.dismiss();runCatching{seekTimeline(VideoFilmstripScale.outputTime(clips,index,at))}
                            .onSuccess{status.text="Audio region selected. Timeline edits are unchanged."}.onFailure{status.text="The source time could not be opened. Timeline edits are unchanged."}
                    }};review.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);track(review)
                }.onFailure{status.text=(it.message?:"Source audio could not be inspected")+". Timeline edits are unchanged."}
            }
        },"noble-source-audio-levels").start()
    }

    private fun captionOptions() {
        if(!editorIdle()) return
        val current=project?:return;val index=selectedClip;val clip=current.segments[index]
        val choices=listOf("Add caption at playhead","Import SRT for this clip","Export project SRT","Clear this clip's captions","Review project caption readability","Find caption text in project","Copy project caption text") + clip.captions.map { "${VideoCaptions.formatTime(it.startMs)}  ${it.text.replace('\n',' ')}" }
        android.app.AlertDialog.Builder(this).setTitle("Timed captions · clip ${index+1}")
            .setItems(choices.toTypedArray()){_,choice->
                if(!editorIdle()||project!==current||selectedClip!=index||current.segments[index]!=clip)return@setItems
                when(choice){
                    0->editCaption(current,index,clip,null)
                    1->importCaptions(current,index,clip)
                    2->exportSubtitles()
                    3->android.app.AlertDialog.Builder(this).setTitle("Clear clip captions?").setMessage("Removes ${clip.captions.size} captions from this clip. Undo restores them.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Clear"){_,_->if(editorIdle()&&project===current&&selectedClip==index&&current.segments[index]==clip){current.setCaptions(index,emptyList());showClip();previewProject()}}.show()
                    4->captionReadability()
                    5->findCaptionText()
                    6->copyCaptionText()
                    else->editCaption(current,index,clip,choice-7)
                }
            }.setNegativeButton("Close",null).show()
    }

    private fun copyCaptionText(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList();val mode=accessMode;val owner=videoLookNamespace()?:return
        fun valid()=editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&accessMode==mode&&videoLookNamespace()==owner
        var prepared:VideoCaptionText.Result?=runCatching{VideoCaptionText.create(clips)}.getOrElse{status.text=it.message?:"Caption text could not be prepared.";return}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        val choice=android.widget.RadioGroup(this).apply{orientation=LinearLayout.HORIZONTAL}
        val plain=android.widget.RadioButton(this).apply{id=android.view.View.generateViewId();text="Text only";setTextColor(Color.BLACK);choice.addView(this)}
        val timed=android.widget.RadioButton(this).apply{id=android.view.View.generateViewId();text="Playback times";setTextColor(Color.BLACK);choice.addView(this)}
        plain.isChecked=true;body.addView(choice)
        val info=TextView(this).apply{setTextColor(Color.DKGRAY);body.addView(this)}
        body.addView(TextView(this).apply{text="Existing visible captions only, following edited trims and speed. Adjacent identical cues may merge; repeated clips remain. Times use playback milliseconds rounded outward. Use Export project SRT for numbered subtitle files. Copy writes to the system clipboard only when you tap Copy all text.";setTextColor(Color.DKGRAY)})
        val preview=TextView(this).apply{setTextColor(Color.BLACK);textSize=14f;setPadding(0,dp(12),0,0);contentDescription="Edited caption text preview";body.addView(this)}
        fun render(result:VideoCaptionText.Result){info.text="${result.cues} visible playback cues · ${result.text.codePointCount(0,result.text.length)} characters";preview.text=result.preview()}
        render(requireNotNull(prepared))
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Copy caption text").setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Close",null).setPositiveButton("Copy all text"){_,_->
                if(!valid()){status.text="Project or session changed. Reopen caption text before copying.";return@setPositiveButton}
                val result=prepared?:return@setPositiveButton
                runCatching{
                    val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager?:error("Clipboard unavailable")
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Edited caption text",result.text))
                }.onSuccess{status.text="Caption text copied. Project edits are unchanged."}.onFailure{status.text="Could not copy caption text. Try again."}
            }.create()
        choice.setOnCheckedChangeListener{_,checked->
            if(!valid()){status.text="Project or session changed. Reopen caption text.";dialog.dismiss();return@setOnCheckedChangeListener}
            runCatching{VideoCaptionText.create(clips,checked==timed.id)}.onSuccess{prepared=it;render(it);dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled=true}
                .onFailure{prepared=null;preview.text="";info.text="Caption text could not be prepared. Close and try again.";dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled=false}
        };dialog.show()
    }

    private fun findCaptionText(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList();val mode=accessMode;val owner=videoLookNamespace()?:return
        fun valid()=editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&accessMode==mode&&videoLookNamespace()==owner
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Find literal text in this project's visible captions. One result per matching cue, including repeated clips. Line breaks match spaces. This searches existing caption text only, on this device; it does not transcribe audio or search video pixels.";setTextColor(Color.DKGRAY)})
        val query=EditText(this).apply{hint="Caption text to find";contentDescription="Caption text to find";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(160));body.addView(this)}
        val matchCase=android.widget.CheckBox(this).apply{text="Match letter case";setTextColor(Color.BLACK);body.addView(this)}
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Find caption text").setView(body).setNegativeButton("Close",null).setPositiveButton("Find",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!valid()){dialog.dismiss();return@setOnClickListener}
            runCatching{VideoCaptionSearch.find(clips,query.text.toString(),matchCase.isChecked)}.onSuccess{matches->
                if(matches.isEmpty()){query.error="No visible caption matches. Try different text or check the trimmed ranges.";return@onSuccess}
                android.app.AlertDialog.Builder(this).setTitle("${matches.size} matching captions")
                    .setItems(matches.map{it.label()}.toTypedArray()){_,position->
                        if(!valid()){dialog.dismiss();status.text="Project or account changed. Find captions again.";return@setItems}
                        val match=matches[position]
                        android.app.AlertDialog.Builder(this).setTitle("Clip ${match.clipIndex+1} · caption ${match.cueIndex+1}")
                            .setMessage("${match.text}\n\nPlayback ${VideoCaptions.formatTime(match.outputUs/1000)} · original source ${VideoCaptions.formatTime(match.sourceMs)}")
                            .setNegativeButton("Back",null).setNeutralButton("Jump to cue"){_,_->
                                if(valid()){dialog.dismiss();jumpToCaption(match)}
                            }.setPositiveButton("Jump and edit"){_,_->
                                if(valid()){dialog.dismiss();jumpToCaption(match);editCaption(current,match.clipIndex,clips[match.clipIndex],match.cueIndex){valid()}}
                            }.show()
                    }.setNegativeButton("Back to find",null).show()
            }.onFailure{query.error=it.message?:"Enter valid caption text"}
        }};dialog.show()
    }
    private fun jumpToCaption(match:VideoCaptionSearch.Match){
        video.pause();stopProjectPreview();selectedClip=match.clipIndex;showClip()
        pendingSeekMs=match.sourceMs;if(sourceReady)video.seekTo(match.sourceMs.toInt())
        playheadUs=match.outputUs;timeline.playhead(playheadUs);updateTimecode()
        status.text="Jumped to clip ${match.clipIndex+1}, caption ${match.cueIndex+1}. Preview project to check the cue; edits are unchanged."
    }

    private fun captionReadability(){
        if(!editorIdle()||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList();val mode=accessMode
        val owner=videoLookNamespace()?:return
        fun valid()=editorIdle()&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&accessMode==mode&&videoLookNamespace()==owner
        runCatching{VideoCaptionReadability.inspect(clips).text()}.onSuccess{report->
            val body=TextView(this).apply{text=report;setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12));setTextIsSelectable(true);contentDescription="Caption readability report"}
            android.app.AlertDialog.Builder(this).setTitle("Caption readability")
                .setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setPositiveButton("Copy report"){_,_->
                    if(!valid()){status.text="Project or account changed. Reopen the report before copying.";return@setPositiveButton}
                    runCatching{
                        val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager?:error("Clipboard unavailable")
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Caption readability review",report))
                    }.onSuccess{status.text="Readability report copied. Captions and project edits are unchanged."}
                        .onFailure{status.text="Could not copy the report. Try again."}
                }.setNegativeButton("Close",null).show()
        }.onFailure{status.text="Caption report could not be prepared. Existing captions were kept."}
    }

    private fun editCaption(current:VideoEditProject,index:Int,clip:VideoSegment,cueIndex:Int?,canApply:()->Boolean={true}) {
        if(!canApply())return
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
        if(cueIndex!=null)builder.setNeutralButton("Delete"){_,_->if(canApply()&&editorIdle()&&project===current&&selectedClip==index&&current.segments[index]==clip){current.setCaptions(index,clip.captions.filterIndexed{i,_->i!=cueIndex});showClip();previewProject()}}
        val dialog=builder.create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!canApply()||!editorIdle()||project!==current||selectedClip!=index||current.segments[index]!=clip){dialog.dismiss();return@setOnClickListener}
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
        val bytes=runCatching{VideoCaptions.exportSrt(VideoExportSnapshot.clips(current.segments,selectedClip,from,to))}
            .getOrElse{status.text=it.message?:"Add captions first";return}
        video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)
        pendingSubtitleBytes=bytes;pendingSubtitleOriginals=sourceUris().values.toSet();importing=true
        try{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/x-subrip";putExtra(Intent.EXTRA_TITLE,"Noble-captions-copy.srt")},26)}
        catch(error:Exception){importing=false;pendingSubtitleBytes=null;pendingSubtitleOriginals=null;status.text="No app is available to save subtitles."}
    }

    private fun finishSubtitleExport(resultCode:Int,data:Intent?) {
        val bytes=pendingSubtitleBytes;val originals=pendingSubtitleOriginals;val destination=data?.data
        pendingSubtitleBytes=null;pendingSubtitleOriginals=null
        if(isDestroyed||isFinishing){importing=false;return}
        if(resultCode!=RESULT_OK||destination==null){
            importing=false
            if(bytes!=null && originals!=null)status.text="Subtitle save cancelled. Current edits are unchanged."
            return
        }
        if(bytes==null||originals==null){
            importing=false
            status.text="Subtitle save was interrupted. Export subtitles again. Check Files for an empty or partial copy."
            return
        }
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
            (if(previewPlayer != null) runCatching { splitPosition() }.getOrDefault(clip.startMs) else if(sourceReady) runCatching{video.currentPosition.toLong()}.getOrDefault(pendingSeekMs) else pendingSeekMs).coerceIn(0L, sourceDuration(clip.sourceId)-1),
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
    private fun importSoundtrack(){beginMediaPicker(VideoMediaImportKind.SOUNDTRACK)}

    private fun soundtrackExcerpt(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList()
        val track=current.soundtrack?:run{status.text="Add music or a voiceover first.";return}
        val mode=accessMode;val owner=videoLookNamespace()?:return
        fun valid()=editorIdle()&&workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&current.segments==clips&&current.soundtrack==track&&accessMode==mode&&videoLookNamespace()==owner
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Choose source-audio seconds, from 0 to ${String.format(java.util.Locale.ROOT,"%.3f",track.durationMs/1000.0)}. The excerpt starts at project time zero. Original clip audio stays attached; this edits no audio file.";setTextColor(Color.DKGRAY)})
        fun field(label:String,value:Long)=EditText(this).apply{hint=label;contentDescription=label;setSingleLine(true);setTextColor(Color.BLACK);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(String.format(java.util.Locale.ROOT,"%.3f",value/1000.0));body.addView(this)}
        body.addView(TextView(this).apply{text=runCatching{"Current timing\n"+VideoSoundtrackReport.inspect(clips,track).text()}.getOrElse{"Current soundtrack timing exceeds supported limits. Choose a longer excerpt or turn Loop off."};setTextColor(Color.DKGRAY)})
        val from=field("Soundtrack excerpt start, source seconds",track.startMs)
        val to=field("Soundtrack excerpt end, source seconds",track.endMs)
        val loop=android.widget.CheckBox(this).apply{text="Loop this excerpt to fill the video";setTextColor(Color.BLACK);isChecked=track.loop;body.addView(this)}
        body.addView(TextView(this).apply{text="When Loop is off, audio plays once and stops at the excerpt end or video end. Looping supports up to 1,000 repeats. Preview project to check cuts and the mix; speech is not analyzed.";setTextColor(Color.DKGRAY)})
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Soundtrack excerpt").setView(android.widget.ScrollView(this).apply{addView(body)})
            .setNegativeButton("Cancel",null).setNeutralButton("Full audio",null).setPositiveButton("Review",null).create()
        dialog.setOnShowListener{
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener{from.setText("0.000");to.setText(String.format(java.util.Locale.ROOT,"%.3f",track.durationMs/1000.0))}
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(!valid()){dialog.dismiss();return@setOnClickListener}
                runCatching{
                    track.copy(startMs=VideoCaptions.seconds(from.text.toString()),endMs=VideoCaptions.seconds(to.text.toString()),loop=loop.isChecked).also{
                        require(it!=track){"Choose a different range or loop setting"}
                        VideoTimeline.soundtrackRepeatCount(clips,it)
                        VideoDraftCodec.requireRecoverableCaptions(clips,!it.loop,it.hasExcerpt)
                    }
                }.onSuccess{next->
                    android.app.AlertDialog.Builder(this).setTitle("Use this soundtrack excerpt?")
                        .setMessage("${VideoSoundtrackReport.inspect(clips,next).text()}\n\n${VideoCaptions.formatTime(next.startMs)} → ${VideoCaptions.formatTime(next.endMs)} of the original audio, ${if(next.loop)"looping"else "playing once"} from project start. Volume and original clip audio stay unchanged. Undo restores the previous soundtrack range.")
                        .setNegativeButton("Keep editing",null).setPositiveButton("Apply excerpt"){_,_->
                            if(!valid()){dialog.dismiss();return@setPositiveButton}
                            runCatching{current.setSoundtrack(next)}.onSuccess{dialog.dismiss();showClip();status.text="Soundtrack excerpt applied. Preview project to check the mix; Undo restores the previous range."}
                                .onFailure{to.error="This range cannot be saved within project limits."}
                        }.show()
                }.onFailure{to.error=it.message?:"Choose a valid audio range"}
            }
        };dialog.show()
    }

    private fun visualStoryboard(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList();val sources=sourceUris();val durations=sourceDurations()
        val mode=accessMode;val owner=videoLookNamespace()?:return
        val plan=runCatching{VideoStoryboardPlan.create(clips)}.getOrElse{status.text="This timeline cannot be represented within storyboard limits.";return}
        val provenance=runCatching{app.honorable.editor.ImportedPhotoOriginals.validate(plan.shots.map{requireNotNull(sources[it.request.clip.sourceId]).toString()}.distinct())}.getOrElse{status.text="These originals cannot be safely carried into the photo workspace.";return}
        fun unchanged()=!isDestroyed&&!isFinishing&&workspaceActive&&project===current&&current.segments==clips&&accessMode==mode&&videoLookNamespace()==owner&&sourceUris()==sources
        android.app.AlertDialog.Builder(this).setTitle("Create visual storyboard?")
            .setMessage("Uses ${plan.shots.size} of ${plan.totalClips} clips: ${if(plan.representative)"12 evenly spaced representatives including first and last"else "every clip"}. Each image is the nearest decoded original frame around the middle of its trimmed source range, labelled with clip/title/source and playback time. Very short trims may resolve to a nearby frame. Video effects are excluded. Opens a separate local guest photo workspace; no account draft/privileges transfer. Save an image project or export there to keep the temporary PNG. Original videos and timeline edits are kept.")
            .setNegativeButton("Cancel",null).setPositiveButton("Create local storyboard"){_,_->
                if(!editorIdle()||!unchanged())return@setPositiveButton
                val cancellation=java.util.concurrent.atomic.AtomicBoolean(false);framePhotoCancellation=cancellation
                importing=true;video.pause();stopProjectPreview();status.text="Preparing local storyboard…"
                draftWorker.execute{
                    val result=runCatching{VideoStoryboardSheet.create(this,plan,sources,durations,{
                        check(!cancellation.get()){ "Storyboard cancelled" };check(VideoPrivateOwner.namespace(this,mode)==owner){"Account changed"}
                    }){index,total->runOnUiThread{if(framePhotoCancellation===cancellation&&workspaceActive&&!isDestroyed)status.text="Preparing storyboard frame $index/$total · originals kept"}}}
                    runOnUiThread{
                        if(framePhotoCancellation===cancellation){framePhotoCancellation=null;importing=false}
                        result.onSuccess{file->
                            if(cancellation.get()||!unchanged()){file.delete();if(!isDestroyed&&!isFinishing)status.text="Storyboard cancelled. Original videos and edits were kept.";return@onSuccess}
                            runCatching{
                                val image=androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",file)
                                startActivity(Intent(this,app.honorable.editor.PhotoEditorActivity::class.java).putExtra("uri",image.toString()).putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name)
                                    .putStringArrayListExtra(app.honorable.editor.ImportedPhotoOriginals.EXTRA,ArrayList(provenance)))
                            }.onSuccess{status.text="Storyboard opened in the separate local photo workspace. Timeline edits were kept."}
                                .onFailure{file.delete();status.text="Could not open the storyboard. Original videos and edits were kept."}
                        }.onFailure{if(!isDestroyed&&!isFinishing)status.text="Storyboard could not be prepared. Try smaller source videos; originals and edits were kept."}
                    }
                }
            }.show()
    }

    private fun sourceFramePhoto(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip;val clip=clips[index]
        val mode=accessMode;val owner=videoLookNamespace()?:return
        val source=sourceUris()[clip.sourceId]?:return
        val sourceDuration=sourceDuration(clip.sourceId)
        val provenance=runCatching{app.honorable.editor.ImportedPhotoOriginals.validate(listOf(source.toString()))}.getOrElse{status.text="This original cannot be safely carried into the photo workspace.";return}
        val plan=runCatching{VideoFramePhotoPlan(clip,splitPosition())}.getOrElse{status.text="Seek inside the selected clip before opening a frame photo.";return}
        fun unchanged()=!isDestroyed&&!isFinishing&&workspaceActive&&project===current&&current.segments==clips&&selectedClip==index&&accessMode==mode&&videoLookNamespace()==owner&&sourceUris()[clip.sourceId]==source
        android.app.AlertDialog.Builder(this).setTitle("Open source frame as photo?")
            .setMessage("Makes a separate local PNG from the nearest decoded original frame at ${VideoCaptions.formatTime(plan.sourceMs)}, up to 1920 pixels per edge. Video titles, captions, color, crop and motion effects are excluded. Opens the local guest image workspace, with no account draft or account privileges transferred. Save an image project or export a copy there to keep it; the starting PNG is temporary. Original video and timeline edits are kept.")
            .setNegativeButton("Cancel",null).setPositiveButton("Open local frame photo"){_,_->
                if(!editorIdle()||!unchanged())return@setPositiveButton
                val cancellation=java.util.concurrent.atomic.AtomicBoolean(false);framePhotoCancellation=cancellation
                importing=true;video.pause();stopProjectPreview();status.text="Preparing a local source frame…"
                draftWorker.execute{
                    val result=runCatching{VideoFramePhoto.extract(this,source,sourceDuration,plan){
                        check(!cancellation.get()){ "Frame extraction cancelled" }
                        check(VideoPrivateOwner.namespace(this,mode)==owner){"Account changed"}
                    }}
                    runOnUiThread{
                        if(framePhotoCancellation===cancellation){framePhotoCancellation=null;importing=false}
                        result.onSuccess{file->
                            if(cancellation.get()||!unchanged()){file.delete();if(!isDestroyed&&!isFinishing)status.text="Frame preparation cancelled. Original video and edits were kept.";return@onSuccess}
                            runCatching{
                                val image=androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",file)
                                startActivity(Intent(this,app.honorable.editor.PhotoEditorActivity::class.java)
                                    .putExtra("uri",image.toString()).putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name)
                                    .putStringArrayListExtra(app.honorable.editor.ImportedPhotoOriginals.EXTRA,ArrayList(provenance)))
                            }.onSuccess{status.text="Source frame opened in a separate local photo workspace. Video edits are kept."}
                                .onFailure{file.delete();status.text="Could not open the frame photo. Original video and edits were kept."}
                        }.onFailure{if(!isDestroyed&&!isFinishing)status.text="Could not prepare this frame. Seek another moment or try a smaller source video; originals and edits were kept."}
                    }
                }
            }.show()
    }

    private fun recordVoiceover(){
        if(!editorIdle()||isDestroyed||isFinishing)return
        val current=project?:return
        val owner=videoLookNamespace()?:run{status.text="Session changed. Reopen this video before recording.";return}
        voiceoverTarget=VoiceoverTarget(current,current.segments,current.soundtrack,selectedClip,accessMode,owner)
        importing=true;video.pause();stopProjectPreview()
        try{startActivityForResult(Intent(this,VoiceoverRecorderActivity::class.java).putExtra(EditorAccessMode.EXTRA,accessMode.name),28)}
        catch(_:Exception){importing=false;voiceoverTarget=null;status.text="Voiceover screen is unavailable. Existing edits were kept."}
    }
    private fun finishVoiceover(resultCode:Int,data:Intent?){
        val target=voiceoverTarget;voiceoverTarget=null;val uri=data?.data
        if(resultCode!=RESULT_OK||uri==null||target==null||isDestroyed||isFinishing){importing=false;return}
        fun current()=project===target.project&&target.project.segments==target.clips&&target.project.soundtrack==target.soundtrack&&selectedClip==target.selected&&accessMode==target.mode&&videoLookNamespace()==target.owner
        if(!current()){importing=false;status.text="Project or account changed. Saved voiceovers were kept.";return}
        status.text="Checking private voiceover on this device…"
        draftWorker.execute{
            val result=runCatching{
                require(uri==VoiceoverProvider.uri(this,target.owner,uri.lastPathSegment.orEmpty()))
                VideoPrivateOwner.requireReadable(this,target.owner)
                VideoSoundtrack(uri.toString(),inspectSoundtrack(uri),loop=false)
            }
            runOnUiThread{
                importing=false
                if(isDestroyed||isFinishing)return@runOnUiThread
                if(!current()){status.text="Project or account changed. Saved voiceovers were kept.";return@runOnUiThread}
                result.onSuccess{track->
                    fun apply(){
                        if(!current()){status.text="Project or account changed. Saved voiceover was kept.";return}
                        runCatching{VideoTimeline.soundtrackRepeatCount(target.clips,track);target.project.setSoundtrack(track)}
                            .onSuccess{showClip();status.text="Voiceover added once from project start at 25%. Preview the mix; Undo restores the previous audio."}
                            .onFailure{status.text="Voiceover could not be added within project limits. Saved take and previous audio were kept."}
                    }
                    if(target.soundtrack==null)apply()else android.app.AlertDialog.Builder(this).setTitle("Replace project soundtrack?")
                        .setMessage("This voiceover plays once from the beginning and replaces the current project soundtrack. Original clip audio is kept. Undo restores the previous soundtrack.")
                        .setNegativeButton("Keep current audio",null).setPositiveButton("Use voiceover"){_,_->apply()}.show()
                }.onFailure{status.text="Voiceover could not be added. Saved takes and project edits were kept."}
            }
        }
    }

    private fun finishSoundtrackImport(resultCode:Int,data:Intent?){finishMediaImport(VideoMediaImportKind.SOUNDTRACK,resultCode,data)}

    private fun validateSources(sources: List<VideoSource>) {
        sources.forEach { require(inspectSource(Uri.parse(it.uri)) == it.durationMs) { "Imported source changed or unavailable" } }
    }
    private fun applySavedDraft(current: VideoEditProject, saved: VideoDraft, undoable: Boolean):Boolean {
        val merged = importedSources + saved.sources.associateBy { it.id }
        require(saved.sources.all { importedSources[it.id]?.let { old -> old == it } ?: true })
        require(merged.size <= 31 && merged.values.map { it.uri }.distinct().size == merged.size)
        val durations = merged.mapValues { it.value.durationMs }
        if(undoable) current.applyDraft(saved.segments, durations, saved.soundtrack) else current.restore(saved.segments, durations, saved.soundtrack)
        importedSources.putAll(merged)
        selectedClip = saved.selectedClip
        // The canonical restore has committed. A preview failure must not be reported as a rejected edit.
        val previewReady=runCatching {
            showClip()
            start.progress = (saved.trimStartMs/1000).toInt()
            end.progress = ((saved.trimEndMs+999)/1000).toInt().coerceAtMost(end.max)
            video.seekTo(saved.previewPositionMs.toInt())
            syncSourcePlayhead()
        }.isSuccess
        pendingSeekMs = saved.previewPositionMs
        return previewReady
    }
    private fun importVideo(){beginMediaPicker(VideoMediaImportKind.VIDEO)}
    private fun finishImport(resultCode:Int,data:Intent?){finishMediaImport(VideoMediaImportKind.VIDEO,resultCode,data)}
    private fun beginMediaPicker(kind:VideoMediaImportKind) {
        if(!workspaceActive||!editorIdle()||recoveryBytes!=null||isDestroyed||isFinishing)return
        val current=project?:return
        if(kind==VideoMediaImportKind.VIDEO&&importedSources.size>=31){status.text="A project supports up to 32 source videos.";return}
        val target=VideoMediaImportTarget(kind,current,source.toString(),primaryDurationMs,accessMode.name,selectedClip,importedSources)
        mediaImportTarget=target;importing=true
        video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type=kind.mime
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            },kind.requestCode)
        }catch(_:Exception){finishMediaImportTarget(target);status.text="No document picker is available. Current edits were kept."}
    }
    private fun allowsMediaImportResult(requestCode:Int):Boolean =
        mediaImportTarget?.let{requestCode !in 22..28||requestCode==it.kind.requestCode}?:true
    private fun finishMediaImportTarget(target:VideoMediaImportTarget):Boolean {
        target.cancel()
        if(mediaImportTarget!==target)return false
        mediaImportTarget=null;importing=false
        return true
    }
    private fun mediaImportMatches(target:VideoMediaImportTarget):Boolean =
        target.matches(project,source.toString(),primaryDurationMs,accessMode.name,selectedClip,importedSources)
    private fun finishMediaImport(kind:VideoMediaImportKind,resultCode:Int,data:Intent?) {
        val target=mediaImportTarget
        if(target==null){
            if(!importing&&!isDestroyed&&!isFinishing&&::status.isInitialized)status.text="Import was interrupted. Choose ${kind.action} again. Current edits were kept."
            return
        }
        if(target.kind!=kind||!target.beginValidation())return
        val uri=data?.data
        if(resultCode!=RESULT_OK||uri==null||isDestroyed||isFinishing){finishMediaImportTarget(target);return}
        if(!importing||!mediaImportMatches(target)||saving||analyzing||draftBusy||pendingRange!=null||recoveryBytes!=null){
            finishMediaImportTarget(target)
            if(::status.isInitialized)status.text="Video changed. Choose ${kind.action} again. Current edits were kept."
            return
        }
        status.text="Checking imported ${if(kind==VideoMediaImportKind.VIDEO)"video" else "audio"} on this device..."
        try{draftWorker.execute {
            val result=runCatching {
                target.requireChecking();require(uri.scheme=="content")
                contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                require(contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission})
                target.requireChecking()
                val duration=if(kind==VideoMediaImportKind.VIDEO)inspectSource(uri)else inspectSoundtrack(uri)
                target.requireChecking();duration
            }
            runOnUiThread {
                if(mediaImportTarget!==target)return@runOnUiThread
                val current=target.project
                val canApply=workspaceActive&&!isDestroyed&&!isFinishing&&importing&&target.isChecking&&mediaImportMatches(target)&&
                    !saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null
                finishMediaImportTarget(target)
                if(!canApply){if(!isDestroyed&&!isFinishing)status.text="Import stopped because the video changed or the editor was away. Current edits were kept.";return@runOnUiThread}
                result.onSuccess { duration ->
                    val applied=runCatching {
                        if(kind==VideoMediaImportKind.VIDEO){
                            val existing=if(uri==source)"primary" else importedSources.values.firstOrNull{it.uri==uri.toString()}?.id
                            val id=existing?:java.util.UUID.randomUUID().toString()
                            if(existing!=null)require(sourceDuration(id)==duration){"Source changed"}
                            val metadata=if(id!="primary")VideoSource(id,uri.toString(),duration)else null
                            current.appendSource(id,duration)
                            metadata?.let{importedSources[id]=it}
                            selectedClip=current.segments.lastIndex
                        }else {
                            val track=VideoSoundtrack(uri.toString(),duration)
                            VideoTimeline.soundtrackRepeatCount(current.segments,track);current.setSoundtrack(track)
                        }
                    }
                    if(applied.isFailure){status.text="Media could not be added within source or project limits. Current edits were kept.";return@onSuccess}
                    val previewReady=runCatching{showClip()}.isSuccess
                    status.text=if(kind==VideoMediaImportKind.VIDEO){
                        if(previewReady)"Video added. Preview before exporting; Undo restores the previous timeline."
                        else "Video added; preview is unavailable. Undo restores the previous timeline."
                    }else {
                        if(previewReady)"Soundtrack selected at 25%. Preview the mix."
                        else "Soundtrack selected; preview is unavailable. Review the audio before exporting."
                    }
                }.onFailure{status.text="Choose readable media from a provider that keeps document access. Nothing was added."}
            }
        }}catch(_:java.util.concurrent.RejectedExecutionException){if(finishMediaImportTarget(target)&&::status.isInitialized)status.text="Import could not start. Current edits were kept."}
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EditorAccessMode.EXTRA,accessMode.name)
        recoveryStateBytes()?.let { outState.putByteArray("multiSourceProject", it) }
        if (::start.isInitialized) runCatching{outState.putInt("startProgress", start.progress)}
        if (::end.isInitialized) runCatching{outState.putInt("endProgress", end.progress)}
        if (::video.isInitialized) runCatching{outState.putLong("previewPositionMs", video.currentPosition.toLong())}
        outState.putInt("selectedClip", selectedClip)
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

    private fun recoveryStateBytes():ByteArray? {
        recoveryBytes?.let{return it.copyOf()}
        return runCatching{VideoDraftCodec.encode(currentDraft())}.recoverCatching {
            val current=requireNotNull(project);val clips=current.segments
            val index=selectedClip.coerceIn(clips.indices);val clip=clips[index]
            VideoDraftCodec.encode(VideoDraft(primaryDurationMs,clips.toList(),index,
                pendingSeekMs.coerceIn(clip.startMs,clip.endMs-1),clip.startMs,clip.endMs,
                importedSources.values.toList(),current.soundtrack))
        }.getOrNull()
    }
    private fun blockRecovery(message:String) {
        recoveryBlocked=true;recoveryChecking=false;draftBusy=true
        if(::recoveryPanel.isInitialized)recoveryPanel.visibility=View.VISIBLE
        if(::status.isInitialized)status.text="$message Recovery data is kept. Retry recovery or explicitly start from the original."
    }
    private fun applyPendingRecovery(current:VideoEditProject,saved:VideoDraft):Boolean {
        check(project===current){"Recovery target changed"}
        val previewReady=applySavedDraft(current,saved,false)
        recoveryBytes=null;recoveryBlocked=false;recoveryChecking=false;draftBusy=false
        if(::recoveryPanel.isInitialized)recoveryPanel.visibility=View.GONE
        return previewReady
    }
    private fun retryRecovery() {
        if(!workspaceActive||isDestroyed||isFinishing||recoveryChecking)return
        val current=project?:return;val bytes=recoveryBytes?:return
        val duration=primaryDurationMs
        val saved=runCatching{VideoDraftCodec.decode(bytes,duration)}.getOrNull()
        if(saved==null){blockRecovery("Session recovery is damaged or incompatible.");return}
        val primary=source.toString();val request=++recoveryRequest
        recoveryChecking=true;recoveryBlocked=false;draftBusy=true
        if(::recoveryPanel.isInitialized)recoveryPanel.visibility=View.GONE
        status.text="Checking recovered video sources. Recovery data is kept until restored."
        runCatching{video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        try{draftWorker.execute {
            val checked=runCatching{validateSavedMedia(saved)}
            runOnUiThread {
                if(request!=recoveryRequest||isDestroyed||isFinishing||project!==current||source.toString()!=primary||primaryDurationMs!=duration)return@runOnUiThread
                recoveryChecking=false
                if(!workspaceActive){blockRecovery("Recovery paused while the editor was away.");return@runOnUiThread}
                checked.onSuccess {
                    runCatching{applyPendingRecovery(current,saved)}
                        .onSuccess{ready->status.text=if(ready)"Project recovered. Preview before exporting." else "Project recovered; preview is unavailable. Your restored edits are kept."}
                        .onFailure{blockRecovery("The recovered timeline could not be applied.")}
                }.onFailure{blockRecovery("A recovered original is unavailable or changed.")}
            }
        }}catch(_:java.util.concurrent.RejectedExecutionException){blockRecovery("Recovery could not start.")}
    }
    private fun discardRecovery(current:VideoEditProject,expected:ByteArray):Boolean {
        if(!recoveryBlocked||recoveryChecking||project!==current||primaryDurationMs<=0||recoveryBytes?.contentEquals(expected)!=true)return false
        val fresh=VideoEditProject(primaryDurationMs)
        mediaImportTarget?.let{finishMediaImportTarget(it)}
        recoveryRequest++;recoveryBytes=null;recoveryBlocked=false;recoveryChecking=false;draftBusy=false
        project=fresh;importedSources.clear();selectedClip=0;pendingSeekMs=0
        return true
    }
    private fun confirmDiscardRecovery() {
        if(!workspaceActive||isDestroyed||isFinishing||!recoveryBlocked||recoveryChecking||recoveryDiscardDialog?.isShowing==true)return
        val current=project?:return;val expected=recoveryBytes?.copyOf()?:return
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Start from original?")
            .setMessage("Discard this session's recovery data and start a fresh timeline from the original video. Saved projects and original media are kept. This cannot be undone.")
            .setNegativeButton("Keep recovery",null).setPositiveButton("Discard recovery"){_,_->
                if(recoveryDiscardDialog!==dialog||!workspaceActive||isDestroyed||isFinishing||!discardRecovery(current,expected))return@setPositiveButton
                if(::recoveryPanel.isInitialized)recoveryPanel.visibility=View.GONE
                runCatching{showClip()}
                status.text="Recovery discarded by your choice. The original video is open; saved projects are kept."
            }.create()
        recoveryDiscardDialog=dialog
        dialog.setOnDismissListener{if(recoveryDiscardDialog===dialog)recoveryDiscardDialog=null}
        dialog.show()
    }
    private fun holdRecoveryResult(requestCode:Int):Boolean {
        if(recoveryBytes==null||requestCode !in 22..28)return false
        mediaImportTarget?.let{finishMediaImportTarget(it)}
        pendingRange=null;pendingSegments=null;pendingAudio=null;pendingSubtitleBytes=null;pendingSubtitleOriginals=null
        captionImportTarget=null;captionImportProject=null;voiceoverTarget=null;importing=false
        if(::status.isInitialized)status.text="Finish session recovery before importing or saving again. Check Files for an empty or partial copy."
        return true
    }

    private fun canApproveSavedRestore(captured:VideoCloseState,mode:EditorAccessMode):Boolean =
        workspaceActive&&!isDestroyed&&!isFinishing&&editorIdle()&&accessMode==mode&&captured.stillMatches(closeState())
    private fun requestSavedRestore(guest:Boolean,action:()->Unit) {
        if(!workspaceActive||!editorIdle()||isDestroyed||isFinishing||restoreReviewDialog?.isShowing==true)return
        if(guest!=(accessMode==EditorAccessMode.GUEST_BASIC)||project==null||primaryDurationMs<=0)return
        val captured=closeState();val mode=accessMode
        if(!captured.needsConfirmation()){action();return}
        runCatching{if(::video.isInitialized)video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle(if(guest)"Restore saved project?" else "Restore saved draft?")
            .setMessage("Restore replaces the timeline and selected range. Export a video copy first if you need to keep your current result. Timeline Undo lasts only in this editor session; a range you only selected is not kept by Undo.")
            .setNegativeButton("Keep current",null).setPositiveButton("Restore saved"){_,_->
                if(restoreReviewDialog!==dialog||!canApproveSavedRestore(captured,mode)){
                    if(workspaceActive&&!isDestroyed&&!isFinishing)status.text="Video or operation changed. Review the saved restore again."
                    return@setPositiveButton
                }
                action()
            }.create()
        restoreReviewDialog=dialog
        dialog.setOnDismissListener{if(restoreReviewDialog===dialog)restoreReviewDialog=null}
        dialog.show()
    }

    private fun closeState():VideoCloseState {
        val current=project;val clip=current?.segments?.getOrNull(selectedClip)
        val from=if(clip!=null&&::start.isInitialized)maxOf(start.progress*1000L,clip.startMs)else null
        val to=if(clip!=null&&::end.isInitialized)minOf(end.progress*1000L,clip.endMs)else null
        return VideoCloseState.capture(current,primaryDurationMs,selectedClip,from,to,!editorIdle(),recoveryBytes!=null,
            if(::source.isInitialized)source.toString()else null)
    }
    private fun requestClose() {
        if(!workspaceActive||isDestroyed||isFinishing||closeDialog?.isShowing==true)return
        val captured=closeState()
        if(!captured.needsConfirmation()){finish();return}
        runCatching { if(::video.isInitialized)video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview) }
        val working=!editorIdle()
        val message=if(recoveryBytes!=null)"Session recovery is still pending. Leaving discards this in-memory recovery. Saved projects and original media are kept."
            else if(working)"An operation is in progress. Leaving may interrupt it; check Files for an empty or partial copy. Existing saved projects and original media are kept."
            else "Current timeline edits may be lost when you leave. Keep editing to save a project or draft first. Existing saved projects, exported copies and original media are kept."
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Leave video editor?").setMessage(message)
            .setNegativeButton("Keep editing",null).setPositiveButton("Leave editor"){_,_->
                if(closeDialog!==dialog||!workspaceActive||isDestroyed||isFinishing)return@setPositiveButton
                if(!captured.stillMatches(closeState())){status.text="Video or operation changed. Review it before closing again.";return@setPositiveButton}
                finish()
            }.create()
        closeDialog=dialog
        dialog.setOnDismissListener { if(closeDialog===dialog)closeDialog=null }
        dialog.show()
    }
    @Deprecated("Legacy Back path for Android versions before the platform callback API")
    override fun onBackPressed(){requestClose()}

    override fun onResume(){super.onResume();workspaceActive=true;previewHandler.post(workspaceTick);if(recoveryBytes!=null&&!recoveryChecking&&!recoveryBlocked&&project!=null)retryRecovery()}
    override fun onPause() { versionShelf?.close();versionShelf=null;runCatching{restoreReviewDialog?.dismiss()};restoreReviewDialog=null;mediaImportTarget?.takeIf{it.isChecking}?.let{finishMediaImportTarget(it);if(::status.isInitialized)status.text="Import stopped while the editor was away. Choose ${it.kind.action} again."};runCatching{recoveryDiscardDialog?.dismiss()};recoveryDiscardDialog=null;runCatching{closeDialog?.dismiss()};closeDialog=null;titleStyleShelf?.close();titleStyleShelf=null;quietAudioGeneration++;quietAudioCancellation?.set(true);runCatching{quietAudioDialog?.dismiss()};quietAudioDialog=null;framePhotoCancellation?.set(true);workspaceActive=false;previewHandler.removeCallbacks(workspaceTick); if (::video.isInitialized) video.pause(); stopProjectPreview(); super.onPause() }
    override fun onDestroy() {
        versionShelf?.close();versionShelf=null
        runCatching{restoreReviewDialog?.dismiss()};restoreReviewDialog=null
        mediaImportTarget?.let{finishMediaImportTarget(it)}
        recoveryRequest++
        runCatching{recoveryDiscardDialog?.dismiss()};recoveryDiscardDialog=null
        runCatching{releaseEditorBack?.invoke()};releaseEditorBack=null
        runCatching{closeDialog?.dismiss()};closeDialog=null
        titleStyleShelf?.close();titleStyleShelf=null
        quietAudioGeneration++;quietAudioCancellation?.set(true);runCatching{quietAudioDialog?.dismiss()};quietAudioDialog=null
        framePhotoCancellation?.set(true)
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
