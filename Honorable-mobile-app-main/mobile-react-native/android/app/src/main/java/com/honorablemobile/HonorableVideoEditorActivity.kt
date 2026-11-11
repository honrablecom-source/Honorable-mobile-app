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
    private var projectBackupControls:VideoProjectBackupControls?=null
    private var darkHoldControls:VideoDarkHoldControls?=null
    private var audioOnsetControls:VideoAudioOnsetControls?=null
    private var frameArchiveControls:VideoFrameArchiveControls?=null
    private var timeSliceControls:VideoTimeSliceControls?=null
    private var framePhotoCancellation:java.util.concurrent.atomic.AtomicBoolean?=null
    // Shared ownership for original-source audio analysis jobs and their dialogs.
    private var quietAudioCancellation:AtomicBoolean?=null
    private var quietAudioGeneration=0L
    private var versionShelf:VideoProjectVersionShelf?=null
    private var titleStyleShelf:VideoTitleStyleShelf?=null
    private var timelineCaptionImport:VideoTimelineCaptionControls?=null
    private var captionSyncControls:VideoCaptionSyncControls?=null
    private var speechControls:VideoSpeechControls?=null
    private var visualTextControls:VideoVisualTextControls?=null
    private var translationControls:VideoTranslationControls?=null
    private var captionReviewGeneration=0L
    private var captionReviewDialogs:MutableSet<android.app.AlertDialog>?=null
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
    private var videoSaveRequest:VideoSaveRequest?=null
    private var nextVideoSaveCode=VideoSaveRequest.FIRST_CODE
    private var videoSaveNotice:Pair<VideoSaveRequest,String>?=null
    private var interruptedVideoSave=false
    private var interruptedVideoSaveCode:Int?=null
    private var restoreVideoSaveControls=false
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
        restoreVideoSaveState(savedInstanceState)
        accessMode = runCatching { EditorAccessMode.fromExtra(intent.getStringExtra(EditorAccessMode.EXTRA)) }.getOrElse { finish(); return }
        val restoreMode=savedInstanceState?.getString(EditorAccessMode.EXTRA)
        val sameMode=restoreMode==accessMode.name||(restoreMode==null&&accessMode==EditorAccessMode.ACCOUNT)
        recoveryBytes = if(sameMode)savedInstanceState?.getByteArray("multiSourceProject")?.copyOf()else null
        source = Uri.parse(intent.getStringExtra("uri") ?: run { finish(); return })
        if(savedInstanceState==null&&intent.hasExtra(VideoProjectBackupStore.EXTRA)) {
            val owned=runCatching {
                require(accessMode==EditorAccessMode.GUEST_BASIC)
                val id=requireNotNull(intent.getStringExtra(VideoProjectBackupStore.EXTRA))
                val bytes=requireNotNull(intent.getByteArrayExtra(VideoProjectBackupStore.DRAFT_EXTRA))
                require(bytes.size in 1..VideoProjectBackup.MAX_PROJECT&&VideoProjectBackupStore(this).matches(id,source.toString(),bytes))
                bytes
            }.getOrElse{finish();return}
            recoveryBytes=owned.copyOf()
        }
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
        icon(header,"Undo video edit","Undo"){if(beginImmediateVideoAction()&&project?.undo()==true){selectedClip=selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex);showClip()}}
        icon(header,"Redo video edit","Redo"){if(beginImmediateVideoAction()&&project?.redo()==true){selectedClip=selectedClip.coerceAtMost(requireNotNull(project).segments.lastIndex);showClip()}}
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
        listOf(.5f,1f,1.5f,2f).forEach{rate->speeds.addView(Button(this).apply{text="${rate}x";setOnClickListener{if(beginImmediateVideoAction()){project?.setSpeed(selectedClip,rate);showClip();status.text="Playback speed ${rate}x"}}},LinearLayout.LayoutParams(0,dp(48),1f))};speedTools.addView(speeds)
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
        projectTools.addView(Button(this).apply{text="Keep or export this video";setOnClickListener{videoFinishGuide()}})
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
                if (!canRequestTimelineCaptionImport() || durationMs <= 0 || !sourceReady) return@setOnClickListener
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
            clipActions.addView(Button(this).apply { text = title; setOnClickListener { performImmediateVideoAction(action) } },
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
            if(!beginImmediateVideoAction())return@setOnClickListener
            val clip = project?.segments?.getOrNull(selectedClip) ?: return@setOnClickListener
            runCatching { project?.trim(selectedClip, maxOf(start.progress * 1_000L, clip.startMs), minOf(end.progress * 1_000L, clip.endMs)) }
                .onSuccess { showClip() }.onFailure { status.text = "Select a valid range inside this clip." }
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        extraActions.addView(Button(this).apply { text = "Duplicate"; setOnClickListener {
            if(!beginImmediateVideoAction())return@setOnClickListener
            runCatching { requireNotNull(project).duplicate(selectedClip) }
                .onSuccess { selectedClip++; showClip() }.onFailure { status.text = "Cannot duplicate this clip. Check the project clip limit." }
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        extraActions.addView(Button(this).apply { text = "Mute"; setOnClickListener {
            if(!beginImmediateVideoAction())return@setOnClickListener
            project?.setVolume(selectedClip, 0f); showClip()
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(extraActions)
        controls.addView(Button(this).apply { text="Precise trim";setOnClickListener{preciseTrim()} })
        controls.addView(clipActions)
        val historyActions = LinearLayout(this)
        fun historyButton(title: String, action: () -> Unit) {
            historyActions.addView(Button(this).apply { text = title; setOnClickListener { performImmediateVideoAction(action) } },
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
            audioActions.addView(Button(this).apply { text = title; setOnClickListener { if(canRequestTimelineCaptionImport())action() } },
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
            if(!beginImmediateVideoAction()) return@setOnClickListener
            val current = project ?: return@setOnClickListener
            val clip = current.segments[selectedClip]
            current.setFadeEdges(selectedClip,!clip.fadeEdges); showClip()
            status.text = if(clip.fadeEdges) "Video fade removed." else "Fade through black added. Preview the project to check it."
        } },LinearLayout.LayoutParams(0,-2,1f))
        creativeTools.addView(creative)
        creativeTools.addView(Button(this).apply{text="Title style";setOnClickListener{titleStyle()}})
        creativeTools.addView(Button(this).apply{text="Saved title styles";setOnClickListener{savedTitleStyles()}})
        creativeTools.addView(Button(this).apply{text="AI captions to Spanish";setOnClickListener{requestCaptionTranslation()}})
        creativeTools.addView(Button(this).apply{text="AI read video text";setOnClickListener{requestVisualTextCaptions()}})
        creativeTools.addView(Button(this).apply{text="AI speech captions";setOnClickListener{requestSpeechCaptions()}})
        creativeTools.addView(Button(this).apply{text="Captions";setOnClickListener{captionOptions()}})
        creativeTools.addView(Button(this).apply { text = "Script to captions"; setOnClickListener { scriptCaptionOptions() } })
        val music = LinearLayout(this)
        fun musicButton(title: String, action: () -> Unit) {
            music.addView(Button(this).apply { text = title; setOnClickListener {
                if(canRequestTimelineCaptionImport()) action()
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
        if(!canRequestTimelineCaptionImport())return
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
        flushVideoSaveNotice();syncVideoSaveControls()
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

    private fun canReviewSceneResult(review:VideoCaptionReviewState,current:VideoEditProject,selection:VideoSceneSelection)=
        project===current&&canApplyCaptionReview(review)&&selectedClip==selection.selectedIndex&&current.segments==selection.clips

    private fun suggestScene() {
        if(!allowOnDeviceAi())return
        if (analyzing || saving || draftBusy || importing || pendingRange != null || durationMs <= 0) return
        val review=captureCaptionReview()?:return
        val current=project?:return
        val selection = runCatching { VideoSceneSelection.capture(current.segments, selectedClip,
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
                if (!canReviewSceneResult(review,current,selection)) return@runOnUiThread
                if(analysisCancellation.get()){status.text="Analysis cancelled. Your project is unchanged.";return@runOnUiThread}
                if (!selection.stillMatches(current.segments, selectedClip,
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

    private fun toggleAudioFade(){if(beginImmediateVideoAction()){val current=project?:return;current.setProjectAudioFade(!current.segments.first().audioFade);showClip();status.text="Project audio fade ${if(current.segments.first().audioFade)"on" else "off"}. Preview project to check the mix."}}
    private fun openInspector(){
        if(!editorIdle())return;val current=project?:return
        fun t(label:String,run:()->Unit)=app.honorable.editor.NativeInspectorPanel.Tool(label,run)
        app.honorable.editor.NativeInspectorPanel.show(this,"Video inspector","Clip ${selectedClip+1}/${current.segments.size} · ${current.totalDurationMs/1000f}s project",listOf(
            "Picture" to listOf(t("Precise trim"){preciseTrim()},t("Slip source window"){slipSourceWindow()},t("Replace clip footage"){clipReplacement()},t("Roll cut with next clip"){rollCut()},t("Color controls"){editColor()},t("Motion pan and zoom"){editMotion()},t("White balance"){editColor(true)},t("Rotate / mirror"){transformVideo()},t("Copy color to all"){copyGradeToAll()},t("Saved color looks"){savedColorLooks()}),
            "Titles & audio" to listOf(t("Edit title"){editTitle()},t("Title style"){titleStyle()},t("Saved title styles"){savedTitleStyles()},t("Timed captions"){captionOptions()},t("AI speech captions"){requestSpeechCaptions()},t("AI read video text"){requestVisualTextCaptions()},t("AI captions to Spanish"){requestCaptionTranslation()},t("Suggest audio-onset cuts"){sourceAudioOnsetCuts()},t("Combine bilingual captions"){bilingualCaptions()},t("Find caption text"){findCaptionText()},t("Copy caption text"){copyCaptionText()},t("Script to captions"){scriptCaptionOptions()},t("Project audio fade"){toggleAudioFade()},t("Remove quiet pauses"){quietPauseOptions()},t("Inspect source audio levels"){sourceAudioLevels()},t("Match clip audio level"){matchClipAudio()},t("Record voiceover"){recordVoiceover()},t("Soundtrack excerpt"){soundtrackExcerpt()}),
            "Project" to (listOf(t("Keep or export"){videoFinishGuide()},t("Back up editable project"){backupEditableProject()},t("Open video backups"){openVideoBackups()},t("Build a montage"){montageOptions()},t("Keep playback excerpt"){playbackExcerpt()},t("Move a clip group"){requestClipBlockMove()},t("Timeline report"){timelineReport()},t("Repeated source footage"){repeatedFootage()},t("Review sampled dark holds"){reviewDarkHolds()},t("Remove repeated footage"){uniqueFootage()},t("Build camera-switch edit"){multicamAssembly()},t("Fit all shots to duration"){fitAllShotsDuration()},t("Import video cut list"){importVideoCutList()},t("Silence playback ranges"){silencePlaybackRanges()},t("Saved video versions"){savedVideoVersions()},t("Clip chapter list"){chapterList()},t("Split at pasted playback times"){playbackCuts()},t("Caption translation worksheet"){captionWorksheet()},t("Copy CMX3600 cut list"){videoEdl()},t("Open source frame as photo"){sourceFramePhoto()},t("Create video time-slice photo"){videoTimeSlicePhoto()},t("Export frame sequence archive"){videoFrameArchive()},t("Create visual storyboard"){visualStoryboard()},t("Moment notes"){momentNotes()},t("Import moment notes"){importMomentNotes()},t("Copy moment notes"){copyMomentNotes()},t("Highlights from notes"){momentHighlights()},t("Output format"){outputFormat()},t("Preview project"){previewProject()}) + if(accessMode==EditorAccessMode.GUEST_BASIC)listOf(t("Save project"){guestProjectOperation(false)},t("Restore project"){guestProjectOperation(true)},t("Delete saved project"){deleteGuestProject()})else emptyList()),
            "Batch editing" to VideoBatchTool.values().map{kind->t(kind.title){batchEdit(kind)}},
            "On-device AI" to (if(accessMode.allowsOnDeviceAi)listOf(t("Video looks"){videoLookOptions()},t("Rough cut"){roughCutOptions()})else emptyList())
        ).filter{it.second.isNotEmpty()})
    }
    private fun commitSoundtrackReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,clip:VideoSegment,track:VideoSoundtrack,value:Float?):Boolean =
        commitInspectorReview(dialog,review,current,index,clip){
            require(current.soundtrack==track){"Soundtrack changed. Open soundtrack controls again."}
            require(value==null||value in 0f..1f)
            if(value!=track.volume)current.setSoundtrack(value?.let{track.copy(volume=it)})
        }
    private fun chooseSoundtrackAudio(remove:Boolean){
        val review=captureCaptionReview()?:return
        val current=project?:return;val track=current.soundtrack?:run{status.text="Add a soundtrack first.";return}
        val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        fun applyVolume(dialog:android.app.AlertDialog,value:Float?){
            if(!canUseInspectorReview(dialog,review))return
            runCatching{check(commitSoundtrackReview(dialog,review,current,index,clip,track,value)){"Video or soundtrack changed. Open soundtrack controls again."}}
                .onSuccess{
                    if(value==track.volume){status.text="Soundtrack volume is unchanged.";return@onSuccess}
                    val refreshed=runCatching{showClip();if(value!=null)previewComposition(current.segments,null)}.isSuccess
                    status.text=if(value==null){if(refreshed)"Soundtrack removed from this edit. Undo restores it." else "Soundtrack removed. Preview is unavailable; Undo restores it."}
                        else if(refreshed)"Soundtrack volume applied. Undo restores the previous mix." else "Soundtrack volume applied. Preview is unavailable; Undo restores the previous mix."
                }.onFailure{status.text=it.message?:"Could not change the soundtrack. Existing edits were kept."}
        }
        if(remove){
            lateinit var dialog:android.app.AlertDialog
            dialog=android.app.AlertDialog.Builder(this).setTitle("Remove soundtrack?")
                .setMessage("Removes the separate music or voiceover from this edit. Clip source audio and the original audio file are kept. Undo restores the soundtrack and its settings.")
                .setNegativeButton("Keep soundtrack",null).setPositiveButton("Remove"){_,_->applyVolume(dialog,null)}.create()
            showInspectorReview(dialog,review)
            return
        }
        val choices=mutableListOf(1f,.5f,.25f,0f)
        val labels=mutableListOf("100% · source level","50% · half level","25% · quarter level","0% · mute")
        if(track.volume !in choices){choices.add(track.volume);labels.add(String.format(java.util.Locale.ROOT,"Keep current volume (%.1f%%)",track.volume*100))}
        val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(8));setBackgroundColor(Color.WHITE)}
        title.addView(TextView(this).apply{text="Soundtrack volume";textSize=18f;setTextColor(Color.BLACK)})
        title.addView(TextView(this).apply{text="Changes separate music or voiceover only. Clip source audio is kept. Review the project preview to check the mix.";textSize=13f;setTextColor(Color.DKGRAY);setPadding(0,dp(8),0,0)})
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setCustomTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(),choices.indexOf(track.volume)){_,which->
                if(!canUseInspectorReview(dialog,review)){dialog.dismiss();return@setSingleChoiceItems}
                val value=choices.getOrNull(which)?:return@setSingleChoiceItems
                applyVolume(dialog,value);dialog.dismiss()
            }.setNegativeButton("Cancel",null).create()
        showInspectorReview(dialog,review)
    }
    private fun commitProjectReset(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject):Boolean {
        if(inspectorReviewDialog!==dialog)return false
        val index=selectedClip
        val clip=current.segments.getOrNull(index)?:run{inspectorReviewDialog=null;return false}
        return commitInspectorReview(dialog,review,current,index,clip){current.reset()}
    }
    private fun confirmProjectReset(){
        val review=captureCaptionReview()?:return
        val current=project?:return
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Reset video edits?")
            .setMessage("Returns to the full original first video at normal speed and source volume. Removes added clips, titles, captions, color, framing, motion, fades and the separate soundtrack from this edit. Original media and saved projects stay on your device. Undo restores the previous edit.")
            .setNegativeButton("Keep editing",null).setPositiveButton("Reset edits"){_,_->
                if(!canUseInspectorReview(dialog,review))return@setPositiveButton
                val before=current.segments;val music=current.soundtrack
                runCatching{commitProjectReset(dialog,review,current)}
                    .onSuccess{applied->if(applied){
                        val unchanged=current.segments==before&&current.soundtrack==music
                        selectedClip=0;val ready=runCatching{showClip()}.isSuccess
                        status.text=if(unchanged){if(ready)"Video edits already match the original. No edit was added."else"Video edits already match the original. Preview is unavailable; no edit was added."}
                            else if(ready)"Video edits reset. Undo restores the previous edit."else"Video edits reset, but preview is unavailable. Undo restores the previous edit."
                    }}
                    .onFailure{if(workspaceActive)status.text="Could not reset this edit. Try reopening the video."}
            }.create()
        showInspectorReview(dialog,review)
    }
    private fun commitPlaybackReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,clip:VideoSegment,speed:Boolean,value:Float):Boolean =
        commitInspectorReview(dialog,review,current,index,clip){
            if(speed){require(value in setOf(.5f,1f,1.5f,2f));if(value!=clip.speed)current.setSpeed(index,value)}
            else {require(value in 0f..1f);if(value!=clip.volume)current.setVolume(index,value)}
        }
    private fun chooseClipPlayback(speed:Boolean){
        val review=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip;val clip=clips.getOrNull(index)?:return
        val before=if(speed)clip.speed else clip.volume
        val choices=if(speed)mutableListOf(.5f,1f,1.5f,2f)else mutableListOf(1f,.5f,0f)
        val labels=if(speed)mutableListOf("0.5× · half speed","1× · normal speed","1.5× · faster","2× · double speed")else mutableListOf("100% · source level","50% · half level","0% · mute")
        if(before !in choices){choices.add(before);labels.add(String.format(java.util.Locale.ROOT,"Keep current volume (%.1f%%)",before*100))}
        val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(8));setBackgroundColor(Color.WHITE)}
        title.addView(TextView(this).apply{text="Clip ${index+1} ${if(speed)"speed"else "source volume"}";textSize=18f;setTextColor(Color.BLACK)})
        title.addView(TextView(this).apply{text=if(speed)"Changes this clip's playback duration. Captions follow edited timing. Review the rendered preview after choosing."else "Changes this clip's source audio if present. Separate soundtrack volume is kept. Review the preview after choosing.";textSize=13f;setTextColor(Color.DKGRAY);setPadding(0,dp(8),0,0)})
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setCustomTitle(title).setSingleChoiceItems(labels.toTypedArray(),choices.indexOf(before)){_,which->
            if(!canUseInspectorReview(dialog,review)){dialog.dismiss();return@setSingleChoiceItems}
            val value=choices.getOrNull(which)?:return@setSingleChoiceItems
            runCatching{check(commitPlaybackReview(dialog,review,current,index,clip,speed,value)){"Video or session changed. Open these clip controls again."}}
                .onSuccess{
                    if(value==before)status.text=if(speed)"Clip speed is unchanged." else "Clip source volume is unchanged."
                    else {
                        val refreshed=runCatching{showClip();previewComposition(listOf(current.segments[index]),index)}.isSuccess
                        val action=if(speed)"Clip speed" else "Clip source volume"
                        status.text=if(refreshed)"$action applied. Undo restores the previous setting." else "$action applied. Preview is unavailable; Undo restores the previous setting."
                    }
                }.onFailure{status.text=it.message?:"Could not change this clip. Existing edits were kept."}
            dialog.dismiss()
        }.setNegativeButton("Cancel",null).create()
        showInspectorReview(dialog,review)
    }
    private fun editMotion(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val dialog=VideoMotionControls.create(this,clip,{canUseInspectorReview(it,review)}) applyMotion@{owner,motion->
            if(motion==clip.motion){inspectorReviewDialog=null;status.text="Clip motion is unchanged.";return@applyMotion}
            check(commitInspectorReview(owner,review,current,index,clip){current.setMotion(index,motion)}){"Video or session changed. Open motion again."}
            val refreshed=runCatching{showClip();previewComposition(listOf(current.segments[index]),index)}.isSuccess
            status.text=if(refreshed)"Motion applied. Undo restores the previous motion."else"Motion applied. Preview is unavailable; Undo restores the previous motion."
        }
        showInspectorReview(dialog,review)
    }
    private fun beginImmediateVideoAction():Boolean {
        if(!canRequestTimelineCaptionImport()||project?.segments?.getOrNull(selectedClip)==null)return false
        invalidateCaptionReviews()
        return true
    }
    private fun performImmediateVideoAction(action:()->Unit):Boolean {
        if(!beginImmediateVideoAction())return false
        action()
        return true
    }
    private fun editorIdle()=!saving&&!analyzing&&!draftBusy&&!importing&&pendingRange==null

    private fun timelineReport(){
        if(!editorIdle())return
        val clips=project?.segments?:return
        runCatching{VideoRefinementTools.report(clips).text()}.onSuccess{text->
            android.app.AlertDialog.Builder(this).setTitle("Timeline report").setMessage(text).setPositiveButton("Done",null).show()
        }.onFailure{status.text=it.message?:"Timeline report is unavailable."}
    }

    private fun canUseVersionReview(shelf:VideoProjectVersionShelf,review:VideoCaptionReviewState)=
        versionShelf===shelf&&canApplyCaptionReview(review)
    private fun applyVersionReview(shelf:VideoProjectVersionShelf,review:VideoCaptionReviewState,current:VideoEditProject,saved:VideoDraft):Boolean {
        check(versionShelf===shelf){"Saved version review is no longer current."}
        versionShelf=null // Consume only this owner, including failed approval; Undo cannot revive it.
        check(canApplyCaptionReview(review)&&project===current){"Project or session changed. Open saved versions again."}
        val primary=requireNotNull(review.sources["primary"]).toString()
        return applySavedDraft(current,VideoVersionSources.reconcile(primary,importedSources,saved),true)
    }
    private fun savedVideoVersions() {
        val review=captureCaptionReview()?:return
        val current=project?:return;val primary=source.toString()
        val draft=runCatching{VideoProjectVersion.snapshot(currentDraft())}.getOrElse{status.text="Choose a valid range before using saved versions.";return}
        versionShelf?.close()
        runCatching{video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        lateinit var shelf:VideoProjectVersionShelf
        shelf=VideoProjectVersionShelf(this,review.mode,primary,draft,
            isCurrent={canUseVersionReview(shelf,review)},
            busy={value->if(versionShelf===shelf)draftBusy=value},message={text->if(versionShelf===shelf)status.text=text},
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
            apply=applyVersion@{saved->
                if(versionShelf!==shelf)return@applyVersion
                runCatching{applyVersionReview(shelf,review,current,saved)}
                    .onSuccess{ready->status.text=if(ready)"Reviewed saved edit applied. Timeline Undo restores prior edits." else "Reviewed saved edit applied; preview is unavailable. Timeline Undo restores prior edits."}
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

    private var lookReviewRequest:VideoLookReview?=null
    private var lookReviewDialog:android.app.AlertDialog?=null
    private fun canUseLookStage(dialog:android.app.AlertDialog,request:VideoLookReview,review:VideoCaptionReviewState)=
        lookReviewRequest===request&&lookReviewDialog===dialog&&request.owns(dialog)&&activeCaptionReviewDialog(dialog,review)
    private fun requireLookStage(dialog:android.app.AlertDialog,request:VideoLookReview,review:VideoCaptionReviewState){
        check(lookReviewRequest===request&&lookReviewDialog===dialog&&(request.owns(dialog)||request.isExecuting(dialog))&&activeCaptionReviewDialog(dialog,review)){"Video or session changed. Open saved looks again."}
    }
    private fun showLookStage(dialog:android.app.AlertDialog,request:VideoLookReview,review:VideoCaptionReviewState,onDismiss:()->Unit={}):Boolean {
        if(lookReviewRequest!==request||!canApplyCaptionReview(review)||!request.open(dialog)){request.close();onDismiss();dialog.dismiss();return false}
        val previous=lookReviewDialog;lookReviewDialog=dialog
        showCaptionReview(dialog,review){
            request.dismiss(dialog)
            if(lookReviewRequest===request&&request.isClosed)lookReviewRequest=null
            if(lookReviewDialog===dialog)lookReviewDialog=null
            onDismiss()
        }
        if(previous!==dialog)previous?.dismiss()
        return !request.isClosed
    }
    private fun performLookReview(dialog:android.app.AlertDialog,request:VideoLookReview,review:VideoCaptionReviewState,action:(()->Unit)->Unit):Boolean {
        if(!canUseLookStage(dialog,request,review))return false
        val guard={requireLookStage(dialog,request,review)}
        return request.execute(dialog,guard){action(guard)}
    }
    private fun savedColorLooks(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return
        val music=current.soundtrack
        val store=VideoColorLookStore(java.io.File(noBackupFilesDir,"video-color-looks"),review.namespace)
        lookReviewRequest?.close();lookReviewDialog?.dismiss()
        val request=VideoLookReview();lookReviewRequest=request
        fun selectLook(delete:Boolean){
            val catalog=runCatching{check(canApplyCaptionReview(review));store.load().also{check(canApplyCaptionReview(review))}}.getOrElse{status.text=it.message?:"Saved video looks are unavailable.";return}
            if(catalog.looks.isEmpty()){status.text="No saved video color looks yet. Save this clip's color first.";return}
            lateinit var chooser:android.app.AlertDialog
            chooser=android.app.AlertDialog.Builder(this).setTitle(if(delete)"Delete saved color look" else "Apply saved color look")
                .setItems(catalog.looks.map{it.name}.toTypedArray()){_,chosen->
                    if(!canUseLookStage(chooser,request,review))return@setItems
                    val look=catalog.looks.getOrNull(chosen)?:return@setItems
                    lateinit var confirmation:android.app.AlertDialog
                    confirmation=android.app.AlertDialog.Builder(this).setTitle(if(delete)"Delete ${look.name}?" else "Apply ${look.name}?")
                        .setMessage(if(delete)"Removes this local color recipe. Current edits and original media stay unchanged." else "Replaces only the selected clip's color and white balance. Timing, captions, motion, framing and audio stay unchanged. Undo restores the previous color.")
                        .setNegativeButton("Cancel",null).setPositiveButton(if(delete)"Delete look" else "Apply and preview"){_,_->
                            val result=runCatching{check(performLookReview(confirmation,request,review){guard->
                                if(delete)store.remove(look,guard)
                                else {
                                    VideoLookReview.requireAvailable(store.load(),look);guard()
                                    current.applyDraft(clips.mapIndexed{i,value->if(i==index)look.applyTo(value)else value},soundtrack=music)
                                }
                            }){"Video or session changed. Open saved looks again."}}
                            result.onSuccess{
                                if(delete)status.text="Saved color look deleted. Current edits are unchanged."
                                else runCatching{showClip();previewComposition(listOf(current.segments[index]),index)}
                                    .onSuccess{status.text="Saved color look applied. Undo restores the previous color."}
                                    .onFailure{status.text="Saved color look applied, but preview is unavailable. Undo restores the previous color."}
                            }.onFailure{status.text=it.message?:"Could not use this look. Open saved looks again."}
                        }.create()
                    showLookStage(confirmation,request,review)
                }.setNegativeButton("Cancel",null).create()
            showLookStage(chooser,request,review)
        }
        lateinit var menu:android.app.AlertDialog
        menu=android.app.AlertDialog.Builder(this).setTitle("Saved color looks")
            .setItems(arrayOf("Save this clip's color","Apply a saved look","Delete a saved look")){_,action->
                if(!canUseLookStage(menu,request,review))return@setItems
                when(action){
                    0->{
                        val name=EditText(this).apply{hint="Look name";contentDescription="Video color look name";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(40))}
                        val dialog=android.app.AlertDialog.Builder(this).setTitle("Save video color look").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save on this device",null).create()
                        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                            if(!canUseLookStage(dialog,request,review)){dialog.dismiss();status.text="Video or session changed. Open saved looks again.";return@setOnClickListener}
                            val prepared=runCatching{
                                val look=NamedVideoColorLook(name.text.toString().trim(),clip.grade)
                                requireLookStage(dialog,request,review);store.load().add(look);requireLookStage(dialog,request,review);look
                            }
                            if(prepared.isFailure){name.error=prepared.exceptionOrNull()?.message?:"Could not prepare this look";return@setOnClickListener}
                            val result=runCatching{check(performLookReview(dialog,request,review){guard->store.add(prepared.getOrThrow(),guard)}){"Video or session changed. Open saved looks again."}}
                            dialog.dismiss()
                            status.text=if(result.isSuccess)"Video color look saved on this device." else (result.exceptionOrNull()?.message?:"Could not save. Open saved looks again.")
                        }}
                        showLookStage(dialog,request,review){name.text?.clear()}
                    }
                    1->selectLook(false)
                    2->selectLook(true)
                }
            }.setNegativeButton("Cancel",null).create()
        showLookStage(menu,request,review)
    }

    private var momentReviewRequest:VideoActionReview?=null
    private var momentReviewDialog:android.app.AlertDialog?=null
    private fun canUseMomentStage(dialog:android.app.AlertDialog,request:VideoActionReview,review:VideoCaptionReviewState)=
        momentReviewRequest===request&&momentReviewDialog===dialog&&request.owns(dialog)&&activeCaptionReviewDialog(dialog,review)
    private fun requireMomentStage(dialog:android.app.AlertDialog,request:VideoActionReview,review:VideoCaptionReviewState){
        check(momentReviewRequest===request&&momentReviewDialog===dialog&&(request.owns(dialog)||request.isExecuting(dialog))&&activeCaptionReviewDialog(dialog,review)){"Video or session changed. Open moment notes again."}
    }
    private fun showMomentStage(dialog:android.app.AlertDialog,request:VideoActionReview,review:VideoCaptionReviewState,onDismiss:()->Unit={}):Boolean {
        if(momentReviewRequest!==request||!canApplyCaptionReview(review)||!request.open(dialog)){request.close();onDismiss();dialog.dismiss();return false}
        val previous=momentReviewDialog;momentReviewDialog=dialog
        showCaptionReview(dialog,review){
            request.dismiss(dialog)
            if(momentReviewRequest===request&&request.isClosed)momentReviewRequest=null
            if(momentReviewDialog===dialog)momentReviewDialog=null
            onDismiss()
        }
        if(previous!==dialog)previous?.dismiss()
        return !request.isClosed
    }
    private fun performMomentReview(dialog:android.app.AlertDialog,request:VideoActionReview,review:VideoCaptionReviewState,action:(()->Unit)->Unit):Boolean {
        if(!canUseMomentStage(dialog,request,review))return false
        val guard={requireMomentStage(dialog,request,review)}
        return request.execute(dialog,guard){action(guard)}
    }
    private fun performMomentNoteCopy(dialog:android.app.AlertDialog,request:VideoActionReview,review:VideoCaptionReviewState,catalog:VideoMomentNotes,load:()->VideoMomentNotes,copy:(String)->Unit):Boolean =
        performMomentReview(dialog,request,review){guard->
            check(load()==catalog){"Source notes changed. Open Copy moment notes again."}
            guard()
            copy(VideoMomentNoteText.format(catalog))
        }
    private fun copyMomentNotes(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val clip=current.segments.getOrNull(selectedClip)?:return
        val uri=review.sources[clip.sourceId]?:return;val duration=sourceDuration(clip.sourceId)
        val store=runCatching{VideoMomentNoteStore(java.io.File(noBackupFilesDir,"video-moment-notes"),review.namespace,uri.toString(),duration)}
            .getOrElse{status.text="Copy notes needs a supported local source video.";return}
        val catalog=runCatching{check(canApplyCaptionReview(review));store.load().also{check(canApplyCaptionReview(review))}}
            .getOrElse{status.text="Source notes could not be read. Existing notes were kept.";return}
        var prepared:String?=runCatching{VideoMomentNoteText.format(catalog)}.getOrElse{status.text=it.message?:"Source notes could not be prepared.";return}
        momentReviewRequest?.close();momentReviewDialog?.dismiss()
        val request=VideoActionReview();momentReviewRequest=request
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12));setBackgroundColor(Color.WHITE)}
        body.addView(TextView(this).apply{text="${catalog.notes.size} private source bookmarks. Copy places these timestamps and labels on the system clipboard, where clipboard services or other apps may access or sync them. No media or source/account metadata is added; the log contains timestamps and labels only. Paste with Import moment notes only for the matching original video; timing follows source seconds, not edited playback. Nothing is copied until you tap Copy note log.";setTextColor(Color.DKGRAY)})
        val preview=TextView(this).apply{text=prepared;setTextColor(Color.BLACK);setPadding(0,dp(12),0,0);contentDescription="Source moment note log preview";body.addView(this)}
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Copy source moment notes").setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Cancel",null).setPositiveButton("Copy note log"){_,_->
                if(prepared==null)return@setPositiveButton
                runCatching{check(performMomentNoteCopy(dialog,request,review,catalog,{store.load()}){text->
                    val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE)as?android.content.ClipboardManager?:error("Clipboard is unavailable")
                    clipboard.setPrimaryClip(VideoMomentNoteClipboard.create(text))
                }){"Video or session changed. Open Copy moment notes again."}}
                    .onSuccess{status.text="Source note log copied. Paste only for the matching original video. Project edits are unchanged."}
                    .onFailure{status.text=it.message?:"Could not copy the source notes. Open them again."}
            }.create()
        showMomentStage(dialog,request,review){prepared=null;preview.text=""}
    }

    private fun importMomentNotes(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val clip=current.segments.getOrNull(selectedClip)?:return
        val uri=review.sources[clip.sourceId]?:return;val duration=sourceDuration(clip.sourceId)
        val store=runCatching{VideoMomentNoteStore(java.io.File(noBackupFilesDir,"video-moment-notes"),review.namespace,uri.toString(),duration)}
            .getOrElse{status.text="Note import needs a supported local source video.";return}
        momentReviewRequest?.close();momentReviewDialog?.dismiss()
        val request=VideoActionReview();momentReviewRequest=request
        fun showEditor(initial:String){
            val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
            panel.addView(TextView(this).apply{text="Paste 1–100 lines: original-source seconds | label. Use a decimal point and up to three decimals. Times may be anywhere inside this source video, before playback speed changes. Labels use up to 80 characters. Existing notes are kept; duplicate times and over-limit imports are refused together. These private bookmarks are not captions and are not included in exports. Project Undo does not undo note imports.";setTextColor(Color.BLACK)})
            val input=EditText(this).apply{hint="1.250 | Opening take\n8.500 | Close-up";contentDescription="Timestamped source moment notes";setText(initial);setTextColor(Color.BLACK);minLines=4;maxLines=8;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(16000));panel.addView(this)}
            val editor=android.app.AlertDialog.Builder(this).setTitle("Import source moment notes").setView(android.widget.ScrollView(this).apply{addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review notes",null).create()
            editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(!canUseMomentStage(editor,request,review)){editor.dismiss();return@setOnClickListener}
                val raw=input.text.toString()
                val prepared=runCatching{
                    val notes=VideoMomentNoteText.parse(raw,duration)
                    requireMomentStage(editor,request,review)
                    val merged=VideoMomentNoteText.merge(store.load(),notes)
                    requireMomentStage(editor,request,review);notes to merged.notes.size
                }.getOrElse{input.error=it.message?:"Check these source timestamps and labels";return@setOnClickListener}
                val notes=prepared.first
                val summary=notes.joinToString("\n"){VideoCaptions.formatTime(it.sourceMs)+" · "+it.label}
                lateinit var confirmation:android.app.AlertDialog
                confirmation=android.app.AlertDialog.Builder(this).setTitle("Review ${notes.size} source notes")
                    .setMessage("$summary\n\nAdds ${notes.size} private source bookmarks in source-time order. Current merged total: ${prepared.second}/100. Existing notes and media are kept. Duplicates and limits are checked again when saving; no partial list is imported. Project Undo does not undo note imports.")
                    .setNegativeButton("Back"){_,_->if(canUseMomentStage(confirmation,request,review))showEditor(raw)}
                    .setPositiveButton("Save notes on this device"){_,_->
                        runCatching{check(performMomentReview(confirmation,request,review){guard->store.addAll(notes,guard)}){"Video or session changed. Open note import again."}}
                            .onSuccess{status.text="Imported ${notes.size} source moment notes. Video and project edits are unchanged."}
                            .onFailure{status.text=(it.message?:"Notes could not be verified.")+" Reopen moment notes to check the saved state."}
                    }.create()
                showMomentStage(confirmation,request,review)
            }}
            showMomentStage(editor,request,review){input.text?.clear()}
        }
        showEditor("")
    }

    private fun momentNotes(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return
        val uri=review.sources[clip.sourceId]?:return;val duration=sourceDuration(clip.sourceId)
        val at=runCatching{if(previewPlayer!=null)splitPosition()else if(sourceReady)video.currentPosition.toLong()else pendingSeekMs}
            .getOrElse{status.text=it.message?:"Select the playing clip first.";return}.coerceIn(clip.startMs,clip.endMs-1)
        video.pause();previewPlayer?.pause()
        val store=runCatching{VideoMomentNoteStore(java.io.File(noBackupFilesDir,"video-moment-notes"),review.namespace,uri.toString(),duration)}
            .getOrElse{status.text="Moment notes need a supported local source video.";return}
        val catalog=runCatching{check(canApplyCaptionReview(review));store.load().also{check(canApplyCaptionReview(review))}}.getOrElse{status.text=it.message?:"Moment notes could not be read. Existing notes were kept.";return}
        momentReviewRequest?.close();momentReviewDialog?.dismiss()
        val request=VideoActionReview();momentReviewRequest=request
        fun editNote(before:VideoMomentNote?){
            val position=before?.sourceMs?:at
            val label=EditText(this).apply{hint="Moment label";contentDescription="Moment note label";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(80));setText(before?.label?:"Moment "+VideoCaptions.formatTime(position))}
            val dialog=android.app.AlertDialog.Builder(this).setTitle(if(before==null)"Note this source moment" else "Rename moment note").setView(label).setNegativeButton("Cancel",null).setPositiveButton("Save on this device",null).create()
            dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(!canUseMomentStage(dialog,request,review)){dialog.dismiss();status.text="Video or session changed. Open notes again.";return@setOnClickListener}
                val prepared=runCatching{
                    val note=VideoMomentNote(position,label.text.toString().trim())
                    requireMomentStage(dialog,request,review)
                    val latest=store.load();if(before==null)latest.add(note)else latest.rename(before,note.label)
                    requireMomentStage(dialog,request,review);note
                }
                if(prepared.isFailure){label.error=prepared.exceptionOrNull()?.message?:"Check this label";return@setOnClickListener}
                val result=runCatching{check(performMomentReview(dialog,request,review){guard->
                    if(before==null)store.add(prepared.getOrThrow(),guard)else store.rename(before,prepared.getOrThrow().label,guard)
                }){"Video or session changed. Open notes again."}}
                dialog.dismiss()
                status.text=if(result.isSuccess)"Moment note saved locally. Video and project edits are unchanged." else result.exceptionOrNull()?.message?:"Could not save. Open notes again."
            }}
            showMomentStage(dialog,request,review){label.text?.clear()}
        }
        fun chooseNote(note:VideoMomentNote){
            val canJump=note.sourceMs>=clip.startMs&&note.sourceMs<clip.endMs
            val actions=(if(canJump)listOf("Jump to this moment")else emptyList())+listOf("Rename note","Delete note")
            lateinit var actionsDialog:android.app.AlertDialog
            actionsDialog=android.app.AlertDialog.Builder(this).setTitle(note.label).setItems(actions.toTypedArray()){_,action->
                if(!canUseMomentStage(actionsDialog,request,review))return@setItems
                when(action-(if(canJump)1 else 0)){
                    -1->runCatching{check(performMomentReview(actionsDialog,request,review){guard->
                        check(store.load().notes.contains(note)){"Note changed. Open moment notes again."};guard()
                        seekTimeline(VideoFilmstripScale.outputTime(clips,index,note.sourceMs))
                    }){"Video or session changed. Open notes again."}}
                        .onSuccess{status.text="Jumped to saved source moment. Project edits are unchanged."}
                        .onFailure{status.text=it.message?:"Could not jump. Open notes again."}
                    0->editNote(note)
                    1->{
                        lateinit var confirmation:android.app.AlertDialog
                        confirmation=android.app.AlertDialog.Builder(this).setTitle("Delete moment note?").setMessage("Removes this local note only. Original video and project edits stay unchanged.")
                            .setNegativeButton("Cancel",null).setPositiveButton("Delete note"){_,_->
                                runCatching{check(performMomentReview(confirmation,request,review){guard->store.remove(note,guard)}){"Video or session changed. Open notes again."}}
                                    .onSuccess{status.text="Moment note deleted. Video and project edits are unchanged."}
                                    .onFailure{status.text=it.message?:"Note could not be deleted. Open notes again."}
                            }.create()
                        showMomentStage(confirmation,request,review)
                    }
                }
            }.setNegativeButton("Cancel",null).create()
            showMomentStage(actionsDialog,request,review)
        }
        val visible=catalog.visible(clip.startMs,clip.endMs)
        val choices=listOf("Add note at current source moment","Manage all source notes","Cut highlights from saved notes")+visible.map{VideoCaptions.formatTime(it.sourceMs)+" · "+it.label}
        lateinit var menu:android.app.AlertDialog
        menu=android.app.AlertDialog.Builder(this).setTitle("Moment notes · this clip")
            .setItems(choices.toTypedArray()){_,choice->
                if(!canUseMomentStage(menu,request,review))return@setItems
                when(choice){
                    0->editNote(null)
                    1->{
                        if(catalog.notes.isEmpty()){status.text="No moment notes saved for this source yet.";return@setItems}
                        lateinit var list:android.app.AlertDialog
                        list=android.app.AlertDialog.Builder(this).setTitle("All notes for source video")
                            .setItems(catalog.notes.map{VideoCaptions.formatTime(it.sourceMs)+" · "+it.label}.toTypedArray()){_,selected->
                                if(canUseMomentStage(list,request,review))catalog.notes.getOrNull(selected)?.let{chooseNote(it)}
                            }.setNegativeButton("Close",null).create()
                        showMomentStage(list,request,review)
                    }
                    2->runCatching{check(performMomentReview(menu,request,review){guard->guard();momentHighlights()})}.onFailure{status.text="Open highlights again from the current video."}
                    else->visible.getOrNull(choice-3)?.let{chooseNote(it)}
                }
            }.setNeutralButton("About notes"){_,_->
                if(!canUseMomentStage(menu,request,review))return@setNeutralButton
                val about=android.app.AlertDialog.Builder(this).setTitle("Private source moments").setMessage("Up to 100 labeled moments per source video, saved privately on this device. Times follow the original source, so trims and speed changes do not move notes. The clip list shows in-range moments; Manage all source notes also renames or deletes out-of-range notes. Notes are not rendered into exports or included in project transfer.").setPositiveButton("Done",null).create()
                showMomentStage(about,request,review)
            }.setNegativeButton("Close",null).create()
        showMomentStage(menu,request,review)
    }

    private fun commitMomentHighlightsReview(dialog:android.app.AlertDialog,request:VideoBatchReview,review:VideoCaptionReviewState,catalog:VideoMomentNotes,load:()->VideoMomentNotes):Boolean {
        if(!canUseBatchStage(dialog,request,review)){
            if(batchReviewRequest===request&&batchReviewDialog===dialog&&request.owns(dialog))request.close()
            return false
        }
        return request.apply(dialog,requireNotNull(project)){
            fun requireCurrent(){check(batchReviewRequest===request&&batchReviewDialog===dialog&&activeCaptionReviewDialog(dialog,review)){"Project or session changed. Open highlights again."}}
            requireCurrent()
            check(load()==catalog){"Saved notes changed. Open highlights again."}
            requireCurrent()
        }
    }
    private fun momentHighlights(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return
        val uri=review.sources[clip.sourceId]?:return
        val store=runCatching{VideoMomentNoteStore(java.io.File(noBackupFilesDir,"video-moment-notes"),review.namespace,uri.toString(),sourceDuration(clip.sourceId))}
            .getOrElse{status.text="Highlights need saved notes for a supported local video.";return}
        val catalog=runCatching{check(canApplyCaptionReview(review));store.load().also{check(canApplyCaptionReview(review))}}.getOrElse{status.text="Saved moments could not be read. Original video and notes were kept.";return}
        val notes=catalog.visible(clip.startMs,clip.endMs)
        if(notes.isEmpty()){status.text="Save moment notes inside this clip first, then open Highlights from notes.";return}
        video.pause();previewPlayer?.pause()
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(clips,current.soundtrack);batchReviewRequest=request
        fun showEditor(beforeText:String,afterText:String,chosen:Set<Long>){
            val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
            body.addView(TextView(this).apply{text="Choose saved moments from this clip. Source-time handles keep footage around each note;overlapping or touching excerpts merge. The selected clip is replaced by these forward-playing excerpts. Later clips move earlier;the existing soundtrack keeps its settings. Titles,looks,audio and source-anchored motion stay;only captions intersecting retained footage stay visible. Notes and originals are unchanged. Preview after applying;codec cuts are not promised frame-exact.";setTextColor(Color.DKGRAY)})
            fun field(label:String,initial:String)=EditText(this).apply{hint=label;contentDescription=label;setText(initial);setTextColor(Color.BLACK);setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(8));body.addView(this)}
            val before=field("Seconds before each source moment",beforeText);val after=field("Seconds after each source moment",afterText)
            val checks=notes.map{note->android.widget.CheckBox(this).apply{text=VideoCaptions.formatTime(note.sourceMs)+" · "+note.label;setTextColor(Color.BLACK);isChecked=note.sourceMs in chosen;body.addView(this)}}
            val editor=android.app.AlertDialog.Builder(this).setTitle("Highlights from saved notes").setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setNegativeButton("Cancel",null).setPositiveButton("Review excerpts",null).create()
            editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(!canUseBatchStage(editor,request,review)){editor.dismiss();status.text="Project or session changed. Open highlights again.";return@setOnClickListener}
                val beforeValue=before.text.toString();val afterValue=after.text.toString()
                val selected=notes.filterIndexed{i,_->checks[i].isChecked}.map{it.sourceMs}
                val proposal=runCatching{VideoMomentHighlights.propose(clips,index,selected,VideoCaptions.seconds(beforeValue),VideoCaptions.seconds(afterValue))}
                    .getOrElse{after.error=it.message?:"Choose saved moments and valid source handles.";return@setOnClickListener}
                if(proposal==clips){status.text="These excerpts retain the whole clip. Choose smaller handles to shorten it.";return@setOnClickListener}
                val excerpts=proposal.subList(index,proposal.size-(clips.size-index-1))
                val summary=excerpts.joinToString("\n"){VideoCaptions.formatTime(it.startMs)+" → "+VideoCaptions.formatTime(it.endMs)}
                val oldSeconds=String.format(java.util.Locale.ROOT,"%.3f",VideoPlaybackTime.durationUs(clips)/1_000_000.0)
                val newSeconds=String.format(java.util.Locale.ROOT,"%.3f",VideoPlaybackTime.durationUs(proposal)/1_000_000.0)
                lateinit var confirmation:android.app.AlertDialog
                confirmation=android.app.AlertDialog.Builder(this).setTitle("Review ${excerpts.size} highlight excerpts")
                    .setMessage("Original-source ranges:\n$summary\n\nProject $oldSeconds → $newSeconds seconds. Replace clip ${index+1} as one Undo step,then preview before export. Saved notes and original media stay unchanged.")
                    .setNegativeButton("Back"){_,_->if(canUseBatchStage(confirmation,request,review))showEditor(beforeValue,afterValue,selected.toSet())}
                    .setPositiveButton("Apply and preview"){_,_->
                        runCatching{commitMomentHighlightsReview(confirmation,request,review,catalog){store.load()}}
                            .onSuccess{applied->
                                if(!applied)return@onSuccess
                                selectedClip=index
                                val refreshed=runCatching{showClip();previewProject()}.isSuccess
                                status.text=if(refreshed)"Saved-note highlights applied. Undo restores the previous timeline;notes and originals are unchanged." else "Saved-note highlights applied. Preview is unavailable; Undo restores the previous timeline."
                            }.onFailure{status.text=it.message?:"Highlights could not be applied. Existing edits were kept."}
                    }.create()
                if(showBatchStage(confirmation,request,review))runCatching{check(request.propose(confirmation,proposal))}.onFailure{
                    request.close();confirmation.dismiss();status.text=it.message?:"Highlights could not be prepared. Existing edits were kept."
                }
            }}
            showBatchStage(editor,request,review){before.text?.clear();after.text?.clear();checks.forEach{it.isChecked=false}}
        }
        showEditor("1.000","2.000",notes.map{it.sourceMs}.toSet())
    }

    private fun videoEdl(){
        val review=captureCaptionReview()?:return
        val current=project?:return
        lateinit var dialog:android.app.AlertDialog
        val rates=listOf(24,25,30)
        dialog=android.app.AlertDialog.Builder(this).setTitle("Copy CMX3600 video cut list")
            .setItems(rates.map{"$it fps · non-drop"}.toTypedArray()){_,choice->
                if(!canUseInspectorReview(dialog,review)||project!==current)return@setItems
                inspectorReviewDialog=null
                reviewVideoEdl(review,current,rates[choice])
            }.setNegativeButton("Cancel",null).create()
        showInspectorReview(dialog,review)
    }

    private fun reviewVideoEdl(review:VideoCaptionReviewState,current:VideoEditProject,fps:Int){
        if(project!==current||!canApplyCaptionReview(review))return
        val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val result=runCatching{VideoEdl.prepare(current.segments.map{VideoEdl.Cut(it.sourceId,it.startMs,it.endMs,it.speed)},fps)}
            .getOrElse{status.text=it.message?:"This video cut list is unavailable.";return}
        val mapping=result.reels.entries.joinToString("\n"){(id,reel)->"$reel → Noble source ${sourceNumber(id)}, first used in clip ${current.segments.indexOfFirst{it.sourceId==id}+1} ($id)"}
        var prepared:String?=result.text
        val body=TextView(this).apply{
            text="${result.events} cuts · ${result.fps} fps non-drop · ${result.frames} output frames. ${result.adjustedCuts} cuts rounded inward.\n\nVideo cuts only, all at 1x speed. Source timestamps start at zero; embedded camera timecode is not used. Relink each reel to its original manually using the source-ID mapping below. This contains no source URI, filename or media.\n\n$mapping\n\nSource in/out points round inward to whole frames; tiny clips are refused. Timeline duration may shorten. Titles, captions, color, crop, motion, transitions, clip audio and soundtrack are omitted. This is an editable conforming aid, not the rendered Noble project; compatibility with other editors is unverified.\n\nCopy places the EDL text below on the system clipboard. Other apps or clipboard sync may access it. Sensitive clipboard metadata is requested. Your project and originals stay unchanged.\n\n${result.text}"
            setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12));contentDescription="CMX3600 video cut list review and limitations"
        }
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Review video cut list")
            .setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Cancel",null).setPositiveButton("Copy EDL"){_,_->
                val text=prepared?:return@setPositiveButton
                runCatching{commitInspectorReview(dialog,review,current,index,clip){
                    val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager?:error("Clipboard is unavailable")
                    val data=android.content.ClipData.newPlainText("Noble CMX3600 video cuts",text)
                    data.description.extras=android.os.PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)}
                    clipboard.setPrimaryClip(data)
                }}.onSuccess{copied->if(copied)status.text="Video-only EDL copied. Relink originals and check timing in your external editor."}
                    .onFailure{status.text="Could not copy the cut list. Open it again to retry."}
            }.create()
        inspectorReviewDialog?.dismiss();inspectorReviewDialog=dialog
        showCaptionReview(dialog,review){prepared=null;body.text="";if(inspectorReviewDialog===dialog)inspectorReviewDialog=null}
    }

    private fun chapterList(){
        val review=captureCaptionReview()?:return
        val clips=project?.segments?.toList()?:return
        var prepared:String?=runCatching{VideoChapters.text(clips)}.getOrElse{status.text=it.message?:"Chapter list is unavailable.";return}
        val body=TextView(this).apply{text=prepared;setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12));contentDescription="Edited video chapter list"}
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Clip chapter list")
            .setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setPositiveButton("Copy list"){_,_->
                val text=prepared?:return@setPositiveButton
                runCatching{
                    check(performCaptionClipboardCopy(dialog,review){
                        val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager?:error("Clipboard is unavailable")
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Video clip chapters",text))
                    }){"Project or session changed. Reopen the chapter list before copying."}
                }.onSuccess{status.text="Chapter list copied. Times refer to edited playback."}
                    .onFailure{status.text="Could not copy the chapter list. Reopen it and try again."}
            }.setNegativeButton("Close",null).create()
        showCaptionReview(dialog,review){prepared=null;body.text=""}
    }

    private fun playbackExcerpt(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack
        val duration=runCatching{VideoPlaybackTime.durationUs(snapshot).also{require(it<=Int.MAX_VALUE.toLong()*1000)}}.getOrElse{status.text="Timeline exceeds supported playback timestamp bounds.";return}
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Keep one interval across the edited timeline (%.6fseconds), after clip speeds. Enter decimal playback seconds, up to3decimals. New partial edge pieces need0.1playback second; full short clips stay. Source edges round inward to milliseconds and the last fractional playback millisecond clips to project end. Other footage is removed from this edit, not its original files. Captions keep source times/provenance; appearance and source motion stay. Fades repeat on trimmed edges. Soundtrack settings stay and music starts again at the new timeline beginning, not its old playback offset. One Undo restores the prior timeline.",duration/1_000_000.0);setTextColor(Color.BLACK)})
        fun field(label:String,value:String):EditText {panel.addView(TextView(this).apply{text=label;setTextColor(Color.BLACK)});return EditText(this).apply{setText(value);setTextColor(Color.BLACK);setSingleLine(true);contentDescription=label;inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(16));panel.addView(this)}}
        val from=field("Edited playback start seconds","0.000")
        val to=field("Edited playback end seconds",String.format(java.util.Locale.ROOT,"%.3f",((duration+999)/1000)/1000.0))
        val editor=android.app.AlertDialog.Builder(this).setTitle("Keep playback excerpt").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!canUseBatchStage(editor,request,captured)||project!==current){editor.dismiss();return@setOnClickListener}
            val prepared=runCatching{VideoPlaybackExcerpt.prepare(snapshot,from.text.toString(),to.text.toString(),music)}
            if(prepared.isFailure){from.error=prepared.exceptionOrNull()?.message?:"Choose a playback interval";return@setOnClickListener}
            val result=prepared.getOrThrow()
            val mapping=result.pieces.joinToString("\n"){piece->String.format(java.util.Locale.ROOT,"Original clip %d / source %s: %.3f–%.3fs · original playback %.6f–%.6fs",piece.originalIndex+1,piece.clip.sourceId,piece.clip.startMs/1000.0,piece.clip.endMs/1000.0,piece.originalFromUs/1_000_000.0,piece.originalToUs/1_000_000.0)}
            val body=TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Requested edited playback %.3f–%.3fs; effective end %.6fs.\n%d → %d clips; %.6f → %.6fseconds.\n\n%s\n\nThis keeps only the displayed source ranges, in order. Partial boundaries round inward; this is not a codec frame-exact guarantee. Intersecting captions retain original source timestamps and subtitle binding; captions outside each kept range are removed. Source motion and other clip settings stay; edge/audio fades repeat. Soundtrack settings are unchanged and music restarts from its current source excerpt at the new timeline beginning. Listen/preview before export. Apply is one Undo; original files stay unchanged.",result.requestedFromUs/1_000_000.0,result.requestedToUs/1_000_000.0,result.effectiveToUs/1_000_000.0,snapshot.size,result.clips.size,result.beforeUs/1_000_000.0,result.afterUs/1_000_000.0,mapping);setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
            lateinit var review:android.app.AlertDialog
            review=android.app.AlertDialog.Builder(this).setTitle("Review playback excerpt").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->runCatching{check(project===current){"Project changed. Choose the excerpt again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=0;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Playback excerpt kept. Undo restores the prior timeline."else"Excerpt kept; preview unavailable. Undo restores the prior timeline."}}.onFailure{status.text=it.message?:"Excerpt could not apply."}}.create()
            if(showBatchStage(review,request,captured){body.text=""})runCatching{check(request.propose(review,result.clips))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Excerpt could not be reviewed."}
        }}
        showBatchStage(editor,request,captured){from.text?.clear();to.text?.clear()}
    }

    private fun uniqueFootage(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack
        val prepared=runCatching{VideoUniqueFootage.prepare(snapshot,music)}
        if(prepared.isFailure){status.text=prepared.exceptionOrNull()?.message?:"Repeated footage could not be reviewed.";return}
        val result=prepared.getOrThrow()
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        val mapping=result.pieces.joinToString("\n"){piece->String.format(java.util.Locale.ROOT,"Keep original clip %d / source %s: %.3f–%.3fs at %.1fx",piece.originalIndex+1,piece.clip.sourceId,piece.clip.startMs/1000.0,piece.clip.endMs/1000.0,piece.clip.speed)}
        val removed=if(result.removedClips.isEmpty())"None" else result.removedClips.joinToString(", "){(it+1).toString()}
        val changed=result.changedClips.joinToString(", "){(it+1).toString()}
        val body=TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Keep the FIRST timeline occurrence of each exact source-ID/time range. Later overlapping footage is removed only from this edit. This does not compare pictures or match different source IDs, even if they reference the same file. Intentional repeats are removed too: cancel to keep them.\n\n%d → %d clips; %.6f → %.6fseconds (%.6fseconds removed).\nChanged original clips: %s.\nEntirely removed original clips: %s.\n\nComplete remaining timeline, in order:\n%s\n\nEvery shown source range remains; no unique footage is silently dropped. New partial pieces need0.1playback second; full short originals stay. Appearance, speed, volume, source motion and intersecting source-time captions/provenance stay. Edge/audio fades repeat on new pieces. Soundtrack settings stay and its excerpt starts again at the beginning of the shortened timeline, so music sync changes. Preview/listen before export. This is source-millisecond editing, not codec frame-exact editing. Apply is one Undo; original files stay unchanged.",snapshot.size,result.clips.size,result.beforeUs/1_000_000.0,result.afterUs/1_000_000.0,(result.beforeUs-result.afterUs)/1_000_000.0,changed,removed,mapping);setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
        lateinit var review:android.app.AlertDialog
        review=android.app.AlertDialog.Builder(this).setTitle("Review removal of repeated footage").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->runCatching{check(project===current){"Project changed. Review repeated footage again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=0;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Repeated footage removed. Undo restores intentional repeats."else"Repeated footage removed; preview unavailable. Undo restores the prior timeline."}}.onFailure{status.text=it.message?:"Repeated footage could not apply."}}.create()
        if(showBatchStage(review,request,captured){body.text=""})runCatching{check(request.propose(review,result.clips))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Repeated footage could not be reviewed."}
    }

    private fun multicamAssembly(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack
        val cameras=snapshot.map{it.sourceId}.distinct()
        if(cameras.size<2){status.text="Add at least two camera originals to the timeline first.";return}
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        val catalog=cameras.joinToString("\n"){id->String.format(java.util.Locale.ROOT,"%s: %.3f original seconds",id,(captured.durations[id]?:0)/1000.0)}
        panel.addView(TextView(this).apply{text="Manually synchronize camera originals to a common program clock using a matching event visible/audible in each original. Anchors are sourceID,sourceSeconds,programSeconds; switches are programSeconds,sourceID. Decimal seconds use up to3places. Start switches at0; every cut needs0.1second; use2–200cuts and at least2cameras. The program end is exclusive. All new cuts run at1x, regardless of previous speeds. This is fixed-offset synchronization, with no automatic alignment or clock-drift correction.\n\nEach camera uses its FIRST current timeline clip as its appearance/caption/source-motion template, even for original footage outside that clip's trimmed window. Other clips' looks/cues are not combined. Only intersecting cues from that first template remain. Review every window before replacing the timeline.\n\nAvailable original sources:\n$catalog";setTextColor(Color.BLACK)})
        fun field(label:String,value:String,limit:Int,multiline:Boolean):EditText{panel.addView(TextView(this).apply{text=label;setTextColor(Color.BLACK)});return EditText(this).apply{setText(value);setTextColor(Color.BLACK);contentDescription=label;setSingleLine(!multiline);inputType=if(multiline)android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(limit));panel.addView(this)}}
        val anchors=field("Camera anchors: sourceID,sourceSeconds,programSeconds",cameras.joinToString("\n"){"$it,0.000,0.000"},16000,true)
        val switches=field("Camera switches: programSeconds,sourceID","0.000,${cameras[0]}\n1.000,${cameras[1]}",16000,true)
        val end=field("Program end seconds","2.000",16,false)
        val editor=android.app.AlertDialog.Builder(this).setTitle("Build camera-switch edit").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!canUseBatchStage(editor,request,captured)||project!==current){editor.dismiss();return@setOnClickListener}
            val prepared=runCatching{VideoMulticamAssembly.prepare(snapshot,captured.durations,anchors.text.toString(),switches.text.toString(),end.text.toString(),music)}
            if(prepared.isFailure){end.error=prepared.exceptionOrNull()?.message?:"Check camera anchors and switches";return@setOnClickListener}
            val result=prepared.getOrThrow()
            val calibration=result.anchors.joinToString("\n"){anchor->String.format(java.util.Locale.ROOT,"%s: source %.3fs = program %.3fs; source minus program offset %+.3fs",anchor.sourceId,anchor.sourceMs/1000.0,anchor.programMs/1000.0,anchor.offsetMs/1000.0)}
            val mapping=result.cuts.joinToString("\n"){cut->String.format(java.util.Locale.ROOT,"Program %.3f–%.3fs → %s source %.3f–%.3fs,1x; appearance/cues from original clip%d (%dcues)",cut.programFromMs/1000.0,cut.programToMs/1000.0,cut.clip.sourceId,cut.clip.startMs/1000.0,cut.clip.endMs/1000.0,cut.templateIndex+1,cut.clip.captions.size)}
            val body=TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Replace %d current clips with %d synchronized camera cuts, %.3f program seconds.\n\nCalibration:\n%s\n\nComplete new timeline:\n%s\n\nThis uses your manual anchors, not audio/visual inference or drift correction. Every source window must fit the full original. All cuts are1x; each camera takes appearance, volume, titles, source-motion and intersecting source-time captions/provenance only from its FIRST current clip. Other clip-specific edits/cues are not combined. Motion stays anchored to original source times and clamps outside its old anchors. Edge/audio fades repeat percut. Soundtrack settings stay and its excerpt starts at the new program beginning, so prior music sync changes. Preview/listen before export. Original media stays unchanged. Source-millisecond cuts are not a codec frame-exact guarantee. Apply is one Undo.",snapshot.size,result.clips.size,result.durationMs/1000.0,calibration,mapping);setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
            lateinit var review:android.app.AlertDialog
            review=android.app.AlertDialog.Builder(this).setTitle("Review camera-switch edit").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->runCatching{check(project===current){"Project changed. Calibrate the cameras again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=0;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Camera-switch edit applied. Undo restores the prior timeline."else"Camera-switch edit applied; preview unavailable. Undo restores the prior timeline."}}.onFailure{status.text=it.message?:"Camera-switch edit could not apply."}}.create()
            if(showBatchStage(review,request,captured){body.text=""})runCatching{check(request.propose(review,result.clips))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Camera-switch edit could not be reviewed."}
        }}
        showBatchStage(editor,request,captured){anchors.text?.clear();switches.text?.clear();end.text?.clear()}
    }

    private fun fitAllShotsDuration(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack
        val before=runCatching{VideoPlaybackTime.durationUs(snapshot)}.getOrElse{status.text="Timeline duration is unsupported.";return}
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Keep all %d shots, in order and at their current speeds, while shortening the %.6fsecond timeline to a requested duration. Every shortened shot uses its centered source excerpt. Trim capacity is shared proportionally after reserving0.1playback second per new piece; whole shorter originals stay. Source-millisecond rounding can finish less than2milliseconds below the target, never above it. This does not identify important content: review every retained source window. Original files stay unchanged.",snapshot.size,before/1_000_000.0);setTextColor(Color.BLACK)})
        panel.addView(TextView(this).apply{text="Target playback seconds (shorter than current; up to3decimals)";setTextColor(Color.BLACK)})
        val target=EditText(this).apply{setTextColor(Color.BLACK);setSingleLine(true);contentDescription="Target playback seconds";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(16))};panel.addView(target)
        val editor=android.app.AlertDialog.Builder(this).setTitle("Fit all shots to duration").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!canUseBatchStage(editor,request,captured)||project!==current){editor.dismiss();return@setOnClickListener}
            val prepared=runCatching{VideoFitDuration.prepare(snapshot,target.text.toString(),music)}
            if(prepared.isFailure){target.error=prepared.exceptionOrNull()?.message?:"Choose a shorter feasible duration";return@setOnClickListener}
            val result=prepared.getOrThrow()
            val mapping=result.clips.mapIndexed{index,clip->val old=snapshot[index];String.format(java.util.Locale.ROOT,"Clip%d / source %s: %.3f–%.3fs → %.3f–%.3fs at %.1fx; %.6f → %.6fplayback seconds; %d → %dcues",index+1,clip.sourceId,old.startMs/1000.0,old.endMs/1000.0,clip.startMs/1000.0,clip.endMs/1000.0,clip.speed,old.durationUs/1_000_000.0,clip.durationUs/1_000_000.0,old.captions.size,clip.captions.size)}.joinToString("\n")
            val body=TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Keep all%dshots in original order/speeds. Requested %.6fs; effective %.6fs (%.6fs below target); prior %.6fs; minimum keeping allshots %.6fs.\n\nComplete retained timeline:\n%s\n\nCentered trimming does not recognize important footage. Source-millisecond quantization leaves less than2milliseconds shortfall; this is not codec frame-exact timing. Every new trimmed piece keeps at least0.1playback second; full tiny originals stay. Clip appearance/volume/title/source-motion and intersecting source-time captions/provenance stay; outside cues disappear from this edit. Source motion keeps its original anchors. Edge/audio fades repeat on trimmed edges. Soundtrack settings stay and its excerpt starts at the shortened timeline beginning, so prior music synchronization changes. Preview/listen before export. Apply is one Undo; original media stays unchanged.",result.clips.size,result.requestedUs/1_000_000.0,result.afterUs/1_000_000.0,(result.requestedUs-result.afterUs)/1_000_000.0,result.beforeUs/1_000_000.0,result.minimumUs/1_000_000.0,mapping);setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
            lateinit var review:android.app.AlertDialog
            review=android.app.AlertDialog.Builder(this).setTitle("Review fitted shot durations").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->runCatching{check(project===current){"Project changed. Fit the shots again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=0;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"All shots fitted. Undo restores their prior source windows."else"Shots fitted; preview unavailable. Undo restores their prior source windows."}}.onFailure{status.text=it.message?:"Shot durations could not apply."}}.create()
            if(showBatchStage(review,request,captured){body.text=""})runCatching{check(request.propose(review,result.clips))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Shot durations could not be reviewed."}
        }}
        showBatchStage(editor,request,captured){target.text?.clear()}
    }

    private fun importVideoCutList(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        val sources=(snapshot.map{it.sourceId}+captured.durations.keys).distinct()
        val catalog=sources.joinToString("\n"){id->String.format(java.util.Locale.ROOT,"%s: %.3f original seconds",id,(captured.durations[id]?:0)/1000.0)}
        panel.addView(TextView(this).apply{text="Paste a video-only C-cut list with FCM: NON-DROP FRAME. Choose24/25/30fps explicitly. Source timecodes are zero-based from each original, not embedded camera timecode. Link every reel manually using reel,existingSourceID; comments never relink files. Record cuts must be contiguous at1x; any initial record clock is normalized to program zero. Audio events, transitions, speed rows, gaps and unknown non-comment rows refuse the entire import. Up to999cuts/128000UTF-8bytes and32reels/8192mappingbytes.\n\nImported clips are neutral1x cuts: old clip grades/layout/titles/captions/motion/mutes/fades are not restored. Original camera audio plays at full volume. Existing soundtrack settings stay; its synchronization may change. Every source edge is enveloped to whole milliseconds, widening by less than1ms peredge to retain listed framecodes on inward text re-export. Native timing is shown separately; this is not a frame-exact playback/export guarantee. Review before replacing the timeline. Original media stays unchanged.\n\nAvailable original sources:\n$catalog";setTextColor(Color.BLACK)})
        fun field(label:String,value:String,limit:Int,multiline:Boolean):EditText{panel.addView(TextView(this).apply{text=label;setTextColor(Color.BLACK)});return EditText(this).apply{setText(value);setTextColor(Color.BLACK);contentDescription=label;setSingleLine(!multiline);inputType=if(multiline)android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else android.text.InputType.TYPE_CLASS_NUMBER;filters=arrayOf(android.text.InputFilter.LengthFilter(limit));panel.addView(this)}}
        val fps=field("Non-drop frame rate:24,25or30","30",2,false)
        val links=field("Reel links: reel,existingSourceID",sources.mapIndexed{index,id->String.format(java.util.Locale.ROOT,"N%03d,%s",index+1,id)}.joinToString("\n"),8192,true)
        val input=field("Paste video-only cut list","",128000,true)
        val editor=android.app.AlertDialog.Builder(this).setTitle("Import video cut list").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!canUseBatchStage(editor,request,captured)||project!==current){editor.dismiss();return@setOnClickListener}
            val prepared=runCatching{val rate=fps.text.toString().trim().toIntOrNull()?:error("Choose24,25or30fps");VideoEdlImport.prepare(input.text.toString(),links.text.toString(),rate,captured.durations,music).also{require(it.clips!=snapshot){"This cut list leaves the current timeline unchanged"}}}
            if(prepared.isFailure){input.error=prepared.exceptionOrNull()?.message?:"Check the cut list and explicit reel links";return@setOnClickListener}
            val result=prepared.getOrThrow()
            val mapping=result.events.joinToString("\n"){event->String.format(java.util.Locale.ROOT,"Event%03d / reel%s → source%s: frame%d–%d, native%.3f–%.3fs; record frames%d–%d",event.number,event.reel,event.clip.sourceId,event.sourceInFrame,event.sourceOutFrame,event.clip.startMs/1000.0,event.clip.endMs/1000.0,event.recordInFrame,event.recordOutFrame)}
            val body=TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Replace%d current clips (%d source-time cues) with%d neutral1x video cuts at%dfps non-drop. Original record origin%.6fs is normalized to program zero. Listed frame-span duration%.6fs; native millisecond-envelope duration%.6fs (%.6fs longer). %dcutwindows needed edge widening, less than1ms peredge.\n\nComplete imported timeline and explicit source mapping:\n%s\n\nOnly the reviewed video source windows are imported. Clip grades/layout/titles/captions/motion/mutes/fades are replaced with neutral defaults; original camera audio is full volume. EDL audio/effects/comments do not restore those settings. Existing soundtrack settings remain and start at the new timeline beginning; prior music sync changes. The envelopes preserve listed framecodes on inward cut-list re-export, not guaranteed physical codec frames. Preview/listen before export. Apply is one Undo; original media stays unchanged.",snapshot.size,snapshot.sumOf{it.captions.size},result.clips.size,result.fps,result.recordOriginFrame.toDouble()/result.fps,result.frameDurationUs/1_000_000.0,result.nativeDurationUs/1_000_000.0,(result.nativeDurationUs-result.frameDurationUs)/1_000_000.0,result.widenedEvents,mapping);setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
            lateinit var review:android.app.AlertDialog
            review=android.app.AlertDialog.Builder(this).setTitle("Review imported video cut list").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->runCatching{check(project===current){"Project changed. Relink the cut list again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=0;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Video cut list imported. Undo restores the previous edit."else"Cut list imported; preview unavailable. Undo restores the previous edit."}}.onFailure{status.text=it.message?:"Cut list could not apply."}}.create()
            if(showBatchStage(review,request,captured){body.text=""})runCatching{check(request.propose(review,result.clips))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Cut list could not be reviewed."}
        }}
        showBatchStage(editor,request,captured){input.text?.clear();links.text?.clear();fps.text?.clear()}
    }

    private fun openVideoBackups(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        video.pause();stopProjectPreview()
        runCatching{startActivity(Intent(this,VideoProjectBackupsActivity::class.java).putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name))}.onFailure{status.text="Video backups could not open. Current edits were kept."}
    }
    private fun backupEditableProject(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val sources=sourceUris();val durations=sourceDurations()
        val mode=accessMode;val owner=videoLookNamespace()?:return
        val plan=runCatching{VideoProjectBackup.plan(source.toString(),currentDraft())}.getOrElse{status.text=it.message?:"This editable snapshot cannot be backed up.";return}
        video.pause();stopProjectPreview();importing=true
        val busy=closeState();val generation=captionReviewGeneration
        lateinit var controls:VideoProjectBackupControls
        fun same()=projectBackupControls===controls&&workspaceActive&&!isDestroyed&&!isFinishing&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&project===current&&selectedClip==index&&captionReviewGeneration==generation&&accessMode==mode&&videoLookNamespace()==owner&&busy.stillMatches(closeState())&&sourceUris()==sources&&sourceDurations()==durations
        controls=VideoProjectBackupControls(this,plan,isCurrent={same()},requireWorkerCurrent={check(VideoPrivateOwner.namespace(applicationContext,mode)==owner){"Account changed"}},
            share={file->
                check(same()){"Video or session changed"}
                val archive=androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",file)
                val send=Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,archive).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=android.content.ClipData.newUri(contentResolver,"Reviewed Noble editable video backup",archive)}
                startActivity(Intent.createChooser(send,"Save or share complete video backup").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            },message={text->if(projectBackupControls===controls&&!isDestroyed&&!isFinishing)status.text=text},
            released={if(projectBackupControls===controls){projectBackupControls=null;importing=false}})
        projectBackupControls=controls
        runCatching{controls.open()}.onFailure{controls.close();if(project===current&&canApplyCaptionReview(captured))status.text="Backup controls could not open. Original media and current edits were kept."}
    }

    private fun reviewDarkHolds(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack;val index=selectedClip
        val clip=snapshot.getOrNull(index)?:return
        runCatching{VideoDarkHolds.times(clip)}.getOrElse{status.text=it.message?:"Choose a whole clip of2.5–120source seconds.";return}
        val sources=sourceUris();val durations=sourceDurations();val source=sources[clip.sourceId]?:return;val duration=durations[clip.sourceId]?:return
        val mode=accessMode;val owner=videoLookNamespace()?:return;val generation=captionReviewGeneration
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        video.pause();stopProjectPreview();importing=true
        val busy=closeState()
        lateinit var controls:VideoDarkHoldControls
        fun same()=darkHoldControls===controls&&workspaceActive&&!isDestroyed&&!isFinishing&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&project===current&&current.segments==snapshot&&current.soundtrack==music&&selectedClip==index&&captionReviewGeneration==generation&&accessMode==mode&&videoLookNamespace()==owner&&busy.stillMatches(closeState())&&sourceUris()==sources&&sourceDurations()==durations
        controls=VideoDarkHoldControls(this,source,duration,clip,isCurrent={same()},
            requireWorkerCurrent={check(VideoPrivateOwner.namespace(applicationContext,mode)==owner){"Account changed"}},
            message={text->if(project===current&&canApplyCaptionReview(captured)&&darkHoldControls==null)status.text=text},
            released={if(darkHoldControls===controls){darkHoldControls=null;importing=false}},
            ready={pack->
                val images=mutableListOf<android.widget.ImageView>()
                var ownedRequest:VideoBatchReview?=null
                var ownedDialog:android.app.AlertDialog?=null
                try{
                if(project!==current||!canApplyCaptionReview(captured)||selectedClip!=index)false
                else{
                    val analysis=pack.analysis
                    batchReviewRequest?.close();batchReviewDialog?.dismiss()
                    val request=VideoBatchReview(snapshot,music);ownedRequest=request;batchReviewRequest=request
                    val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12));setBackgroundColor(Color.WHITE)}
                    val description="${analysis.candidates.size} interior sampled dark holds from${analysis.times.size}requested frames; ${analysis.states.count{it==VideoDarkHolds.State.UNKNOWN}}unknown samples. Every removal is UNCHECKED. Review every downsampled frame below, then explicitly select unwanted intervals. Sampling may miss flashes or intentional content; edited titles/captions/effects are excluded. Picture AND original audio in selected intervals disappear; later footage ripples earlier. No edit until the next full review and Apply."
                    val information=TextView(this).apply{text=description;setTextColor(Color.BLACK)};panel.addView(information)
                    val checked=BooleanArray(analysis.candidates.size)
                    lateinit var choices:android.app.AlertDialog
                    analysis.candidates.forEachIndexed{position,candidate->
                        panel.addView(android.widget.CheckBox(this).apply{
                            text="Remove requested source ${VideoCaptions.formatTime(candidate.fromMs)}–${VideoCaptions.formatTime(candidate.toMs)}?";setTextColor(Color.BLACK);buttonTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);minHeight=dp(48);isChecked=false
                            setOnCheckedChangeListener{_,value->if(canUseBatchStage(choices,request,captured))checked[position]=value}
                        })
                        for(sampleIndex in candidate.first-1..candidate.last+1){
                            panel.addView(TextView(this).apply{text="Requested source ${VideoCaptions.formatTime(analysis.times[sampleIndex])} · ${analysis.states[sampleIndex]} · complete decoded downsampled frame";setTextColor(Color.BLACK)})
                            val image=android.widget.ImageView(this).apply{setImageBitmap(pack.frames[sampleIndex]);adjustViewBounds=true;scaleType=android.widget.ImageView.ScaleType.FIT_CENTER;contentDescription="Complete candidate frame and bright neighbors ${sampleIndex+1}"}
                            images+=image;panel.addView(image,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(180)))
                        }
                    }
                    choices=android.app.AlertDialog.Builder(this).setTitle("Inspect and choose dark holds").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Keep current edit",null).setPositiveButton("Review chosen removals",null).create()
                    ownedDialog=choices
                    choices.setOnShowListener{choices.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                        if(!canUseBatchStage(choices,request,captured)||project!==current){choices.dismiss();return@setOnClickListener}
                        val result=runCatching{VideoDarkHolds.prepare(snapshot,index,analysis,checked.indices.filter{checked[it]},music)}
                        if(result.isFailure){information.text=(result.exceptionOrNull()?.message?:"Choose unwanted dark holds")+"\n\n"+description;return@setOnClickListener}
                        val proposed=result.getOrThrow()
                        val body=TextView(this).apply{text=proposed.reviewText;setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
                        lateinit var review:android.app.AlertDialog
                        review=android.app.AlertDialog.Builder(this).setTitle("Review sampled dark-hold removals").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                            .setNegativeButton("Keep current edit",null).setPositiveButton("Apply and preview"){_,_->runCatching{check(project===current){"Project changed. Scan dark holds again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=index;val available=runCatching{showClip();previewProject()}.isSuccess;status.text=if(available)"Chosen sampled dark intervals removed. Watch/listen for missing content; Undo restores the prior edit."else"Intervals removed; preview unavailable. Undo restores the prior edit."}}.onFailure{status.text=it.message?:"Dark-hold removals could not apply."}}.create()
                        if(showBatchStage(review,request,captured){body.text=""})runCatching{check(request.propose(review,proposed.clips))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Dark-hold removals could not be reviewed."}
                    }}
                    showBatchStage(choices,request,captured){images.forEach{it.setImageDrawable(null)};images.clear();information.text="";pack.dispose()}
                }
                }catch(failure:Throwable){
                    images.forEach{it.setImageDrawable(null)};images.clear()
                    ownedRequest?.close();runCatching{ownedDialog?.dismiss()}
                    if(ownedRequest!=null&&batchReviewRequest===ownedRequest)batchReviewRequest=null
                    if(ownedDialog!=null&&batchReviewDialog===ownedDialog)batchReviewDialog=null
                    pack.dispose();throw failure
                }
            })
        darkHoldControls=controls
        runCatching{controls.open()}.onFailure{controls.close();if(project===current&&canApplyCaptionReview(captured))status.text="Could not open sampled-dark-hold controls. Original video and edits were kept."}
    }

    private fun sourceAudioOnsetCuts(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack;val index=selectedClip
        val clip=snapshot.getOrNull(index)?:return
        if(clip.endMs-clip.startMs>120_000||clip.endMs>Int.MAX_VALUE){status.text="Choose a selected clip of at most two source minutes for onset analysis.";return}
        val sources=sourceUris();val durations=sourceDurations();val source=sources[clip.sourceId]?:return
        val mode=accessMode;val owner=videoLookNamespace()?:return;val generation=captionReviewGeneration
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        video.pause();stopProjectPreview();importing=true
        val busy=closeState()
        lateinit var controls:VideoAudioOnsetControls
        fun same()=audioOnsetControls===controls&&workspaceActive&&!isDestroyed&&!isFinishing&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&project===current&&current.segments==snapshot&&current.soundtrack==music&&selectedClip==index&&captionReviewGeneration==generation&&accessMode==mode&&videoLookNamespace()==owner&&busy.stillMatches(closeState())&&sourceUris()==sources&&sourceDurations()==durations
        controls=VideoAudioOnsetControls(this,source,clip,isCurrent={same()},
            requireWorkerCurrent={check(VideoPrivateOwner.namespace(applicationContext,mode)==owner){"Account changed"}},
            message={text->if(project===current&&canApplyCaptionReview(captured)&&audioOnsetControls==null)status.text=text},
            released={if(audioOnsetControls===controls){audioOnsetControls=null;importing=false}},
            ready={analysis->
                if(project===current&&canApplyCaptionReview(captured)&&selectedClip==index){
                    batchReviewRequest?.close();batchReviewDialog?.dismiss()
                    val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
                    val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12));setBackgroundColor(Color.WHITE)}
                    val description="${analysis.events.size} potential cuts; ${analysis.knownMs}/${analysis.totalMs}ms known audio; ${analysis.suppressed} nearby alternatives suppressed. Uncheck false positives, then review all resulting source windows. Original edit is unchanged. Energy rises are not guaranteed beats or speech events; timing is quantized to20ms source windows."
                    val information=TextView(this).apply{text=description;setTextColor(Color.BLACK)};panel.addView(information)
                    val checked=BooleanArray(analysis.events.size){true}
                    lateinit var choices:android.app.AlertDialog
                    analysis.events.forEachIndexed{position,event->panel.addView(android.widget.CheckBox(this).apply{
                        text="Source ${VideoCaptions.formatTime(event.sourceMs)} · peak ${VideoAudioLevels.levelText(event.peakRms)}; baseline ${VideoAudioLevels.levelText(event.baselineRms)}";setTextColor(Color.BLACK);buttonTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);minHeight=dp(48);isChecked=true
                        setOnCheckedChangeListener{_,value->if(canUseBatchStage(choices,request,captured))checked[position]=value}
                    })}
                    choices=android.app.AlertDialog.Builder(this).setTitle("Choose source-audio onsets").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review chosen cuts",null).create()
                    choices.setOnShowListener{choices.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                        if(!canUseBatchStage(choices,request,captured)||project!==current){choices.dismiss();return@setOnClickListener}
                        val result=runCatching{VideoAudioOnsets.prepare(snapshot,index,analysis,analysis.events.filterIndexed{position,_->checked[position]}.map{it.sourceMs},music)}
                        if(result.isFailure){information.text=(result.exceptionOrNull()?.message?:"Choose fewer onsets")+"\n\n"+description;return@setOnClickListener}
                        val proposed=result.getOrThrow()
                        val body=TextView(this).apply{text=proposed.reviewText;setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
                        lateinit var review:android.app.AlertDialog
                        review=android.app.AlertDialog.Builder(this).setTitle("Review audio-onset cuts").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                            .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->runCatching{check(project===current){"Project changed. Analyse onsets again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=index;val available=runCatching{showClip();previewProject()}.isSuccess;status.text=if(available)"Chosen onset cuts applied. Listen and review fades; Undo restores the prior edit."else"Cuts applied; preview unavailable. Undo restores the prior edit."}}.onFailure{status.text=it.message?:"Onset cuts could not apply."}}.create()
                        if(showBatchStage(review,request,captured){body.text=""})runCatching{check(request.propose(review,proposed.clips))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Onset cuts could not be reviewed."}
                    }}
                    showBatchStage(choices,request,captured){information.text=""}
                }
            })
        audioOnsetControls=controls
        runCatching{controls.open()}.onFailure{controls.close();if(project===current&&canApplyCaptionReview(captured))status.text="Could not open onset controls. Original video and edits were kept."}
    }

    private fun silencePlaybackRanges(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack
        val duration=runCatching{VideoPlaybackTime.durationUs(snapshot)}.getOrElse{status.text="Timeline duration is unsupported.";return}
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Paste 1–100 start,end intervals in current edited playback seconds, with up to three decimal places. Current timeline: %.6f seconds. Overlapping/touching ranges merge. This splits source clips and sets only covered original clip audio to zero; every video source millisecond and its order/speed stays. Existing muted clips need no splitting.\n\nSource boundaries round outward by less than one source millisecond (less than two playback milliseconds at slow speeds). Tiny source pieces are allowed; this is not a sample/frame-exact mute guarantee. Hard mute edges have no smoothing. Splitting repeats existing VIDEO and audio edge fades on the new pieces. Source motion stays anchored; intersecting captions/provenance remain.\n\nSoundtrack/voiceover audio is NOT muted, captions remain readable, and original files are unchanged. This is not a privacy certification. Review all source windows and listen before export. One Undo restores the prior edit.",duration/1_000_000.0);setTextColor(Color.BLACK)})
        panel.addView(TextView(this).apply{text="Playback ranges: start,end seconds per line";setTextColor(Color.BLACK)})
        val input=EditText(this).apply{setTextColor(Color.BLACK);contentDescription="Playback ranges to silence";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(16000))};panel.addView(input)
        val editor=android.app.AlertDialog.Builder(this).setTitle("Silence playback ranges").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!canUseBatchStage(editor,request,captured)||project!==current){editor.dismiss();return@setOnClickListener}
            val prepared=runCatching{VideoPlaybackMute.prepare(snapshot,input.text.toString(),music)}
            if(prepared.isFailure){input.error=prepared.exceptionOrNull()?.message?:"Check the playback intervals";return@setOnClickListener}
            val result=prepared.getOrThrow()
            val intervals=result.intervals.joinToString("\n"){range->String.format(java.util.Locale.ROOT,"Original edited playback %.6f–%.6f seconds",range.from/1_000_000.0,range.to/1_000_000.0)}
            val mapping=result.pieces.joinToString("\n"){piece->val old=snapshot[piece.originalIndex];String.format(java.util.Locale.ROOT,"Original clip %d / source %s: %.3f–%.3fs at %.1fx; volume %.3f → %.3f%s; %d cues",piece.originalIndex+1,piece.clip.sourceId,piece.clip.startMs/1000.0,piece.clip.endMs/1000.0,piece.clip.speed,old.volume,piece.clip.volume,if(piece.newlyMuted)" (newly muted)"else" (retained)",piece.clip.captions.size)}
            val body=TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Keep all video source milliseconds and their order/speed. %d → %d pieces; %d newly muted pieces. Playback %.6f → %.6f seconds; %.6f seconds of microsecond rounding contraction.\n\nMerged requested intervals on the original edited clock:\n%s\n\nComplete resulting source/volume timeline:\n%s\n\nOnly original clip audio is silenced. Soundtrack/voiceover remains audible; captions and originals stay. Source-edge rounding can widen muting by less than one source millisecond per boundary. Hard edges have no smoothing; this is not a sample/frame-exact guarantee. Existing video/audio fades repeat per new piece and may visibly change the edit; source motion stays anchored. Intersecting captions keep their source timestamps/provenance. Integer timing can contract by up to one microsecond per added piece; requested intervals refer to the edit before these splits. Preview/listen before export. Apply is one Undo.",snapshot.size,result.clips.size,result.pieces.count{it.newlyMuted},result.beforeUs/1_000_000.0,result.afterUs/1_000_000.0,(result.beforeUs-result.afterUs)/1_000_000.0,intervals,mapping);setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
            lateinit var review:android.app.AlertDialog
            review=android.app.AlertDialog.Builder(this).setTitle("Review silenced source audio").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->runCatching{check(project===current){"Project changed. Choose the audio ranges again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=0;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Source audio ranges silenced. Soundtrack remains audible. Undo restores the prior edit."else"Ranges silenced; preview unavailable. Soundtrack remains audible. Undo restores the prior edit."}}.onFailure{status.text=it.message?:"Audio ranges could not apply."}}.create()
            if(showBatchStage(review,request,captured){body.text=""})runCatching{check(request.propose(review,result.clips))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Audio ranges could not be reviewed."}
        }}
        showBatchStage(editor,request,captured){input.text?.clear()}
    }

    private fun matchClipAudio(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return;val music=current.soundtrack
        if(!VideoAudioMatch.eligible(clip)){status.text="Use an unmuted selected clip of at most two source minutes for audio matching.";return}
        val references=clips.indices.filter{it!=index&&VideoAudioMatch.eligible(clips[it])}
        if(references.isEmpty()){status.text="Add another unmuted clip of at most two source minutes as a reference.";return}
        val sources=captured.sources;val generation=quietAudioGeneration
        fun analyse(reference:Int){
            if(project!==current||!canApplyCaptionReview(captured))return
            val ref=clips[reference];val selectedUri=sources[clip.sourceId]?:return;val referenceUri=sources[ref.sourceId]?:return
            stopProjectPreview();video.pause()
            val cancellation=AtomicBoolean(false);quietAudioCancellation=cancellation;importing=true
            status.text="Measuring two original audio ranges locally. Current volume settings stay until Apply."
            val progress=android.app.AlertDialog.Builder(this).setTitle("Matching source RMS levels")
                .setMessage("Reads at most two source minutes per clip, with a checked30second limit per decode.95% source-time coverage by known energy windows is required. No media is uploaded. Blocked providers may delay cancellation.")
                .setNegativeButton("Cancel"){_,_->cancellation.set(true)}.create()
            progress.setOnCancelListener{cancellation.set(true)}
            quietAudioDialog=progress;progress.setOnDismissListener{if(quietAudioDialog===progress)quietAudioDialog=null};progress.show()
            val appContext=applicationContext
            Thread({
                val result=runCatching{
                    val selected=VideoAudioMatch.measure(clip,VideoQuietAudio.scan(appContext,selectedUri,clip.startMs,clip.endMs){cancellation.get()})
                    if(cancellation.get())throw java.util.concurrent.CancellationException("Audio matching cancelled")
                    val other=if(clip.sourceId==ref.sourceId&&clip.startMs==ref.startMs&&clip.endMs==ref.endMs)selected else VideoAudioMatch.measure(ref,VideoQuietAudio.scan(appContext,referenceUri,ref.startMs,ref.endMs){cancellation.get()})
                    if(cancellation.get())throw java.util.concurrent.CancellationException("Audio matching cancelled")
                    VideoAudioMatch.prepare(clips,index,reference,selected,other)
                }
                runOnUiThread{
                    if(quietAudioCancellation===cancellation){quietAudioCancellation=null;importing=false}
                    runCatching{progress.dismiss()}
                    if(project!==current||quietAudioGeneration!=generation||!canApplyCaptionReview(captured))return@runOnUiThread
                    if(cancellation.get()){status.text="Audio matching cancelled. Volume settings stayed unchanged.";return@runOnUiThread}
                    result.onFailure{status.text=it.message?:"Audio levels could not be matched. Existing edits stay."}.onSuccess{plan->
                        batchReviewRequest?.close();batchReviewDialog?.dismiss()
                        val request=VideoBatchReview(clips,music);batchReviewRequest=request
                        lateinit var review:android.app.AlertDialog
                        review=android.app.AlertDialog.Builder(this).setTitle("Review clip audio level match")
                            .setMessage(String.format(java.util.Locale.ROOT,"Selected clip %d → reference clip %d.\n\nVolume gain %.6f → %.6f (allowed0–1). Estimated selected RMS %s → %s. Reference at its current volume: %s.\n\n%s\n\nDuration-weighted original decoded RMS with current clip gains; this excludes audio fades, speed-processing differences and soundtrack/mix effects. It is not LUFS, perceived-loudness matching or a clipping guarantee. Both ranges need95%% source-time coverage by known20ms energy windows, not exact sample completeness, and source/target levels above -60dBFS. Muted choices are preserved. Only the selected clip volume changes; the reference, footage, captions, other edits and soundtrack stay. Apply is one Undo. Listen to preview before export.",index+1,reference+1,clip.volume,plan.gain,VideoAudioLevels.levelText(plan.beforeRms),VideoAudioLevels.levelText(plan.predictedRms),VideoAudioLevels.levelText(plan.targetRms),if(plan.capped)"Target cannot be reached within1.0 gain; the proposed result stays below it."else"The proposed gain aims at this measured reference RMS."))
                            .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->
                                runCatching{check(project===current){"Project changed. Measure audio again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=index;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Clip audio level matched. Undo restores its previous gain."else"Gain applied; preview unavailable. Undo restores its previous gain."}}.onFailure{status.text=it.message?:"Gain could not apply. Existing edits stay."}
                            }.create()
                        if(showBatchStage(review,request,captured))runCatching{check(request.propose(review,plan.clips))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Audio match could not be reviewed."}
                    }
                }
            },"noble-audio-match").start()
        }
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Match selected audio · choose reference")
            .setItems(references.map{reference->"Clip ${reference+1} · source ${clips[reference].sourceId}"}.toTypedArray()){_,choice->if(canUseInspectorReview(dialog,captured)&&project===current)analyse(references[choice])}
            .setNegativeButton("Cancel",null).create()
        showInspectorReview(dialog,captured)
    }

    private fun bilingualCaptions(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val index=selectedClip;val clip=snapshot.getOrNull(index)?:return;val music=current.soundtrack
        if(clip.captions.isEmpty()){status.text="Add the first language's captions to this clip before combining.";return}
        val duration=captured.durations[clip.sourceId]?:return
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply{text="Paste a second language as UTF-8 SRT text. Both tracks use this original video's source timestamps before speed changes, up to ${VideoCaptions.formatTime(duration)}. Existing text appears above the second track when cues overlap; single-track gaps show the available text. This flattens both tracks into one editable caption list, including stored cues outside current trim visibility. No translation, speech analysis or upload. Each combined cue must fit240characters/3lines, result up to200cues and project limits. Existing clip edits, source times, subtitle-document protection and soundtrack stay. Review before Apply; Undo restores the first track.";setTextColor(Color.BLACK)})
        val field=EditText(this).apply{setTextColor(Color.BLACK);minLines=4;maxLines=10;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(VideoCaptions.MAX_SRT_BYTES));contentDescription="Second language source-time SRT text"};panel.addView(field)
        val editor=android.app.AlertDialog.Builder(this).setTitle("Combine bilingual captions").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!canUseBatchStage(editor,request,captured)||project!==current){editor.dismiss();return@setOnClickListener}
            val result=runCatching{VideoBilingualCaptions.prepare(snapshot,index,duration,field.text.toString(),music)}
            if(result.isFailure){field.error=result.exceptionOrNull()?.message?:"Check this second subtitle track";return@setOnClickListener}
            val prepared=result.getOrThrow();val next=prepared.clips
            val body=TextView(this).apply{text="${prepared.primaryCount} first-track + ${prepared.secondaryCount} second-track cues → ${prepared.outputCount} combined cues. ${prepared.overlapSourceMs} source milliseconds overlap. Adjacent identical combined text is joined; timing uses the union of source boundaries. Single-track gaps keep their text and empty gaps stay empty. This is one flattened track; separate languages are not retained independently. Other clips, timing/speed/appearance, subtitle binding and soundtrack stay. Apply is one Undo; preview before exporting.\n\n"+next[index].captions.joinToString("\n\n"){cue->"${VideoCaptions.formatTime(cue.startMs)} → ${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}"};setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
            lateinit var review:android.app.AlertDialog
            review=android.app.AlertDialog.Builder(this).setTitle("Review bilingual caption track").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->
                    runCatching{check(project===current){"Project changed. Combine the tracks again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=index;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Bilingual captions combined. Undo restores the first track."else"Captions combined; preview unavailable. Undo restores the first track."}}.onFailure{status.text=it.message?:"Bilingual captions could not apply."}
                }.create()
            if(showBatchStage(review,request,captured){body.text=""})runCatching{check(request.propose(review,next))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Combined captions could not be reviewed."}
        }}
        showBatchStage(editor,request,captured){field.text?.clear()}
    }

    private fun clipReplacement(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val index=selectedClip;val clip=snapshot.getOrNull(index)?:return;val music=current.soundtrack
        val durations=captured.durations
        val targets=runCatching{VideoClipReplacement.targets(snapshot,index,durations)}.getOrElse{status.text=it.message?:"Replacement sources are unavailable.";return}
        if(targets.isEmpty()){status.text="Add another video source long enough for this clip first. Replace uses sources already added to this project.";return}
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        fun choose(target:VideoClipReplacement.Target){
            val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
            panel.addView(TextView(this).apply{text=String.format(java.util.Locale.ROOT,"Clip %d will use source %s. Enter original-source start seconds,0–%.3f, up to3decimals. The %.3fsecond source span and %.3fsecond playback duration stay fixed at %.1fx. Title, color, framing, fades and original-audio volume settings stay; audio will come from the new source. This clears this clip's %d old captions, subtitle-document binding and source-anchored motion. Undo restores them. No media is overwritten. Review before Apply.",index+1,target.id,target.latestStartMs/1000.0,(clip.endMs-clip.startMs)/1000.0,clip.durationUs/1_000_000.0,clip.speed,clip.captions.size);setTextColor(Color.BLACK)})
            val field=EditText(this).apply{setText("0.000");setTextColor(Color.BLACK);setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(16));contentDescription="Replacement original-source start seconds"};panel.addView(field)
            val editor=android.app.AlertDialog.Builder(this).setTitle("Replacement source window").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
            editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(!canUseBatchStage(editor,request,captured)||project!==current){editor.dismiss();return@setOnClickListener}
                val proposed=runCatching{VideoClipReplacement.propose(snapshot,index,durations,target.id,VideoCaptions.seconds(field.text.toString()),music)}
                if(proposed.isFailure){field.error=proposed.exceptionOrNull()?.message?:"Choose a source window";return@setOnClickListener}
                val next=proposed.getOrThrow();val replacement=next[index]
                lateinit var review:android.app.AlertDialog
                review=android.app.AlertDialog.Builder(this).setTitle("Review replacement footage")
                    .setMessage(String.format(java.util.Locale.ROOT,"Clip %d only: source %s %.3f–%.3fs → source %s %.3f–%.3fs.\n\nSame %.6fsecond playback duration, speed, title, title appearance, color, framing, fades and audio-volume settings. Other clips and soundtrack stay unchanged. New footage/source audio may look or sound different; no visual match or codec frame accuracy is promised.\n\nClears %d old source-time captions and this clip's subtitle binding; resets source-anchored motion. The old media and subtitle documents are not deleted. Apply is one Undo step. Preview before export.",index+1,clip.sourceId,clip.startMs/1000.0,clip.endMs/1000.0,replacement.sourceId,replacement.startMs/1000.0,replacement.endMs/1000.0,clip.durationUs/1_000_000.0,clip.captions.size))
                    .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->
                        runCatching{check(project===current){"Project changed. Prepare replacement again."};commitBatchReview(review,request,captured)}.onSuccess{applied->if(applied){selectedClip=index;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Clip footage replaced. Undo restores the original clip and annotations."else"Footage replaced; preview unavailable. Undo restores the original clip and annotations."}}.onFailure{status.text=it.message?:"Replacement could not apply. Existing edits stay."}
                    }.create()
                if(showBatchStage(review,request,captured))runCatching{check(request.propose(review,next))}.onFailure{request.close();review.dismiss();status.text=it.message?:"Replacement could not be reviewed."}
            }}
            showBatchStage(editor,request,captured){field.text?.clear()}
        }
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Replace clip footage · added sources")
            .setItems(targets.map{String.format(java.util.Locale.ROOT,"Source %s · %.3fs available",it.id,it.durationMs/1000.0)}.toTypedArray()){_,position->if(canUseBatchStage(dialog,request,captured)&&project===current)choose(targets[position])}
            .setNegativeButton("Cancel",null).create()
        showBatchStage(dialog,request,captured)
    }

    private fun repeatedFootage(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments
        val result=runCatching{VideoRepeatedFootage.inspect(clips){check(project===current&&canRequestTimelineCaptionImport()){"Project changed. Reopen repeated footage."}}}.getOrElse{status.text=it.message?:"Repeated footage is unavailable.";return}
        if(!canApplyCaptionReview(review))return
        if(result.pairs.isEmpty()){
            val dialog=android.app.AlertDialog.Builder(this).setTitle("Repeated source footage").setMessage("No overlapping source ranges were found across different timeline clips. This compares source IDs and millisecond ranges, not pixels: visually similar scenes or duplicate files imported as different sources are not detected. The timeline and original media stay unchanged.").setPositiveButton("Close",null).create()
            showInspectorReview(dialog,review);return
        }
        lateinit var list:android.app.AlertDialog
        list=android.app.AlertDialog.Builder(this).setTitle("${result.totalPairs} repeated source pairs")
            .setItems((listOf("About this report · ${result.pairs.size} shown / ${result.omittedPairs} omitted")+result.pairs.map{pair->String.format(java.util.Locale.ROOT,"Clips %d + %d · source %s · %.3f–%.3fs",pair.first.clipIndex+1,pair.second.clipIndex+1,pair.sourceId,pair.startMs/1000.0,pair.endMs/1000.0)}).toTypedArray()){_,choice->
                if(!canUseInspectorReview(list,review)||project!==current||current.segments!=clips)return@setItems
                if(choice==0){
                    lateinit var info:android.app.AlertDialog
                    info=android.app.AlertDialog.Builder(this).setTitle("About repeated source footage").setMessage("${result.totalPairs} overlapping same-source clip pairs; showing up to100longest original-source overlaps, with clip-order ties. ${result.omittedPairs} pairs omitted. Overlap durations are per pair and can cover the same footage more than twice. Different speed, color, crop, audio, titles or captions do not hide source reuse. This is not visual duplicate detection and does not compare files imported under different source IDs. Cuts touching at an edge do not overlap. Nothing is deleted or edited. Reopen the report after timeline changes.").setPositiveButton("Back"){_,_->if(canUseInspectorReview(info,review)&&project===current&&current.segments==clips)repeatedFootage()}.setNegativeButton("Close",null).create()
                    showInspectorReview(info,review);return@setItems
                }
                val pair=result.pairs[choice-1]
                lateinit var dialog:android.app.AlertDialog
                fun jump(occurrence:VideoRepeatedFootage.Occurrence){
                    if(!canUseInspectorReview(dialog,review)||project!==current||current.segments!=clips)return
                    inspectorReviewDialog=null;invalidateCaptionReviews()
                    runCatching{
                        video.pause();stopProjectPreview();selectedClip=occurrence.clipIndex;showClip()
                        pendingSeekMs=occurrence.sourceMs;if(sourceReady)video.seekTo(occurrence.sourceMs.toInt())
                        playheadUs=occurrence.outputUs;timeline.playhead(playheadUs);updateTimecode()
                    }.onSuccess{status.text="Jumped to repeated source footage in clip ${occurrence.clipIndex+1}. Timeline edits stay unchanged."}.onFailure{status.text="Occurrence selected; preview is unavailable. Timeline edits stay unchanged."}
                }
                dialog=android.app.AlertDialog.Builder(this).setTitle("Repeated source · clips ${pair.first.clipIndex+1} and ${pair.second.clipIndex+1}")
                    .setMessage(String.format(java.util.Locale.ROOT,"Original source %s: %.3f–%.3fs.\n\nFirst occurrence: edited playback %.6fs, shared stretch %.6fs.\nSecond occurrence: edited playback %.6fs, shared stretch %.6fs.\n\nJump selects the existing clip at the beginning of this shared source range. Playback positions account for clip speed. This compares source identity/ranges only; different edits or intentional reprises can be valid. No removal or Undo edit is made.",pair.sourceId,pair.startMs/1000.0,pair.endMs/1000.0,pair.first.outputUs/1_000_000.0,pair.first.overlapPlaybackUs/1_000_000.0,pair.second.outputUs/1_000_000.0,pair.second.overlapPlaybackUs/1_000_000.0))
                    .setNeutralButton("Jump to first"){_,_->jump(pair.first)}.setPositiveButton("Jump to second"){_,_->jump(pair.second)}.setNegativeButton("Back"){_,_->if(canUseInspectorReview(dialog,review)&&project===current&&current.segments==clips)repeatedFootage()}.create()
                showInspectorReview(dialog,review)
            }.setNegativeButton("Close",null).create()
        showInspectorReview(list,review)
    }

    private fun playbackCuts(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        fun review(result:VideoPlaybackCuts.Result){
            if(batchReviewRequest!==request||request.isClosed||project!==current||!canApplyCaptionReview(captured))return
            val mapping=result.cuts.joinToString("\n"){cut->String.format(java.util.Locale.ROOT,"%.3fs → %.6fs · clip %d / source %s at %.3fs",cut.requestedUs/1_000_000.0,cut.actualUs/1_000_000.0,cut.clipIndex+1,cut.sourceId,cut.sourceTimeMs/1000.0)}
            lateinit var dialog:android.app.AlertDialog
            dialog=android.app.AlertDialog.Builder(this).setTitle("Review playback cuts")
                .setMessage("${result.cuts.size} new cuts · ${result.existingBoundaries} existing boundaries kept. ${snapshot.size} → ${result.clips.size} clips.\n\nRequested → actual edited-playback times and original source positions:\n$mapping\n\nAll source footage stays in order without gaps or repeats. Cuts round to the nearest source millisecond; total duration changes by ${result.durationDriftUs} microseconds from rounding. This is not a codec frame-exact cut guarantee. Captions crossing cuts stay on both pieces with original timestamps and provenance. Titles, motion, framing, color and audio settings stay; edge/audio fades repeat on new pieces and can change the rendered result. Soundtrack settings stay. Apply as one Undo step and preview before exporting. Originals are never written or uploaded.")
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->
                    runCatching{check(project===current){"Project changed. Prepare playback cuts again."};commitBatchReview(dialog,request,captured)}.onSuccess{applied->
                        if(applied){selectedClip=0;val refreshed=runCatching{showClip();previewProject()}.isSuccess;status.text=if(refreshed)"Playback cuts applied. Undo restores the previous timeline."else"Playback cuts applied; preview is unavailable. Undo restores the previous timeline."}
                    }.onFailure{status.text=it.message?:"Cuts could not apply. Existing edits stay."}
                }.create()
            if(showBatchStage(dialog,request,captured))runCatching{check(request.propose(dialog,result.clips))}.onFailure{request.close();dialog.dismiss();status.text=it.message?:"Cuts could not be reviewed."}
        }
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply{text="Paste 1–100 strictly increasing edited-playback times, one per line: decimal seconds (12.500) or HH:MM:SS.mmm. Times are after speed changes and must be inside the project. Existing exact boundaries are kept; all-new pieces need 0.1 playback second. This splits footage without deleting/reordering it. Review source-millisecond rounding before Apply; fades repeat on new pieces.";setTextColor(Color.BLACK)})
        val field=EditText(this).apply{setTextColor(Color.BLACK);minLines=3;maxLines=8;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(16000));contentDescription="Pasted edited-playback cut times"};panel.addView(field)
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Split at pasted playback times").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!canUseBatchStage(dialog,request,captured)){dialog.dismiss();return@setOnClickListener}
            val result=runCatching{VideoPlaybackCuts.prepare(snapshot,field.text.toString(),music)}
            if(result.isFailure){field.error=result.exceptionOrNull()?.message?:"Check the playback times";return@setOnClickListener}
            review(result.getOrThrow())
        }}
        showBatchStage(dialog,request,captured){field.text?.clear()}
    }

    private fun captionWorksheet(){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack
        val original=runCatching{VideoCaptionWorksheet.export(snapshot)}.getOrElse{status.text=it.message?:"No caption worksheet is available.";return}
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        fun review(result:VideoCaptionWorksheet.Result){
            if(batchReviewRequest!==request||request.isClosed||project!==current||!canApplyCaptionReview(captured))return
            val changes=snapshot.flatMapIndexed{i,clip->clip.captions.mapIndexedNotNull{j,cue->val next=result.clips[i].captions[j];if(next.text==cue.text)null else "${i+1}.${j+1} · ${VideoCaptions.formatTime(cue.startMs)} → ${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}\n→ ${next.text}"}}.joinToString("\n\n")
            val body=TextView(this).apply{text="${result.changedCues} of ${snapshot.sumOf{it.captions.size}} captions change. Original source timestamps, clip ranges/speeds, subtitle provenance, all appearance and soundtrack stay unchanged. This replaces text, not speech/audio; review each translation and preview before export. Apply is one Undo step.\n\n$changes";setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
            lateinit var dialog:android.app.AlertDialog
            dialog=android.app.AlertDialog.Builder(this).setTitle("Review caption text worksheet").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->
                    runCatching{check(project===current){"Project changed. Copy a new worksheet."};commitBatchReview(dialog,request,captured)}.onSuccess{applied->if(applied){selectedClip=0;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Caption text replaced. Undo restores every original cue."else"Caption text replaced; preview unavailable. Undo restores every original cue."}}.onFailure{status.text=it.message?:"Caption text could not apply."}
                }.create()
            if(showBatchStage(dialog,request,captured){body.text=""})runCatching{check(request.propose(dialog,result.clips))}.onFailure{request.close();dialog.dismiss();status.text=it.message?:"Caption text could not be reviewed."}
        }
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply{text="This includes all stored cues, even outside trimmed visibility. Edit or paste a matching worksheet. Keep the first identity line and every numbered key plus its tab separator. Rows may be reordered. Edit only text: \\n means a line break, \\\\ means a literal backslash. Each caption allows240characters and3lines; all project limits remain. Original timing/source/provenance must still match. No translation service is contacted. Copy puts all caption text on the system clipboard; other apps or clipboard sync may access it. Only copy if you agree. You can edit here without copying.";setTextColor(Color.BLACK)})
        val field=EditText(this).apply{setText(original);setTextColor(Color.BLACK);minLines=4;maxLines=10;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(VideoCaptionWorksheet.LIMIT));contentDescription="Caption translation worksheet text"};panel.addView(field)
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Caption translation worksheet").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)}).setNegativeButton("Cancel",null).setNeutralButton("Copy original worksheet",null).setPositiveButton("Review",null).create()
        dialog.setOnShowListener{
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener{
                if(!canUseBatchStage(dialog,request,captured)||project!==current)return@setOnClickListener
                runCatching{
                    val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE)as?android.content.ClipboardManager?:error("Clipboard is unavailable")
                    val data=android.content.ClipData.newPlainText("Noble caption text worksheet",original);data.description.extras=android.os.PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)};clipboard.setPrimaryClip(data)
                }.onSuccess{status.text="Original caption worksheet copied. Keep its keys and identity line."}.onFailure{status.text="Worksheet could not copy. You can still edit the text here."}
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(!canUseBatchStage(dialog,request,captured)||project!==current){dialog.dismiss();return@setOnClickListener}
                val result=runCatching{VideoCaptionWorksheet.prepare(snapshot,field.text.toString(),music)}
                if(result.isFailure){field.error=result.exceptionOrNull()?.message?:"Check this worksheet";return@setOnClickListener};review(result.getOrThrow())
            }
        }
        showBatchStage(dialog,request,captured){field.text?.clear()}
    }

    private var batchReviewRequest:VideoBatchReview?=null
    private var batchReviewDialog:android.app.AlertDialog?=null
    private fun canUseBatchStage(dialog:android.app.AlertDialog,request:VideoBatchReview,review:VideoCaptionReviewState)=
        batchReviewRequest===request&&batchReviewDialog===dialog&&request.owns(dialog)&&activeCaptionReviewDialog(dialog,review)
    private fun showBatchStage(dialog:android.app.AlertDialog,request:VideoBatchReview,review:VideoCaptionReviewState,onDismiss:()->Unit={}):Boolean {
        if(batchReviewRequest!==request||!canApplyCaptionReview(review)||!request.open(dialog)){request.close();onDismiss();dialog.dismiss();return false}
        val previous=batchReviewDialog;batchReviewDialog=dialog
        showCaptionReview(dialog,review){
            request.dismiss(dialog)
            if(batchReviewRequest===request&&request.isClosed)batchReviewRequest=null
            if(batchReviewDialog===dialog)batchReviewDialog=null
            onDismiss()
        }
        if(previous!==dialog)previous?.dismiss()
        return !request.isClosed
    }
    private fun commitBatchReview(dialog:android.app.AlertDialog,request:VideoBatchReview,review:VideoCaptionReviewState):Boolean {
        if(!canUseBatchStage(dialog,request,review)){
            if(batchReviewRequest===request&&batchReviewDialog===dialog&&request.owns(dialog))request.close()
            return false
        }
        return request.apply(dialog,requireNotNull(project)){
            check(batchReviewRequest===request&&batchReviewDialog===dialog&&activeCaptionReviewDialog(dialog,review)){"Project or session changed. Prepare this edit again."}
        }
    }
    private fun batchOptions(){
        val review=captureCaptionReview()?:return
        val tools=VideoBatchTool.values()
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Batch editing").setItems(tools.map{it.title}.toTypedArray()){_,index->
            if(activeCaptionReviewDialog(dialog,review))batchEdit(tools[index])
        }.setNegativeButton("Cancel",null).create()
        showCaptionReview(dialog,review)
    }

    private fun batchEdit(kind:VideoBatchTool){
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack;val index=selectedClip
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        fun valid()=batchReviewRequest===request&&!request.isClosed&&canApplyCaptionReview(captured)
        fun review(proposal:()->List<VideoSegment>){
            if(!valid())return
            val proposed=runCatching(proposal).getOrElse{status.text=it.message?:"This batch could not be prepared.";return}
            if(proposed==snapshot){status.text="These settings leave the project unchanged.";return}
            val duration=String.format(java.util.Locale.ROOT,"%.2f",proposed.sumOf{it.durationUs}/1_000_000.0)
            val explanation=when(kind){
                VideoBatchTool.SOURCE_RANGE_LIST->"Replaces the selected clip with your pasted original-source ranges in entered order. Repeats and overlaps are intentional; every excerpt plays forward at the current speed. Other clips and soundtrack settings stay. Captions outside each excerpt are removed; source-anchored motion and other appearance settings stay. Codec cuts are not promised frame-exact.\n\nSource ranges (seconds):\n"+proposed.subList(index,proposed.size-(snapshot.size-index-1)).joinToString("\n"){String.format(java.util.Locale.ROOT,"%.3f → %.3f",it.startMs/1000.0,it.endMs/1000.0)}
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
            lateinit var dialog:android.app.AlertDialog
            dialog=android.app.AlertDialog.Builder(this).setTitle("Review ${kind.title.lowercase()}")
                .setMessage("$explanation\n\n${snapshot.size} → ${proposed.size} clips · $duration seconds. Existing source files are unchanged. Apply as one undoable edit, then preview before export.")
                .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->
                    runCatching{commitBatchReview(dialog,request,captured)}
                        .onSuccess{applied->
                            if(!applied)return@onSuccess
                            selectedClip=if(kind in setOf(VideoBatchTool.SOURCE_RANGE_LIST,VideoBatchTool.CAPTION_SHIFT,VideoBatchTool.CAPTION_WRAP,VideoBatchTool.WORD_CAPTIONS,VideoBatchTool.MERGE_CAPTIONS,VideoBatchTool.TIMECODES,VideoBatchTool.STORYBOARD_TITLES,VideoBatchTool.CAPTION_HOLDS,VideoBatchTool.CAPTION_CASE,VideoBatchTool.CAPTION_REPLACE))index else 0
                            val refreshed=runCatching{showClip();previewProject()}.isSuccess
                            status.text=if(refreshed)"${kind.title} applied. Undo restores the previous project."else"${kind.title} applied. Preview is unavailable; Undo restores the previous project."
                        }.onFailure{status.text=it.message?:"Project limits reached. Existing edits are unchanged."}
                }.create()
            if(showBatchStage(dialog,request,captured))runCatching{check(request.propose(dialog,proposed))}.onFailure{
                request.close();dialog.dismiss();status.text=it.message?:"This batch could not be prepared. Existing edits are unchanged."
            }
        }
        fun options(labels:Array<String>,choose:(Int)->List<VideoSegment>){
            lateinit var dialog:android.app.AlertDialog
            dialog=android.app.AlertDialog.Builder(this).setTitle(kind.title).setItems(labels){_,i->
                if(canUseBatchStage(dialog,request,captured))review{choose(i)}
            }.setNegativeButton("Cancel",null).create()
            showBatchStage(dialog,request,captured)
        }
        fun textInput(hint:String,initial:String,choose:(String)->List<VideoSegment>){
            val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
            panel.addView(TextView(this).apply{text=hint;setTextColor(Color.BLACK)})
            val field=EditText(this).apply{setText(initial);setTextColor(Color.BLACK);minLines=3;maxLines=8;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(16000));contentDescription=kind.title};panel.addView(field)
            val dialog=android.app.AlertDialog.Builder(this).setTitle(kind.title).setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
            dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(!canUseBatchStage(dialog,request,captured)){dialog.dismiss();return@setOnClickListener}
                val proposed=runCatching{choose(field.text.toString())}
                if(proposed.isFailure){field.error=proposed.exceptionOrNull()?.message?:"Check these values";return@setOnClickListener}
                review{proposed.getOrThrow()}
            }};showBatchStage(dialog,request,captured){field.text?.clear()}
        }

        when(kind){
            VideoBatchTool.SOURCE_RANGE_LIST->{
                val clip=snapshot[index]
                val initial=String.format(java.util.Locale.ROOT,"%.3f, %.3f",clip.startMs/1000.0,clip.endMs/1000.0)
                textInput("Paste 1–100 lines: start,end in original-source seconds (decimal point, up to three decimals). Each range must stay inside this selected clip: ${VideoCaptions.formatTime(clip.startMs)} to ${VideoCaptions.formatTime(clip.endMs)}. Entered order, repeated ranges and overlaps are kept. Each excerpt needs at least 0.1 playback second. This replaces the selected clip; review before applying.",initial){VideoRangeAssembly.propose(snapshot,index,it)}
            }
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
                    if(!canUseBatchStage(dialog,request,captured)){dialog.dismiss();return@setOnClickListener}
                    val proposal=runCatching{VideoCaptionReplace.propose(snapshot,find.text.toString(),replacement.text.toString(),if(all.isChecked)null else index,!matchCase.isChecked)}
                    if(proposal.isFailure){find.error=proposal.exceptionOrNull()?.message?:"Check this replacement";return@setOnClickListener}
                    review{proposal.getOrThrow()}
                }};showBatchStage(dialog,request,captured){find.text?.clear();replacement.text?.clear()}
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
                lateinit var dialog:android.app.AlertDialog
                dialog=android.app.AlertDialog.Builder(this).setTitle("Manual tempo").setItems(tempos.map{"$it BPM"}.toTypedArray()){_,tempo->
                    if(!canUseBatchStage(dialog,request,captured))return@setItems
                    val beats=listOf(1,2,4);options(beats.map{"$it beat(s) per cut"}.toTypedArray()){VideoAuthoringTools.beatGrid(snapshot,tempos[tempo],beats[it])}
                }.setNegativeButton("Cancel",null).create()
                showBatchStage(dialog,request,captured)
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

    private fun commitClipBlockMove(review:VideoCaptionReviewState,plan:VideoClipBlockMove):Boolean {
        if(!canApplyCaptionReview(review)||!plan.changed)return false
        plan.apply(requireNotNull(project)){check(canApplyCaptionReview(review)){"Video or session changed. Select the clip group again."}}
        return true
    }
    private fun requestClipBlockMove(){
        val review=captureCaptionReview()?:return
        val clips=project?.segments?:return;val clip=clips.getOrNull(selectedClip)?:return
        if(clips.size<2){status.text="Add or split clips before moving a clip group.";return}
        if(!::start.isInitialized||!::end.isInitialized)return
        if(!timelineCaptionRangeMatches(clip,start.progress,end.progress)){status.text="Apply or reset the selected trim before moving a clip group.";return}
        val chosen=BooleanArray(clips.size)
        val labels=clips.mapIndexed{i,value->"Clip ${i+1} · source ${sourceNumber(value.sourceId)} · ${VideoCaptions.formatTime(value.startMs)}–${VideoCaptions.formatTime(value.endMs)}${if(value.title.isBlank())""else" · ${value.title}"}"}
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Select clips to move together")
            .setMultiChoiceItems(labels.toTypedArray(),chosen){d,which,checked->if(!canApplyCaptionReview(review))d.dismiss()else chosen[which]=checked}
            .setNegativeButton("Keep order",null).setPositiveButton("Choose destination",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!activeCaptionReviewDialog(dialog,review)){dialog.dismiss();return@setOnClickListener}
            val selected=clips.indices.filter{chosen[it]}
            if(selected.size !in 1..VideoClipBlockMove.MAX_SELECTION){status.text="Select 1–200 clips to move as a group.";return@setOnClickListener}
            dialog.dismiss();chooseClipBlockDestination(review,clips,selected)
        }};showCaptionReview(dialog,review)
    }
    private fun chooseClipBlockDestination(review:VideoCaptionReviewState,clips:List<VideoSegment>,selected:List<Int>){
        if(!canApplyCaptionReview(review))return
        val destinations=clips.indices.filter{it !in selected}+clips.size
        val labels=destinations.map{if(it==clips.size)"At timeline end"else"Before original clip ${it+1}${if(clips[it].title.isBlank())""else" · ${clips[it].title}"}"}
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Where should the clip group go?").setItems(labels.toTypedArray()){_,which->
            if(!activeCaptionReviewDialog(dialog,review))return@setItems
            runCatching{VideoClipBlockMove.prepare(clips,selected,destinations[which]){check(canApplyCaptionReview(review)){"Video or session changed"}}}
                .onSuccess{plan->if(!plan.changed)status.text="This destination keeps the current clip order. Nothing changed."else reviewClipBlockMove(review,plan)}
                .onFailure{status.text=it.message?:"Clip group could not be prepared"}
        }.setNegativeButton("Keep order",null).create();showCaptionReview(dialog,review)
    }
    private fun reviewClipBlockMove(review:VideoCaptionReviewState,plan:VideoClipBlockMove){
        if(!canApplyCaptionReview(review))return
        val before=plan.order.getOrNull(plan.insertionIndex-1)?.let{"original clip ${it+1}"}?:"timeline start"
        val after=plan.order.getOrNull(plan.insertionIndex+plan.selected.size)?.let{"original clip ${it+1}"}?:"timeline end"
        val order=plan.order.take(16).joinToString(" → "){"${it+1}"};val extra=if(plan.order.size>16)"\nFirst 16 of ${plan.order.size} clips shown; all clips are retained."else""
        val body=TextView(this).apply{setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12));contentDescription="Clip group move review";text="Move original clips ${plan.selected.joinToString{(it+1).toString()}} together, keeping their relative order. The group will sit between $before and $after.\n\nNew original-clip order: $order$extra\n\nAll ${plan.segments.size} clips stay, with unchanged source ranges, speed, appearance, captions and source audio. Unselected clips keep their relative order. Total playback length stays ${VideoCaptions.formatTime(plan.durationUs/1000)}. The separate soundtrack stays tied to project time and does not move with the group; preview the mix. Originals and saved versions stay unchanged. One Undo restores the previous order."}
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Move selected clip group?").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)}).setNegativeButton("Keep order",null).setPositiveButton("Move group"){_,_->
            if(!activeCaptionReviewDialog(dialog,review))return@setPositiveButton
            runCatching{check(commitClipBlockMove(review,plan)){"Video or session changed. Select the clip group again."}}
                .onSuccess{selectedClip=plan.insertionIndex;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Clip group moved. Preview to check cuts and sound; Undo restores order."else"Clip group moved, but preview is unavailable. Undo restores order."}
                .onFailure{if(workspaceActive)status.text=it.message?:"Clip group was not moved"}
        }.create();showCaptionReview(dialog,review)
    }

    private fun montageOptions() {
        val captured=captureCaptionReview()?:return
        val current=project?:return;val snapshot=current.segments;val music=current.soundtrack
        batchReviewRequest?.close();batchReviewDialog?.dismiss()
        val request=VideoBatchReview(snapshot,music);batchReviewRequest=request
        val targets=listOf(15_000L,30_000L,60_000L)
        lateinit var chooser:android.app.AlertDialog
        chooser=android.app.AlertDialog.Builder(this).setTitle("Build a montage")
            .setItems(arrayOf("15 seconds","30 seconds","60 seconds")){_,choice->
                if(!canUseBatchStage(chooser,request,captured))return@setItems
                val proposed=runCatching{VideoStoryTools.montage(snapshot,targets[choice])}.getOrElse{status.text="Cannot build a montage from this project.";return@setItems}
                if(proposed==snapshot){status.text="These clips already fit this montage length. Current edits are unchanged.";return@setItems}
                val seconds=String.format(java.util.Locale.ROOT,"%.2f",proposed.sumOf{it.durationUs}/1_000_000.0)
                lateinit var confirmation:android.app.AlertDialog
                confirmation=android.app.AlertDialog.Builder(this).setTitle("Review montage")
                    .setMessage("$seconds seconds · ${proposed.size} clips. Uses balanced centre excerpts in your current order. Short footage stays shorter; nothing is repeated or generated. Existing color, speed, titles, visible captions and music are kept. Replaces the timeline as one undoable edit. Preview before exporting.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Apply and preview"){_,_->
                        runCatching{commitBatchReview(confirmation,request,captured)}.onSuccess{applied->
                            if(!applied)return@onSuccess
                            selectedClip=0;val refreshed=runCatching{showClip();previewProject()}.isSuccess
                            status.text=if(refreshed)"Montage applied. Undo restores the full timeline."else"Montage applied. Preview is unavailable; Undo restores the full timeline."
                        }.onFailure{status.text=it.message?:"Montage could not be applied; existing edits are unchanged."}
                    }.create()
                if(showBatchStage(confirmation,request,captured))runCatching{check(request.propose(confirmation,proposed))}.onFailure{
                    request.close();confirmation.dismiss();status.text="Montage could not be prepared. Existing edits are unchanged."
                }
            }.setNegativeButton("Cancel",null).create()
        showBatchStage(chooser,request,captured)
    }

    private var scriptCaptionEditor:android.app.AlertDialog?=null
    private var scriptCaptionConfirmation:android.app.AlertDialog?=null
    private fun commitScriptCaptionReview(editor:android.app.AlertDialog,confirmation:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,clip:VideoSegment,cues:List<VideoCaption>):Boolean {
        if(scriptCaptionEditor!==editor||scriptCaptionConfirmation!==confirmation||!activeCaptionReviewDialog(editor,review)||!activeCaptionReviewDialog(confirmation,review))return false
        return commitCaptionReview(review,current,index,clip,cues)
    }
    private fun scriptCaptionOptions() {
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply{text="Paste one caption per line, up to 100 lines and 240 characters per line. Timing is estimated from text length across this clip's stored trim, not speech recognition. Live trim sliders do not change this range. Review and adjust in Captions. Applying replaces this clip's captions; Undo restores them.";setTextColor(Color.DKGRAY)})
        val script=EditText(this).apply{hint="Write or paste your script";contentDescription="Caption script, one caption per line";minLines=4;maxLines=7;gravity=Gravity.TOP;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(16_000))}
        panel.addView(script)
        lateinit var editor:android.app.AlertDialog
        editor=android.app.AlertDialog.Builder(this).setTitle("Script to captions").setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Review timing",null).create()
        editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(scriptCaptionEditor!==editor||!activeCaptionReviewDialog(editor,review)){editor.dismiss();return@setOnClickListener}
            val proposed=runCatching{VideoStoryTools.scriptCaptions(clip,script.text.toString())}.getOrElse{script.error=it.message;return@setOnClickListener}
            val overview=proposed.mapIndexed{i,cue->"${i+1}. ${VideoTrimRange.format(cue.startMs)}–${VideoTrimRange.format(cue.endMs)} source seconds\n${cue.text}"}.joinToString("\n\n")
            val body=TextView(this).apply{text="Estimated timing, not synchronized to speech. Replaces ${clip.captions.size} existing captions on this clip with ${proposed.size} cues. Originals and audio stay unchanged. One Undo restores previous captions.\n\n$overview";setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
            scriptCaptionConfirmation?.dismiss()
            lateinit var confirmation:android.app.AlertDialog
            confirmation=android.app.AlertDialog.Builder(this).setTitle("Review ${proposed.size} captions")
                .setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
                .setNegativeButton("Back",null).setPositiveButton("Apply and preview"){_,_->
                    runCatching{commitScriptCaptionReview(editor,confirmation,review,current,index,clip,proposed)}.onSuccess{applied->
                        if(!applied){confirmation.dismiss();return@onSuccess}
                        editor.dismiss();val refreshed=runCatching{showClip();previewProject()}.isSuccess
                        status.text=if(refreshed)"Script captions applied. Review timing in Captions; Undo restores previous captions."else"Script captions applied. Preview is unavailable; reopen it to check timing. Undo restores previous captions."
                    }.onFailure{script.error="Caption limits reached. Existing edits are unchanged."}
                }.create()
            scriptCaptionConfirmation=confirmation
            showCaptionReview(confirmation,review){body.text="";if(scriptCaptionConfirmation===confirmation)scriptCaptionConfirmation=null}
        }}
        scriptCaptionConfirmation?.dismiss();scriptCaptionConfirmation=null
        scriptCaptionEditor?.dismiss();scriptCaptionEditor=editor
        showCaptionReview(editor,review){
            script.text?.clear()
            if(scriptCaptionEditor===editor){scriptCaptionEditor=null;val confirmation=scriptCaptionConfirmation;scriptCaptionConfirmation=null;confirmation?.dismiss()}
        }
    }

    private var rollCutEditor:android.app.AlertDialog?=null
    private var rollCutConfirmation:android.app.AlertDialog?=null
    private fun commitRollCutReview(editor:android.app.AlertDialog,confirmation:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,delta:Long):Boolean {
        if(rollCutEditor!==editor||rollCutConfirmation!==confirmation)return false
        rollCutConfirmation=null // Consume only this approval, including stale/failed attempts.
        if(!activeCaptionReviewDialog(editor,review)||!activeCaptionReviewDialog(confirmation,review)||project!==current||selectedClip!=index)return false
        current.rollCut(index,delta);return true
    }
    private fun rollCut(){
        if(!canRequestTimelineCaptionImport())return
        video.pause();previewPlayer?.pause();updateWorkspace()
        val review=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip
        if(index>=clips.lastIndex){status.text="Select a clip with a following neighbor to roll their cut.";return}
        val durations=sourceDurations()
        val limits=runCatching{VideoRollCut.bounds(clips,index,durations)}.getOrElse{status.text="Source metadata changed. Reopen this video.";return}
        if(limits.first==0L&&limits.second==0L){status.text="Trim a boundary first to leave original source footage for rolling this cut.";return}
        val initial=if(limits.second>0)minOf(500L,limits.second)else maxOf(-500L,limits.first)
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Move the cut between clips ${index+1} and ${index+2} without changing their combined playback length or later clip positions. Positive offsets extend the first clip and shorten the next; negative offsets do the reverse. Each source needs enough original footage at the boundary. Available offset: ${String.format(java.util.Locale.ROOT,"%+.3f",limits.first/1000.0)} to ${String.format(java.util.Locale.ROOT,"%+.3f",limits.second/1000.0)} playback seconds.";setTextColor(Color.DKGRAY)})
        val offset=EditText(this).apply{hint="Signed cut offset, playback seconds";contentDescription="Signed cut offset, playback seconds";setSingleLine(true);setTextColor(Color.BLACK);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;filters=arrayOf(android.text.InputFilter.LengthFilter(16));setText(String.format(java.util.Locale.ROOT,"%.3f",initial/1000.0));body.addView(this)}
        body.addView(TextView(this).apply{text="Use 0.002-second steps, for example 0.500 or -0.250. Each side follows its own speed; different speeds may leave a gap or overlap in same-source footage. Clip settings and source-anchored captions/motion remain, so visible cues may change. Video/audio move together. Preview after applying; codec output is not guaranteed frame-exact.";setTextColor(Color.DKGRAY)})
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Roll cut").setView(android.widget.ScrollView(this).apply{addView(body)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(rollCutEditor!==dialog||!activeCaptionReviewDialog(dialog,review)){status.text="Project or session changed. Reopen Roll cut.";dialog.dismiss();return@setOnClickListener}
            runCatching{val delta=VideoRollCut.offset(offset.text.toString());delta to VideoRollCut.propose(clips,index,delta,durations)}.onSuccess{(delta,proposal)->
                val left=proposal[index];val right=proposal[index+1]
                rollCutConfirmation?.dismiss()
                lateinit var confirmation:android.app.AlertDialog
                confirmation=android.app.AlertDialog.Builder(this).setTitle("Move this cut?")
                    .setMessage("Clip ${index+1}: ${VideoTrimRange.format(left.startMs)}–${VideoTrimRange.format(left.endMs)} source seconds.\nClip ${index+2}: ${VideoTrimRange.format(right.startMs)}–${VideoTrimRange.format(right.endMs)} source seconds.\n\nTheir combined playback length, later clips and project soundtrack stay in place. Existing caption/motion source timestamps stay anchored. Undo restores both ranges.")
                    .setNegativeButton("Keep editing",null).setPositiveButton("Apply roll cut"){_,_->
                        runCatching{commitRollCutReview(dialog,confirmation,review,current,index,delta)}.onSuccess appliedResult@{applied->
                            if(!applied){confirmation.dismiss();return@appliedResult}
                            dialog.dismiss();val refreshed=runCatching{showClip()}.isSuccess
                            status.text=if(refreshed)"Cut moved with combined playback length kept. Preview project; Undo restores both ranges."else"Cut moved. Preview could not refresh; reopen it to check the cut. Undo restores both ranges."
                        }.onFailure{offset.error="This cut could not be saved. Existing edits were kept."}
                    }.create()
                rollCutConfirmation=confirmation
                showCaptionReview(confirmation,review){if(rollCutConfirmation===confirmation)rollCutConfirmation=null}
            }.onFailure{offset.error=it.message?:"Choose an offset with available source footage"}
        }}
        rollCutConfirmation?.dismiss();rollCutConfirmation=null
        rollCutEditor?.dismiss();rollCutEditor=dialog
        showCaptionReview(dialog,review){
            offset.text?.clear()
            if(rollCutEditor===dialog){rollCutEditor=null;val confirmation=rollCutConfirmation;rollCutConfirmation=null;confirmation?.dismiss()}
        }
    }

    private var slipWindowEditor:android.app.AlertDialog?=null
    private var slipWindowConfirmation:android.app.AlertDialog?=null
    private fun commitSlipWindowReview(editor:android.app.AlertDialog,confirmation:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,startMs:Long):Boolean {
        if(slipWindowEditor!==editor||slipWindowConfirmation!==confirmation)return false
        slipWindowConfirmation=null // Consume only this approval, including stale/failed attempts.
        if(!activeCaptionReviewDialog(editor,review)||!activeCaptionReviewDialog(confirmation,review)||project!==current||selectedClip!=index)return false
        current.slipSourceWindow(index,startMs);return true
    }
    private fun slipSourceWindow(){
        if(!canRequestTimelineCaptionImport())return
        video.pause();previewPlayer?.pause();updateWorkspace()
        val review=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments.toList();val index=selectedClip;val clip=clips[index]
        val duration=sourceDuration(clip.sourceId);if(sourceUris()[clip.sourceId]==null)return
        val latest=runCatching{VideoClipWindow.latestStart(clip,duration)}.getOrElse{status.text="Original source metadata changed. Reopen the video.";return}
        if(latest==0L){status.text="Trim a shorter clip first to leave alternative source footage.";return}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Choose different footage from this same original while keeping this clip's length and neighboring playback positions. Current source range ${VideoTrimRange.format(clip.startMs)}–${VideoTrimRange.format(clip.endMs)} seconds. New start: 0–${VideoTrimRange.format(latest)} source seconds.";setTextColor(Color.DKGRAY)})
        val from=EditText(this).apply{hint="New window start, source seconds";contentDescription="New window start, source seconds";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(16));setText(VideoTrimRange.format(clip.startMs));setTextColor(Color.BLACK);body.addView(this)}
        body.addView(TextView(this).apply{text="Source video and its audio move together. Grade, framing, title, volume and fades remain. Captions and motion keep their original-source timestamps, so what is visible may change. Removed cues are not regenerated. Preview after applying; this does not promise frame-exact seeking.";setTextColor(Color.DKGRAY)})
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Slip source window").setView(android.widget.ScrollView(this).apply{addView(body)}).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(slipWindowEditor!==dialog||!activeCaptionReviewDialog(dialog,review)){dialog.dismiss();return@setOnClickListener}
            runCatching{VideoClipWindow.move(clip,VideoCaptions.seconds(from.text.toString()),duration)}.onSuccess{proposal->
                slipWindowConfirmation?.dismiss()
                lateinit var confirmation:android.app.AlertDialog
                confirmation=android.app.AlertDialog.Builder(this).setTitle("Use this source window?")
                    .setMessage("${VideoTrimRange.format(proposal.startMs)}–${VideoTrimRange.format(proposal.endMs)} source seconds, same ${String.format(java.util.Locale.ROOT,"%.3f",clip.durationUs/1000000.0)} seconds of playback. Other clips and the project soundtrack stay in place. Existing captions/motion retain source timestamps. Undo restores this window.")
                    .setNegativeButton("Keep editing",null).setPositiveButton("Apply window"){_,_->
                        runCatching{commitSlipWindowReview(dialog,confirmation,review,current,index,proposal.startMs)}.onSuccess appliedResult@{applied->
                            if(!applied){confirmation.dismiss();return@appliedResult}
                            dialog.dismiss();val refreshed=runCatching{showClip()}.isSuccess
                            status.text=if(refreshed)"Source window changed with playback length kept. Preview project; Undo restores the old window."else"Source window changed. Preview could not refresh; reopen it to check footage. Undo restores the old window."
                        }.onFailure{from.error="This window could not be saved. Existing edits were kept."}
                    }.create()
                slipWindowConfirmation=confirmation
                showCaptionReview(confirmation,review){if(slipWindowConfirmation===confirmation)slipWindowConfirmation=null}
            }.onFailure{from.error=it.message?:"Choose a start inside the original source"}
        }}
        slipWindowConfirmation?.dismiss();slipWindowConfirmation=null
        slipWindowEditor?.dismiss();slipWindowEditor=dialog
        showCaptionReview(dialog,review){
            from.text?.clear()
            if(slipWindowEditor===dialog){slipWindowEditor=null;val confirmation=slipWindowConfirmation;slipWindowConfirmation=null;confirmation?.dismiss()}
        }
    }

    private var preciseTrimEditor:android.app.AlertDialog?=null
    private fun commitPreciseTrimReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,clip:VideoSegment,range:VideoTrimRange):Boolean {
        if(preciseTrimEditor!==dialog)return false
        preciseTrimEditor=null // Consume only this approval, including stale/failed attempts.
        if(!activeCaptionReviewDialog(dialog,review)||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip)return false
        val checked=VideoTrimRange.parse(VideoTrimRange.format(range.startMs),VideoTrimRange.format(range.endMs),clip,sourceDuration(clip.sourceId))
        if(checked.startMs==clip.startMs&&checked.endMs==clip.endMs)return false
        current.trim(index,checked.startMs,checked.endMs);return true
    }
    private fun preciseTrim() {
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val originalDuration=sourceDuration(clip.sourceId)
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(this).apply{text="Original-source seconds, up to 3 decimal places. This trims inside the current clip and is undoable. Codec output is not guaranteed frame-exact.";textSize=12f;setTextColor(Color.DKGRAY)})
        fun field(name:String,value:Long)=EditText(this).apply{hint=name;contentDescription=name;inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(VideoTrimRange.format(value));setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(16));panel.addView(this)}
        val from=field("Trim start in source seconds",clip.startMs);val to=field("Trim end in source seconds",clip.endMs)
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Precise trim").setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(preciseTrimEditor!==dialog||!activeCaptionReviewDialog(dialog,review)){status.text="Clip or session changed. Open Precise trim again.";dialog.dismiss();return@setOnClickListener}
            val range=runCatching{VideoTrimRange.parse(from.text.toString(),to.text.toString(),clip,originalDuration)}.getOrElse{to.error=it.message;return@setOnClickListener}
            if(range.startMs==clip.startMs&&range.endMs==clip.endMs){dialog.dismiss();status.text="This clip already uses that range. Current edits are unchanged.";return@setOnClickListener}
            runCatching{commitPreciseTrimReview(dialog,review,current,index,clip,range)}.onSuccess{applied->
                if(!applied){dialog.dismiss();return@onSuccess}
                dialog.dismiss();val refreshed=runCatching{showClip()}.isSuccess
                status.text=if(refreshed)"Trim applied: ${VideoTrimRange.format(range.startMs)}–${VideoTrimRange.format(range.endMs)} source seconds. Undo restores the clip."else"Trim applied. Preview could not refresh; reopen it to check the range. Undo restores the clip."
            }.onFailure{to.error="Trim could not be applied; existing edits are unchanged."}
        }}
        preciseTrimEditor?.dismiss();preciseTrimEditor=dialog
        showCaptionReview(dialog,review){from.text?.clear();to.text?.clear();if(preciseTrimEditor===dialog)preciseTrimEditor=null}
    }
    private fun allowOnDeviceAi():Boolean {
        if(!canRequestTimelineCaptionImport())return false
        if(accessMode.allowsOnDeviceAi)return true
        status.text="On-device editing tools are unavailable in this editor session."
        return false
    }
    private fun outputFormat(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val dialog=VideoInspectorControls.createFormat(this,current.segments.first().layout,{canUseInspectorReview(it,review)}) applyFormat@{owner,ratio,crop->
            if(current.segments.all{it.layout.ratio==ratio&&it.layout.crop==crop}){inspectorReviewDialog=null;status.text="Output format is unchanged.";return@applyFormat}
            runCatching{commitInspectorReview(owner,review,current,index,clip){current.setFormat(ratio,crop)}}.onSuccess{applied->
                if(!applied)return@onSuccess
                val refreshed=runCatching{showClip();previewProject()}.isSuccess
                status.text=if(refreshed)"Output format applied. Undo restores the previous format."else"Output format applied. Preview is unavailable; Undo restores the previous format."
            }.onFailure{status.text="Output format could not be applied. Existing edits were kept."}
        }
        showInspectorReview(dialog,review)
    }
    private fun commitTransformReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,clip:VideoSegment,choice:Int):Boolean {
        var changed=false
        val applied=commitInspectorReview(dialog,review,current,index,clip){
            require(choice in 0..3){"Choose an available transform."}
            val l=clip.layout
            val next=when(choice){0->l.copy(rotation=(l.rotation+90)%360);1->l.copy(mirrorX=!l.mirrorX);2->l.copy(mirrorY=!l.mirrorY);else->l.copy(rotation=0,mirrorX=false,mirrorY=false)}
            if(next!=l){current.setLayout(index,next);changed=true}
        }
        return applied&&changed
    }
    private fun transformVideo(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Video transform").setItems(arrayOf("Rotate 90° clockwise","Flip horizontally","Flip vertically","Reset transform")){_,choice->
            if(!canUseInspectorReview(dialog,review))return@setItems
            runCatching{commitTransformReview(dialog,review,current,index,clip,choice)}.onSuccess{changed->
                if(!changed){status.text="Video transform is unchanged.";return@onSuccess}
                val refreshed=runCatching{showClip();previewComposition(listOf(current.segments[index]),index)}.isSuccess
                status.text=if(refreshed)"Video transform applied. Undo restores the previous transform."else"Video transform applied. Preview is unavailable; Undo restores the previous transform."
            }.onFailure{status.text="Transform could not be applied. Existing edits were kept."}
        }.setNegativeButton("Cancel",null).create()
        showInspectorReview(dialog,review)
    }
    private fun canUseSavedTitleReview(shelf:VideoTitleStyleShelf,review:VideoCaptionReviewState)=
        titleStyleShelf===shelf&&canApplyCaptionReview(review)
    private fun commitSavedTitleReview(shelf:VideoTitleStyleShelf,review:VideoCaptionReviewState,style:NamedVideoTitleStyle,all:Boolean):Boolean {
        if(titleStyleShelf!==shelf)return false
        titleStyleShelf=null // Consume this shelf before validation; Undo cannot revive it.
        check(canApplyCaptionReview(review)){"Project or session changed. Open saved title styles again."}
        val current=requireNotNull(project);val clips=current.segments;val index=selectedClip
        val proposed=clips.mapIndexed{i,value->if(all||i==index)style.applyTo(value)else value}
        if(proposed==clips)return false
        current.applyDraft(proposed,soundtrack=current.soundtrack)
        return true
    }
    private fun savedTitleStyles(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        titleStyleShelf?.close()
        video.pause();previewPlayer?.pause()
        lateinit var shelf:VideoTitleStyleShelf
        shelf=VideoTitleStyleShelf(this,review.mode,clip.titleStyle,
            isCurrent={canUseSavedTitleReview(shelf,review)},
            busy={value->if(titleStyleShelf===shelf)draftBusy=value},
            message={text->if(titleStyleShelf===shelf)status.text=text},
            apply=applyStyle@{style,all->
                if(titleStyleShelf!==shelf)return@applyStyle
                runCatching{commitSavedTitleReview(shelf,review,style,all)}
                    .onSuccess{applied->
                        if(!applied){status.text="The selected title appearance already matches. No project edit was added.";return@onSuccess}
                        val refreshed=runCatching{showClip();previewComposition(listOf(current.segments[index]),index)}.isSuccess
                        status.text=if(refreshed)"Saved title appearance applied. Project Undo restores the previous style." else "Title appearance applied; preview unavailable. Project Undo is available."
                    }.onFailure{status.text=it.message?:"Title appearance could not be applied. Open saved title styles again."}
            })
        titleStyleShelf=shelf;shelf.open()
    }

    private var inspectorReviewDialog:android.app.AlertDialog?=null
    private fun canUseInspectorReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState)=inspectorReviewDialog===dialog&&activeCaptionReviewDialog(dialog,review)
    private fun showInspectorReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState){
        inspectorReviewDialog?.dismiss();inspectorReviewDialog=dialog
        showCaptionReview(dialog,review){if(inspectorReviewDialog===dialog)inspectorReviewDialog=null}
    }
    private fun commitInspectorReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,clip:VideoSegment,commit:()->Unit):Boolean {
        if(inspectorReviewDialog!==dialog)return false
        if(!canUseInspectorReview(dialog,review)||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip){inspectorReviewDialog=null;return false}
        commit();inspectorReviewDialog=null;return true
    }
    private fun titleStyle(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val dialog=VideoInspectorControls.createTitle(this,clip.titleStyle,{canUseInspectorReview(it,review)}) applyStyle@{owner,style->
            if(style==clip.titleStyle){inspectorReviewDialog=null;status.text="Title style is unchanged.";return@applyStyle}
            runCatching{commitInspectorReview(owner,review,current,index,clip){current.setTitleStyle(index,style)}}.onSuccess{applied->
                if(!applied)return@onSuccess
                val refreshed=runCatching{showClip();previewComposition(listOf(current.segments[index]),index)}.isSuccess
                status.text=if(refreshed)"Title style applied. Undo restores the previous style."else"Title style applied. Preview is unavailable; Undo restores the previous style."
            }.onFailure{status.text="Title style could not be applied. Existing edits were kept."}
        }
        showInspectorReview(dialog,review)
    }
    private var colorAllConfirmation:android.app.AlertDialog?=null
    private fun commitColorAllReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int):Boolean {
        if(colorAllConfirmation!==dialog)return false
        colorAllConfirmation=null // Consume only this approval, including stale/failed attempts.
        if(!activeCaptionReviewDialog(dialog,review)||project!==current||selectedClip!=index)return false
        val clip=current.segments.getOrNull(index)?:return false
        if(current.segments.all{it.grade==clip.grade})return false
        current.applyGradeToAll(index);return true
    }
    private fun copyGradeToAll(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        if(current.segments.all{it.grade==clip.grade}){status.text="All clips already use this color. Current edits are unchanged.";return}
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Apply color to all clips?")
            .setMessage("Copies clip ${index+1}'s exposure, contrast, saturation and white balance to ${current.segments.size} clips as one undoable edit. Clip ranges, captions, motion, titles, framing, audio and original files stay unchanged.")
            .setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->
                runCatching{commitColorAllReview(dialog,review,current,index)}.onSuccess{applied->
                    if(!applied)return@onSuccess
                    val refreshed=runCatching{showClip();previewProject()}.isSuccess
                    status.text=if(refreshed)"Color applied to all clips. Undo restores their previous colors."else"Color applied to all clips. Preview is unavailable; Undo restores their previous colors."
                }.onFailure{status.text="Color could not be applied. Existing edits were kept."}
            }.create()
        colorAllConfirmation?.dismiss();colorAllConfirmation=dialog
        showCaptionReview(dialog,review){if(colorAllConfirmation===dialog)colorAllConfirmation=null}
    }

    private fun editColor(balanceOnly:Boolean=false) {
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val dialog=VideoGradeControls.create(this,clip.grade,balanceOnly,{canUseInspectorReview(it,review)}) applyGrade@{owner,grade->
            if(grade==clip.grade){inspectorReviewDialog=null;status.text="Clip color is unchanged.";return@applyGrade}
            runCatching{commitInspectorReview(owner,review,current,index,clip){current.setGrade(index,grade)}}.onSuccess{applied->
                if(!applied)return@onSuccess
                val refreshed=runCatching{showClip();previewComposition(listOf(current.segments[index]),index)}.isSuccess
                status.text=if(refreshed)"Clip color applied. Undo restores the previous color."else"Clip color applied. Preview is unavailable; Undo restores the previous color."
            }.onFailure{status.text="Clip color could not be applied. Existing edits were kept."}
        }
        showInspectorReview(dialog,review)
    }

    private fun canReviewAiLook(review:VideoCaptionReviewState,current:VideoEditProject,selection:VideoSceneSelection)=
        project===current&&canApplyCaptionReview(review)&&selectedClip==selection.selectedIndex&&current.segments==selection.clips
    private fun commitAiLookReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,selection:VideoSceneSelection,grade:VideoGrade):Boolean {
        if(inspectorReviewDialog!==dialog)return false
        if(!canReviewAiLook(review,current,selection)){inspectorReviewDialog=null;return false}
        val clip=selection.clips.getOrNull(selection.selectedIndex)?:return false
        return commitInspectorReview(dialog,review,current,selection.selectedIndex,clip){current.setGrade(selection.selectedIndex,grade)}
    }

    private fun videoLookOptions() {
        if(!allowOnDeviceAi())return
        if(saving||analyzing||draftBusy||importing||pendingRange!=null||!sourceReady)return
        val review=captureCaptionReview()?:return
        val names=arrayOf("Auto scene look","Cinematic","Soft portrait","Vivid","Monochrome")
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("AI video look").setItems(names){_,which->
            if(!activeCaptionReviewDialog(dialog,review))return@setItems
            suggestVideoLook(PhotoLook.values()[which])
        }.setNegativeButton("Cancel",null).create()
        showCaptionReview(dialog,review)
    }

    private fun suggestVideoLook(look:PhotoLook) {
        if(!allowOnDeviceAi())return
        if(saving||analyzing||draftBusy||importing||pendingRange!=null||!sourceReady)return
        val review=captureCaptionReview()?:return
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
                if(!canReviewAiLook(review,current,selection))return@runOnUiThread
                if(analysisCancellation.get()){status.text="Analysis cancelled. Your color is unchanged.";return@runOnUiThread}
                result.onSuccess{grade->
                    lateinit var dialog:android.app.AlertDialog
                    dialog=android.app.AlertDialog.Builder(this).setTitle("Video look ready")
                        .setMessage("Based on $samplesRead sampled frames. Replaces this entire clip's color with exposure ${grade.exposure} EV, contrast ${(grade.contrast*100).toInt()}% and saturation ${(grade.saturation*100).toInt()}%. Preview before exporting.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Apply look"){_,_->
                            val changed=current.segments.getOrNull(selection.selectedIndex)?.grade!=grade
                            runCatching{commitAiLookReview(dialog,review,current,selection,grade)}.onSuccess{applied->
                                if(!applied)return@onSuccess
                                val ready=runCatching{showClip();previewComposition(listOf(current.segments[selectedClip]),selectedClip)}.isSuccess
                                status.text=if(!changed)"Video color is unchanged."else if(ready)"AI video look applied. Preview before exporting; Undo restores your color."else"AI video look applied, but preview is unavailable. Undo restores your color."
                            }.onFailure{if(canUseInspectorReview(dialog,review))status.text="Could not apply this look. Try analyzing video color again."}
                        }.create()
                    showInspectorReview(dialog,review)
                }.onFailure{status.text="Video look unavailable or timed out. Your color is unchanged."}
            }
        }.start()
    }

    private fun canReportRoughCut(review:VideoCaptionReviewState,current:VideoEditProject)=
        workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&accessMode==review.mode&&captionReviewGeneration==review.generation
    private fun canReviewRoughCut(review:VideoCaptionReviewState,current:VideoEditProject,selection:VideoSceneSelection,track:VideoSoundtrack?)=
        canReportRoughCut(review,current)&&canApplyCaptionReview(review)&&current.soundtrack==track&&selectedClip==selection.selectedIndex&&current.segments==selection.clips
    private fun commitRoughCutReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,selection:VideoSceneSelection,track:VideoSoundtrack?,clips:List<VideoSegment>):Boolean {
        if(inspectorReviewDialog!==dialog)return false
        if(!canReviewRoughCut(review,current,selection,track)){inspectorReviewDialog=null;return false}
        val clip=selection.clips.getOrNull(selection.selectedIndex)?:return false
        return commitInspectorReview(dialog,review,current,selection.selectedIndex,clip){current.applyDraft(clips,soundtrack=track)}
    }

    private fun roughCutOptions() {
        if(!allowOnDeviceAi())return
        if(isDestroyed||isFinishing||analyzing||saving||draftBusy||importing||pendingRange!=null)return
        val review=captureCaptionReview()?:return
        val clips=project?.segments?:return
        if(clips.size>VideoRoughCut.MAX_CLIPS){status.text="AI rough cut supports up to 8 timeline clips. Keep a smaller selection first.";return}
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        content.addView(TextView(this).apply{text="Create a shorter draft from the timeline. Local scene labels choose moments from four sampled frames per clip. This does not analyze speech or musical beats.";setTextColor(Color.DKGRAY)})
        val length=Spinner(this).apply{adapter=ArrayAdapter(this@HonorableVideoEditorActivity,android.R.layout.simple_spinner_dropdown_item,listOf("15 seconds","30 seconds","60 seconds"));setSelection(1);contentDescription="Rough cut duration"}
        val focus=Spinner(this).apply{adapter=ArrayAdapter(this@HonorableVideoEditorActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Balanced","People","Scenery"));contentDescription="Rough cut focus"}
        content.addView(length,LinearLayout.LayoutParams(-1,dp(48)));content.addView(focus,LinearLayout.LayoutParams(-1,dp(48)))
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("AI-assisted rough cut").setView(content).setNegativeButton("Cancel",null)
            .setPositiveButton("Create draft"){_,_->
                if(!activeCaptionReviewDialog(dialog,review))return@setPositiveButton
                createRoughCut(listOf(15_000L,30_000L,60_000L)[length.selectedItemPosition],RoughCutFocus.values()[focus.selectedItemPosition])
            }.create()
        showCaptionReview(dialog,review)
    }

    private fun createRoughCut(target:Long,focus:RoughCutFocus) {
        if(!allowOnDeviceAi())return
        if(isDestroyed||isFinishing||analyzing||saving||draftBusy||importing||pendingRange!=null||!sourceReady)return
        val review=captureCaptionReview()?:return
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
                            runOnUiThread{if(analyzing&&canReportRoughCut(review,current))status.text="Analyzed clip ${index+1}/${effective.size}"}
                            bestTime
                        }finally{retriever.release()}
                    }
                    check(!analysisCancellation.get() && System.nanoTime()<deadline)
                    VideoRoughCut.assemble(effective,centers,target)
                }finally{labeler.close()}
            }
            runOnUiThread {
                analyzing=false
                if(!canReviewRoughCut(review,current,selection,track))return@runOnUiThread
                if(analysisCancellation.get()){status.text="Analysis cancelled. Your project is unchanged.";return@runOnUiThread}
                result.onSuccess{clips->
                    lateinit var dialog:android.app.AlertDialog
                    dialog=android.app.AlertDialog.Builder(this).setTitle("Rough cut ready")
                        .setMessage("${clips.size} clips, ${clips.sumOf{it.durationMs}/1000f} seconds. Replaces the timeline as one undoable edit. Sampled labels may miss important action; preview before exporting.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Apply draft"){_,_->
                            val changed=current.segments!=clips
                            runCatching{commitRoughCutReview(dialog,review,current,selection,track,clips)}.onSuccess{applied->
                                if(!applied)return@onSuccess
                                selectedClip=0
                                val ready=runCatching{showClip()}.isSuccess
                                status.text=if(!changed)"The rough cut matches your timeline. No new edit was made."else if(ready)"AI-assisted rough cut applied. Preview project; Undo restores your timeline."else"AI-assisted rough cut applied, but preview is unavailable. Undo restores your timeline."
                            }.onFailure{if(canUseInspectorReview(dialog,review))status.text="Could not apply this draft. Your project is unchanged."}
                        }.create()
                    showInspectorReview(dialog,review)
                }.onFailure{status.text="Rough cut unavailable or timed out. No project edits were changed."}
            }
        }.start()
    }

    private fun saveAudioCopy()=beginMediaCopy(VideoSaveRequest.Kind.AUDIO)

    private fun videoFinishGuide(){
        if(!canRequestTimelineCaptionImport()||durationMs<=0||!::start.isInitialized||!::end.isInitialized)return
        runCatching{if(::video.isInitialized)video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        val review=captureCaptionReview()?:return
        val clips=project?.segments?:return;val clip=clips.getOrNull(selectedClip)?:return
        val fromProgress=start.progress;val toProgress=end.progress
        val info=runCatching{VideoFinishGuide.describe(clips,selectedClip,maxOf(fromProgress*1000L,clip.startMs),minOf(toProgress*1000L,clip.endMs),accessMode)}
            .getOrElse{status.text=it.message?:"Choose a valid range before keeping this video.";return}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12));setBackgroundColor(Color.WHITE)}
        body.addView(TextView(this).apply{text=info.summary;setTextColor(Color.BLACK);setPadding(0,0,0,dp(12));contentDescription="Video finishing choices"})
        lateinit var dialog:android.app.AlertDialog
        val route=VideoFinishGuide.Route(info.choices.map{it.action}){activeCaptionReviewDialog(dialog,review)&&start.progress==fromProgress&&end.progress==toProgress}
        for(choice in info.choices){
            body.addView(TextView(this).apply{text=choice.title;setTextColor(Color.BLACK);textSize=16f;setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(0,dp(12),0,dp(4))})
            body.addView(TextView(this).apply{text=choice.detail;setTextColor(Color.DKGRAY)})
            body.addView(Button(this).apply{text=choice.button;contentDescription=choice.button;setTextColor(Color.BLACK);setAllCaps(false);minimumHeight=dp(48);setOnClickListener{
                runCatching{route.choose(choice.action,{dialog.dismiss()}){action->when(action){
                    VideoFinishGuide.Action.VIDEO_COPY->saveCopy()
                    VideoFinishGuide.Action.NAMED_VERSION->savedVideoVersions()
                    VideoFinishGuide.Action.QUICK_SAVE->if(accessMode==EditorAccessMode.GUEST_BASIC)guestProjectOperation(false)else draftOperation(false)
                }}}.onFailure{if(workspaceActive)status.text="This option could not be opened. Close the guide and try again."}
            }})
        }
        dialog=android.app.AlertDialog.Builder(this).setTitle("Keep or export this video").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Keep editing"){_,_->route.close()}.create()
        dialog.setOnCancelListener{route.close()};showCaptionReview(dialog,review)
    }

    private fun restoreVideoSaveState(state:Bundle?){
        nextVideoSaveCode=(state?.getInt("nextVideoSaveCode",VideoSaveRequest.FIRST_CODE)?:VideoSaveRequest.FIRST_CODE).coerceIn(VideoSaveRequest.FIRST_CODE,VideoSaveRequest.LAST_CODE+1)
        interruptedVideoSave=state?.getBoolean("videoSaveHadDestination",false)==true
        interruptedVideoSaveCode=if(interruptedVideoSave)null else state?.getInt("videoSaveRequestCode",-1)?.takeIf{VideoSaveRequest.ownsCode(it)}
    }
    private fun writeVideoSaveState(state:Bundle){
        state.putInt("nextVideoSaveCode",nextVideoSaveCode)
        state.putInt("videoSaveRequestCode",videoSaveRequest?.requestCode?:-1)
        state.putBoolean("videoSaveHadDestination",videoSaveRequest?.let{it.isReady||it.isWriting}==true)
    }
    private fun allocateVideoSaveCode():Int {val code=nextVideoSaveCode;require(VideoSaveRequest.ownsCode(code)){"Reopen this editor before exporting again."};nextVideoSaveCode=code+1;return code}
    private fun syncVideoSaveControls(){
        if(!restoreVideoSaveControls||!workspaceActive||isDestroyed||isFinishing||!editorIdle())return
        if(::start.isInitialized)start.isEnabled=true;if(::end.isInitialized)end.isEnabled=true;restoreVideoSaveControls=false
    }
    private fun releaseVideoSave(request:VideoSaveRequest):Boolean {
        val writing=request.isWriting;request.close()
        if(videoSaveRequest!==request)return false
        videoSaveRequest=null;pendingRange=null
        if(writing){saving=false;when(request.kind){VideoSaveRequest.Kind.AUDIO->audioExporter=null;VideoSaveRequest.Kind.VIDEO->exporter=null;VideoSaveRequest.Kind.SUBTITLES->Unit}}
        restoreVideoSaveControls=true;syncVideoSaveControls();return true
    }
    private fun flushVideoSaveNotice(){
        if(!workspaceActive||!editorIdle()||!::status.isInitialized||isDestroyed||isFinishing||captionReviewDialogs?.isNotEmpty()==true)return
        if(interruptedVideoSave){interruptedVideoSave=false;status.text="Export was interrupted. Export again; check Files for an empty or partial copy."}
        val notice=videoSaveNotice?:return;videoSaveNotice=null
        if(accessMode==notice.first.mode&&videoLookNamespace()==notice.first.namespace)status.text=notice.second
    }
    private fun notifyVideoSave(request:VideoSaveRequest,message:String){videoSaveNotice=request to message;flushVideoSaveNotice()}
    private fun canDispatchVideoSave(request:VideoSaveRequest):Boolean {
        if(videoSaveRequest!==request||!request.isReady||!workspaceActive||isDestroyed||isFinishing||saving||analyzing||draftBusy||importing||pendingRange==null||recoveryBytes!=null||recoveryBlocked||recoveryChecking||!::source.isInitialized)return false
        return request.matches(closeState(),accessMode,videoLookNamespace(),sourceUris(),sourceDurations(),importedSources)
    }
    private fun acceptVideoSaveResult(code:Int,resultCode:Int,data:Intent?){
        val request=videoSaveRequest
        if(request==null){if(code==interruptedVideoSaveCode){interruptedVideoSaveCode=null;if(resultCode==RESULT_OK){interruptedVideoSave=true;flushVideoSaveNotice()}};return}
        if(!request.accept(code,resultCode==RESULT_OK,data?.data))return
        if(request.isClosed){if(releaseVideoSave(request))notifyVideoSave(request,"Video save cancelled or destination unavailable. Current edits are unchanged.");return}
        resumeVideoSave()
    }
    private fun resumeVideoSave(){
        val request=videoSaveRequest?:return
        if(!request.isReady||!workspaceActive)return
        if(!canDispatchVideoSave(request)){if(releaseVideoSave(request))notifyVideoSave(request,"Media or session changed. Export again; check Files for an empty destination.");return}
        val destination=request.beginWriting()?:return
        val audio=request.kind==VideoSaveRequest.Kind.AUDIO
        saving=true;videoSaveNotice=null
        if(::start.isInitialized)start.isEnabled=false;if(::end.isInitialized)end.isEnabled=false
        if(::status.isInitialized)status.text=when(request.kind){VideoSaveRequest.Kind.AUDIO->"Saving audio copy…";VideoSaveRequest.Kind.VIDEO->"Saving video copy…";VideoSaveRequest.Kind.SUBTITLES->"Saving subtitle copy…"}
        fun complete(result:Result<Unit>){
            if(videoSaveRequest!==request)return
            if(releaseVideoSave(request))notifyVideoSave(request,result.fold(
                {when(request.kind){VideoSaveRequest.Kind.AUDIO->"Audio copy saved. Originals preserved. Check the sound.";VideoSaveRequest.Kind.VIDEO->"Copy saved. Original preserved. Check playback and audio.";VideoSaveRequest.Kind.SUBTITLES->"Subtitle copy saved. Timings match the edited project."}},
                {"${if(audio)"Audio export"else"Export"} failed: ${it.message?:"Unknown error"}. Check Files for a partial copy."}))
        }
        runCatching{when(request.kind){
            VideoSaveRequest.Kind.VIDEO->{val writer=VideoCompositionExporter(this);exporter=writer;writer.export(request.sources,destination,request.clips,request.durations,request.music,::complete)}
            VideoSaveRequest.Kind.AUDIO->{val writer=VideoAudioExporter(this);audioExporter=writer;writer.export(request.sources,destination,request.clips,request.durations,request.music,::complete)}
            VideoSaveRequest.Kind.SUBTITLES->draftWorker.execute{
                val copied=runCatching{VideoSubtitleDocuments.writeCopy(this,destination,request.subtitleBytes(),request.sources.values.toSet()){
                    check(request.isWriting&&!Thread.currentThread().isInterrupted){"Subtitle export cancelled"}
                }}
                runOnUiThread{complete(copied)}
            }
        }}.onFailure{complete(Result.failure(it))}
    }
    private fun saveCopy()=beginMediaCopy(VideoSaveRequest.Kind.VIDEO)
    private fun beginMediaCopy(kind:VideoSaveRequest.Kind) {
        if(!canRequestTimelineCaptionImport()||durationMs<=0||!::start.isInitialized||!::end.isInitialized)return
        val current=project?:return;val clip=current.segments.getOrNull(selectedClip)?:return
        val from=maxOf(start.progress*1000L,clip.startMs);val to=minOf(end.progress*1000L,clip.endMs)
        if(to<=from){status.text="End must be after start";return}
        val clips=runCatching{VideoExportSnapshot.clips(current.segments,selectedClip,from,to)}.getOrElse{status.text=it.message?:"Choose a valid video range";return}
        val namespace=videoLookNamespace()?:return
        if(kind!=VideoSaveRequest.Kind.VIDEO)runCatching{if(::video.isInitialized)video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        val code=runCatching{allocateVideoSaveCode()}.getOrElse{status.text=it.message;return}
        pendingRange=from to to
        val request=runCatching{if(kind==VideoSaveRequest.Kind.SUBTITLES)VideoSaveRequest(code,closeState(),accessMode,namespace,clips,current.soundtrack,sourceUris(),sourceDurations(),importedSources,VideoCaptions.exportSrt(clips))else VideoSaveRequest(code,closeState(),accessMode,namespace,clips,current.soundtrack,sourceUris(),sourceDurations(),importedSources,kind)}.getOrElse{pendingRange=null;status.text=it.message?:"Export could not be prepared. Current edits were kept.";return}
        videoSaveRequest=request;videoSaveNotice=null;interruptedVideoSave=false;interruptedVideoSaveCode=null
        start.isEnabled=false;end.isEnabled=false
        try{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type=kind.mime;putExtra(Intent.EXTRA_TITLE,kind.filename)},code)}
        catch(_:Exception){if(releaseVideoSave(request))notifyVideoSave(request,"No app is available to save a copy. Current edits were kept.")}
    }

    @Deprecated("Uses the system document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(videoSaveRequest!=null&&!allowsMediaImportResult(requestCode))return
        if(VideoSaveRequest.ownsCode(requestCode)){acceptVideoSaveResult(requestCode,resultCode,data);return}
        // Older picker results must not release busy/recovery state owned by this import.
        if((timelineCaptionImport!=null||captionSyncControls!=null||speechControls!=null||visualTextControls!=null||translationControls!=null)&&!allowsMediaImportResult(requestCode))return
        if(requestCode==VideoTimelineCaptionControls.REQUEST_CODE||requestCode==VideoTimelineCaptionControls.SOURCE_REQUEST_CODE){timelineCaptionImport?.takeIf{it.requestCode==requestCode}?.accept(resultCode,data);return}
        if(holdRecoveryResult(requestCode)||!allowsMediaImportResult(requestCode))return
        if(requestCode == 28) { finishVoiceover(resultCode,data); return }
        if(requestCode == 27)return // Unbound legacy audio result cannot release another operation.
        if(requestCode == 26)return // Unbound legacy subtitle result cannot release another operation.
        if(requestCode == 24) { finishSoundtrackImport(resultCode,data); return }
        if(requestCode == 23) { finishImport(resultCode, data); return }
        // Legacy code22 has no immutable request after recreation; never start an unbound export.
        if(requestCode==22)return
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
        return VoiceoverProvider.ownsReadable(this,uri)||PhotoVideoProjectStore(this).ownsReadableSource(uri)||VideoProjectBackupStore(this).owned(uri)||contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission}
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
    private var clipTitleEditor:android.app.AlertDialog?=null
    private fun commitClipTitleReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,clip:VideoSegment,text:String):Boolean {
        if(clipTitleEditor!==dialog)return false
        if(!activeCaptionReviewDialog(dialog,review)||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip){clipTitleEditor=null;return false}
        val title=text.trim()
        if(title==clip.title){clipTitleEditor=null;return false}
        current.setTitle(index,title) // Invalid input leaves the current form owned and editable.
        clipTitleEditor=null;return true
    }
    private fun editTitle() {
        val review=captureCaptionReview()?:return
        val current = project ?: return
        val index = selectedClip; val clip = current.segments.getOrNull(index)?:return
        val input = EditText(this).apply {
            setText(clip.title); setTextColor(Color.BLACK); setBackgroundColor(Color.WHITE)
            hint = "Title (leave blank to remove)"; maxLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(120))
        }
        lateinit var dialog:android.app.AlertDialog
        dialog = android.app.AlertDialog.Builder(this,android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Clip title").setView(input)
            .setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(clipTitleEditor!==dialog||!activeCaptionReviewDialog(dialog,review)){dialog.dismiss();return@setOnClickListener}
            val text=input.text.toString().trim()
            if(text==clip.title){dialog.dismiss();status.text="This clip already uses that title. Current edits are unchanged.";return@setOnClickListener}
            runCatching{commitClipTitleReview(dialog,review,current,index,clip,text)}.onSuccess{applied->
                if(!applied){dialog.dismiss();return@onSuccess}
                dialog.dismiss();val refreshed=runCatching{showClip()}.isSuccess
                status.text=if(refreshed)"Title updated. Preview the project before saving a copy."else"Title updated. Preview could not refresh; reopen it to check the title. Undo restores the previous title."
            }.onFailure{input.error="Use up to 120 characters and three lines; project title text is limited."}
        } }
        clipTitleEditor?.dismiss();clipTitleEditor=dialog
        showCaptionReview(dialog,review){input.text?.clear();if(clipTitleEditor===dialog)clipTitleEditor=null}
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

    private fun captureCaptionReview():VideoCaptionReviewState? {
        if(!canRequestTimelineCaptionImport())return null
        val namespace=videoLookNamespace()?:return null
        return VideoCaptionReviewState(closeState(),accessMode,namespace,captionReviewGeneration,sourceUris(),sourceDurations())
    }
    private fun canApplyCaptionReview(review:VideoCaptionReviewState):Boolean {
        if(!canRequestTimelineCaptionImport()||captionReviewGeneration!=review.generation||accessMode!=review.mode||videoLookNamespace()!=review.namespace)return false
        return review.state.stillMatches(closeState())&&review.sources==sourceUris()&&review.durations==sourceDurations()
    }
    private fun invalidateCaptionReviews(){
        momentReviewRequest?.close();momentReviewRequest=null;momentReviewDialog=null
        lookReviewRequest?.close();lookReviewRequest=null;lookReviewDialog=null
        batchReviewRequest?.close();batchReviewRequest=null;batchReviewDialog=null
        captionReviewGeneration++
        val old=captionReviewDialogs?.toList().orEmpty();captionReviewDialogs?.clear()
        old.forEach{runCatching{it.dismiss()}}
    }
    private fun showCaptionReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState)=showCaptionReview(dialog,review,{})
    private fun showCaptionReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,onDismiss:()->Unit){
        if(!canApplyCaptionReview(review)){onDismiss();dialog.dismiss();return}
        val dialogs=captionReviewDialogs?:linkedSetOf<android.app.AlertDialog>().also{captionReviewDialogs=it}
        dialogs.add(dialog);dialog.setOnDismissListener{dialogs.remove(dialog);onDismiss()};dialog.show()
    }
    private fun performCaptionClipboardCopy(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,copy:()->Unit):Boolean {
        if(!activeCaptionReviewDialog(dialog,review))return false
        copy();return true
    }
    private fun commitCaptionReview(review:VideoCaptionReviewState,current:VideoEditProject,index:Int,clip:VideoSegment,cues:List<VideoCaption>):Boolean {
        if(!canApplyCaptionReview(review)||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip)return false
        current.setCaptions(index,cues);return true
    }
    private fun activeCaptionReviewDialog(dialog:android.app.AlertDialog,review:VideoCaptionReviewState)=captionReviewDialogs?.contains(dialog)==true&&dialog.isShowing&&canApplyCaptionReview(review)
    private fun commitVisibleCaptionReview(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,index:Int,clip:VideoSegment,cues:List<VideoCaption>):Boolean =
        activeCaptionReviewDialog(dialog,review)&&commitCaptionReview(review,current,index,clip,cues)
    private fun clearCaptionReview(current:VideoEditProject,index:Int,clip:VideoSegment){
        if(!canRequestTimelineCaptionImport()||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip)return
        val review=captureCaptionReview()?:return
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Clear clip captions?").setMessage("Removes ${clip.captions.size} captions from this clip. Undo restores them.")
            .setNegativeButton("Cancel",null).setPositiveButton("Clear"){_,_->if(commitVisibleCaptionReview(dialog,review,current,index,clip,emptyList())){showClip();previewProject()}}.create()
        showCaptionReview(dialog,review)
    }

    private fun captionOptions() {
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments[index]
        val choices=listOf("Add caption at playhead","Import SRT for this clip","Export project SRT","Clear this clip's captions","Review project caption readability","Find caption text in project","Copy project caption text","Import SRT for edited playback timeline","Synchronize subtitle timing","Build highlights from caption text","Text to paced captions") + clip.captions.map { "${VideoCaptions.formatTime(it.startMs)}  ${it.text.replace('\n',' ')}" }
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Timed captions · clip ${index+1}")
            .setItems(choices.toTypedArray()){_,choice->
                if(!activeCaptionReviewDialog(dialog,review)||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip)return@setItems
                when(choice){
                    0->editCaption(current,index,clip,null)
                    1->importCaptions(current,index,clip)
                    2->exportSubtitles()
                    3->clearCaptionReview(current,index,clip)
                    4->captionReadability()
                    5->findCaptionText()
                    6->copyCaptionText()
                    7->requestTimelineCaptionImport()
                    8->requestCaptionSync()
                    9->requestCaptionHighlights()
                    10->requestPacedCaptions()
                    else->editCaption(current,index,clip,choice-11)
                }
            }.setNegativeButton("Close",null).create()
        showCaptionReview(dialog,review)
    }

    private fun commitPacedCaptions(dialog:android.app.AlertDialog,review:VideoCaptionReviewState,plan:VideoPacedCaptions):Boolean {
        if(!activeCaptionReviewDialog(dialog,review)||!plan.changed)return false
        plan.apply(requireNotNull(project)){check(canApplyCaptionReview(review)){"Video or session changed. Prepare captions again."}}
        return true
    }
    private fun requestPacedCaptions(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Paste up to 8,000 characters (16 KB) of your own text. Words receive an even pace across this clip's stored trim (${VideoCaptions.formatTime(clip.startMs)} → ${VideoCaptions.formatTime(clip.endMs)} of the source), following its speed. Live trim sliders do not change this range. This does not listen to or align with speech. Review timing and edit cues afterward. Applying replaces all captions on this clip; Undo restores them.";setTextColor(Color.DKGRAY)})
        val input=EditText(this).apply{hint="Text for this clip";contentDescription="Text to turn into paced captions";setTextColor(Color.BLACK);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=3;maxLines=8;filters=arrayOf(android.text.InputFilter.LengthFilter(VideoPacedCaptions.MAX_CHARS));body.addView(this)}
        val count=EditText(this).apply{hint="Words per caption, 1–12";contentDescription=hint;inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText("6");setTextColor(Color.BLACK);body.addView(this)}
        lateinit var editor:android.app.AlertDialog
        editor=android.app.AlertDialog.Builder(this).setTitle("Text to paced captions").setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Cancel",null).setPositiveButton("Review",null).create()
        editor.setOnShowListener{editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!activeCaptionReviewDialog(editor,review)){editor.dismiss();return@setOnClickListener}
            runCatching{VideoPacedCaptions.prepare(current.segments,index,input.text.toString(),count.text.toString().toIntOrNull()?:0,current.soundtrack){check(canApplyCaptionReview(review))}}
                .onSuccess{plan->
                    val report=plan.cues.mapIndexed{i,cue->"${i+1}. ${VideoCaptions.formatTime(cue.startMs)} → ${VideoCaptions.formatTime(cue.endMs)} source\n${cue.text}"}.joinToString("\n\n")
                    val preview=TextView(this).apply{text="${plan.cues.size} proposed cues replace ${clip.captions.size} existing cues. Approximate pacing only; check speech and readability in playback. Original audio, clip appearance and source files stay unchanged.\n\n$report";setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12))}
                    lateinit var confirmation:android.app.AlertDialog
                    confirmation=android.app.AlertDialog.Builder(this).setTitle("Review paced captions")
                        .setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(preview)})
                        .setNegativeButton("Cancel",null).setPositiveButton("Replace clip captions"){_,_->
                            runCatching{commitPacedCaptions(confirmation,review,plan)}.onSuccess appliedResult@{applied->
                                if(!applied){status.text="Video or session changed, or captions already match. Prepare captions again.";return@appliedResult}
                                val refreshed=runCatching{showClip()}.isSuccess
                                status.text=if(refreshed)"Paced captions applied. Preview and adjust speech timing; Undo restores the previous captions."else"Paced captions applied. Reopen preview to check timing; Undo restores the previous captions."
                            }.onFailure{status.text="Captions could not be applied. Existing edits were kept."}
                        }.create()
                    editor.dismiss();showCaptionReview(confirmation,review){preview.text=""}
                }.onFailure{input.error=it.message?:"Check text and word groups."}
        }}
        showCaptionReview(editor,review){input.text?.clear()}
    }

    private fun activeCaptionHighlightDialog(dialog:android.app.AlertDialog,review:VideoCaptionReviewState)=activeCaptionReviewDialog(dialog,review)
    private fun commitCaptionHighlights(review:VideoCaptionReviewState,plan:VideoCaptionHighlights):Boolean {
        if(!canApplyCaptionReview(review)||!plan.changed)return false
        plan.apply(requireNotNull(project)){check(canApplyCaptionReview(review)){"Video or session changed. Select caption moments again."}}
        return true
    }
    private fun requestCaptionHighlights(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments;val clip=clips.getOrNull(selectedClip)?:return
        if(!::start.isInitialized||!::end.isInitialized)return
        if(!timelineCaptionRangeMatches(clip,start.progress,end.progress)){status.text="Apply or reset the selected trim before building caption highlights.";return}
        if(clips.none{it.captions.isNotEmpty()}){status.text="Add or import captions before choosing captioned moments.";return}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Find existing visible caption text on this device, then explicitly select moments to keep in timeline order. This replaces the whole timeline with those caption ranges as one Undo. Only cues intersecting kept moments carry into highlights; originals remain. No speech detection or upload.";setTextColor(Color.BLACK)})
        val query=EditText(this).apply{hint="Caption text to find";contentDescription="Caption text for highlights";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(160));body.addView(this)}
        val matchCase=android.widget.CheckBox(this).apply{text="Match letter case";setTextColor(Color.BLACK);body.addView(this)}
        val input=android.app.AlertDialog.Builder(this).setTitle("Highlights from caption text").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)}).setNegativeButton("Keep timeline",null).setPositiveButton("Find moments",null).create()
        input.setOnShowListener{input.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!activeCaptionHighlightDialog(input,review)){input.dismiss();return@setOnClickListener}
            runCatching{VideoCaptionSearch.find(clips,query.text.toString(),matchCase.isChecked).also{require(it.isNotEmpty()){"No visible matches. Try different caption text."};require(it.size<=VideoCaptionHighlights.MAX_SELECTION){"More than 200 matches. Refine the text before selecting moments."}}}
                .onSuccess{matches->input.dismiss();selectCaptionHighlights(review,clips,matches)}.onFailure{query.error=it.message?:"Check caption text"}
        }};showCaptionReview(input,review)
    }
    private fun selectCaptionHighlights(review:VideoCaptionReviewState,clips:List<VideoSegment>,matches:List<VideoCaptionSearch.Match>){
        if(!canApplyCaptionReview(review))return
        val chosen=BooleanArray(matches.size)
        val selection=android.app.AlertDialog.Builder(this).setTitle("Select caption moments to keep")
            .setMultiChoiceItems(matches.map{it.label()}.toTypedArray(),chosen){dialog,which,checked->if(!canApplyCaptionReview(review))dialog.dismiss()else chosen[which]=checked}
            .setNegativeButton("Keep timeline",null).setPositiveButton("Review highlights",null).create()
        selection.setOnShowListener{selection.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!activeCaptionHighlightDialog(selection,review)){selection.dismiss();return@setOnClickListener}
            runCatching{VideoCaptionHighlights.prepare(clips,matches.filterIndexed{i,_->chosen[i]}.map{VideoCaptionHighlights.Selection(it.clipIndex,it.cueIndex)},project?.soundtrack){check(canApplyCaptionReview(review)){"Video or session changed"}}}
                .onSuccess{plan->if(!plan.changed)status.text="Selected moments already match this timeline. Nothing changed."else{selection.dismiss();reviewCaptionHighlights(review,plan)}}
                .onFailure{status.text=it.message?:"Select at least one caption moment"}
        }};showCaptionReview(selection,review)
    }
    private fun reviewCaptionHighlights(review:VideoCaptionReviewState,plan:VideoCaptionHighlights){
        if(!canApplyCaptionReview(review))return
        val sample=plan.excerpts.take(12).joinToString("\n"){"Clip ${it.clipIndex+1}: source ${VideoCaptions.formatTime(it.startMs)}–${VideoCaptions.formatTime(it.endMs)}"}
        val extra=if(plan.excerpts.size>12)"\nFirst 12 of ${plan.excerpts.size} included excerpts shown."else""
        val body=TextView(this).apply{setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12));contentDescription="Caption highlight timeline review";text="${plan.selectedCues} selected caption moments become ${plan.excerpts.size} ordered excerpts. Playback ${VideoCaptions.formatTime(plan.beforeUs/1000)} → ${VideoCaptions.formatTime(plan.afterUs/1000)}. Touching ranges merge only within the same original timeline clip; repeated clips stay separate.\n\nReplaces the whole timeline. Only caption cues intersecting kept excerpts carry over, using unchanged source times/text. Clip appearance, speed, audio settings and subtitle-document protection stay. Music keeps its existing excerpt/loop and begins at project start on the shorter timeline; check the mix. Originals are unchanged. One Undo restores the previous timeline.\n\n"+sample+extra}
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Replace timeline with caption highlights?").setView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)}).setNegativeButton("Keep timeline",null).setPositiveButton("Apply highlights"){_,_->
            if(!activeCaptionHighlightDialog(dialog,review))return@setPositiveButton
            runCatching{check(commitCaptionHighlights(review,plan)){"Video or session changed. Select caption moments again."}}
                .onSuccess{selectedClip=0;val ready=runCatching{showClip();previewProject()}.isSuccess;status.text=if(ready)"Caption highlights applied. Preview to check cuts and sound; Undo restores the timeline."else"Highlights applied, but preview is unavailable. Undo restores the timeline."}
                .onFailure{if(workspaceActive)status.text=it.message?:"Highlights were not applied"}
        }.create();showCaptionReview(dialog,review)
    }

    private fun ownsCaptionTranslation(owner:VideoTranslationControls)=translationControls===owner&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&!isDestroyed&&!isFinishing
    private fun releaseCaptionTranslation(owner:VideoTranslationControls){if(translationControls!==owner)return;translationControls=null;importing=false}
    private fun commitCaptionTranslation(owner:VideoTranslationControls,current:VideoEditProject,index:Int,clip:VideoSegment,cues:List<VideoCaption>,stillCurrent:()->Boolean):Boolean {
        if(!workspaceActive||!ownsCaptionTranslation(owner)||!stillCurrent()||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip)return false
        VideoCaptions.validate(cues)
        require(cues.size==clip.captions.size&&cues.indices.all{cues[it].startMs==clip.captions[it].startMs&&cues[it].endMs==clip.captions[it].endMs})
        current.setCaptions(index,cues,null)
        return true
    }
    private fun requestCaptionTranslation(){
        if(!allowOnDeviceAi())return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return
        if(clip.captions.isEmpty()){status.text="Add captions before local English-to-Spanish translation.";return}
        if(!::start.isInitialized||!::end.isInitialized)return
        val duration=sourceDuration(clip.sourceId)
        val mode=accessMode;val namespace=videoLookNamespace()?:return;val sources=sourceUris().toMap();val music=current.soundtrack
        val from=start.progress;val to=end.progress;val generation=captionReviewGeneration
        runCatching{if(::video.isInitialized)video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        importing=true;val captured=closeState()
        lateinit var controls:VideoTranslationControls
        fun approved()=workspaceActive&&ownsCaptionTranslation(controls)&&captionReviewGeneration==generation&&accessMode==mode&&videoLookNamespace()==namespace&&captured.stillMatches(closeState())&&project===current&&current.segments===clips&&current.soundtrack===music&&selectedClip==index&&sourceUris()==sources&&start.progress==from&&end.progress==to
        controls=VideoTranslationControls(this,clip.captions,duration,
            isCurrent={approved()},
            apply={cues->check(commitCaptionTranslation(controls,current,index,clip,cues){approved()}){ "Video or session changed" }},
            committed={if(workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&accessMode==mode&&videoLookNamespace()==namespace){val ready=runCatching{showClip();start.progress=from;end.progress=to;showRange();previewProject()}.isSuccess;status.text=if(ready)"Reviewed Spanish captions applied. Preview for errors and sync; Undo restores prior captions."else"Reviewed Spanish captions applied. Preview unavailable; Undo restores prior captions."}},
            release={releaseCaptionTranslation(controls)},message={if(workspaceActive&&::status.isInitialized)status.text=it})
        translationControls=controls;controls.open()
    }

    private fun ownsVisualText(owner:VideoVisualTextControls)=visualTextControls===owner&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&!isDestroyed&&!isFinishing
    private fun releaseVisualText(owner:VideoVisualTextControls){if(visualTextControls!==owner)return;visualTextControls=null;importing=false}
    private fun commitVisualText(owner:VideoVisualTextControls,current:VideoEditProject,index:Int,clip:VideoSegment,cues:List<VideoCaption>,stillCurrent:()->Boolean):Boolean {
        if(!workspaceActive||!ownsVisualText(owner)||!stillCurrent()||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip)return false
        VideoCaptions.validate(cues)
        require(cues.isNotEmpty()&&cues.all{it.startMs>=clip.startMs&&it.endMs<=clip.endMs})
        current.setCaptions(index,cues,null)
        return true
    }
    private fun requestVisualTextCaptions(){
        if(!allowOnDeviceAi())return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return
        if(clip.endMs-clip.startMs !in 500..10_000){status.text="Trim this clip to 0.5–10 source seconds for experimental video OCR.";return}
        if(!::start.isInitialized||!::end.isInitialized)return
        val uri=sourceUri(clip.sourceId);val duration=sourceDuration(clip.sourceId)
        val mode=accessMode;val namespace=videoLookNamespace()?:return;val sources=sourceUris().toMap();val music=current.soundtrack
        val from=start.progress;val to=end.progress;val generation=captionReviewGeneration
        runCatching{if(::video.isInitialized)video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        importing=true;val captured=closeState()
        lateinit var controls:VideoVisualTextControls
        fun approved()=workspaceActive&&ownsVisualText(controls)&&captionReviewGeneration==generation&&accessMode==mode&&videoLookNamespace()==namespace&&captured.stillMatches(closeState())&&project===current&&current.segments===clips&&current.soundtrack===music&&selectedClip==index&&sourceUris()==sources&&start.progress==from&&end.progress==to
        controls=VideoVisualTextControls(this,uri,clip.startMs,clip.endMs,duration,clip.captions.size,
            isCurrent={approved()},
            apply={cues->check(commitVisualText(controls,current,index,clip,cues){approved()}){ "Video or session changed" }},
            committed={if(workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&accessMode==mode&&videoLookNamespace()==namespace){val ready=runCatching{showClip();start.progress=from;end.progress=to;showRange();previewProject()}.isSuccess;status.text=if(ready)"Reviewed visible-text captions applied. Preview for errors and sync; Undo restores prior captions."else"Reviewed visible-text captions applied. Preview unavailable; Undo restores prior captions."}},
            release={releaseVisualText(controls)},message={if(workspaceActive&&::status.isInitialized)status.text=it})
        visualTextControls=controls;controls.open()
    }

    private fun ownsSpeech(owner:VideoSpeechControls)=speechControls===owner&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&!isDestroyed&&!isFinishing
    private fun releaseSpeech(owner:VideoSpeechControls){if(speechControls!==owner)return;speechControls=null;importing=false}
    private fun commitSpeech(owner:VideoSpeechControls,current:VideoEditProject,index:Int,clip:VideoSegment,cues:List<VideoCaption>,stillCurrent:()->Boolean):Boolean {
        if(!workspaceActive||!ownsSpeech(owner)||!stillCurrent()||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip)return false
        VideoCaptions.validate(cues)
        require(cues.isNotEmpty()&&cues.all{it.startMs>=clip.startMs&&it.endMs<=clip.endMs})
        current.setCaptions(index,cues,null)
        return true
    }
    private fun requestSpeechCaptions(){
        if(!allowOnDeviceAi())return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return
        if(clip.endMs-clip.startMs !in 500..20_000){status.text="Trim this clip to 0.5–20 source seconds for experimental AI captions.";return}
        if(!::start.isInitialized||!::end.isInitialized)return
        val uri=sourceUri(clip.sourceId);val duration=sourceDuration(clip.sourceId)
        val mode=accessMode;val namespace=videoLookNamespace()?:return;val sources=sourceUris().toMap();val music=current.soundtrack
        val from=start.progress;val to=end.progress;val generation=captionReviewGeneration
        runCatching{if(::video.isInitialized)video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        importing=true;val captured=closeState()
        lateinit var controls:VideoSpeechControls
        fun approved()=workspaceActive&&ownsSpeech(controls)&&captionReviewGeneration==generation&&accessMode==mode&&videoLookNamespace()==namespace&&captured.stillMatches(closeState())&&project===current&&current.segments===clips&&current.soundtrack===music&&selectedClip==index&&sourceUris()==sources&&start.progress==from&&end.progress==to
        controls=VideoSpeechControls(this,uri,clip.startMs,clip.endMs,duration,clip.captions.size,
            isCurrent={approved()},
            apply={cues->check(commitSpeech(controls,current,index,clip,cues){approved()}){ "Video or session changed" }},
            committed={if(workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&accessMode==mode&&videoLookNamespace()==namespace){val ready=runCatching{showClip();start.progress=from;end.progress=to;showRange();previewProject()}.isSuccess;status.text=if(ready)"Reviewed AI captions applied. Preview for errors and sync; Undo restores prior captions."else"Reviewed AI captions applied. Preview unavailable; Undo restores prior captions."}},
            release={releaseSpeech(controls)},message={if(workspaceActive&&::status.isInitialized)status.text=it})
        speechControls=controls;controls.open()
    }

    private fun ownsCaptionSync(owner:VideoCaptionSyncControls)=captionSyncControls===owner&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&!isDestroyed&&!isFinishing
    private fun canApproveCaptionSync(owner:VideoCaptionSyncControls,captured:VideoCloseState,mode:EditorAccessMode,namespace:String)=workspaceActive&&ownsCaptionSync(owner)&&accessMode==mode&&videoLookNamespace()==namespace&&captured.stillMatches(closeState())
    private fun releaseCaptionSync(owner:VideoCaptionSyncControls){if(captionSyncControls!==owner)return;captionSyncControls=null;importing=false}
    private fun commitCaptionSync(owner:VideoCaptionSyncControls,captured:VideoCloseState,mode:EditorAccessMode,namespace:String,plan:VideoCaptionSync):Boolean {
        if(!canApproveCaptionSync(owner,captured,mode,namespace)||plan.index!=selectedClip||!plan.changed)return false
        plan.apply(requireNotNull(project)){check(canApproveCaptionSync(owner,captured,mode,namespace)){"Video or session changed. Review subtitle timing again."}}
        return true
    }
    private fun requestCaptionSync(){
        if(!canRequestTimelineCaptionImport())return
        val current=project?:return;val clips=current.segments;val index=selectedClip;val clip=clips.getOrNull(index)?:return
        if(clip.captions.size<2){status.text="Add at least two captions to synchronize their timing.";return}
        if(!::start.isInitialized||!::end.isInitialized)return
        val duration=sourceDuration(clip.sourceId);val mode=accessMode;val namespace=videoLookNamespace()?:return;val sources=sourceUris().toMap();val from=start.progress;val to=end.progress;val music=current.soundtrack
        runCatching{if(::video.isInitialized)video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        importing=true;val captured=closeState()
        lateinit var controls:VideoCaptionSyncControls
        controls=VideoCaptionSyncControls(this,clips,index,duration,music,
            isCurrent={canApproveCaptionSync(controls,captured,mode,namespace)&&project===current&&current.segments===clips&&current.soundtrack===music&&sourceUris()==sources&&start.progress==from&&end.progress==to},
            apply={plan->check(commitCaptionSync(controls,captured,mode,namespace,plan)){"Video or session changed. Existing captions were kept."}},
            committed={if(workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&accessMode==mode&&videoLookNamespace()==namespace){val ready=runCatching{showClip();start.progress=from;end.progress=to;showRange();previewProject()}.isSuccess;status.text=if(ready)"Subtitle timing synchronized. Preview to check sync; Undo restores previous timing."else"Subtitle timing applied, but preview is unavailable. Undo restores previous timing."}},
            release={releaseCaptionSync(controls)},message={if(workspaceActive)status.text=it})
        captionSyncControls=controls;controls.open()
    }

    private fun canRequestTimelineCaptionImport()=workspaceActive&&editorIdle()&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&!isDestroyed&&!isFinishing&&project!=null&&::source.isInitialized
    private fun ownsTimelineCaptionImport(owner:VideoTimelineCaptionControls)=timelineCaptionImport===owner&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&!isDestroyed&&!isFinishing
    private fun canApproveTimelineCaptionImport(owner:VideoTimelineCaptionControls,captured:VideoCloseState,mode:EditorAccessMode,namespace:String)=workspaceActive&&ownsTimelineCaptionImport(owner)&&accessMode==mode&&videoLookNamespace()==namespace&&captured.stillMatches(closeState())
    private fun releaseTimelineCaptionImport(owner:VideoTimelineCaptionControls){if(timelineCaptionImport!==owner)return;timelineCaptionImport=null;importing=false}
    private fun commitTimelineCaptionImport(owner:VideoTimelineCaptionControls,captured:VideoCloseState,mode:EditorAccessMode,namespace:String,plan:VideoTimelineCaptionImport):Boolean {
        if(!canApproveTimelineCaptionImport(owner,captured,mode,namespace)||!plan.changed)return false
        plan.apply(requireNotNull(project)){check(canApproveTimelineCaptionImport(owner,captured,mode,namespace)){"Timeline or session changed. Existing captions were kept."}}
        return true
    }
    private fun timelineCaptionRangeMatches(clip:VideoSegment,from:Int,to:Int)=maxOf(from*1000L,clip.startMs)==clip.startMs&&minOf(to*1000L,clip.endMs)==clip.endMs
    private fun requestTimelineCaptionImport()=requestCaptionImport(null)
    private fun requestCaptionImport(sourceTarget:VideoTimelineCaptionImport.SourceTarget?){
        if(!canRequestTimelineCaptionImport())return
        val current=project?:return;val clips=current.segments;val clip=clips.getOrNull(selectedClip)?:return
        if(!::start.isInitialized||!::end.isInitialized)return
        if(sourceTarget==null&&!timelineCaptionRangeMatches(clip,start.progress,end.progress)){status.text="Apply or reset the selected trim before importing playback subtitles. Existing captions were kept.";return}
        val mode=accessMode;val namespace=videoLookNamespace()?:return;val primary=source.toString();val duration=primaryDurationMs;val index=selectedClip;val music=current.soundtrack;val sources=sourceUris().toMap();val catalog=importedSources.toMap();val from=start.progress;val to=end.progress
        runCatching{if(::video.isInitialized)video.pause();stopProjectPreview();previewHandler.removeCallbacks(stopPreview)}
        importing=true;val captured=closeState()
        lateinit var controls:VideoTimelineCaptionControls
        controls=VideoTimelineCaptionControls(this,clips,music,
            isCurrent={ownsTimelineCaptionImport(controls)&&project===current&&current.segments===clips&&current.soundtrack===music&&accessMode==mode&&source.toString()==primary&&primaryDurationMs==duration&&selectedClip==index&&importedSources==catalog},
            isForeground={workspaceActive},canReview={canApproveTimelineCaptionImport(controls,captured,mode,namespace)&&sourceUris()==sources&&start.progress==from&&end.progress==to},
            launch={intent->startActivityForResult(intent,controls.requestCode)},
            apply={plan,guard->guard();check(commitTimelineCaptionImport(controls,captured,mode,namespace,plan)){"Timeline or session changed. Existing captions were kept."}},
            committed={plan->if(workspaceActive&&!isDestroyed&&!isFinishing&&project===current&&accessMode==mode&&videoLookNamespace()==namespace){val ready=runCatching{showClip();start.progress=from;end.progress=to;showRange();previewProject()}.isSuccess;status.text=if(ready)"Imported ${plan.inputCues} ${if(sourceTarget==null)"playback"else"original-source"} cues. Undo restores previous captions. Check timing."else"Captions imported, but preview is unavailable. Undo restores previous captions."}},
            release={releaseTimelineCaptionImport(controls)},message={status.text=it},sourceTarget=sourceTarget)
        timelineCaptionImport=controls;controls.open()
    }

    private fun copyCaptionText(){
        val review=captureCaptionReview()?:return
        val clips=project?.segments?.toList()?:return
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
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Copy caption text").setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Close",null).setPositiveButton("Copy all text"){_,_->
                val result=prepared?:return@setPositiveButton
                runCatching{
                    check(performCaptionClipboardCopy(dialog,review){
                        val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager?:error("Clipboard unavailable")
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Edited caption text",result.text))
                    }){"Project or session changed. Reopen caption text before copying."}
                }.onSuccess{status.text="Caption text copied. Project edits are unchanged."}.onFailure{status.text="Could not copy caption text. Try again."}
            }.create()
        choice.setOnCheckedChangeListener{_,checked->
            if(!activeCaptionReviewDialog(dialog,review)){status.text="Project or session changed. Reopen caption text.";dialog.dismiss();return@setOnCheckedChangeListener}
            runCatching{VideoCaptionText.create(clips,checked==timed.id)}.onSuccess{prepared=it;render(it);dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled=true}
                .onFailure{prepared=null;preview.text="";info.text="Caption text could not be prepared. Close and try again.";dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled=false}
        };showCaptionReview(dialog,review){prepared=null;preview.text="";info.text=""}
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
        val review=captureCaptionReview()?:return
        val clips=project?.segments?.toList()?:return
        var report:String?=runCatching{VideoCaptionReadability.inspect(clips).text()}.getOrElse{status.text="Caption report could not be prepared. Existing captions were kept.";return}
        val body=TextView(this).apply{text=report;setTextColor(Color.BLACK);setPadding(dp(20),dp(12),dp(20),dp(12));setTextIsSelectable(true);contentDescription="Caption readability report"}
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Caption readability")
            .setView(android.widget.ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setPositiveButton("Copy report"){_,_->
                val captured=report?:return@setPositiveButton
                runCatching{
                    check(performCaptionClipboardCopy(dialog,review){
                        val clipboard=getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager?:error("Clipboard unavailable")
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Caption readability review",captured))
                    }){"Project or session changed. Reopen the report before copying."}
                }.onSuccess{status.text="Readability report copied. Captions and project edits are unchanged."}
                    .onFailure{status.text="Could not copy the report. Reopen it and try again."}
            }.setNegativeButton("Close",null).create()
        showCaptionReview(dialog,review){report=null;body.text=""}
    }

    private fun editCaption(current:VideoEditProject,index:Int,clip:VideoSegment,cueIndex:Int?,canApply:()->Boolean={true}) {
        if(!canApply()||!canRequestTimelineCaptionImport()||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip||cueIndex!=null&&cueIndex !in clip.captions.indices)return
        val review=captureCaptionReview()?:return
        val cue=cueIndex?.let{clip.captions[it]}
        val from=cue?.startMs ?: runCatching{splitPosition()}.getOrDefault(clip.startMs).coerceIn(clip.startMs,clip.endMs-1)
        val to=cue?.endMs ?: minOf(from+3000,clip.endMs)
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        body.addView(TextView(this).apply{text="Use original video timestamps in seconds. Captions follow this clip's trims and speed. Up to 200 cues per clip; plain text only.";setTextColor(Color.DKGRAY)})
        fun timeField(label:String,value:Long)=EditText(this).apply{hint=label;contentDescription=label;setText(String.format(java.util.Locale.ROOT,"%.3f",value/1000.0));inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL}.also{body.addView(it)}
        val a=timeField("Caption start, source seconds",from);val b=timeField("Caption end, source seconds",to)
        val text=EditText(this).apply{hint="Caption text";contentDescription="Caption text";setText(cue?.text.orEmpty());maxLines=3;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(240))}
        body.addView(text)
        lateinit var dialog:android.app.AlertDialog
        val builder=android.app.AlertDialog.Builder(this).setTitle(if(cue==null)"Add caption" else "Edit caption").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Apply",null)
        if(cueIndex!=null)builder.setNeutralButton("Delete"){_,_->if(canApply()&&commitVisibleCaptionReview(dialog,review,current,index,clip,clip.captions.filterIndexed{i,_->i!=cueIndex})){showClip();previewProject()}}
        dialog=builder.create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!canApply()||!activeCaptionReviewDialog(dialog,review)||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip){dialog.dismiss();return@setOnClickListener}
            runCatching{
                val next=VideoCaption(VideoCaptions.seconds(a.text.toString()),VideoCaptions.seconds(b.text.toString()),text.text.toString().trim())
                val cues=clip.captions.toMutableList().apply{if(cueIndex==null)add(next)else set(cueIndex,next)}.sortedBy{it.startMs}
                check(commitVisibleCaptionReview(dialog,review,current,index,clip,cues)){"Video or session changed. Reopen captions before editing."}
            }.onSuccess{dialog.dismiss();showClip();previewProject();status.text="Captions updated. Preview the project before exporting."}.onFailure{text.error=it.message?:"Check caption timing and text"}
        }}
        showCaptionReview(dialog,review)
    }

    private fun importCaptions(current:VideoEditProject,index:Int,clip:VideoSegment) {
        if(!canRequestTimelineCaptionImport()||project!==current||selectedClip!=index||current.segments.getOrNull(index)!==clip)return
        val target=runCatching{VideoTimelineCaptionImport.SourceTarget(index,sourceDuration(clip.sourceId))}.getOrElse{return}
        requestCaptionImport(target)
    }

    private fun exportSubtitles()=beginMediaCopy(VideoSaveRequest.Kind.SUBTITLES)

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

    private var soundtrackExcerptEditor:android.app.AlertDialog?=null
    private var soundtrackExcerptConfirmation:android.app.AlertDialog?=null
    private fun commitSoundtrackExcerpt(editor:android.app.AlertDialog,confirmation:android.app.AlertDialog,review:VideoCaptionReviewState,current:VideoEditProject,track:VideoSoundtrack,next:VideoSoundtrack):Boolean {
        if(soundtrackExcerptEditor!==editor||soundtrackExcerptConfirmation!==confirmation||!activeCaptionReviewDialog(editor,review)||!activeCaptionReviewDialog(confirmation,review)||project!==current||current.soundtrack!=track)return false
        require(next!=track){"Choose a different range or loop setting"}
        require(next.copy(startMs=track.startMs,endMs=track.endMs,loop=track.loop)==track){"Soundtrack source or mix changed"}
        VideoTimeline.soundtrackRepeatCount(current.segments,next)
        VideoDraftCodec.requireRecoverableCaptions(current.segments,!next.loop,next.hasExcerpt)
        current.setSoundtrack(next);return true
    }
    private fun soundtrackExcerpt(){
        val review=captureCaptionReview()?:return
        val current=project?:return;val clips=current.segments.toList()
        val track=current.soundtrack?:run{status.text="Add music or a voiceover first.";return}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        body.addView(TextView(this).apply{text="Choose source-audio seconds, from 0 to ${String.format(java.util.Locale.ROOT,"%.3f",track.durationMs/1000.0)}. The excerpt starts at project time zero. Original clip audio stays attached; this edits no audio file.";setTextColor(Color.DKGRAY)})
        fun field(label:String,value:Long)=EditText(this).apply{hint=label;contentDescription=label;setSingleLine(true);setTextColor(Color.BLACK);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(String.format(java.util.Locale.ROOT,"%.3f",value/1000.0));body.addView(this)}
        body.addView(TextView(this).apply{text=runCatching{"Current timing\n"+VideoSoundtrackReport.inspect(clips,track).text()}.getOrElse{"Current soundtrack timing exceeds supported limits. Choose a longer excerpt or turn Loop off."};setTextColor(Color.DKGRAY)})
        val from=field("Soundtrack excerpt start, source seconds",track.startMs)
        val to=field("Soundtrack excerpt end, source seconds",track.endMs)
        val loop=android.widget.CheckBox(this).apply{text="Loop this excerpt to fill the video";setTextColor(Color.BLACK);isChecked=track.loop;body.addView(this)}
        body.addView(TextView(this).apply{text="When Loop is off, audio plays once and stops at the excerpt end or video end. Looping supports up to 1,000 repeats. Preview project to check cuts and the mix; speech is not analyzed.";setTextColor(Color.DKGRAY)})
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Soundtrack excerpt").setView(android.widget.ScrollView(this).apply{addView(body)})
            .setNegativeButton("Cancel",null).setNeutralButton("Full audio",null).setPositiveButton("Review",null).create()
        dialog.setOnShowListener{
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener{if(soundtrackExcerptEditor!==dialog||!activeCaptionReviewDialog(dialog,review))return@setOnClickListener;from.setText("0.000");to.setText(String.format(java.util.Locale.ROOT,"%.3f",track.durationMs/1000.0))}
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(soundtrackExcerptEditor!==dialog||!activeCaptionReviewDialog(dialog,review)){dialog.dismiss();return@setOnClickListener}
                runCatching{
                    track.copy(startMs=VideoCaptions.seconds(from.text.toString()),endMs=VideoCaptions.seconds(to.text.toString()),loop=loop.isChecked).also{
                        require(it!=track){"Choose a different range or loop setting"}
                        VideoTimeline.soundtrackRepeatCount(clips,it)
                        VideoDraftCodec.requireRecoverableCaptions(clips,!it.loop,it.hasExcerpt)
                    }
                }.onSuccess{next->
                    soundtrackExcerptConfirmation?.dismiss()
                    lateinit var confirmation:android.app.AlertDialog
                    confirmation=android.app.AlertDialog.Builder(this).setTitle("Use this soundtrack excerpt?")
                        .setMessage("${VideoSoundtrackReport.inspect(clips,next).text()}\n\n${VideoCaptions.formatTime(next.startMs)} → ${VideoCaptions.formatTime(next.endMs)} of the original audio, ${if(next.loop)"looping"else "playing once"} from project start. Volume and original clip audio stay unchanged. Undo restores the previous soundtrack range.")
                        .setNegativeButton("Keep editing",null).setPositiveButton("Apply excerpt"){_,_->
                            runCatching{commitSoundtrackExcerpt(dialog,confirmation,review,current,track,next)}.onSuccess appliedResult@{applied->
                                if(!applied){confirmation.dismiss();return@appliedResult}
                                dialog.dismiss()
                                val refreshed=runCatching{showClip()}.isSuccess
                                status.text=if(refreshed)"Soundtrack excerpt applied. Preview project to check the mix; Undo restores the previous range."else"Soundtrack excerpt applied. Preview could not refresh; reopen preview to check the mix. Undo restores the previous range."
                            }.onFailure{to.error=it.message?:"This range cannot be saved within project limits."}
                        }.create()
                    soundtrackExcerptConfirmation=confirmation
                    showCaptionReview(confirmation,review){if(soundtrackExcerptConfirmation===confirmation)soundtrackExcerptConfirmation=null}
                }.onFailure{to.error=it.message?:"Choose a valid audio range"}
            }
        }
        soundtrackExcerptConfirmation?.dismiss();soundtrackExcerptConfirmation=null
        soundtrackExcerptEditor?.dismiss();soundtrackExcerptEditor=dialog
        showCaptionReview(dialog,review){
            if(soundtrackExcerptEditor===dialog){soundtrackExcerptEditor=null;val confirmation=soundtrackExcerptConfirmation;soundtrackExcerptConfirmation=null;confirmation?.dismiss()}
        }
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

    private fun videoFrameArchive(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val sources=sourceUris();val durations=sourceDurations();val original=sources[clip.sourceId]?:return
        val duration=durations[clip.sourceId]?:return;val mode=accessMode;val owner=videoLookNamespace()?:return
        val from=maxOf(start.progress*1000L,clip.startMs);val to=minOf(end.progress*1000L,clip.endMs)
        if(to-from<4){status.text="Choose a valid source selection of at least four milliseconds.";return}
        video.pause();stopProjectPreview();importing=true
        val captured=closeState();val generation=captionReviewGeneration
        lateinit var controls:VideoFrameArchiveControls
        fun same()=frameArchiveControls===controls&&workspaceActive&&!isDestroyed&&!isFinishing&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&project===current&&selectedClip==index&&captionReviewGeneration==generation&&accessMode==mode&&videoLookNamespace()==owner&&captured.stillMatches(closeState())&&sourceUris()==sources&&sourceDurations()==durations
        controls=VideoFrameArchiveControls(this,original,duration,from,to,
            isCurrent={same()},requireWorkerCurrent={check(VideoPrivateOwner.namespace(applicationContext,mode)==owner){"Account changed"}},
            message={text->if(frameArchiveControls===controls&&!isDestroyed&&!isFinishing)status.text=text},
            shareArchive={file->
                check(same()){ "Video or session changed" }
                val archive=androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",file)
                val send=Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,archive)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=android.content.ClipData.newUri(contentResolver,"Reviewed video frame archive",archive)}
                startActivity(Intent.createChooser(send,"Save or share reviewed frame archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            },released={if(frameArchiveControls===controls){frameArchiveControls=null;importing=false}})
        frameArchiveControls=controls
        runCatching{controls.open()}.onFailure{controls.close();if(!isDestroyed&&!isFinishing)status.text="Could not open frame archive controls. Original video and edits were kept."}
    }

    private fun videoTimeSlicePhoto(){
        if(!editorIdle()||!workspaceActive||isDestroyed||isFinishing)return
        val current=project?:return;val index=selectedClip;val clip=current.segments.getOrNull(index)?:return
        val sources=sourceUris();val durations=sourceDurations();val original=sources[clip.sourceId]?:return
        val duration=durations[clip.sourceId]?:return;val mode=accessMode;val owner=videoLookNamespace()?:return
        val from=maxOf(start.progress*1000L,clip.startMs);val to=minOf(end.progress*1000L,clip.endMs)
        if(to-from<4){status.text="Choose a valid source selection of at least four milliseconds.";return}
        val provenance=runCatching{app.honorable.editor.ImportedPhotoOriginals.validate(listOf(original.toString()))}.getOrElse{status.text="This original cannot be safely carried into a photo workspace.";return}
        video.pause();stopProjectPreview();importing=true
        val captured=closeState();val generation=captionReviewGeneration
        lateinit var controls:VideoTimeSliceControls
        fun same()=timeSliceControls===controls&&workspaceActive&&!isDestroyed&&!isFinishing&&importing&&!saving&&!analyzing&&!draftBusy&&pendingRange==null&&recoveryBytes==null&&!recoveryBlocked&&!recoveryChecking&&project===current&&selectedClip==index&&captionReviewGeneration==generation&&accessMode==mode&&videoLookNamespace()==owner&&captured.stillMatches(closeState())&&sourceUris()==sources&&sourceDurations()==durations
        controls=VideoTimeSliceControls(this,original,duration,from,to,
            isCurrent={same()},requireWorkerCurrent={check(VideoPrivateOwner.namespace(applicationContext,mode)==owner){"Account changed"}},
            message={text->if(timeSliceControls===controls&&!isDestroyed&&!isFinishing)status.text=text},
            openImage={file->
                check(same()){"Video or session changed"}
                val image=androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",file)
                startActivity(Intent(this,app.honorable.editor.PhotoEditorActivity::class.java).putExtra("uri",image.toString()).putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name)
                    .putStringArrayListExtra(app.honorable.editor.ImportedPhotoOriginals.EXTRA,ArrayList(provenance)))
            },released={if(timeSliceControls===controls){timeSliceControls=null;importing=false}})
        timeSliceControls=controls
        runCatching{controls.open()}.onFailure{controls.close();if(!isDestroyed&&!isFinishing)status.text="Could not open time-slice controls. Original video and edits were kept."}
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
    private fun allowsMediaImportResult(requestCode:Int):Boolean {
        videoSaveRequest?.let{return requestCode !in 22..29&&!VideoSaveRequest.ownsCode(requestCode)||requestCode==it.requestCode}
        if(captionSyncControls!=null||speechControls!=null||visualTextControls!=null||translationControls!=null)return requestCode !in 22..29
        timelineCaptionImport?.let{return requestCode !in 22..29||requestCode==it.requestCode}
        return mediaImportTarget?.let{requestCode !in 22..28||requestCode==it.kind.requestCode}?:true
    }
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
        writeVideoSaveState(outState)
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
        videoSaveRequest?.let{releaseVideoSave(it)}
        pendingRange=null
        voiceoverTarget=null;importing=false
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

    override fun onResume(){super.onResume();workspaceActive=true;resumeVideoSave();syncVideoSaveControls();flushVideoSaveNotice();timelineCaptionImport?.resume();previewHandler.post(workspaceTick);if(recoveryBytes!=null&&!recoveryChecking&&!recoveryBlocked&&project!=null)retryRecovery()}
    override fun onPause() { projectBackupControls?.close();projectBackupControls=null;darkHoldControls?.close();darkHoldControls=null;audioOnsetControls?.close();audioOnsetControls=null;frameArchiveControls?.close();frameArchiveControls=null;timeSliceControls?.close();timeSliceControls=null;invalidateCaptionReviews();translationControls?.close();visualTextControls?.close();speechControls?.close();captionSyncControls?.close();timelineCaptionImport?.onPause();versionShelf?.close();versionShelf=null;runCatching{restoreReviewDialog?.dismiss()};restoreReviewDialog=null;mediaImportTarget?.takeIf{it.isChecking}?.let{finishMediaImportTarget(it);if(::status.isInitialized)status.text="Import stopped while the editor was away. Choose ${it.kind.action} again."};runCatching{recoveryDiscardDialog?.dismiss()};recoveryDiscardDialog=null;runCatching{closeDialog?.dismiss()};closeDialog=null;titleStyleShelf?.close();titleStyleShelf=null;quietAudioGeneration++;quietAudioCancellation?.set(true);runCatching{quietAudioDialog?.dismiss()};quietAudioDialog=null;framePhotoCancellation?.set(true);workspaceActive=false;previewHandler.removeCallbacks(workspaceTick); if (::video.isInitialized) video.pause(); stopProjectPreview(); super.onPause() }
    override fun onDestroy() {
        projectBackupControls?.close();projectBackupControls=null
        darkHoldControls?.close();darkHoldControls=null
        audioOnsetControls?.close();audioOnsetControls=null
        frameArchiveControls?.close();frameArchiveControls=null
        timeSliceControls?.close();timeSliceControls=null
        invalidateCaptionReviews();translationControls?.close();translationControls=null;visualTextControls?.close();visualTextControls=null;speechControls?.close();speechControls=null;captionSyncControls?.close();captionSyncControls=null;timelineCaptionImport?.close();timelineCaptionImport=null
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
        videoSaveRequest?.close();videoSaveRequest=null;videoSaveNotice=null
        exportCancellation.cancel()
        exporter?.cancel()
        audioExporter?.cancel()
        super.onDestroy()
    }
}
