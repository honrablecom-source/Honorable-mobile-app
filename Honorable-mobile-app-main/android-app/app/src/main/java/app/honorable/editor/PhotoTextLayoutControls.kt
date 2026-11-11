package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Correct real OCR before creating editable typography. No original pixels or existing layers removed. */
class PhotoTextLayoutControls(
    private val activity: Activity,
    private val base: PhotoEdit,
    private val worker: Executor,
    private val prepareInput: (() -> Unit) -> Bitmap,
    private val isCurrent: () -> Boolean,
    private val requireWorkerCurrent: () -> Unit,
    private val apply: (PhotoTextLayout.Plan) -> Boolean,
    private val message: (String) -> Unit,
    private val released: () -> Unit,
    private val read: (Bitmap, () -> Unit, () -> Unit) -> PhotoTextLayout.Batch = { bitmap, current, settled ->
        PhotoTextLayoutReader.read(bitmap, current, settled)
    },
) : AutoCloseable, Application.ActivityLifecycleCallbacks {
    private data class Recognized(val batch: PhotoTextLayout.Batch, val reference: Bitmap)
    private data class Preview(val plan: PhotoTextLayout.Plan, val bitmap: Bitmap)
    private val closed = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val readerPending = AtomicBoolean(false)
    private val releaseOnce = AtomicBoolean(false)
    @Volatile private var work: Any? = null
    private var registered = false
    private var stage: Any? = null
    private var dialog: AlertDialog? = null
    private val images = mutableListOf<ImageView>()
    private var recognized: Recognized? = null
    private var prepared: Preview? = null
    private val chosen = linkedSetOf<Int>()
    private val wording = linkedMapOf<Int, String>()
    private val capacity = maxOf(0, 31 - base.layers.objects.size)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun live() = !closed.get() && !activity.isDestroyed && !activity.isFinishing && runCatching(isCurrent).getOrDefault(false)
    private fun owns(token: Any) = live() && stage === token && work == null
    private fun panel() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(8), dp(12), dp(8)) }
    private fun text(value: String) = TextView(activity).apply { text = value; setTextColor(Color.BLACK); setPadding(dp(8), dp(8), dp(8), dp(8)) }
    private fun clearDialog() { stage = null; dialog?.dismiss(); dialog = null; images.forEach { it.setImageDrawable(null) }; images.clear() }
    private fun show(value: AlertDialog, token: Any) {
        if (!live()) { close(); return }
        clearDialog(); dialog = value; stage = token
        value.setOnCancelListener { if (stage === token) close() }
        value.setOnDismissListener { if (stage === token) close() }
        value.show(); value.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.WHITE))
        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) value.getButton(which)?.apply { setTextColor(Color.BLACK); minHeight = dp(48) }
    }
    private fun picture(body: LinearLayout, bitmap: Bitmap, token: Any, description: String) {
        val image = ImageView(activity).apply { setImageBitmap(bitmap); contentDescription = description; scaleType = ImageView.ScaleType.FIT_XY }
        body.addView(HorizontalScrollView(activity).apply { addView(image, android.view.ViewGroup.LayoutParams(bitmap.width, bitmap.height)) })
        images += image
        body.addView(LinearLayout(activity).apply {
            for (zoom in listOf(1, 2, 4)) addView(Button(activity).apply {
                text = "${zoom}×"; minHeight = dp(48); setTextColor(Color.BLACK)
                setOnClickListener { if (owns(token)) image.layoutParams = image.layoutParams.apply { width = bitmap.width * zoom; height = bitmap.height * zoom } }
            })
        })
    }
    fun open() {
        if (!live()) { close(); return }
        if (registered) return
        if (capacity == 0) { message("Free two composition-object slots before rebuilding lettering. Your photo and objects were kept."); close(); return }
        registered = true; activity.application.registerActivityLifecycleCallbacks(this)
        val token = Any(); val body = panel()
        body.addView(text("Read Latin-script lettering from the complete edited preview on this device, then correct and choose lines to rebuild as editable text. None will be selected automatically. The bundled ML Kit OCR model is used; SDK diagnostics may be collected. No photo or query upload is needed."))
        body.addView(text("The reconstruction adds white paper above your photo and existing objects, then black horizontal sans-serif text. Original photo and all existing layers stay underneath. Fonts, colors, rotation and original typography are not recovered; positions are approximate. You can edit or remove the new objects afterwards, or Undo the whole insertion once."))
        body.addView(text("There is room for $capacity chosen lines plus paper. Review every corrected line and the complete rendered layout before inserting. OCR may miss or misread text. Stop keeps the original; the SDK may finish reading its private snapshot after the controls close."))
        val value = AlertDialog.Builder(activity).setTitle("AI text to editable layout · beta").setView(ScrollView(activity).apply { addView(body) }).setNegativeButton("Cancel", null).setPositiveButton("Recognize lettering", null).create()
        show(value, token)
        value.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { if (stage === token) close() }
        value.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            if (!owns(token)) { if (stage === token && !live()) close(); return@setOnClickListener }
            job("Reading lettering on this device", { current -> recognize(current) }, { result ->
                recognized = result; result.batch.lines.forEach { wording[it.id] = it.text }; form()
            }, { result -> if (!result.reference.isRecycled) result.reference.recycle() })
        }
    }

    private fun <T> job(title: String, action: (() -> Unit) -> T, done: (T) -> Unit, discard: (T) -> Unit = {},
                        failed: (Throwable) -> Unit = { fail() }) {
        if (!live() || !running.compareAndSet(false, true)) return
        val token = Any(); work = token
        try {
            val progress = AlertDialog.Builder(activity).setTitle(title).setMessage("Stop keeps your photo and edits. OCR input remains private and alive until the SDK finishes reading it.").setNegativeButton("Stop", null).create()
            show(progress, token)
            progress.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { if (work === token) close() }
            worker.execute {
                fun current() { check(!closed.get() && work === token && !Thread.currentThread().isInterrupted) { "Text layout work stopped" }; requireWorkerCurrent() }
                val result = try { runCatching {
                    current(); val value = action(::current)
                    try { current(); value } catch (failure: Throwable) { runCatching { discard(value) }; throw failure }
                } } finally { running.set(false) }
                activity.runOnUiThread {
                    if (!live() || work !== token) {
                        result.onSuccess { runCatching { discard(it) } }; close(); maybeRelease(); return@runOnUiThread
                    }
                    work = null; clearDialog()
                    result.onSuccess { value -> runCatching { done(value) }.onFailure { runCatching { discard(value) }; fail() } }
                        .onFailure { error -> runCatching { failed(error) }.onFailure { fail() } }
                }
            }
        } catch (_: Throwable) { running.set(false); work = null; fail() }
    }

    private fun recognize(current: () -> Unit): Recognized {
        current()
        val input = prepareInput(current)
        val snapshot = try {
            current(); require(!input.isRecycled && input.width in 1..8192 && input.height in 1..8192 && input.width.toLong() * input.height <= 6_000_000)
            Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).also { copy ->
                try { Canvas(copy).apply { drawColor(Color.WHITE); drawBitmap(input, null, Rect(0, 0, copy.width, copy.height), Paint(Paint.FILTER_BITMAP_FLAG)) }; current() }
                catch (failure: Throwable) { copy.recycle(); throw failure }
            }
        } finally { if (!input.isRecycled) input.recycle() }
        var handedToReader = false
        var reference: Bitmap? = null
        try {
            current()
            val width = snapshot.width; val height = snapshot.height
            val scale = minOf(1f, 768f / maxOf(width, height))
            val copy = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            reference = copy
            Canvas(copy).drawBitmap(snapshot, null, Rect(0, 0, copy.width, copy.height), Paint(Paint.FILTER_BITMAP_FLAG))
            current(); readerPending.set(true); handedToReader = true
            val batch = read(snapshot, current) {
                readerPending.set(false); activity.runOnUiThread { maybeRelease() }
            }
            current(); require(batch.width == width && batch.height == height) { "OCR result belongs to a different working grid" }
            val canvas = Canvas(copy); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 2f; textSize = 18f }
            for (line in batch.lines) {
                current()
                val b = line.box; val left = b.left * copy.width.toFloat() / width; val right = b.right * copy.width.toFloat() / width
                val top = b.top * copy.height.toFloat() / height; val bottom = b.bottom * copy.height.toFloat() / height
                paint.style = Paint.Style.STROKE; canvas.drawRect(left, top, right, bottom, paint)
                paint.style = Paint.Style.FILL; val label = line.id.toString(); val labelTop = maxOf(0f, top - 22f)
                paint.color = Color.WHITE; canvas.drawRect(left, labelTop, left + paint.measureText(label) + 4, labelTop + 22, paint)
                paint.color = Color.BLACK; canvas.drawText(label, left + 2, labelTop + 18, paint)
            }
            current(); return Recognized(batch, copy)
        } catch (failure: Throwable) { reference?.let { if (!it.isRecycled) it.recycle() }; throw failure }
        finally { if (!handedToReader && !snapshot.isRecycled) snapshot.recycle() }
    }

    private fun form(error: String? = null) {
        if (!live()) { close(); return }
        val value = recognized ?: return
        clearDialog(); prepared?.bitmap?.let { if (!it.isRecycled) it.recycle() }; prepared = null
        val token = Any(); val body = panel()
        body.addView(text("Correct the recognized wording, then choose lines. Numbers identify the source regions; these markers will not be inserted. Selected wording becomes editable horizontal black sans-serif text on white paper. Original text pixels and existing layers remain underneath. Up to $capacity lines can fit; an overlong or unreadable layout is refused, not shortened."))
        if (error != null) body.addView(text(error))
        val sourcePanel = panel(); body.addView(sourcePanel)
        val counter = text(""); body.addView(counter)
        val fields = linkedMapOf<Int, EditText>()
        fun refresh() {
            counter.text = "${chosen.size} lines selected · room for $capacity plus white paper"
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = chosen.size in 1..capacity
        }
        for (line in value.batch.lines) {
            body.addView(CheckBox(activity).apply {
                text = "Use line ${line.id}"; contentDescription = text; minHeight = dp(48); setTextColor(Color.BLACK); buttonTintList = ColorStateList.valueOf(Color.BLACK); isChecked = line.id in chosen
                setOnCheckedChangeListener { _, checked -> if (owns(token)) { if (checked) chosen += line.id else chosen -= line.id; refresh() } }
            })
            val field = EditText(activity).apply {
                hint = "Correct line ${line.id}"; contentDescription = hint; setTextColor(Color.BLACK)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                maxLines = 3; filters = arrayOf(InputFilter.LengthFilter(PhotoTextLayout.MAX_RAW_LINE)); setText(wording[line.id] ?: line.text)
            }
            fields[line.id] = field; body.addView(field)
        }
        val view = AlertDialog.Builder(activity).setTitle("Correct and choose lettering").setView(ScrollView(activity).apply { addView(body) }).setNegativeButton("Cancel", null).setPositiveButton("Preview editable layout", null).create()
        show(view, token)
        if (!owns(token)) return
        picture(sourcePanel, value.reference, token, "OCR source reference with numbered line regions; markers are not inserted")
        refresh()
        view.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { if (stage === token) close() }
        view.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            if (!owns(token)) { if (stage === token && !live()) close(); return@setOnClickListener }
            if (chosen.size !in 1..capacity) return@setOnClickListener
            fields.forEach { (id, field) -> wording[id] = field.text.toString() }
            val corrections = chosen.associateWith { requireNotNull(wording[it]) }
            job("Preparing complete editable lettering", { current ->
                val plan = PhotoTextLayout.prepare(base, value.batch, corrections, PhotoTextLayoutTypography, current = current)
                current()
                val bitmap = Bitmap.createBitmap(value.batch.width, value.batch.height, Bitmap.Config.ARGB_8888)
                try { bitmap.eraseColor(Color.WHITE); PhotoLayersRenderer.draw(bitmap, plan.edit.layers); current(); Preview(plan, bitmap) }
                catch (failure: Throwable) { bitmap.recycle(); throw failure }
            }, { preview -> prepared = preview; review(preview) }, { preview -> if (!preview.bitmap.isRecycled) preview.bitmap.recycle() }, { failure ->
                val detail = if (failure is IllegalArgumentException) failure.message?.take(240) else null
                form(detail ?: "The complete selected lettering could not fit. Correct or shorten the wording, or choose fewer lines. Nothing was inserted.")
            })
        }
    }

    private fun review(value: Preview) {
        val token = Any(); val body = panel()
        body.addView(text("${value.plan.selected.size} corrected text objects plus white paper will be added. This is the full rendered working layout (${value.bitmap.width} × ${value.bitmap.height}). Pan and zoom to inspect every part. Fonts, colors and rotation are not reconstructed. Text may need adjustment when resized or exported. Original photo and old layers remain underneath; one Undo restores the previous edit."))
        val previewPanel = panel(); body.addView(previewPanel)
        for (line in value.plan.selected) body.addView(text("Line ${line.id}\n${line.text}"))
        val confirmed = CheckBox(activity).apply { text = "I reviewed all selected wording and the complete layout"; isChecked = false; minHeight = dp(48); setTextColor(Color.BLACK); buttonTintList = ColorStateList.valueOf(Color.BLACK) }
        body.addView(confirmed)
        val view = AlertDialog.Builder(activity).setTitle("Review editable text layout").setView(ScrollView(activity).apply { addView(body) }).setNegativeButton("Back", null).setPositiveButton("Insert editable layout", null).create()
        show(view, token)
        if (!owns(token)) return
        picture(previewPanel, value.bitmap, token, "Complete editable lettering preview on white paper; pan and zoom to inspect all text")
        view.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { if (stage === token) { if (live()) form() else close() } }
        view.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isEnabled = false
            confirmed.setOnCheckedChangeListener { _, checked -> if (owns(token)) isEnabled = checked }
            setOnClickListener {
                if (!owns(token)) { if (stage === token && !live()) close(); return@setOnClickListener }
                if (!confirmed.isChecked || prepared !== value) return@setOnClickListener
                stage = null; isEnabled = false // Consume before the parent can reenter or trigger lifecycle events.
                if (runCatching { live() && value.plan.matches(base) && apply(value.plan) }.getOrDefault(false).not() && live()) message("Photo or session changed. Current edits were kept; reopen this control.")
                close()
            }
        }
    }
    private fun fail() { if (live()) message("Text layout could not finish. Your photo and edits were kept; try a closer, sharper crop."); close(); maybeRelease() }
    private fun maybeRelease() { if (closed.get() && !running.get() && !readerPending.get() && releaseOnce.compareAndSet(false, true)) released() }
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            work = null; clearDialog()
            prepared?.bitmap?.let { if (!it.isRecycled) it.recycle() }; prepared = null
            recognized?.reference?.let { if (!it.isRecycled) it.recycle() }; recognized = null
            chosen.clear(); wording.clear()
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
}
