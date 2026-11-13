package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Original-audio sound recognition → explicit suggestions → correction → complete owned review. */
internal class VideoSoundControls(
    private val activity:Activity,private val uri:Uri,private val from:Long,private val to:Long,
    private val sourceDuration:Long,private val original:List<VideoCaption>,
    private val isCurrent:()->Boolean,private val apply:(List<VideoCaption>)->Unit,
    private val committed:()->Unit,private val release:()->Unit,private val message:(String)->Unit,
    private val work:((Boolean,VideoSoundModel.Stop)->List<VideoSoundEvents.Window>)?=null,
    private val launch:((()->Unit)->Unit)={Thread(it,"noble-sound-captions").start()}
){
    private val stop=VideoSoundModel.Stop()
    private var closed=false;private var working=false;private var released=false
    private var dialog:AlertDialog?=null
    private fun active()=!closed&&isCurrent()
    private fun releaseOnce(){if(!released){released=true;release()}}
    fun close(){if(!closed){closed=true;runCatching{stop.cancel()};val old=dialog;dialog=null;old?.dismiss()};if(!working)releaseOnce()}
    private fun show(next:AlertDialog){if(!active()){close();return};val old=dialog;dialog=null;old?.dismiss();dialog=next;next.setOnDismissListener{if(dialog===next)close()};next.show()}
    private fun body()=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(32,20,32,20);setBackgroundColor(Color.WHITE)}
    private fun LinearLayout.note(value:String)=TextView(activity).apply{text=value;setTextColor(Color.BLACK);addView(this)}
    private fun scroll(body:LinearLayout)=ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(body)}
    private fun directory()=File(activity.filesDir,"optional-yamnet-sounds")
    fun open(){
        if(!active()){close();return}
        if(uri.scheme!="content"||from !in 0..Int.MAX_VALUE.toLong()||sourceDuration !in 1..Int.MAX_VALUE.toLong()||to>sourceDuration||to-from !in 500..20000){message("Trim this clip to 0.5–20 source seconds before recognizing sounds.");close();return}
        val panel=body()
        panel.note("Recognize sound categories in this clip's original source audio locally, such as applause, barking or sirens. This is not spoken-word transcription or a safety detector. It ignores playback mute, speed, effects and soundtrack. Mixed sounds can be missed or mislabeled; scores are not calibrated confidence. No automatic choice is made. You choose suggestions, then correct every caption and timing before replacing all ${original.size} existing captions on this clip.")
        panel.note("Optional public setup downloads about16.2MB of YAMNet weights and labels from Hugging Face/CDN. Normal request/IP metadata reaches that host; no audio, media, caption, query or account is uploaded. Downloads may use mobile data. Verified files remain in private app storage, including completed files after stopped setup. Later recognition works offline. Original media and other edits stay unchanged.")
        panel.addView(Button(activity).apply{text="Sound model provenance and licenses";setOnClickListener{notice()}})
        val consent=CheckBox(activity).apply{text="I allow downloading missing or damaged public sound-model files (about16.2MB)";isChecked=false;setTextColor(Color.BLACK);panel.addView(this)}
        val available=VideoSoundStore.present(directory())
        val setup=AlertDialog.Builder(activity).setTitle("AI sound captions · experimental").setView(scroll(panel)).setNegativeButton("Cancel"){_,_->close()}
            .setPositiveButton(if(available)"Recognize locally"else"Set up and recognize",null).apply{if(available)setNeutralButton("Repair sound setup",null)}.create()
        setup.setOnShowListener{
            fun begin(download:Boolean){if(dialog!==setup||!active()){close();return};if(download&&!consent.isChecked){consent.error="Confirm public downloads first";return};start(download)}
            setup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{begin(!available)}
            if(available)setup.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{begin(true)}
        };show(setup)
    }
    private fun notice(){
        if(!active()){close();return}
        val text=runCatching{activity.assets.open("noble_sound_model_notice.txt").use{it.readBytes()}.also{require(it.size in 10000..60000)}.toString(Charsets.UTF_8)}
            .getOrElse{message("Sound-model notice unavailable. Setup was stopped.");close();return}
        val panel=body();panel.note(text);lateinit var next:AlertDialog
        next=AlertDialog.Builder(activity).setTitle("Sound model and labels").setView(scroll(panel)).setNegativeButton("Cancel"){_,_->close()}.setPositiveButton("Back to setup"){_,_->if(dialog===next&&active())open()else close()}.create();show(next)
    }
    private fun start(download:Boolean){
        if(!active()||working){close();return};working=true
        val progress=AlertDialog.Builder(activity).setTitle("Recognizing original sounds").setMessage("Checking public models, decoding original audio and running the local sound model. Nothing is applied. Stop retains the native worker lease until it settles.").setNegativeButton("Stop"){_,_->close()}.create();show(progress)
        val launched=runCatching{launch{
            val result=runCatching{
                if(work!=null)work.invoke(download,stop)else{
                    val folder=VideoSoundStore.obtain(directory(),download,stop)
                    val labels=VideoSoundModel.labels(File(folder,"yamnet_class_map.csv"),stop)
                    val pcm=VideoSpeechAudio.decode(activity,uri,from,to){stop.check();false};stop.check()
                    VideoSoundModel.recognize(File(folder,"yamnet.onnx"),pcm,from,labels,stop)
                }
            }
            activity.runOnUiThread{
                working=false;if(!active()){close();return@runOnUiThread}
                if(runCatching{stop.check()}.isFailure){close();return@runOnUiThread}
                result.onSuccess{windows->runCatching{choose(windows)}.onFailure{message("Sound suggestions could not be reviewed. Existing captions were kept.");close()}}
                    .onFailure{message("No sound captions applied. Check model setup, storage, original audio and the 0.5–20 second range. Existing captions were kept.");close()}
            }
        }}
        if(launched.isFailure){working=false;message("Sound recognition could not start. Existing captions were kept.");close()}
    }
    private fun choose(windows:List<VideoSoundEvents.Window>){
        if(!active()){close();return}
        require(windows.size==VideoSoundEvents.frames(((to-from)*16).toInt())&&windows.first().startMs==from&&windows.last().endMs==to)
        require(windows.zipWithNext().all{(a,b)->a.endMs==b.startMs})
        val panel=body();panel.note("Every analyzed interval is below; all start as Skip. Each model window uses about0.975s of audio with0.48s hops and padding at the end. Assigned intervals are approximate, not exact event boundaries. Top3 labels can all be wrong; scores are model scores, not percentages or calibrated confidence. Only one suggestion per interval can be chosen. Consecutive identical chosen labels merge; skipped intervals remain gaps. Correct names and timings next.")
        val selected=linkedMapOf<Int,Int>()
        for((index,window)in windows.withIndex()){
            require(window.choices.size==3&&window.choices.map{it.index}.toSet().size==3)
            panel.note("${VideoCaptions.formatTime(window.startMs)}–${VideoCaptions.formatTime(window.endMs)}")
            val group=RadioGroup(activity);val mapping=linkedMapOf<Int,Int>()
            val skip=RadioButton(activity).apply{id=View.generateViewId();text="Skip interval ${index+1}";setTextColor(Color.BLACK);group.addView(this)}
            for(choice in window.choices){
                val button=RadioButton(activity).apply{id=View.generateViewId();text="${choice.label} · score ${String.format(java.util.Locale.ROOT,"%.3f",choice.score)}";contentDescription="Interval ${index+1}: ${choice.label}";setTextColor(Color.BLACK)}
                mapping[button.id]=choice.index;group.addView(button)
            }
            group.check(skip.id);group.setOnCheckedChangeListener{_,id->val value=mapping[id];if(value==null)selected.remove(index)else selected[index]=value};panel.addView(group)
        }
        val error=panel.note("")
        val chooser=AlertDialog.Builder(activity).setTitle("Choose sound suggestions").setView(scroll(panel)).setNegativeButton("Keep existing"){_,_->close()}.setPositiveButton("Correct chosen captions",null).create()
        chooser.setOnShowListener{chooser.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(dialog!==chooser||!active()){close();return@setOnClickListener}
            runCatching{VideoSoundEvents.selected(windows,selected.toMap())}.onSuccess{edit(it)}.onFailure{error.text=it.message?:"Choose at least one sound suggestion"}
        }};show(chooser)
    }
    private fun srt(cues:List<VideoCaption>)=cues.mapIndexed{index,cue->"${index+1}\n${VideoCaptions.formatTime(cue.startMs)} --> ${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}"}.joinToString("\n\n")
    private fun edit(cues:List<VideoCaption>){
        if(!active()){close();return};val panel=body()
        panel.note("Correct every sound label and approximate source timestamp. Use nonoverlapping captions inside ${VideoCaptions.formatTime(from)}–${VideoCaptions.formatTime(to)}; up to240characters/three lines per cue. This replaces the whole clip caption track, including existing speech captions; it does not mix tracks. Nothing applies before the next full review. Existing captions:\n\n"+original.joinToString("\n\n"){"${VideoCaptions.formatTime(it.startMs)}–${VideoCaptions.formatTime(it.endMs)}\n${it.text}"})
        val field=EditText(activity).apply{contentDescription="Correct sound caption text and source times";setTextColor(Color.BLACK);minLines=8;setHorizontallyScrolling(false);filters=arrayOf(InputFilter.LengthFilter(VideoCaptions.MAX_SRT_BYTES));setText(srt(cues));panel.addView(this)}
        val editor=AlertDialog.Builder(activity).setTitle("Correct sound captions").setView(scroll(panel)).setNegativeButton("Keep existing"){_,_->close()}.setPositiveButton("Review all captions",null).create()
        editor.setOnShowListener{editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(dialog!==editor||!active()){close();return@setOnClickListener}
            runCatching{
                VideoCaptions.parseSrt(field.text.toString().toByteArray(Charsets.UTF_8),sourceDuration).also{corrected->require(corrected.isNotEmpty()&&corrected.all{it.startMs>=from&&it.endMs<=to}){"Keep every cue inside the analyzed source range"}}
            }.onSuccess{review(it)}.onFailure{field.error=it.message?:"Check sound captions and times"}
        }};show(editor)
    }
    private fun review(cues:List<VideoCaption>){
        if(!active()){close();return};val panel=body()
        panel.note("Replace all ${original.size} existing captions on this clip with exactly ${cues.size} corrected sound captions below. Existing speech/other captions on this clip are replaced, including outside the analyzed range. Original media, other clips, trims, speeds, looks, titles, volumes and soundtrack stay. One Undo restores the previous captions and subtitle-file binding. The obsolete current binding is removed. Preview afterward for readability and sync.")
        for(cue in cues)panel.note("${VideoCaptions.formatTime(cue.startMs)}–${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}\n")
        val confirmed=CheckBox(activity).apply{text="I checked every sound caption, timing and replacement";isChecked=false;setTextColor(Color.BLACK);panel.addView(this)}
        val review=AlertDialog.Builder(activity).setTitle("Review every sound caption").setView(scroll(panel)).setNegativeButton("Keep existing"){_,_->close()}.setNeutralButton("Correct again",null).setPositiveButton("Apply captions",null).create()
        review.setOnShowListener{
            review.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{if(dialog===review&&active())edit(cues)else close()}
            review.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(dialog!==review||!active()){close();return@setOnClickListener}
                if(!confirmed.isChecked){confirmed.error="Review all captions first";return@setOnClickListener}
                val result=runCatching{apply(cues)};close();result.onSuccess{committed()}.onFailure{message("Video or session changed. Existing captions were kept.")}
            }
        };show(review)
    }
}
