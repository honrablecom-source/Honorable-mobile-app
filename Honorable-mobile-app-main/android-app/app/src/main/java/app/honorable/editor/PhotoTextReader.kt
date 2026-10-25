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

/** Owns a private edit snapshot until inference finishes. Never logs or uploads recognized text. */
object PhotoTextReader {
    fun read(activity: Activity, snapshot: Bitmap, finished: (String) -> Unit) {
        val recognizer = try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        } catch (failure: Exception) {
            snapshot.recycle()
            finished("Unable to start text recognition. Try again.")
            return
        } catch (failure: OutOfMemoryError) {
            snapshot.recycle()
            finished("Not enough memory to read this photo. Try a smaller image.")
            return
        }
        try {
            recognizer.process(InputImage.fromBitmap(snapshot, 0))
                .addOnSuccessListener { result ->
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        val text = result.text.trim()
                        if (text.isEmpty()) {
                            finished("No text found. Try a closer, sharper photo. Latin script is supported.")
                        } else {
                            finished("Text recognized on this device. Review it before copying.")
                            val body = TextView(activity).apply {
                                this.text = text
                                textSize = 17f
                                setTextIsSelectable(true)
                                val inset = (24 * resources.displayMetrics.density).toInt()
                                setPadding(inset, inset, inset, inset)
                            }
                            AlertDialog.Builder(activity)
                                .setTitle("Text from your photo")
                                .setView(ScrollView(activity).apply { addView(body) })
                                .setNegativeButton("Close", null)
                                .setPositiveButton("Copy text") { _, _ ->
                                    val clip = ClipData.newPlainText("Text from Noble", text)
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        clip.description.extras = PersistableBundle().apply {
                                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                        }
                                    }
                                    (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(clip)
                                    finished("Text copied. Your photo stays on this device.")
                                }
                                .show()
                        }
                    }
                }
                .addOnFailureListener {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        finished("Text recognition failed. Try again with a sharper photo.")
                    }
                }
                .addOnCompleteListener {
                    try { recognizer.close() } finally { snapshot.recycle() }
                }
        } catch (failure: Exception) {
            recognizer.close()
            snapshot.recycle()
            if (!activity.isFinishing && !activity.isDestroyed) finished("Unable to read this photo. Try again.")
        }
    }
}
