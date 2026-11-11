package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.text.InputFilter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

/** Actual video OCR acquisition, then corrected source-time captions and explicit one-use Apply. */
internal class VideoVisualTextControls(
    private val activity:Activity,private val uri:Uri,private val from:Long,private val to:Long,
    private val sourceDuration:Long,private val existingCount:Int,
    private val isCurrent:()->Boolean,private val apply:(List<VideoCaption>)->Unit,
    private val committed:()->Unit,private val release:()->Unit,private val message:(String)->Unit,
    private val work:((VideoVisualText.Crop,AtomicBoolean)->List<VideoCaption>)?=null,
    private val launch:((()->Unit)->Unit)={Thread(it,"noble-video-ocr").start()}
){
    private val cancelled=AtomicBoolean(false)
    private var closed=false
    private var working=false
    private var released=false
    private var dialog:AlertDialog?=null
    private fun active()=!closed&&isCurrent()
    private fun releaseOnce(){if(!released){released=true;release()}}
    fun close(){
        if(!closed){closed=true;cancelled.set(true);val old=dialog;dialog=null;old?.dismiss()}
        if(!working)releaseOnce()
    }
    private fun show(next:AlertDialog){
        if(!active()){close();return}
        val old=dialog;dialog=null;old?.dismiss();dialog=next
        next.setOnDismissListener{if(dialog===next)close()};next.show()
    }
    private fun body()=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(32,20,32,20);setBackgroundColor(Color.WHITE)}
    private fun LinearLayout.note(value:String){addView(TextView(activity).apply{text=value;setTextColor(Color.BLACK)})}
    private fun scroll(body:LinearLayout)=ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(body)}
    fun open(){
        if(!active()){close();return}
        if(uri.scheme!="content"||from<0||to>sourceDuration||to-from !in 500..10000){message("Trim this clip to 0.5–10 source seconds before reading visible text.");close();return}
        val panel=body()
        panel.note("Recover visible Latin-script video text into editable captions using the existing on-device OCR model. This reads pictures, not speech. Original source frames are sampled twice per second, before playback effects/speed. Short, blurred, animated or rotated text may be missed; logos may be included. Nearest decoded frames may differ from requested times, so boundaries are approximate 0.5-second bins. Identical neighboring readings merge; blank readings are never filled with invented interpolation. Correct every text and timing before applying.")
        panel.note("Choose a vertical image region from top to bottom in percent (0 is top, 100 is bottom); full width is used. Default lower 40% helps exclude logos but may miss text. Choose 0–100 for the whole image. At least 10% tall. Frames are reduced to at most1280pixels per edge. Original files, audio and existing captions stay until explicit Apply. Leaving this editor discards the draft. No microphone, new model download or media upload; existing ML Kit SDK diagnostics policy still applies.")
        fun field(label:String,value:String):EditText{panel.note(label);return EditText(activity).apply{setTextColor(Color.BLACK);contentDescription=label;setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_NUMBER;filters=arrayOf(InputFilter.LengthFilter(3));setText(value);panel.addView(this)}}
        val top=field("Top of OCR region (%)","60");val bottom=field("Bottom of OCR region (%)","100")
        val setup=AlertDialog.Builder(activity).setTitle("AI read video text").setView(scroll(panel)).setNegativeButton("Cancel"){_,_->close()}.setPositiveButton("Read selected source region",null).create()
        setup.setOnShowListener{setup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(dialog!==setup||!active()){close();return@setOnClickListener}
            runCatching{VideoVisualText.Crop(top.text.toString().toInt(),bottom.text.toString().toInt())}.onSuccess{start(it)}.onFailure{bottom.error=it.message?:"Choose 0–100 percentages"}
        }};show(setup)
    }
    private fun start(crop:VideoVisualText.Crop){
        if(!active()||working){close();return};working=true
        show(AlertDialog.Builder(activity).setTitle("Reading visible video text").setMessage("Recognizing cropped original frames locally. Stop keeps existing captions. A running SDK task retains its bitmap and editor work lease until it settles; no further frames are started after Stop.").setNegativeButton("Stop"){_,_->close()}.create())
        val dispatched=runCatching{launch{
            val result=runCatching{if(work!=null)work.invoke(crop,cancelled)else VideoVisualTextReader.read(activity.applicationContext,uri,sourceDuration,from,to,crop){cancelled.get()}}
            activity.runOnUiThread{
                working=false
                if(!active()||cancelled.get()){close();return@runOnUiThread}
                result.onSuccess{cues->if(cues.isEmpty()){message("No visible text was recognized. Existing captions were kept.");close()}else edit(cues)}
                    .onFailure{message("Video text was not applied. Try a sharper, shorter clip or a tighter crop. Existing captions were kept.");close()}
            }
        }}
        if(dispatched.isFailure){working=false;message("Video text processing could not start. Existing captions were kept.");close()}
    }
    private fun edit(captions: List<VideoCaption>) {
        if(!active()){close();return}
        val panel=body()
        panel.note("Correct every OCR reading and timing below. These are approximate ORIGINAL source times, before speed changes. Readings can include logos, omit brief text or be wrong. Boundaries reflect approximate 0.5-second sampled bins. All $existingCount existing captions on this clip will be replaced only after the next full review. Other edits and original files stay. One Undo restores previous captions.")
        val field=EditText(activity).apply {
            contentDescription="Correct OCR caption text and source timestamps";setTextColor(Color.BLACK);minLines=8;setHorizontallyScrolling(false)
            filters=arrayOf(InputFilter.LengthFilter(VideoCaptions.MAX_SRT_BYTES))
            setText(captions.mapIndexed{i,cue->"${i+1}\n${VideoCaptions.formatTime(cue.startMs)} --> ${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}\n"}.joinToString("\n"));panel.addView(this)
        }
        val editor=AlertDialog.Builder(activity).setTitle("Correct visible-text captions").setView(scroll(panel)).setNegativeButton("Keep existing"){_,_->close()}.setPositiveButton("Review all captions",null).create()
        editor.setOnShowListener { editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(dialog!==editor||!active()){close();return@setOnClickListener}
            runCatching {
                VideoCaptions.parseSrt(field.text.toString().toByteArray(Charsets.UTF_8),sourceDuration).also { cues ->
                    require(cues.all{it.startMs>=from&&it.endMs<=to}){"Keep all captions inside the analyzed source range"}
                }
            }.onSuccess{review(it)}.onFailure{field.error=it.message?:"Check caption text and source times"}
        } }
        show(editor)
    }
    private fun review(cues: List<VideoCaption>) {
        if(!active()){close();return}
        val panel=body()
        panel.note("Replace all $existingCount existing captions on this clip with exactly ${cues.size} reviewed captions below. Timing is approximate original-source time; preview afterward to check sync. Other clips, trims, speed, looks, titles, audio and original media stay unchanged. One Undo restores the previous captions. The old subtitle-file binding is removed.")
        for(cue in cues)panel.note("${VideoCaptions.formatTime(cue.startMs)}–${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}\n")
        val confirmed=CheckBox(activity).apply{text="I checked every caption and its timing";isChecked=false;setTextColor(Color.BLACK);panel.addView(this)}
        val review=AlertDialog.Builder(activity).setTitle("Review every OCR caption").setView(scroll(panel))
            .setNegativeButton("Keep existing"){_,_->close()}.setNeutralButton("Correct again",null).setPositiveButton("Apply captions",null).create()
        review.setOnShowListener {
            review.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{if(dialog===review&&active())edit(cues)else close()}
            review.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(dialog!==review||!active()){close();return@setOnClickListener}
                if(!confirmed.isChecked){confirmed.error="Review all captions first";return@setOnClickListener}
                val result=runCatching{apply(cues)}
                close()
                result.onSuccess{committed()}.onFailure{message("Video or session changed. Existing captions were kept.")}
            }
        }
        show(review)
    }
}
