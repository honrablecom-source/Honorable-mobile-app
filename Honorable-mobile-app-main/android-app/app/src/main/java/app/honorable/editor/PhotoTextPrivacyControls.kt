package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns a copy of an already-recognized image. Does not run OCR or classify sensitive information. */
class PhotoTextPrivacyControls(
    private val activity: Activity,
    private val input: Input,
    private val isCurrent: () -> Boolean,
    private val finished: (String?) -> Unit,
    private val worker: Executor = Executors.newSingleThreadExecutor(),
    private val share: (File) -> Unit = { file ->
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.creation", file)
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Redacted photo copy", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share redacted copy"))
    },
) : AutoCloseable, Application.ActivityLifecycleCallbacks {
    class Input internal constructor(internal val bitmap: Bitmap, internal val width: Int, internal val height: Int, internal val boxes: List<PhotoTextPrivacy.Box>) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() { if (closed.compareAndSet(false, true) && !bitmap.isRecycled) bitmap.recycle() }
    }
    companion object {
        /** Call while the SDK source is alive. The returned bitmap has independent storage. */
        fun capture(source: Bitmap, boxes: List<PhotoTextPrivacy.Box>, current: () -> Unit): Input {
            current(); require(!source.isRecycled && boxes.size in 1..PhotoTextPrivacy.MAX_REGIONS) { "Use 1–100 detected text regions" }
            val width = source.width; val height = source.height
            val scale = minOf(1.0, PhotoTextPrivacy.MAX_EDGE.toDouble() / maxOf(width, height))
            val copy = Bitmap.createBitmap(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()), Bitmap.Config.ARGB_8888)
            try {
                Canvas(copy).apply { drawColor(Color.WHITE); drawBitmap(source, null, Rect(0, 0, copy.width, copy.height), Paint(Paint.FILTER_BITMAP_FLAG)) }
                current(); return Input(copy, width, height, boxes.toList())
            } catch (failure: Throwable) { copy.recycle(); throw failure }
        }
    }
    private data class Proof(val bytes: ByteArray, val sha: ByteArray, val count: Int, val padding: Int)
    private val closed = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private var registered = false
    private var stage: Any? = null
    private var work: Any? = null
    private var dialog: AlertDialog? = null
    private var review: PhotoTextPrivacy.Review? = null
    private val selected = linkedSetOf<Int>()
    private var padding = 4
    private val pictures = mutableListOf<Pair<ImageView, Bitmap>>()
    private val files = mutableSetOf<File>()
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun live() = !closed.get() && !activity.isFinishing && !activity.isDestroyed && runCatching(isCurrent).getOrDefault(false)
    private fun owns(token: Any) = live() && stage === token && work == null
    private fun text(value: String) = TextView(activity).apply { text = value; setTextColor(Color.BLACK); setPadding(dp(8), dp(8), dp(8), dp(8)) }
    private fun panel() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(8), dp(12), dp(8)) }
    private fun clearDialog() {
        stage = null; dialog?.dismiss(); dialog = null
        pictures.forEach { (view, bitmap) -> view.setImageDrawable(null); if (!bitmap.isRecycled) bitmap.recycle() }; pictures.clear()
    }
    private fun show(value: AlertDialog, token: Any) {
        if (!live()) { close(); return }
        clearDialog(); dialog = value; stage = token
        value.setOnCancelListener { if (stage === token) close() }
        value.setOnDismissListener { if (stage === token) close() }
        value.show(); value.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.WHITE))
        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) value.getButton(which)?.apply { setTextColor(Color.BLACK); minHeight = dp(48) }
    }
    private fun picture(body: LinearLayout, bitmap: Bitmap, token: Any) {
        val image = ImageView(activity).apply { setImageBitmap(bitmap); contentDescription = "Photo preview; scroll horizontally and vertically to inspect the full image"; scaleType = ImageView.ScaleType.FIT_XY }
        val scroll = HorizontalScrollView(activity).apply { addView(image, android.view.ViewGroup.LayoutParams(bitmap.width, bitmap.height)) }
        body.addView(scroll); pictures += image to bitmap
        body.addView(LinearLayout(activity).apply {
            for (zoom in listOf(1, 2, 4)) addView(Button(activity).apply {
                text = "${zoom}×"; contentDescription = "View photo at ${zoom} times"; minHeight = dp(48); setTextColor(Color.BLACK)
                setOnClickListener { if (owns(token)) image.layoutParams = image.layoutParams.apply { width = bitmap.width * zoom; height = bitmap.height * zoom } }
            })
        })
    }
    fun open() {
        if (!live()) { close(); return }
        if (registered || work != null || review != null) return
        registered = true; activity.application.registerActivityLifecycleCallbacks(this)
        job("Preparing detected text regions", { current ->
            val bitmap = input.bitmap; current()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = PhotoTextPrivacy.Source.capture(bitmap.width, bitmap.height, pixels, current)
            PhotoTextPrivacy.Review.create(source, input.width, input.height, input.boxes, current)
        }) { value -> review = value; selection() }
    }
    private fun <T> job(title: String, action: (() -> Unit) -> T, done: (T) -> Unit) {
        if (!live()) { close(); return }
        if (work != null) return
        val token = Any(); work = token; running.set(true)
        val progress = AlertDialog.Builder(activity).setTitle(title).setMessage("Working on this device. Stop keeps the original photo and your edits.")
            .setNegativeButton("Stop") { _, _ -> if (work === token) close() }.create()
        show(progress, token)
        try { worker.execute {
            val began = System.nanoTime(); var checkedAt = Long.MIN_VALUE
            fun current() {
                check(!closed.get() && !Thread.currentThread().isInterrupted) { "Text privacy work stopped" }
                val now = System.nanoTime(); check(now - began < 60_000_000_000L) { "Text privacy work timed out" }
                if (checkedAt == Long.MIN_VALUE || now - checkedAt > 100_000_000L) { check(runCatching(isCurrent).getOrDefault(false)) { "Photo or account changed" }; checkedAt = now }
            }
            val result = try { runCatching { current(); action(::current).also { current() } } }
                finally { input.close(); running.set(false) }
            activity.runOnUiThread {
                if (!live() || work !== token) { close(); return@runOnUiThread }
                work = null; clearDialog()
                result.onSuccess { value -> runCatching { done(value) }.onFailure { closeWith("Unable to prepare the redacted copy. Your photo and edits were kept.") } }
                    .onFailure { closeWith("Unable to prepare the redacted copy. Your photo and edits were kept.") }
            }
        } } catch (_: RuntimeException) { running.set(false); work = null; closeWith("Text privacy work could not start. Your photo was kept.") }
    }
    private fun selection() {
        if (!live()) { close(); return }
        val currentReview = review ?: return
        val token = Any(); val body = panel()
        body.addView(text("Choose text regions to cover. None are selected automatically. OCR can miss text or group unrelated words. This is not a sensitive-data classifier or a guarantee that all private content is hidden."))
        body.addView(text("This white-matted copy is ${currentReview.source.width} × ${currentReview.source.height} pixels, at most 1024 per edge. The full-resolution original and editor state are kept. Numbers mark detected regions; the exported PNG has no number overlays."))
        val display = Bitmap.createBitmap(currentReview.source.width, currentReview.source.height, Bitmap.Config.ARGB_8888).apply { setPixels(currentReview.source.copyPixels(), 0, width, 0, 0, width, height) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 2f; textSize = 18f }
        Canvas(display).apply { for (region in currentReview.regions) {
            val b = region.box; paint.style = Paint.Style.STROKE; drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
            paint.style = Paint.Style.FILL; val label = region.id.toString(); val top = maxOf(0, b.top - 20).toFloat()
            paint.color = Color.WHITE; drawRect(b.left.toFloat(), top, b.left + paint.measureText(label) + 4, top + 22, paint)
            paint.color = Color.BLACK; drawText(label, b.left + 2f, top + 18, paint)
        } }
        // Add after show(), since replacing a dialog releases its old image views.
        val choices = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        for (region in currentReview.regions) choices.addView(CheckBox(activity).apply {
            text = "Cover text region ${region.id}"; contentDescription = text; setTextColor(Color.BLACK); buttonTintList = ColorStateList.valueOf(Color.BLACK); minHeight = dp(48); isChecked = region.id in selected
            setOnCheckedChangeListener { _, checked -> if (owns(token)) { if (checked) selected.add(region.id) else selected.remove(region.id); dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = selected.isNotEmpty() } }
        })
        body.addView(choices)
        body.addView(text("Border around each selected region, in preview pixels"))
        body.addView(RadioGroup(activity).apply {
            for (amount in listOf(0, 4, 8, 16, 24)) addView(RadioButton(activity).apply {
                id = android.view.View.generateViewId(); text = "$amount pixels"; minHeight = dp(48); setTextColor(Color.BLACK); buttonTintList = ColorStateList.valueOf(Color.BLACK); isChecked = amount == padding
                setOnCheckedChangeListener { _, checked -> if (checked && owns(token)) padding = amount }
            })
        })
        val value = AlertDialog.Builder(activity).setTitle("Review detected text").setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Cancel", null).setPositiveButton("Preview covered copy", null).create()
        show(value, token)
        if (!owns(token)) { display.recycle(); return }
        picture(body, display, token)
        value.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { if (owns(token)) close() }
        value.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = selected.isNotEmpty()
            setOnClickListener { if (owns(token) && selected.isNotEmpty()) {
                val ids = selected.toSet(); val border = padding
                job("Encoding your reviewed copy", { current ->
                    val result = currentReview.render(ids, border, current); val bytes = result.png(current)
                    Proof(bytes, MessageDigest.getInstance("SHA-256").digest(bytes), ids.size, border)
                }, ::proof)
            } }
        }
    }
    private fun proof(proof: Proof) {
        if (!live()) { close(); return }
        val token = Any(); val body = panel(); val source = review?.source ?: return
        body.addView(text("Exact PNG: ${source.width} × ${source.height}; ${proof.bytes.size} bytes; ${proof.count} regions covered with ${proof.padding} pixels of border. Pan and zoom to inspect the entire image. Unselected or missed text remains visible. The original is kept."))
        val confirmation = CheckBox(activity).apply {
            text = "I reviewed the whole copy for missed private content"; setTextColor(Color.BLACK); buttonTintList = ColorStateList.valueOf(Color.BLACK); minHeight = dp(48); isChecked = false
        }; body.addView(confirmation)
        val value = AlertDialog.Builder(activity).setTitle("Review flattened PNG").setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Change selection", null).setPositiveButton("Share this PNG", null).create()
        show(value, token)
        if (!owns(token)) return
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(proof.bytes, 0, proof.bytes.size))
        require(bitmap.width == source.width && bitmap.height == source.height)
        picture(body, bitmap, token)
        value.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { if (owns(token)) selection() }
        value.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            confirmation.setOnCheckedChangeListener { _, checked -> isEnabled = checked && owns(token) }
            setOnClickListener { if (owns(token) && confirmation.isChecked) {
                job("Preparing the exact PNG for sharing", { current -> write(proof, current) }) { file ->
                    if (!live()) { close(); return@job }
                    try {
                        verify(proof, file) { check(!closed.get()) }; check(live())
                        synchronized(files) { files.remove(file) }
                        share(file); closeWith("Redacted copy shared. The original and edits were kept.") }
                    catch (_: Exception) { file.delete(); closeWith("Sharing did not open. Your photo and edits were kept.") }
                }
            } }
        }
    }
    private fun write(proof: Proof, current: () -> Unit): File {
        current(); val base = activity.cacheDir.canonicalFile; val dir = File(base, "created-images")
        require(dir.canonicalFile == dir.absoluteFile && (dir.isDirectory || dir.mkdirs())) { "Private copy storage unavailable" }
        val prefix = "noble-text-privacy-"; val cutoff = System.currentTimeMillis() - 86_400_000L
        fun copies() = dir.listFiles()?.filter { it.name.startsWith(prefix) && it.extension == "png" && it.canonicalFile == it.absoluteFile && it.isFile } ?: emptyList()
        copies().filter { it.lastModified() < cutoff }.forEach { current(); it.delete() }
        val old = copies(); require(old.size < 8 && old.sumOf { it.length() } + proof.bytes.size <= 32_000_000L) { "Temporary redacted copies are full" }
        val file = File(dir, "$prefix${UUID.randomUUID()}.png")
        require(file.createNewFile()); synchronized(files) { if (closed.get()) { file.delete(); error("Stopped") }; files += file }
        FileOutputStream(file).use { output ->
            var at = 0
            while (at < proof.bytes.size) { current(); val count = minOf(8192, proof.bytes.size - at); output.write(proof.bytes, at, count); at += count }
            output.flush(); current(); output.fd.sync()
        }
        verify(proof, file, current)
        return file
    }
    private fun verify(proof: Proof, file: File, current: () -> Unit) {
        current(); require(file.canonicalFile == file.absoluteFile && file.isFile && file.length() == proof.bytes.size.toLong())
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer = ByteArray(8192); while (true) { current(); val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        current(); require(MessageDigest.isEqual(proof.sha, digest.digest())) { "Copy changed before sharing" }
    }
    private fun closeWith(message: String?) {
        if (!closed.compareAndSet(false, true)) return
        clearDialog(); review = null; selected.clear()
        if (!running.get()) input.close()
        synchronized(files) { files.forEach { it.delete() }; files.clear() }
        if (registered) { activity.application.unregisterActivityLifecycleCallbacks(this); registered = false }
        (worker as? ExecutorService)?.shutdown()
        finished(if (message != null && runCatching(isCurrent).getOrDefault(false)) message else null)
    }
    override fun close() = closeWith(null)
    override fun onActivityPaused(value: Activity) { if (value === activity) close() }
    override fun onActivityDestroyed(value: Activity) { if (value === activity) close() }
    override fun onActivityCreated(value: Activity, state: Bundle?) {}
    override fun onActivityStarted(value: Activity) {}
    override fun onActivityResumed(value: Activity) {}
    override fun onActivityStopped(value: Activity) {}
    override fun onActivitySaveInstanceState(value: Activity, state: Bundle) {}
}
