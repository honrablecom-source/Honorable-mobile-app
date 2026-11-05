package com.honorablemobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.honorable.editor.EditorAccessMode
import java.io.File
import java.util.Date

/** Foreground, explicit-consent capture. Kept takes are separate private audio originals. */
class VoiceoverRecorderActivity:Activity() {
    private lateinit var mode:EditorAccessMode
    private lateinit var owner:String
    private lateinit var files:VoiceoverFiles
    private lateinit var capture:VoiceoverCapture
    private lateinit var status:TextView
    private lateinit var takeName:android.widget.EditText
    private lateinit var library:LinearLayout
    private lateinit var start:Button
    private lateinit var stop:Button
    private lateinit var previewTake:Button
    private lateinit var keep:Button
    private lateinit var use:Button
    private lateinit var discard:Button
    private var player:MediaPlayer?=null
    private var active=false
    private var closed=false
    private var startedAt=0L
    private val handler=Handler(Looper.getMainLooper())
    private val tick=object:Runnable{
        override fun run(){
            if(!active||closed||!::capture.isInitialized)return
            if(runCatching{requireOwner()}.isFailure){stopPreview();runCatching{capture.discard()};finish();return}
            if(capture.phase==VoiceoverCapture.Phase.RECORDING){
                val seconds=((SystemClock.elapsedRealtime()-startedAt)/1000).coerceAtLeast(0)
                status.text="Recording · ${seconds/60}:${(seconds%60).toString().padStart(2,'0')} / 3:00 · stays on this device"
            }
            handler.postDelayed(this,1000)
        }
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun requireOwner(){check(!closed&&VideoPrivateOwner.namespace(this,mode)==owner){"Recording account changed"}}
    private fun requireActive(){requireOwner();check(active&&!isFinishing&&!isDestroyed)}
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        window.statusBarColor=Color.WHITE;window.navigationBarColor=if(Build.VERSION.SDK_INT>=26)Color.WHITE else Color.BLACK
        window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if(Build.VERSION.SDK_INT>=26)android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        mode=runCatching{EditorAccessMode.fromExtra(intent.getStringExtra(EditorAccessMode.EXTRA))}.getOrElse{finish();return}
        owner=VideoPrivateOwner.namespace(this,mode)?:run{finish();return}
        files=VoiceoverFiles(File(noBackupFilesDir,"voiceovers"),owner)
        val backend=object:VoiceoverBackend{
            private var recorder:MediaRecorder?=null
            private var limited=false
            override fun start(file:File){
                limited=false
                @Suppress("DEPRECATION") val value=if(Build.VERSION.SDK_INT>=31)MediaRecorder(this@VoiceoverRecorderActivity)else MediaRecorder()
                recorder=value
                value.setAudioSource(MediaRecorder.AudioSource.MIC);value.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                value.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);value.setAudioChannels(1);value.setAudioSamplingRate(48000);value.setAudioEncodingBitRate(96000)
                value.setMaxDuration(VoiceoverFiles.MAX_RECORD_MS);value.setMaxFileSize(VoiceoverFiles.MAX_BYTES);value.setOutputFile(file.absolutePath)
                value.setOnInfoListener{_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED||what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED){limited=true;runOnUiThread{if(!closed&&capture.phase==VoiceoverCapture.Phase.RECORDING)finishCapture()}}}
                value.setOnErrorListener{_,_,_->runOnUiThread{if(!closed){runCatching{capture.discard()};status.text="Recording failed. The unfinished take was discarded; project edits are unchanged.";render()}}}
                value.prepare();value.start()
            }
            override fun stop(){try{requireNotNull(recorder).stop()}catch(failure:RuntimeException){if(!limited)throw failure}}
            override fun release(){val value=recorder;recorder=null;try{value?.reset()}finally{value?.release()}}
        }
        capture=VoiceoverCapture(files,backend){requireOwner()}
        runCatching{files.cleanAbandoned(VoiceoverCapture.livePaths())}
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16));setBackgroundColor(Color.WHITE)}
        app.honorable.editor.NativeWorkspaceInsets.install(this,panel)
        fun text(value:String,size:Float=15f)=TextView(this).apply{text=value;textSize=size;setTextColor(Color.BLACK);setPadding(0,dp(8),0,dp(8));panel.addView(this)}
        text("Voiceover",24f).apply{if(Build.VERSION.SDK_INT>=28)isAccessibilityHeading=true}
        text("Microphone access is requested only when you tap Start. Up to three minutes per take. Keep this screen open; leaving discards an unfinished take. Saved takes stay private on this device. Use plays once from project start as the project soundtrack; it replaces existing music after confirmation.")
        status=text("Ready. No audio is being recorded.")
        fun button(label:String,action:()->Unit)=Button(this).apply{text=label;contentDescription=label;app.honorable.editor.EditorChrome.style(this);setOnClickListener{action()};panel.addView(this)}
        start=button("Start recording"){
            if(runCatching{requireActive()}.isFailure)return@button
            if(!packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)){status.text="No microphone is available. Saved takes can still be used.";return@button}
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),MIC_REQUEST);return@button}
            stopPreview()
            runCatching{capture.start(true,active);startedAt=SystemClock.elapsedRealtime()}
                .onSuccess{status.text="Recording on this device…"}.onFailure{status.text="Could not start. Check microphone access, or delete an unused saved take if the 20-take shelf is full."};render()
        }
        stop=button("Stop and review"){finishCapture()}
        previewTake=button("Preview unfinished take"){capture.pending?.let{preview(it)}}
        takeName=android.widget.EditText(this).apply{hint="Optional name for this take";contentDescription="Optional voiceover take name";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(160));panel.addView(this)}
        text("After stopping, optionally name this take before keeping it. Up to 80 characters on one line;leave blank to use the date label. Names stay private on this device.")
        keep=button("Keep take on this device"){accept(false)}
        use=button("Keep and use in project"){accept(true)}
        discard=button("Discard unfinished take"){stopPreview();runCatching{capture.discard()};status.text="Unfinished take discarded.";render()}
        button("Close voiceover"){finish()}
        text("Saved takes",20f).apply{if(Build.VERSION.SDK_INT>=28)isAccessibilityHeading=true}
        library=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;panel.addView(this)}
        setContentView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(panel)});render()
    }
    private fun finishCapture(){
        if(!active||closed||!::capture.isInitialized||capture.phase!=VoiceoverCapture.Phase.RECORDING)return
        runCatching{requireActive();capture.stop(active)}.onSuccess{status.text="Take ready for review. It is not kept until you choose Keep or Use."}
            .onFailure{status.text="Could not finish the take. Record a little longer and try again."};render()
    }
    private fun inspect(file:File):Long {
        require(file.isFile&&file.length() in 1..VoiceoverFiles.MAX_BYTES)
        val extractor=MediaExtractor()
        try{
            extractor.setDataSource(file.absolutePath)
            val index=(0 until extractor.trackCount).firstOrNull{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm"}?:error("No AAC audio track")
            extractor.selectTrack(index);require(extractor.sampleTime>=0){"Recording has no audio samples"}
            val metadata=MediaMetadataRetriever()
            try{metadata.setDataSource(file.absolutePath);return requireNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong().also{require(it in 1..VoiceoverFiles.MAX_ACCEPTED_MS)}}finally{metadata.release()}
        }finally{extractor.release()}
    }
    private fun accept(returnToProject:Boolean){
        if(runCatching{requireActive()}.isFailure)return
        stopPreview()
        val label=runCatching{takeName.text.toString().trim().takeIf{it.isNotEmpty()}?.let{VoiceoverFiles.checkedName(it)}}
            .getOrElse{takeName.error=it.message?:"Use up to 80 characters on one line,or leave blank.";return}
        runCatching{capture.accept{inspect(it)}}.onSuccess{file->
            if(label!=null){
                val named=runCatching{files.renameSaved(file.name.removeSuffix(".m4a"),label){requireActive()}}
                if(named.isFailure){status.text="Audio was kept privately,but its name could not be saved. Rename or use this take from Saved takes.";render();return@onSuccess}
            }
            status.text="Voiceover kept privately on this device.";render()
            if(returnToProject)returnFile(file)
        }.onFailure{status.text="This take could not be kept. Preview or discard it and try another recording.";render()}
    }
    private fun preview(file:File){
        if(runCatching{requireActive()}.isFailure)return
        stopPreview()
        runCatching{
            inspect(file)
            val value=MediaPlayer();player=value
            value.setDataSource(file.absolutePath);value.setOnCompletionListener{stopPreview();if(!closed)status.text="Preview finished."};value.prepare();value.start();status.text="Playing local voiceover preview."
        }.onFailure{stopPreview();status.text="This take could not be previewed. It has been kept unchanged."}
    }
    private fun stopPreview(){val value=player;player=null;runCatching{value?.release()}}
    private fun returnFile(file:File){
        runCatching{requireActive();inspect(file);val id=file.name.removeSuffix(".m4a");setResult(RESULT_OK,Intent().setData(VoiceoverProvider.uri(this,owner,id)));finish()}
            .onFailure{status.text="This voiceover cannot be used right now. Saved takes were kept."}
    }
    private fun render(){
        if(!::capture.isInitialized||!::status.isInitialized)return
        if(runCatching{requireOwner()}.isFailure){finish();return}
        val phase=capture.phase
        start.isEnabled=active&&phase==VoiceoverCapture.Phase.IDLE;stop.isEnabled=active&&phase==VoiceoverCapture.Phase.RECORDING
        previewTake.isEnabled=active&&phase==VoiceoverCapture.Phase.REVIEW;keep.isEnabled=previewTake.isEnabled;use.isEnabled=previewTake.isEnabled
        discard.isEnabled=active&&phase!=VoiceoverCapture.Phase.IDLE
        takeName.isEnabled=active&&phase==VoiceoverCapture.Phase.REVIEW
        takeName.alpha=if(takeName.isEnabled)1f else .4f
        if(phase==VoiceoverCapture.Phase.IDLE){takeName.setText("");takeName.error=null}
        listOf(start,stop,previewTake,keep,use,discard).forEach{it.alpha=if(it.isEnabled)1f else .4f}
        library.removeAllViews()
        val saved=runCatching{files.saved()}.getOrElse{status.text="Saved takes could not be read. Existing recordings were kept.";return}
        if(saved.isEmpty())library.addView(TextView(this).apply{text="No saved voiceovers yet.";setTextColor(Color.DKGRAY)})
        for(file in saved)library.addView(Button(this).apply{
            val label=(files.readName(file.name.removeSuffix(".m4a"))?:"Voiceover")+" · "+android.text.format.DateFormat.format("MMM d, HH:mm",Date(file.lastModified()))+" · ${file.length()/1024} KB"
            text=label;contentDescription=label;app.honorable.editor.EditorChrome.style(this);isEnabled=active&&phase==VoiceoverCapture.Phase.IDLE;alpha=if(isEnabled)1f else .4f
            setOnClickListener{
                if(runCatching{requireActive()}.isFailure)return@setOnClickListener
                AlertDialog.Builder(this@VoiceoverRecorderActivity).setTitle(label).setItems(arrayOf("Preview saved take","Use in project","Rename saved take","Delete saved take")){_,action->
                    if(runCatching{requireActive()}.isFailure)return@setItems
                    when(action){0->preview(file);1->returnFile(file);2->renameTake(file);3->AlertDialog.Builder(this@VoiceoverRecorderActivity).setTitle("Delete this voiceover?")
                        .setMessage("This removes this private audio original. Saved projects using it may no longer play it. Export a finished copy before deleting a take you still need.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Delete take"){_,_->
                            stopPreview();runCatching{requireActive();files.deleteSaved(file.name.removeSuffix(".m4a"))}
                                .onSuccess{status.text="Saved voiceover deleted.";render()}.onFailure{status.text="Voiceover could not be deleted. Existing files were kept."}
                        }.show()}
                }.setNegativeButton("Close",null).show()
            }
        })
    }
    private fun renameTake(file:File){
        if(runCatching{requireActive()}.isFailure||capture.phase!=VoiceoverCapture.Phase.IDLE)return
        val id=file.name.removeSuffix(".m4a")
        val name=android.widget.EditText(this).apply{hint="Voiceover name";contentDescription="Voiceover name";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(160));setText(files.readName(id).orEmpty())}
        val dialog=AlertDialog.Builder(this).setTitle("Rename saved voiceover").setMessage("This private label leaves the audio and all saved project references unchanged. Up to 80 characters.").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save name",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            runCatching{requireActive();check(capture.phase==VoiceoverCapture.Phase.IDLE);files.renameSaved(id,name.text.toString()){requireActive()}}
                .onSuccess{dialog.dismiss();status.text="Voiceover name saved privately. Audio and projects are unchanged.";render()}
                .onFailure{name.error="Name could not be saved. Use 1–80 characters on one line and keep this screen open."}
        }};dialog.show()
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,results)
        if(requestCode==MIC_REQUEST&&::status.isInitialized)status.text=if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)"Microphone access allowed. Tap Start when you are ready; recording has not started." else "Microphone access was not allowed. Saved takes remain available; you can close this screen."
    }
    override fun onResume(){super.onResume();active=true;if(::capture.isInitialized){if(runCatching{requireOwner()}.isFailure){finish();return};render();handler.post(tick)}}
    override fun onPause(){
        active=false;handler.removeCallbacks(tick);stopPreview()
        if(::capture.isInitialized){val unfinished=capture.phase!=VoiceoverCapture.Phase.IDLE;runCatching{capture.discard()};if(unfinished&&::status.isInitialized)status.text="Unfinished take discarded when the recording screen was left."}
        super.onPause()
    }
    override fun onDestroy(){closed=true;active=false;handler.removeCallbacks(tick);stopPreview();if(::capture.isInitialized)runCatching{capture.discard()};super.onDestroy()}
    companion object{private const val MIC_REQUEST=501}
}
