package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** One picker/read/review lease. Workers only read; caption/grant commit runs on the editor thread. */
internal class VideoTimelineCaptionControls(private val activity:Activity,private val clips:List<VideoSegment>,private val music:VideoSoundtrack?,
    private val isCurrent:()->Boolean,private val isForeground:()->Boolean,private val canReview:()->Boolean,
    private val launch:(Intent)->Unit,private val apply:(VideoTimelineCaptionImport,()->Unit)->Unit,private val committed:(VideoTimelineCaptionImport)->Unit,
    private val release:()->Unit,private val message:(String)->Unit,private val sourceTarget:VideoTimelineCaptionImport.SourceTarget?=null){
    val requestCode:Int get()=if(sourceTarget==null)REQUEST_CODE else SOURCE_REQUEST_CODE
    private val label:String get()=if(sourceTarget==null)"Playback" else "Clip"
    private val phase=AtomicInteger(DISCLOSURE)
    private val worker=Executors.newSingleThreadExecutor()
    private var dialog:AlertDialog?=null
    private var token:Any?=null
    private var pending:Pair<String,Int>?=null
    private fun owns(value:Any)=phase.get()!=CLOSED&&token===value
    private fun current()=phase.get()!=CLOSED&&isCurrent()
    private fun active()=current()&&isForeground()&&canReview()
    private fun guard(){check(current()&&isForeground()&&!Thread.currentThread().isInterrupted){"Timeline subtitle import stopped"}}
    fun close(){if(phase.getAndSet(CLOSED)==CLOSED)return;token=null;pending=null;val old=dialog;dialog=null;old?.dismiss();worker.shutdownNow();release()}
    fun onPause(){if(phase.get()!=PICKER&&phase.get()!=RESULT)close()}
    private fun say(text:String){if(isForeground())message(text)}
    private fun show(value:AlertDialog,next:Any){
        if(!active()){close();return}
        token=null;dialog?.dismiss();dialog=value;token=next
        value.setOnCancelListener{if(owns(next)){say("$label subtitle import cancelled. Existing captions were kept.");close()}};value.show()
    }
    fun open(){
        if(!active()){close();return}
        val next=Any()
        show(AlertDialog.Builder(activity).setTitle(if(sourceTarget==null)"Import playback-timed SRT?"else"Import original-source SRT?")
            .setMessage(if(sourceTarget!=null)"Replace captions only on clip ${sourceTarget.index+1}, as one Undo. Times refer to the original video before trimming or speed changes; cues outside its trim stay stored. Other clips, music and originals are kept. UTF-8, up to 128 KB / 200 non-overlapping cues. Choose a local Files document allowing lasting read access. Review before replacement; no upload."else "Use subtitles timed to this whole edited project, after its stored trims, clip order and speeds. This replaces all project captions as one Undo. Titles, music, video settings and originals are kept. Up to 128 KB UTF-8 / 1000 input cues; mapped clip/project limits still apply. Source timing uses a one-millisecond grid, which can shift display boundaries slightly. A local Files document must allow lasting read access. You will review before replacement; no transcription or upload.")
            .setNegativeButton("Keep captions"){_,_->if(owns(next)){say("Kept existing project captions.");close()}}
            .setPositiveButton(if(sourceTarget==null)"Choose playback SRT"else"Choose clip SRT"){_,_->
                if(!owns(next))return@setPositiveButton
                if(!active()||!phase.compareAndSet(DISCLOSURE,PICKER)){close();return@setPositiveButton}
                token=null;dialog?.dismiss();dialog=null
                try{launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)})}
                catch(_:Exception){say("No subtitle document picker is available. Existing captions were kept.");close()}
            }.create(),next)
    }
    fun accept(resultCode:Int,data:Intent?){
        if(!phase.compareAndSet(PICKER,RESULT))return
        if(!current()||resultCode!=Activity.RESULT_OK||data?.data==null){close();return}
        val uri=data.data!!.toString()
        if(runCatching{VideoTimelineCaptionImport.validateDocument(uri)}.isFailure){say("Choose a readable local subtitle document. Existing captions were kept.");close();return}
        pending=uri to data.flags;resume()
    }
    fun resume(){
        if(phase.get()!=RESULT||!isForeground())return
        if(!active()){say("Timeline or session changed. Import subtitles again.");close();return}
        val result=pending?:run{close();return};if(!phase.compareAndSet(RESULT,CHECKING))return;pending=null
        val uri=Uri.parse(result.first);say("Reading ${label.lowercase()} subtitles on this device…")
        val reading=Any()
        show(AlertDialog.Builder(activity).setTitle("Reading ${label.lowercase()} subtitles")
            .setMessage("Reading and mapping the chosen document on this device. Existing captions are unchanged. Cancel stops this import; a provider read may take time to finish.")
            .setNegativeButton("Cancel import"){_,_->if(owns(reading)){say("$label subtitle import cancelled. Existing captions were kept.");close()}}.create(),reading)
        if(phase.get()!=CHECKING)return
        try{worker.execute{
            val prepared=runCatching{
                guard();val resolver=activity.contentResolver
                resolver.openFileDescriptor(uri,"r").use{input->requireNotNull(input){"Cannot read subtitle document"};require(android.system.Os.fstat(input.fileDescriptor).st_ino!=0L){"Cannot protect this subtitle original"}}
                guard();val bytes=resolver.openInputStream(uri)?.use{source->VideoSubtitleDocuments.readBounded(object:InputStream(){
                    override fun read():Int{guard();return source.read()}
                    override fun read(buffer:ByteArray,offset:Int,length:Int):Int{guard();return source.read(buffer,offset,length)}
                })}?:error("Cannot read subtitle document")
                sourceTarget?.let{VideoTimelineCaptionImport.prepareSourceClip(clips,it,bytes,result.first,music,::guard)}?:VideoTimelineCaptionImport.prepare(clips,bytes,result.first,music,::guard)
            }
            activity.runOnUiThread{
                if(phase.get()!=CHECKING)return@runOnUiThread
                if(!active()){say("Timeline or session changed. Existing captions were kept.");close();return@runOnUiThread}
                prepared.onSuccess{plan->
                    if(!plan.changed){say("This document already matches the project captions. Nothing changed.");close()}
                    else if(phase.compareAndSet(CHECKING,REVIEW))review(plan,result.second)
                }.onFailure{say("$label subtitles could not be imported: ${it.message?:"Check document access and timing"}. Existing captions were kept.");close()}
            }
        }}catch(_:java.util.concurrent.RejectedExecutionException){close()}
    }
    private fun review(plan:VideoTimelineCaptionImport,flags:Int){
        say(if(plan.sourceClip==null)"Review playback subtitles before replacing existing project captions."else"Review original-source subtitles before replacing selected-clip captions.")
        val next=Any();val sample=plan.segments.flatMapIndexed{index,clip->if(plan.sourceClip==null||index==plan.sourceClip)clip.captions.map{index+1 to it}else emptyList()}.take(8)
            .joinToString("\n\n"){(index,cue)->"Clip $index · source ${VideoCaptions.formatTime(cue.startMs)}–${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}"}
        val extra=if(plan.mappedCues>8)"\n\nPreview shows 8 of ${plan.mappedCues} source fragments; all are included."else""
        val old=plan.sourceClip?.let{plan.base[it].captions.size}?:plan.base.sumOf{it.captions.size}
        val summary=if(plan.sourceClip!=null)"${plan.inputCues} original-source cues replace $old captions only on clip ${plan.sourceClip+1}. Source times are preserved, including cues outside the current trim. Other clips, music and clip settings stay. One Undo restores previous captions. Check timing in preview.\n\n"else"${plan.inputCues} playback cues map to ${plan.mappedCues} source fragments in ${plan.changedClips} changed clips. Replaces $old existing caption entries across the project. ${plan.adjustedFragments} fragments need source-millisecond rounding; boundaries can move by up to one source millisecond (up to 2 playback milliseconds). The last playback millisecond is clipped to the actual project end. One Undo restores previous captions. Titles, music and clip settings stay. Check timing in preview.\n\n"
        show(AlertDialog.Builder(activity).setTitle(if(plan.sourceClip==null)"Replace whole-project captions?"else"Replace selected-clip captions?").setMessage(summary+sample+extra)
            .setNegativeButton("Keep captions"){_,_->if(owns(next)){say("Kept existing project captions.");close()}}
            .setPositiveButton(if(plan.sourceClip==null)"Replace project captions"else"Replace clip captions"){_,_->
                if(!owns(next))return@setPositiveButton
                if(!active()){say("Timeline or session changed. Existing captions were kept.");close();return@setPositiveButton}
                val uri=Uri.parse(plan.document)
                val result=runCatching{VideoCaptionImportPermissions.commit(
                    hasReadGrant={activity.contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission}},
                    takeReadGrant={guard();require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0&&flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION!=0){"Choose a Files provider that permits lasting subtitle read access"};activity.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)},
                    releaseReadGrant={activity.contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)},
                    applyCaptions={guard();apply(plan,::guard)}
                )}
                close()
                result.onSuccess{committed(plan)}.onFailure{say("$label captions were not replaced: ${it.message?:"Check subtitle access"}. Existing captions were kept.")}
            }.create(),next)
    }
    companion object{private const val DISCLOSURE=0;private const val PICKER=1;private const val RESULT=2;private const val CHECKING=3;private const val REVIEW=4;private const val CLOSED=5;const val REQUEST_CODE=29;const val SOURCE_REQUEST_CODE=25}
}
