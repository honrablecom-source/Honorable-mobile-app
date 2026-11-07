package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** One foreground review lease; no provider, media, network or grant I/O. */
internal class VideoCaptionSyncControls(private val activity:Activity,private val clips:List<VideoSegment>,private val index:Int,private val durationMs:Long,private val music:VideoSoundtrack?,
    private val isCurrent:()->Boolean,private val apply:(VideoCaptionSync)->Unit,private val committed:()->Unit,private val release:()->Unit,private val message:(String)->Unit){
    private var closed=false
    private var token:Any?=null
    private var dialog:AlertDialog?=null
    private fun owns(value:Any)=!closed&&token===value
    private fun active()=!closed&&isCurrent()
    fun close(){if(closed)return;closed=true;token=null;val old=dialog;dialog=null;old?.dismiss();release()}
    private fun show(value:AlertDialog,next:Any){
        if(!active()){close();return};token=null;dialog?.dismiss();dialog=value;token=next
        value.setOnCancelListener{if(owns(next))close()};value.show()
    }
    fun open(){
        if(!active()){close();return}
        val cues=clips[index].captions
        if(cues.size<2){message("Add at least two captions to synchronize their timing.");close();return}
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(32,16,32,16)}
        fun note(text:String){body.addView(TextView(activity).apply{this.text=text;setTextColor(Color.BLACK)})}
        note("Match the first and last caption starts to the original video, before trims or speed changes. Every cue shifts and stretches evenly; text and other clips stay. Cues outside the trim stay stored. Review before applying; one Undo restores timing. This does not detect speech or change video speed.")
        fun anchor(label:String,cue:VideoCaption):EditText {
            note("$label · currently ${VideoCaptions.formatTime(cue.startMs)}\n${cue.text.take(96)}")
            note("Matching original-video start in seconds (up to 3 decimals)")
            return EditText(activity).apply{contentDescription="$label matching source seconds";setSingleLine(true);setTextColor(Color.BLACK);inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(InputFilter.LengthFilter(12));setText(VideoTrimRange.format(cue.startMs));body.addView(this)}
        }
        val first=anchor("First caption",cues.first());val last=anchor("Last caption",cues.last());val next=Any()
        val input=AlertDialog.Builder(activity).setTitle("Synchronize clip ${index+1} subtitles").setView(ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Keep timing"){_,_->if(owns(next))close()}.setPositiveButton("Review timing",null).create()
        input.setOnShowListener{input.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!owns(next))return@setOnClickListener
            if(!active()){close();return@setOnClickListener}
            runCatching{VideoCaptionSync.prepare(clips,index,durationMs,VideoCaptions.seconds(first.text.toString()),VideoCaptions.seconds(last.text.toString()),music){check(active()){"Video or session changed"}}}
                .onSuccess{plan->if(!plan.changed)last.error="These anchors keep the current timing. Nothing changed."else review(plan)}
                .onFailure{last.error=it.message?:"Check source times"}
        }}
        show(input,next)
    }
    private fun review(plan:VideoCaptionSync){
        if(!active()){close();return}
        val next=Any();val sample=plan.before.indices.take(6).joinToString("\n\n"){i->val old=plan.before[i];val cue=plan.after[i];"Caption ${i+1}: ${VideoCaptions.formatTime(old.startMs)}–${VideoCaptions.formatTime(old.endMs)} → ${VideoCaptions.formatTime(cue.startMs)}–${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}"}
        val extra=if(plan.after.size>6)"\n\nPreview shows 6 of ${plan.after.size} included cues."else""
        val body=TextView(activity).apply{setTextColor(Color.BLACK);setPadding(32,16,32,16);text="${plan.changedCues} of ${plan.after.size} cues change on clip ${index+1}. First/last starts become ${VideoCaptions.formatTime(plan.firstMs)} / ${VideoCaptions.formatTime(plan.lastMs)} in the original source. All boundaries round to the nearest source millisecond; no cues are dropped or clamped. Text, other clips, music, trims, speed and original media stay. Preview to check sync. One Undo restores prior timing.\n\n"+sample+extra;contentDescription="Subtitle timing change review"}
        show(AlertDialog.Builder(activity).setTitle("Apply synchronized subtitle timing?").setView(ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Keep timing"){_,_->if(owns(next))close()}.setPositiveButton("Apply timing"){_,_->
                if(!owns(next))return@setPositiveButton
                if(!active()){close();return@setPositiveButton}
                token=null
                val result=runCatching{apply(plan)};close()
                result.onSuccess{committed()}.onFailure{message("Timing was not applied: ${it.message?:"Review the current clip again"}")}
            }.create(),next)
    }
}
