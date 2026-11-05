package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PersistableBundle
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Owns a private edit snapshot until inference finishes. Never logs or uploads recognized text.
 * A null completion clears busy state without publishing a stale status message. */
object PhotoTextReader {
    fun read(activity:Activity,snapshot:Bitmap,isCurrent:()->Boolean,finished:(String?)->Unit) {
        fun current()=!activity.isFinishing&&!activity.isDestroyed&&runCatching{isCurrent()}.getOrDefault(false)
        fun signal(message:String?){finished(if(current())message else null)}
        if(!current()){snapshot.recycle();finished(null);return}
        val recognizer=try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }catch(failure:Exception){snapshot.recycle();signal("Unable to start text recognition. Try again.");return}
        catch(failure:OutOfMemoryError){snapshot.recycle();signal("Not enough memory to read this photo. Try a smaller image.");return}
        fun copy(value:String):Boolean {
            if(!current()){finished(null);return false}
            return runCatching {
                val clip=ClipData.newPlainText("Text from Noble",value)
                if(Build.VERSION.SDK_INT>=33)clip.description.extras=PersistableBundle().apply{putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true)}
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            }.fold({signal("Text copied. Your photo stays on this device.");true},{signal("Could not copy text. Select a smaller passage or try again.");false})
        }
        try {
            recognizer.process(InputImage.fromBitmap(snapshot,0))
                .addOnSuccessListener { result ->
                    if(!current()){finished(null);return@addOnSuccessListener}
                    try {
                        val text=result.text.trim()
                        if(text.isEmpty())signal("No text found. Try a closer, sharper photo. Latin script is supported.")
                        else {
                            signal("Text recognized on this device. Review it before copying.")
                            var dialog:AlertDialog?=null
                            val body=object:TextView(activity){
                                override fun onTextContextMenuItem(id:Int):Boolean {
                                    if(!current()){dialog?.dismiss();finished(null);return true}
                                    if(id==android.R.id.copy){
                                        val from=minOf(selectionStart,selectionEnd);val to=maxOf(selectionStart,selectionEnd)
                                        if(from>=0&&to>from&&to<=text.length)copy(text.substring(from,to))
                                        return true
                                    }
                                    return super.onTextContextMenuItem(id)
                                }
                            }.apply {
                                this.text=text;textSize=17f;setTextIsSelectable(true)
                                val inset=(24*resources.displayMetrics.density).toInt();setPadding(inset,inset,inset,inset)
                            }
                            val review=AlertDialog.Builder(activity).setTitle("Text from your photo")
                                .setView(ScrollView(activity).apply{addView(body)}).setNegativeButton("Close",null)
                                .setPositiveButton("Copy text",null).create()
                            dialog=review
                            review.setOnShowListener{review.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                                if(!current()){review.dismiss();finished(null);return@setOnClickListener}
                                if(copy(text))review.dismiss()
                            }}
                            if(current())review.show()else finished(null)
                        }
                    }catch(failure:Exception){signal("Unable to display recognized text. Try again.")}
                    catch(failure:OutOfMemoryError){signal("Not enough memory to display recognized text. Try a smaller photo.")}
                }
                .addOnFailureListener {signal("Text recognition failed. Try again with a sharper photo.")}
                .addOnCanceledListener {finished(null)}
                .addOnCompleteListener {try{recognizer.close()}finally{snapshot.recycle()}}
        }catch(failure:Exception){try{recognizer.close()}finally{snapshot.recycle()};signal("Unable to read this photo. Try again.")}
        catch(failure:OutOfMemoryError){try{recognizer.close()}finally{snapshot.recycle()};signal("Not enough memory to read this photo. Try a smaller image.")}
    }
}
