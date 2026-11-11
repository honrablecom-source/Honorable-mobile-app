package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.text.InputFilter
import android.widget.CheckBox
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Experimental recognition → editable source-time SRT → full exact review → one explicit Apply. */
internal class VideoSpeechControls(
    private val activity: Activity, private val uri: Uri, private val from: Long, private val to: Long,
    private val sourceDuration: Long, private val existingCount: Int,
    private val isCurrent: () -> Boolean, private val apply: (List<VideoCaption>) -> Unit,
    private val committed: () -> Unit, private val release: () -> Unit, private val message: (String) -> Unit,
    private val work: ((Boolean, VideoSpeechModel.Stop) -> VideoSpeechCaptions.Draft)? = null,
    private val launch: ((() -> Unit) -> Unit) = { Thread(it,"noble-speech-captions").start() }
) {
    private val stop = VideoSpeechModel.Stop()
    private var closed = false
    private var working = false
    private var released = false
    private var dialog: AlertDialog? = null
    private fun active() = !closed && isCurrent()
    private fun releaseOnce() { if (!released) { released = true; release() } }
    fun close() {
        if (!closed) {
            closed = true; runCatching { stop.cancel() }
            val old = dialog; dialog = null; old?.dismiss()
        }
        if (!working) releaseOnce()
    }
    private fun show(next: AlertDialog) {
        if (!active()) { close(); return }
        val old = dialog; dialog = null; old?.dismiss(); dialog = next
        next.setOnDismissListener { if (dialog === next) close() }
        next.show()
    }
    private fun body() = LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(32,20,32,20);setBackgroundColor(Color.WHITE) }
    private fun LinearLayout.note(value: String) { addView(TextView(activity).apply { text=value;setTextColor(Color.BLACK) }) }
    private fun scroll(body: LinearLayout) = ScrollView(activity).apply { setBackgroundColor(Color.WHITE);addView(body) }

    fun open() {
        if (!active()) { close(); return }
        if (from < 0 || to > sourceDuration || to - from !in 500..20_000 || uri.scheme != "content") {
            message("Trim the selected clip to 0.5–20 source seconds before generating speech captions.");close();return
        }
        val panel=body()
        panel.note("Experimental English speech recognition for the selected original audio (${VideoTrimRange.format(from)}–${VideoTrimRange.format(to)} source seconds). It can miss or invent words. Music, noise, accents and synthetic voices can produce major errors. No punctuation or exact word alignment. Correct text and approximate timings before applying. Playback mute, speed, effects and soundtrack do not alter the analyzed source audio.")
        panel.note("Optional 95.3 MB Wav2Vec2 English model (Meta, converted by Xenova; Apache 2.0). Download goes directly to Hugging Face/CDN, which receives the normal network request and IP address. No video, audio, transcript, search query or account is sent. Once installed, recognition runs locally without a microphone or upload. A download may use mobile data. Model weights remain in private app storage across editor sessions.")
        panel.addView(Button(activity).apply{text="Model provenance and license";setOnClickListener{showModelNotice()}})
        val consent=CheckBox(activity).apply { text="I allow the public 95.3 MB model download";isChecked=false;setTextColor(Color.BLACK);panel.addView(this) }
        val available=VideoSpeechModelStore.model(File(activity.filesDir,"optional-speech")).isFile
        val setup=AlertDialog.Builder(activity).setTitle("AI speech captions · experimental").setView(scroll(panel))
            .setNegativeButton("Cancel") { _,_->close() }.setPositiveButton(if(available)"Use saved model" else "Download model",null)
            .apply { if(available)setNeutralButton("Download again",null) }.create()
        setup.setOnShowListener {
            setup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(dialog!==setup||!active()){close();return@setOnClickListener}
                if(!available&&!consent.isChecked){consent.error="Confirm the model download first";return@setOnClickListener}
                start(download = !available)
            }
            if(available)setup.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if(dialog!==setup||!active()){close();return@setOnClickListener}
                if(!consent.isChecked){consent.error="Confirm the model download first";return@setOnClickListener}
                start(download=true)
            }
        }
        show(setup)
    }
    private fun showModelNotice() {
        if(!active()){close();return}
        val notice=runCatching{activity.assets.open("noble_speech_model_notice.txt").use{input->
            val bytes=input.readBytes();require(bytes.size in 10000..24000);bytes.toString(Charsets.UTF_8)
        }}.getOrElse{message("Model notice is unavailable. Speech setup was stopped.");close();return}
        val panel=body();panel.note(notice)
        lateinit var license:AlertDialog
        license=AlertDialog.Builder(activity).setTitle("Speech model and license").setView(scroll(panel))
            .setNegativeButton("Cancel"){_,_->close()}.setPositiveButton("Back to setup"){_,_->if(dialog===license&&active())open()else close()}.create()
        show(license)
    }
    private fun start(download: Boolean) {
        if(!active()||working){close();return}
        working=true
        show(AlertDialog.Builder(activity).setTitle(if(download)"Setting up speech model" else "Recognizing original audio")
            .setMessage("Processing the selected source audio locally. First setup downloads public weights only. Stop keeps all captions unchanged; native work may take a moment to settle.")
            .setNegativeButton("Stop"){_,_->close()}.create())
        val dispatched=runCatching { launch {
            val result=runCatching {
                if(work!=null)work.invoke(download,stop) else {
                    val model=VideoSpeechModelStore.obtain(File(activity.filesDir,"optional-speech"),download,stop)
                    val pcm=VideoSpeechAudio.decode(activity.applicationContext,uri,from,to){stop.isCancelled()}
                    VideoSpeechModel.recognize(model,pcm,from,stop)
                }
            }
            activity.runOnUiThread {
                working=false
                if(!active()||stop.isCancelled()){close();return@runOnUiThread}
                result.onSuccess { draft ->
                    if(draft.captions.isEmpty()){message("No speech captions were recognized. Existing captions were kept.");close()}
                    else edit(draft.captions)
                }.onFailure { message("Speech captions were not applied. Try a shorter range or download the model again.");close() }
            }
        } }
        if(dispatched.isFailure){working=false;message("Speech processing could not start. Existing captions were kept.");close()}
    }
    private fun edit(captions: List<VideoCaption>) {
        if(!active()){close();return}
        val panel=body()
        panel.note("Correct every recognized word and timing below. These are approximate ORIGINAL source times, before speed changes. English uppercase output is unpunctuated and may be wrong. All $existingCount existing captions on this clip will be replaced only after the next full review. Other edits and original files stay. One Undo restores previous captions.")
        val field=EditText(activity).apply {
            contentDescription="Correct AI caption text and source timestamps";setTextColor(Color.BLACK);minLines=8;setHorizontallyScrolling(false)
            filters=arrayOf(InputFilter.LengthFilter(VideoCaptions.MAX_SRT_BYTES))
            setText(captions.mapIndexed{i,cue->"${i+1}\n${VideoCaptions.formatTime(cue.startMs)} --> ${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}\n"}.joinToString("\n"));panel.addView(this)
        }
        val editor=AlertDialog.Builder(activity).setTitle("Correct AI captions").setView(scroll(panel)).setNegativeButton("Keep existing"){_,_->close()}.setPositiveButton("Review all captions",null).create()
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
        val review=AlertDialog.Builder(activity).setTitle("Review every AI caption").setView(scroll(panel))
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
