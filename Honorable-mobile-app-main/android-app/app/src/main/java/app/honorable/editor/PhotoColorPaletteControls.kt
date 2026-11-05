package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.PersistableBundle
import android.text.InputFilter
import android.widget.*
import java.util.concurrent.Executor

/** A local palette shelf. Activity closes it on pause; worker writes check ownership at commit. */
class PhotoColorPaletteControls(
    private val activity: Activity,
    private val store: PhotoColorPaletteStore,
    private val worker: Executor,
    private val isCurrent: () -> Boolean
) {
    @Volatile private var active = true
    private val dialogs = mutableListOf<AlertDialog>()
    private var pending = false

    fun close() {
        active = false
        dialogs.toList().forEach { it.dismiss() }
        dialogs.clear()
    }

    private fun current() = active && !activity.isFinishing && !activity.isDestroyed &&
        runCatching { isCurrent() }.getOrDefault(false)

    private fun panel() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(20)
        setPadding(padding, padding, padding, padding)
        setBackgroundColor(Color.WHITE)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun label(body: LinearLayout, value: String) = TextView(activity).apply {
        text = value; setTextColor(Color.BLACK); textSize = 15f
        setPadding(0, dp(6), 0, dp(6)); body.addView(this)
    }
    private fun button(body: LinearLayout, value: String, action: () -> Unit) = Button(activity).apply {
        text = value; EditorChrome.style(this)
        setOnClickListener { if (!pending && current()) action() }
        body.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    private fun show(title: String, body: LinearLayout): AlertDialog {
        val dialog = AlertDialog.Builder(activity).setTitle(title)
            .setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Close", null).create()
        dialogs.add(dialog)
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.setOnShowListener {
            for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
                dialog.getButton(which)?.let { it.setTextColor(Color.BLACK); it.isAllCaps = false; it.minHeight = dp(48) }
            }
        }
        if (current()) dialog.show()
        return dialog
    }

    /** All disk work is off the UI thread; every write checks the still-open request before commit. */
    private fun <T> submit(dialog: AlertDialog, status: TextView, operation: (() -> Unit) -> T, done: (T) -> Unit) {
        if (pending || !current() || !dialog.isShowing) return
        pending = true
        dialog.setCancelable(false)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
        status.text = "Working on this device…"
        val guard = { check(current()) { "Photo or session changed. Reopen saved palettes." } }
        try {
            worker.execute {
                val result = runCatching { guard(); operation(guard) }
                activity.runOnUiThread {
                    pending = false
                    if (!current()) { close(); return@runOnUiThread }
                    if (!dialog.isShowing) return@runOnUiThread
                    dialog.setCancelable(true)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = true
                    result.onSuccess(done).onFailure { error ->
                        status.text = when (error) {
                            is IllegalArgumentException -> error.message ?: "Check the palette name and try again."
                            else -> "Unable to read or save palettes. Existing saved data is preserved. Close and try again."
                        }
                    }
                }
            }
        } catch (_: Exception) {
            pending = false
            dialog.setCancelable(true)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = true
            status.text = "Palette storage is unavailable. Close and try again."
        }
    }

    fun save(colors: List<Int>) {
        if (!current()) return
        val body = panel()
        label(body, "Keep these colors for later photos. Up to 20 named palettes stay on this device, separate for this guest or account. Palettes contain names and RGB codes only; they are not backed up or included in image projects.")
        val input = EditText(activity).apply {
            hint = "Palette name"; contentDescription = "Palette name"
            setSingleLine(true); setTextColor(Color.BLACK)
            // Two UTF-16 units per code point; the model enforces the 40-character limit.
            filters = arrayOf(InputFilter.LengthFilter(80))
            body.addView(this)
        }
        val status = label(body, "")
        lateinit var dialog: AlertDialog
        button(body, "Save palette") {
            val palette = runCatching { PhotoColorPalette(input.text.toString().trim(), colors) }.getOrElse {
                input.error = "Use 1–40 characters without control characters."
                return@button
            }
            submit(dialog, status, { guard -> store.save(palette, guard) }) {
                input.isEnabled = false
                status.text = "Palette saved. Open Saved color palettes to copy a color."
                // Prevent an accidental second save while retaining the confirmation.
                for (index in 0 until body.childCount) (body.getChildAt(index) as? Button)?.isEnabled = false
            }
        }
        dialog = show("Save color palette", body)
    }

    fun shelf() {
        if (!current()) return
        val body = panel()
        label(body, "Your local guest or account palettes. Copy a HEX code and paste it into a photo object's custom color field. Saving a palette does not recolor the photo.")
        val entries = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; body.addView(this) }
        val status = label(body, "Loading palettes…")
        val dialog = show("Saved color palettes", body)
        submit(dialog, status, { guard -> store.load().also { guard() } }) { catalog ->
            status.text = if (catalog.values.isEmpty()) "No palettes yet. Use Extract preview palette, then Save palette." else "${catalog.values.size} of 20 palettes saved. Tap a palette to use its colors."
            catalog.values.forEach { palette ->
                button(entries, "${palette.name} · ${palette.colors.size} colors") { dialog.dismiss(); detail(palette) }
            }
        }
    }

    private fun detail(palette: PhotoColorPalette) {
        if (!current()) return
        val body = panel()
        label(body, "Tap a color to copy its HEX code, then paste it into an object's custom color field.")
        val status = label(body, "")
        palette.colors.forEachIndexed { index, color ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(activity).apply { setBackgroundColor(color); contentDescription = "Color ${palette.hex(index)}" }, LinearLayout.LayoutParams(dp(48), dp(48)))
            val action = Button(activity).apply {
                text = "Copy ${palette.hex(index)}"; EditorChrome.style(this)
                setOnClickListener { if (!pending) copy(palette.hex(index), status) }
            }
            row.addView(action, LinearLayout.LayoutParams(0, -2, 1f)); body.addView(row)
        }
        button(body, "Copy all HEX codes") { copy(palette.hexText(), status) }
        lateinit var dialog: AlertDialog
        button(body, "Delete palette…") { dialog.dismiss(); confirmDelete(palette) }
        button(body, "Back to palettes") { dialog.dismiss(); shelf() }
        dialog = show(palette.name, body)
    }

    private fun copy(value: String, status: TextView) {
        if (!current()) { close(); return }
        runCatching {
            val clip = ClipData.newPlainText("Noble saved palette", value)
            if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        }.onSuccess { status.text = "HEX copied. Paste into a custom color field." }
            .onFailure { status.text = "Clipboard unavailable. Close and try again." }
    }

    private fun confirmDelete(palette: PhotoColorPalette) {
        if (!current()) return
        val body = panel()
        label(body, "Delete “${palette.name}” from this device's current palette shelf? Other palettes and photos stay unchanged. You can Undo on the next screen before closing it.")
        val status = label(body, "")
        lateinit var dialog: AlertDialog
        button(body, "Delete this palette") {
            submit(dialog, status, { guard -> store.delete(palette, guard) }) { deletion ->
                dialog.dismiss(); deleted(deletion)
            }
        }
        button(body, "Keep palette") { dialog.dismiss(); detail(palette) }
        dialog = show("Delete saved palette?", body)
    }

    private fun deleted(deletion: PhotoColorPaletteStore.Deletion) {
        if (!current()) return
        val body = panel()
        val status = label(body, "Palette deleted. Undo is available while this screen stays open and the shelf has not changed.")
        lateinit var dialog: AlertDialog
        button(body, "Undo deletion") {
            submit(dialog, status, { guard -> store.undo(deletion, guard) }) { dialog.dismiss(); shelf() }
        }
        button(body, "Return to palettes") { dialog.dismiss(); shelf() }
        dialog = show("Palette deleted", body)
    }
}
