package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground-owned review of actual model results; export only explicitly selected, verified PNG bytes. */
class PhotoSubjectAssetControls(
    private val activity: Activity,
    private val worker: Executor,
    private val prepareInput: (() -> Unit) -> Bitmap,
    private val isCurrent: () -> Boolean,
    private val requireWorkerCurrent: () -> Unit,
    private val share: (File) -> Unit,
    private val message: (String) -> Unit,
    private val released: () -> Unit,
    private val infer: (android.content.Context, Bitmap, Boolean, () -> Unit, (String) -> Unit) -> PhotoSubjectSegmenter.Batch = PhotoSubjectSegmenter::analyze,
) : AutoCloseable {
    private data class Candidates(val source: PhotoSubjectAssets.Source, val images: List<PhotoSubjectAssetArchive.Encoded>)
    private val closed = AtomicBoolean(false)
    private val files = mutableSetOf<File>()
    private val views = mutableListOf<ImageView>()
    private var dialog: AlertDialog? = null
    private var stage: Any? = null
    private var work: Any? = null
    private var candidates: Candidates? = null
    private var reviewed: PhotoSubjectAssetArchive.Prepared? = null
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun live() = !closed.get() && !activity.isDestroyed && !activity.isFinishing && isCurrent()
    private fun owns(token: Any) = live() && stage === token && work == null
    private fun text(value: String) = TextView(activity).apply { text = value; setTextColor(Color.BLACK); setPadding(dp(8), dp(8), dp(8), dp(8)) }
    private fun panel() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(8), dp(12), dp(8)) }
    private fun forgetViews() { views.forEach { it.setImageDrawable(null) }; views.clear() }
    private fun clearDialog() { stage = null; dialog?.dismiss(); dialog = null; forgetViews() }
    private fun show(value: AlertDialog, token: Any, newViews: List<ImageView> = emptyList()) {
        if (!live()) { value.dismiss(); close(); return }
        clearDialog(); views += newViews; dialog = value; stage = token
        value.setOnCancelListener { if (stage === token) close() }
        value.setOnDismissListener { if (stage === token) close() }
        value.show(); value.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) value.getButton(which)?.apply { setTextColor(Color.BLACK); minHeight = dp(48) }
    }
    private fun own(file: File): File {
        synchronized(files) { if (closed.get()) { file.delete(); error("AI subject work stopped") }; files += file }
        return file
    }
    private fun temporary(extension: String): File {
        check(!closed.get()); val base = activity.cacheDir.canonicalFile; val directory = File(base, "created-images")
        require(directory.canonicalFile == directory.absoluteFile && directory.parentFile?.canonicalFile == base)
        require(directory.isDirectory || directory.mkdirs()) { "Private asset storage is unavailable" }
        val cutoff = System.currentTimeMillis() - 86_400_000L
        directory.listFiles()?.filter { it.name.startsWith("noble-subject-") && it.extension in listOf("png", "zip") && it.isFile && it.canonicalFile == it.absoluteFile && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
        return own(File.createTempFile("noble-subject-", extension, directory).canonicalFile)
    }
    fun open() {
        if (!live()) { close(); return }
        val token = Any(); val body = panel()
        body.addView(text("AI separates prominent foreground subjects into individual cutout assets. It uses an opaque edited preview reduced to at most 1024 pixels per edge, not the full-resolution original. Nearby or touching subjects may merge. Hair, fine edges and object boundaries can be wrong. Faint pixels can retain background detail: this is not privacy redaction. Nothing in your photo edit is replaced."))
        body.addView(text("Requires compatible Google Play services. Model setup may download data, including over mobile data, and can finish after Stop. Google Play services/ML Kit send diagnostics. The photo is processed on this device. Inspect every cutout, then choose which PNGs to include; none are selected automatically."))
        val consent = CheckBox(activity).apply { text = "Allow AI model setup and SDK diagnostics"; setTextColor(Color.BLACK); buttonTintList = ColorStateList.valueOf(Color.BLACK); minHeight = dp(48); isChecked = false }
        body.addView(consent)
        val value = AlertDialog.Builder(activity).setTitle("AI subject asset pack · beta").setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Cancel", null).setPositiveButton("Find subjects", null).create()
        show(value, token)
        value.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isEnabled = false
            consent.setOnCheckedChangeListener { _, checked -> isEnabled = checked && owns(token) }
            setOnClickListener { if (owns(token) && consent.isChecked) detect() }
        }
    }
    private fun <T> job(title: String, action: (() -> Unit, (String) -> Unit) -> T, done: (T) -> Unit) {
        if (!live() || work != null) return
        clearDialog(); val token = Any(); work = token
        val progress = AlertDialog.Builder(activity).setTitle(title).setMessage("Working on this device. Stop keeps your photo and edits. A model download or in-flight inference may finish after the controls close.")
            .setNegativeButton("Stop") { _, _ -> if (work === token) { message("AI subject work stopped. Your photo and edits were kept."); close() } }.create()
        show(progress, token)
        try { worker.execute {
            val began = System.nanoTime(); var checkedOwnerAt = Long.MIN_VALUE
            fun current() {
                check(!closed.get() && !Thread.currentThread().isInterrupted) { "AI subject work stopped" }
                val now = System.nanoTime(); check(now - began < 180_000_000_000L) { "AI subject work timed out" }
                if (checkedOwnerAt == Long.MIN_VALUE || now - checkedOwnerAt >= 100_000_000L) { requireWorkerCurrent(); checkedOwnerAt = now }
            }
            fun phase(value: String) { activity.runOnUiThread { if (live() && work === token && stage === token) progress.setMessage(value) } }
            val result = runCatching { current(); action(::current, ::phase).also { current() } }
            activity.runOnUiThread {
                if (!live() || work !== token) { close(); return@runOnUiThread }
                work = null; clearDialog()
                result.onSuccess { value -> runCatching { done(value) }.onFailure { fail(it) } }.onFailure { fail(it) }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { work = null; fail() }
    }
    private fun fail(error: Throwable? = null) {
        if (live()) message(if (error is PhotoSubjectSegmenter.ModelUnavailable) requireNotNull(error.message) else "AI subject assets could not be prepared. Check model availability, use a clear opaque photo and try again. Your photo and edits were kept.")
        close()
    }
    private fun detect() {
        job("Finding separate AI subjects", { current, phase ->
            val input = prepareInput(current)
            // analyze owns this independent bitmap until its SDK task completes, including cancellation.
            val batch = infer(activity.applicationContext, input, true, current, phase)
            val encoded = batch.cutouts.mapIndexed { index, cutout ->
                current(); phase("Preparing cutout ${index + 1} of ${batch.cutouts.size}")
                val file = temporary(".png"); val image = Bitmap.createBitmap(cutout.pixels(), cutout.width, cutout.height, Bitmap.Config.ARGB_8888)
                try { FileOutputStream(file).use { output -> current(); check(image.compress(Bitmap.CompressFormat.PNG, 100, output)); output.fd.sync() } }
                finally { image.recycle() }
                current(); PhotoSubjectAssetArchive.capture(file, cutout, current)
            }
            Candidates(batch.source, encoded)
        }, { value -> candidates = value; showCandidates(value) })
    }
    private fun image(body: LinearLayout, value: PhotoSubjectAssetArchive.Encoded): ImageView {
        val options = BitmapFactory.Options().apply { inSampleSize = 1; while (maxOf(value.entry.width, value.entry.height) / inSampleSize > 384) inSampleSize *= 2 }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(value.file.absolutePath, options)) { "Cutout preview is unavailable" }
        val view = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "AI subject ${value.entry.subject}, estimated transparent cutout"; background = TransparencyGridDrawable(dp(12)); setImageBitmap(bitmap) }
        body.addView(view, LinearLayout.LayoutParams(-1, dp(240))); return view
    }
    private fun showCandidates(value: Candidates) {
        val token = Any(); val selected = linkedSetOf<Int>(); val body = panel(); val images = mutableListOf<ImageView>()
        body.addView(text("Review every candidate. Select the cutouts you want in the asset pack. Transparent areas use the checkerboard. These are fitted previews; PNG dimensions are listed. AI can merge subjects or miss edges. Original photo and edits remain unchanged."))
        value.images.forEach { encoded ->
            images += image(body, encoded); val e = encoded.entry
            body.addView(text("Subject ${e.subject}: ${e.width}×${e.height} PNG, ${e.visiblePixels} visible pixels; ${e.softPixels} soft-edge pixels. Source-grid position ${e.x},${e.y}."))
            body.addView(CheckBox(activity).apply {
                text = "Include subject ${e.subject}"; setTextColor(Color.BLACK); buttonTintList = ColorStateList.valueOf(Color.BLACK); minHeight = dp(48); isChecked = false
                setOnCheckedChangeListener { _, checked -> if (owns(token) && candidates === value) { if (checked) selected += e.subject else selected -= e.subject } }
            })
        }
        val view = AlertDialog.Builder(activity).setTitle("Choose AI cutout assets").setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Cancel", null).setPositiveButton("Review selected pack", null).create()
        show(view, token, images)
        view.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            if (!owns(token) || candidates !== value) return@setOnClickListener
            if (selected.isEmpty()) { view.setTitle("Select at least one cutout"); return@setOnClickListener }
            val chosen = value.images.filter { it.entry.subject in selected }
            job("Preparing selected asset pack", { current, _ -> PhotoSubjectAssetArchive.create(temporary(".zip"), value.source, chosen, current) }, { pack -> reviewed = pack; reviewPack(value, chosen, pack) })
        }
    }
    private fun reviewPack(value: Candidates, chosen: List<PhotoSubjectAssetArchive.Encoded>, pack: PhotoSubjectAssetArchive.Prepared) {
        val token = Any(); val body = panel(); val images = mutableListOf<ImageView>()
        body.addView(text("Only these ${chosen.size} estimated cutouts are included. Faint pixels can retain background details that a receiving app could reveal. The manifest records source-grid positions, dimensions, alpha counts and SHA-256, plus a fingerprint of the working preview. The full original photo file, filename/URI and EXIF are not included. A receiving app can retain the ZIP and selected subject images. Your original photo and current edits stay here."))
        chosen.forEach { encoded -> images += image(body, encoded); body.addView(text("${encoded.entry.name}\n${encoded.entry.width}×${encoded.entry.height} · ${encoded.entry.bytes} bytes\nSHA-256 ${encoded.entry.sha256}")) }
        body.addView(text("Working preview ${value.source.width}×${value.source.height}\nGrid SHA-256 ${value.source.sha256}\nZIP ${pack.bytes} bytes\nZIP SHA-256 ${pack.sha256}\n\nSave the ZIP using the chosen app. These controls keep temporary copies only; cancelling deletes them. Shared cache copies may expire after a day. Model quality and edge accuracy are not guaranteed."))
        val view = AlertDialog.Builder(activity).setTitle("Review selected AI asset pack").setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save or share ZIP", null).create()
        show(view, token, images)
        view.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            if (!owns(token) || reviewed !== pack || candidates !== value) return@setOnClickListener
            job("Checking reviewed ZIP bytes", { current, _ -> PhotoSubjectAssetArchive.verify(pack, current); pack }, { checked ->
                if (reviewed !== checked || !live()) { close(); return@job }
                // Transfer before the chooser pauses this Activity. Remaining private PNGs are deleted.
                synchronized(files) { check(files.remove(checked.file)); reviewed = null }
                runCatching { share(checked.file) }.onFailure { checked.file.delete(); if (live()) message("The asset pack could not be shared. Your photo and edits were kept.") }
                close()
            })
        }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stage = null; work = null; runCatching { dialog?.dismiss() }; dialog = null; forgetViews()
        candidates = null; reviewed = null
        synchronized(files) { files.forEach { runCatching { it.delete() } }; files.clear() }
        released()
    }
}
