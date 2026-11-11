package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A separately reviewed PNG copy; never mutates the editor or writes to the source URI. */
class PhotoNeuralUpscaleControls(
    private val activity: Activity,
    private val worker: Executor,
    private val prepareInput: (() -> Unit) -> Bitmap,
    private val isCurrent: () -> Boolean,
    private val requireWorkerCurrent: () -> Unit,
    private val share: (File) -> Unit,
    private val message: (String) -> Unit,
    private val released: () -> Unit,
    private val reconstruct: (PhotoNeuralUpscale.Source, PhotoNeuralModel.Cancellation, () -> Unit, (Int, Int) -> Unit) -> PhotoNeuralUpscale.Result = { source, cancellation, current, phase ->
        PhotoNeuralModel.open(activity, cancellation, current).use { model -> PhotoNeuralUpscale.reconstruct(source, model, current, phase) }
    },
) : AutoCloseable, Application.ActivityLifecycleCallbacks {
    private data class Prepared(val file: File, val sha: ByteArray, val bytes: Long, val sourceWidth: Int, val sourceHeight: Int, val preview: Bitmap)
    private val closed = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val releaseOnce = AtomicBoolean(false)
    private val cancellation = PhotoNeuralModel.Cancellation()
    private val files = mutableSetOf<File>()
    private var registered = false
    private var stage: Any? = null
    @Volatile private var work: Any? = null
    private var dialog: AlertDialog? = null
    private var prepared: Prepared? = null
    private var picture: ImageView? = null

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun live() = !closed.get() && !activity.isDestroyed && !activity.isFinishing && runCatching(isCurrent).getOrDefault(false)
    private fun owns(token: Any) = live() && stage === token && work == null
    private fun panel() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(8), dp(12), dp(8)) }
    private fun text(value: String) = TextView(activity).apply { text = value; setTextColor(Color.BLACK); setPadding(dp(8), dp(8), dp(8), dp(8)) }
    private fun clearDialog() { stage = null; dialog?.dismiss(); dialog = null; picture?.setImageDrawable(null); picture = null }
    private fun show(value: AlertDialog, token: Any) {
        if (!live()) { close(); return }
        clearDialog(); dialog = value; stage = token
        value.setOnCancelListener { if (stage === token) close() }
        value.setOnDismissListener { if (stage === token) close() }
        value.show(); value.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.WHITE))
        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) value.getButton(which)?.apply { setTextColor(Color.BLACK); minHeight = dp(48) }
    }

    fun open() {
        if (!live()) { close(); return }
        if (registered) return
        registered = true; activity.application.registerActivityLifecycleCallbacks(this)
        setup()
    }

    private fun setup() {
        val token = Any(); val body = panel()
        body.addView(text("Create an experimental AI copy of the complete edited preview. Larger previews are fitted within 1024 pixels per edge before inference; nothing is cropped. AI doubles this working copy's width and height, up to 2048 per edge. This is not a 2× enlargement of the full-resolution original. Original media and your current edit stay unchanged. Transparency is flattened onto white."))
        body.addView(text("The bundled Swin2SR model runs on this device without uploading your image or query. AI estimates detail and can change faces, letters and patterns. Large working images may take several minutes or exceed device memory. Stop discards this attempt. Review the full output before saving or sharing; improvement is not guaranteed."))
        val value = AlertDialog.Builder(activity).setTitle("AI upscale copy · experimental").setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Cancel", null).setNeutralButton("Model notice", null).setPositiveButton("Create 2× copy", null).create()
        show(value, token)
        value.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { if (owns(token)) close() }
        value.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { if (owns(token)) notice() }
        value.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            if (!owns(token)) return@setOnClickListener
            job("Creating local AI copy", { current -> create(current) }, { value -> prepared = value; review(value) }, { value -> value.preview.recycle(); value.file.delete() })
        }
    }

    private fun notice() {
        val token = Any(); val body = panel()
        val notice = runCatching {
            activity.assets.open("neural-upscale/NOTICE.txt").bufferedReader().use { it.readText() } + "\n\n" +
                activity.assets.open("neural-upscale/LICENSE-APACHE-2.0.txt").bufferedReader().use { it.readText() }
        }.getOrElse { "The bundled model notice could not be read. Cancel and check the app installation." }
        body.addView(text(notice))
        val value = AlertDialog.Builder(activity).setTitle("Swin2SR model notice").setView(ScrollView(activity).apply { addView(body) }).setNegativeButton("Cancel", null).setPositiveButton("Back", null).create()
        show(value, token)
        value.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { if (owns(token)) close() }
        value.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { if (owns(token)) setup() }
    }

    private fun <T> job(title: String, action: (() -> Unit) -> T, done: (T) -> Unit, discard: (T) -> Unit = {}) {
        if (!live() || !running.compareAndSet(false, true)) return
        val token = Any(); work = token
        val progress = AlertDialog.Builder(activity).setTitle(title).setMessage("Working on this device. Stop keeps your photo and edits. Native model setup may take time to settle after Stop.")
            .setNegativeButton("Stop", null).create()
        show(progress, token)
        progress.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { if (work === token) close() }
        try { worker.execute {
            val began = System.nanoTime()
            val watchdog = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "noble-upscale-stop").apply { isDaemon = true } }
            val timeout = 900_000_000_000L
            fun current() {
                cancellation.check(); check(!closed.get() && work === token) { "AI upscale stopped" }
                check(System.nanoTime() - began < timeout) { "AI upscale timed out" }
                requireWorkerCurrent()
            }
            watchdog.scheduleAtFixedRate({
                if (closed.get() || System.nanoTime() - began >= timeout || runCatching(requireWorkerCurrent).isFailure) runCatching { cancellation.stop() }
            }, 250, 250, TimeUnit.MILLISECONDS)
            val result = try { runCatching {
                current()
                val value = action(::current)
                try { current(); value } catch (failure: Throwable) { runCatching { discard(value) }; throw failure }
            } }
                finally { watchdog.shutdownNow(); running.set(false) }
            activity.runOnUiThread {
                if (!live() || work !== token) {
                    result.onSuccess { runCatching { discard(it) } }
                    close(); maybeRelease(); return@runOnUiThread
                }
                work = null; clearDialog()
                result.onSuccess { value -> runCatching { done(value) }.onFailure { runCatching { discard(value) }; fail() } }.onFailure { fail() }
            }
        } } catch (_: RuntimeException) { running.set(false); work = null; fail() }
    }

    private fun temporary(current: () -> Unit): File = synchronized(files) {
        current()
        val root = activity.cacheDir.canonicalFile; val directory = File(root, "created-images")
        require(directory.canonicalFile == directory.absoluteFile && directory.parentFile?.canonicalFile == root)
        require(directory.isDirectory || directory.mkdirs()) { "Private image storage is unavailable" }
        val cutoff = System.currentTimeMillis() - 86_400_000L
        val previous = directory.listFiles()?.filter { it.name.startsWith("noble-upscale-") && it.extension == "png" && it.isFile && it.canonicalFile == it.absoluteFile }.orEmpty()
        previous.filter { it.lastModified() < cutoff && it !in files }.forEach { it.delete() }
        val retained = previous.filter { it.exists() }
        require(retained.size < 4 && retained.sumOf { it.length() } + MAX_BYTES <= 64_000_000L) { "Temporary AI image storage is full" }
        current()
        File.createTempFile("noble-upscale-", ".png", directory).canonicalFile.also { files += it }
    }

    private fun create(current: () -> Unit): Prepared {
        current()
        val bitmap = prepareInput(current)
        val source = try {
            current(); require(!bitmap.isRecycled && bitmap.width in 1..PhotoNeuralUpscale.MAX_EDGE && bitmap.height in 1..PhotoNeuralUpscale.MAX_EDGE) { "Working image exceeds the AI size limit" }
            val input = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(input, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            PhotoNeuralUpscale.Source.capture(bitmap.width, bitmap.height, input, current)
        } finally { if (!bitmap.isRecycled) bitmap.recycle() }
        val result = reconstruct(source, cancellation, current) { index, total ->
            val token = work
            activity.runOnUiThread { if (token != null && work === token && live()) dialog?.setMessage("Reconstructing image region $index of $total on this device. Stop keeps your photo and edits.") }
        }
        current()
        require(result.width == source.width * 2 && result.height == source.height * 2 && result.sourceFingerprint == source.fingerprint) { "AI copy does not match this image" }
        val pixels = result.pixels()
        val file = temporary(current)
        val encoded = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
        try {
            encoded.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
            FileOutputStream(file).use { output ->
                var count = 0L
                val bounded = object : OutputStream() {
                    override fun write(value: Int) { current(); require(++count <= MAX_BYTES) { "AI PNG exceeds the safe size limit" }; output.write(value) }
                    override fun write(value: ByteArray, offset: Int, length: Int) { current(); require(length >= 0 && count + length <= MAX_BYTES) { "AI PNG exceeds the safe size limit" }; output.write(value, offset, length); count += length }
                }
                check(encoded.compress(Bitmap.CompressFormat.PNG, 100, bounded)) { "AI PNG encoding failed" }
                output.fd.sync()
            }
            current()
            val preview = requireNotNull(BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 })) { "AI PNG could not be reviewed" }
            try {
                require(preview.width == result.width && preview.height == result.height) { "AI PNG dimensions changed" }
                val decoded = IntArray(pixels.size)
                preview.getPixels(decoded, 0, preview.width, 0, 0, preview.width, preview.height)
                for (y in 0 until result.height) {
                    current()
                    for (x in 0 until result.width) require(decoded[y * result.width + x] == pixels[y * result.width + x]) { "AI PNG pixels changed" }
                }
                val proof = Prepared(file, digest(file, current), file.length(), source.width, source.height, preview)
                verify(proof, current); return proof
            } catch (failure: Throwable) { preview.recycle(); throw failure }
        } finally { encoded.recycle() }
    }

    private fun review(value: Prepared) {
        val token = Any(); val body = panel()
        body.addView(text("Working image ${value.sourceWidth} × ${value.sourceHeight} → AI PNG ${value.preview.width} × ${value.preview.height}. This is the exact PNG copy that will be shared. Pan horizontally and vertically to inspect all of it; use zoom to check faces, lettering and patterns. AI can invent or change detail. Original media and current edits remain unchanged."))
        val image = ImageView(activity).apply { setImageBitmap(value.preview); scaleType = ImageView.ScaleType.FIT_XY; contentDescription = "Complete AI upscale PNG; pan and zoom to review every part" }
        body.addView(HorizontalScrollView(activity).apply { addView(image, android.view.ViewGroup.LayoutParams(value.preview.width, value.preview.height)) })
        body.addView(LinearLayout(activity).apply {
            for (zoom in listOf(1, 2, 4)) addView(Button(activity).apply {
                text = "${zoom}×"; minHeight = dp(48); setTextColor(Color.BLACK)
                setOnClickListener { if (owns(token)) image.layoutParams = image.layoutParams.apply { width = value.preview.width * zoom; height = value.preview.height * zoom } }
            })
        })
        val consent = CheckBox(activity).apply { text = "I reviewed the complete AI copy and want to share it"; isChecked = false; minHeight = dp(48); setTextColor(Color.BLACK); buttonTintList = ColorStateList.valueOf(Color.BLACK) }
        body.addView(consent)
        body.addView(text("The receiving app can retain this PNG. The PNG contains rendered RGB pixels, without original EXIF, filename/URI, layers or editor history. Save it using the app you choose. Shared temporary copies can expire after a day."))
        val view = AlertDialog.Builder(activity).setTitle("Review AI upscale copy").setView(ScrollView(activity).apply { addView(body) }).setNegativeButton("Discard", null).setPositiveButton("Save or share PNG", null).create()
        show(view, token)
        if (!owns(token)) return
        picture = image
        view.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { if (owns(token)) close() }
        view.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isEnabled = false
            consent.setOnCheckedChangeListener { _, checked -> if (owns(token)) isEnabled = checked }
            setOnClickListener {
                if (!owns(token) || !consent.isChecked || prepared !== value) return@setOnClickListener
                isEnabled = false
                job("Checking reviewed PNG", { current -> verify(value, current); value }, { checked ->
                    if (!live() || prepared !== checked) { close(); return@job }
                    // Recheck the exact reviewed bytes immediately before transferring ownership.
                    verify(checked) { check(live()); requireWorkerCurrent() }
                    synchronized(files) { check(files.remove(checked.file)) }
                    runCatching { share(checked.file) }.onFailure { checked.file.delete(); if (live()) message("The AI copy could not be shared. Your photo and edits were kept.") }
                    close()
                })
            }
        }
    }

    private fun digest(file: File, current: () -> Unit): ByteArray {
        current(); require(file.isFile && file.length() in 1..MAX_BYTES) { "AI PNG is unavailable" }
        val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
        file.inputStream().use { stream ->
            val buffer = ByteArray(32768)
            while (true) { current(); val read = stream.read(buffer); if (read < 0) break; require(read > 0 && count + read <= MAX_BYTES); count += read; digest.update(buffer, 0, read) }
        }
        current(); return digest.digest()
    }
    private fun verify(value: Prepared, current: () -> Unit) { current(); require(value.file.length() == value.bytes && MessageDigest.isEqual(digest(value.file, current), value.sha)) { "Reviewed AI PNG changed" }; current() }
    private fun fail() { if (live()) message("AI upscaling could not finish. Your photo and edits were kept; try a smaller working image or another device."); close(); maybeRelease() }
    private fun maybeRelease() { if (closed.get() && !running.get() && releaseOnce.compareAndSet(false, true)) released() }
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { cancellation.stop() }; work = null; clearDialog()
            prepared?.preview?.let { if (!it.isRecycled) it.recycle() }; prepared = null
            synchronized(files) { files.forEach { runCatching { it.delete() } }; files.clear() }
            if (registered) activity.application.unregisterActivityLifecycleCallbacks(this)
        }
        maybeRelease()
    }
    override fun onActivityPaused(value: Activity) { if (value === activity) close() }
    override fun onActivityDestroyed(value: Activity) { if (value === activity) close() }
    override fun onActivityCreated(value: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(value: Activity) = Unit
    override fun onActivityResumed(value: Activity) = Unit
    override fun onActivityStopped(value: Activity) = Unit
    override fun onActivitySaveInstanceState(value: Activity, state: Bundle) = Unit
    private companion object { const val MAX_BYTES = 16_000_000L }
}
