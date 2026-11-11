package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.InputFilter
import android.widget.CheckBox
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Complete local translation, text-only correction, full paired review and one source-owned Apply. */
internal class VideoTranslationControls(
    private val activity:Activity,private val original:List<VideoCaption>,private val sourceDuration:Long,
    private val isCurrent:()->Boolean,private val apply:(List<VideoCaption>)->Unit,
    private val committed:()->Unit,private val release:()->Unit,private val message:(String)->Unit,
    private val work:((Boolean,VideoMarianModel.Stop)->List<String>)?=null,
    private val launch:((()->Unit)->Unit)={Thread(it,"noble-caption-translation").start()}
){
    private val stop=VideoMarianModel.Stop()
    private var closed=false
    private var working=false
    private var released=false
    private var dialog:AlertDialog?=null
    private fun active()=!closed&&isCurrent()
    private fun releaseOnce(){if(!released){released=true;release()}}
    fun close(){if(!closed){closed=true;runCatching{stop.cancel()};val old=dialog;dialog=null;old?.dismiss()};if(!working)releaseOnce()}
    private fun show(next:AlertDialog){if(!active()){close();return};val old=dialog;dialog=null;old?.dismiss();dialog=next;next.setOnDismissListener{if(dialog===next)close()};next.show()}
    private fun body()=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(32,20,32,20);setBackgroundColor(Color.WHITE)}
    private fun LinearLayout.note(value:String){addView(TextView(activity).apply{text=value;setTextColor(Color.BLACK)})}
    private fun scroll(body:LinearLayout)=ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(body)}
    private fun directory()=File(activity.filesDir,"optional-marian-en-es")
    fun open(){
        if(!active()){close();return}
        if(runCatching{VideoTranslationPlan.source(original)}.isFailure){message("Add a valid caption track before translating.");close();return}
        val panel=body()
        panel.note("Translate all ${original.size} stored captions on this clip from English to Spanish, including captions outside its current trim. No language detection or other direction is supported. Separate local AI sessions read only caption text, not video/audio. Results can be wrong or mishandle names; correct every result before applying. All exact source timestamps and cue count stay. Leaving the editor discards this draft; existing captions stay until Apply.")
        panel.note("Optional public model setup downloads about116MB from Hugging Face/CDN. Normal request/IP metadata reaches that host; no caption text, personal media, queries or account is sent. Downloads may use mobile data. Verified weights remain in private app storage across sessions, including completed files after stopped setup. Once installed, translation needs no network. Model/license provenance is available below; originals and other edits stay.")
        panel.addView(Button(activity).apply{text="Model provenance and licenses";setOnClickListener{notice()}})
        val english=CheckBox(activity).apply{text="My source caption text is English";isChecked=false;setTextColor(Color.BLACK);panel.addView(this)}
        val consent=CheckBox(activity).apply{text="I allow downloading missing or damaged public model files (about116MB total)";isChecked=false;setTextColor(Color.BLACK);panel.addView(this)}
        val available=VideoMarianStore.present(directory())
        val setup=AlertDialog.Builder(activity).setTitle("Local AI captions → Spanish · experimental").setView(scroll(panel)).setNegativeButton("Cancel"){_,_->close()}
            .setPositiveButton(if(available)"Translate locally"else"Set up and translate",null).apply{if(available)setNeutralButton("Repair model setup",null)}.create()
        setup.setOnShowListener{
            fun begin(download:Boolean){
                if(dialog!==setup||!active()){close();return}
                if(!english.isChecked){english.error="Confirm the supported source language";return}
                if(download&&!consent.isChecked){consent.error="Confirm public model downloads first";return}
                start(download)
            }
            setup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{begin(!available)}
            if(available)setup.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{begin(true)}
        };show(setup)
    }
    private fun notice(){
        if(!active()){close();return}
        val text=runCatching{activity.assets.open("noble_translation_model_notice.txt").use{it.readBytes()}.also{require(it.size in 10000..30000)}.toString(Charsets.UTF_8)}
            .getOrElse{message("Model notice unavailable. Translation setup was stopped.");close();return}
        val panel=body();panel.note(text);lateinit var next:AlertDialog
        next=AlertDialog.Builder(activity).setTitle("Translation model and licenses").setView(scroll(panel)).setNegativeButton("Cancel"){_,_->close()}.setPositiveButton("Back to setup"){_,_->if(dialog===next&&active())open()else close()}.create();show(next)
    }
    private fun start(download:Boolean){
        if(!active()||working){close();return};working=true
        val progress=AlertDialog.Builder(activity).setTitle("Preparing local translation").setMessage("Checking optional models, then translating every caption. Stop keeps all existing captions. Native work retains its resource lease until it settles.").setNegativeButton("Stop"){_,_->close()}.create();show(progress)
        val launched=runCatching{launch{
            val result=runCatching{
                if(work!=null)work.invoke(download,stop)else{
                    val folder=VideoMarianStore.obtain(directory(),download,stop)
                    val joint=VideoMarianStore.vocabulary(folder,stop);stop.check()
                    val tokenizer=VideoMarianTokenizer(File(folder,"source.spm").readBytes(),joint);stop.check()
                    VideoMarianModel.open(folder,tokenizer,joint,stop).use{model->original.mapIndexed{index,cue->
                        stop.check();val translated=model.translate(cue.text,stop)
                        activity.runOnUiThread{if(active()&&working&&dialog===progress)progress.setMessage("Translated ${index+1} of ${original.size} captions locally. Nothing applied yet.")};translated
                    }}
                }
            }
            activity.runOnUiThread{
                working=false
                if(!active()){close();return@runOnUiThread}
                if(runCatching{stop.check()}.isFailure){close();return@runOnUiThread}
                result.onSuccess{translated->runCatching{VideoTranslationPlan.draft(original,translated);edit(translated)}.onFailure{message("Translation draft could not be reviewed. Existing captions were kept.");close()}}
                    .onFailure{message("Translation was not applied. Check model setup, supported English input, storage or caption length. Existing captions were kept.");close()}
            }
        }}
        if(launched.isFailure){working=false;message("Translation could not start. Existing captions were kept.");close()}
    }
    private fun edit(translated: List<String>) {
        if(!active()){close();return}
        val panel=body()
        panel.note("These are automatic English-to-Spanish translations. Check every meaning, name and punctuation. Every original source timestamp and caption must stay; edit text only. Long translations are shown completely and must be shortened to the existing 240-character/three-line cue limit. Nothing applies until the next complete before/after review. Original captions:\n\n"+original.mapIndexed{i,c->"${i+1}. ${c.text}"}.joinToString("\n\n"))
        val field=EditText(activity).apply {
            contentDescription="Correct translated caption text without changing timestamps";setTextColor(Color.BLACK);minLines=8;setHorizontallyScrolling(false)
            filters=arrayOf(InputFilter.LengthFilter(VideoCaptions.MAX_SRT_BYTES))
            setText(VideoTranslationPlan.draft(original,translated));panel.addView(this)
        }
        val editor=AlertDialog.Builder(activity).setTitle("Correct Spanish captions").setView(scroll(panel)).setNegativeButton("Keep existing"){_,_->close()}.setPositiveButton("Review all captions",null).create()
        editor.setOnShowListener { editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(dialog!==editor||!active()){close();return@setOnClickListener}
            runCatching {
                VideoTranslationPlan.corrected(original,field.text.toString(),sourceDuration)
            }.onSuccess{review(it)}.onFailure{field.error=it.message?:"Check caption text and source times"}
        } }
        show(editor)
    }
    private fun review(cues: List<VideoCaption>) {
        if(!active()){close();return}
        val panel=body()
        panel.note("Replace all ${original.size} captions on this clip with exactly ${cues.size} corrected Spanish captions below. Every original source timestamp stays. Other clips, trims, speed, looks, titles, audio and original media stay unchanged. One Undo restores the previous captions and subtitle-file binding. Preview afterward to check readability. The obsolete current subtitle-file binding is removed.")
        for((index,cue)in cues.withIndex())panel.note("${VideoCaptions.formatTime(cue.startMs)}–${VideoCaptions.formatTime(cue.endMs)}\nOriginal: ${original[index].text}\nSpanish: ${cue.text}\n")
        val confirmed=CheckBox(activity).apply{text="I checked every caption and its timing";isChecked=false;setTextColor(Color.BLACK);panel.addView(this)}
        val review=AlertDialog.Builder(activity).setTitle("Review every translated caption").setView(scroll(panel))
            .setNegativeButton("Keep existing"){_,_->close()}.setNeutralButton("Correct again",null).setPositiveButton("Apply captions",null).create()
        review.setOnShowListener {
            review.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{if(dialog===review&&active())edit(cues.map{it.text})else close()}
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
