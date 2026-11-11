package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Owns the SDK snapshot until inference completes; retains only a separate bounded redaction copy. */
object PhotoTextReader {
    // Public entry ABI retained for both editor hosts and the existing frozen integration boundary.
    fun read(activity: Activity, snapshot: Bitmap, isCurrent: () -> Boolean, finished: (String?) -> Unit) {
        fun current() = !activity.isFinishing && !activity.isDestroyed && runCatching(isCurrent).getOrDefault(false)
        fun signal(message: String?) { finished(if (current()) message else null) }
        if (!current()) { snapshot.recycle(); finished(null); return }
        val recognizer = try { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
        catch (_: Exception) { snapshot.recycle(); signal("Unable to start text recognition. Try again."); return }
        catch (_: OutOfMemoryError) { snapshot.recycle(); signal("Not enough memory to read this photo. Try a smaller image."); return }
        try {
            recognizer.process(InputImage.fromBitmap(snapshot, 0))
                .addOnSuccessListener { result ->
                    if (!current()) { finished(null); return@addOnSuccessListener }
                    try {
                        val text = result.text.trim()
                        if (text.isEmpty()) signal("No text found. Try a closer, sharper photo. Latin script is supported.")
                        else {
                            // Real SDK geometry, no fake/heuristic detections. 101 disables redaction;
                            // dense results are never silently reduced to the first 100 regions.
                            val boxes = result.textBlocks.asSequence().flatMap { it.lines.asSequence() }.mapNotNull { line ->
                                line.boundingBox?.takeIf { it.left < it.right && it.top < it.bottom }?.let { PhotoTextPrivacy.Box(it.left, it.top, it.right, it.bottom) }
                            }.take(PhotoTextPrivacy.MAX_REGIONS + 1).toList()
                            showRecognized(activity, snapshot, text, boxes, ::current, ::signal)
                        }
                    } catch (_: Exception) { signal("Unable to display recognized text. Try again.") }
                    catch (_: OutOfMemoryError) { signal("Not enough memory to display recognized text. Try a smaller photo.") }
                }
                .addOnFailureListener { signal("Text recognition failed. Try again with a sharper photo.") }
                .addOnCanceledListener { finished(null) }
                .addOnCompleteListener { try { recognizer.close() } finally { snapshot.recycle() } }
        } catch (_: Exception) { try { recognizer.close() } finally { snapshot.recycle() }; signal("Unable to read this photo. Try again.") }
        catch (_: OutOfMemoryError) { try { recognizer.close() } finally { snapshot.recycle() }; signal("Not enough memory to read this photo. Try a smaller image.") }
    }

    /** Presentation boundary for testing synthetic detections; production callers come only from real SDK success above. */
    internal fun showRecognized(activity: Activity, snapshot: Bitmap, text: String, boxes: List<PhotoTextPrivacy.Box>, isCurrent: () -> Boolean, finished: (String?) -> Unit,
        openPrivacy: (PhotoTextPrivacyControls.Input, () -> Boolean, (String?) -> Unit) -> Unit = { owned, current, signal -> PhotoTextPrivacyControls(activity, owned, current, signal).open() }) {
        fun current() = !activity.isFinishing && !activity.isDestroyed && runCatching(isCurrent).getOrDefault(false)
        fun signal(message: String?) { finished(if (current()) message else null) }
        if (!current()) { finished(null); return }
        var privacyInput = if (boxes.size in 1..PhotoTextPrivacy.MAX_REGIONS) runCatching {
            PhotoTextPrivacyControls.capture(snapshot, boxes) { check(current()) { "Photo changed" } }
        }.getOrNull() else null
        if (!current()) { privacyInput?.close(); finished(null); return }
        var dialog: AlertDialog? = null
        var registered = false
        val observer = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(value: Activity) { if (value === activity) dialog?.dismiss() }
            override fun onActivityDestroyed(value: Activity) { if (value === activity) dialog?.dismiss() }
            override fun onActivityCreated(value: Activity, state: Bundle?) {}
            override fun onActivityStarted(value: Activity) {}
            override fun onActivityResumed(value: Activity) {}
            override fun onActivityStopped(value: Activity) {}
            override fun onActivitySaveInstanceState(value: Activity, state: Bundle) {}
        }
        fun release() { privacyInput?.close(); privacyInput = null; if (registered) { activity.application.unregisterActivityLifecycleCallbacks(observer); registered = false } }
        fun copy(value: String): Boolean {
            if (!current()) { dialog?.dismiss(); finished(null); return false }
            return runCatching {
                val clip = ClipData.newPlainText("Text from Noble", value)
                if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            }.fold({ signal("Text copied. Your photo stays on this device."); true }, { signal("Could not copy text. Select a smaller passage or try again."); false })
        }
        try {
            val body = object : TextView(activity) {
                override fun onTextContextMenuItem(id: Int): Boolean {
                    if (!current()) { dialog?.dismiss(); finished(null); return true }
                    if (id == android.R.id.copy) {
                        val from = minOf(selectionStart, selectionEnd); val to = maxOf(selectionStart, selectionEnd)
                        if (from >= 0 && to > from && to <= text.length) copy(text.substring(from, to))
                        return true
                    }
                    return super.onTextContextMenuItem(id)
                }
            }.apply { this.text = text; textSize = 17f; setTextIsSelectable(true); val inset = (24 * resources.displayMetrics.density).toInt(); setPadding(inset, inset, inset, inset) }
            val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; addView(body); addView(TextView(activity).apply {
                this.text = if (privacyInput == null) "Text redaction needs 1–100 detected regions and a private image copy. Try a smaller crop. Copy text is still available."
                    else "Review text redaction to choose detected regions and share a flattened copy. OCR can miss text. The original and edits stay unchanged."
                setPadding(24, 8, 24, 16)
            }) }
            val builder = AlertDialog.Builder(activity).setTitle("Text from your photo").setView(ScrollView(activity).apply { addView(content) })
                .setNegativeButton("Close", null).setPositiveButton("Copy text", null)
            if (privacyInput != null) builder.setNeutralButton("Review text redaction", null)
            val review = builder.create(); dialog = review
            review.setOnDismissListener { release() }
            review.setOnShowListener {
                review.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (!current()) { review.dismiss(); finished(null); return@setOnClickListener }
                    if (copy(text)) review.dismiss()
                }
                review.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    if (!current()) { review.dismiss(); finished(null); return@setOnClickListener }
                    val owned = privacyInput ?: return@setOnClickListener
                    privacyInput = null; review.dismiss()
                    try { openPrivacy(owned, ::current, ::signal) }
                    catch (_: Exception) { owned.close(); signal("Unable to open text redaction. Your photo was kept.") }
                    catch (_: OutOfMemoryError) { owned.close(); signal("Not enough memory for text redaction. Try a smaller photo.") }
                }
            }
            if (current()) {
                activity.application.registerActivityLifecycleCallbacks(observer); registered = true
                review.show(); signal("Text recognized on this device. Review it before copying.")
            } else { release(); finished(null) }
        } catch (failure: Throwable) { release(); throw failure }
    }
}
