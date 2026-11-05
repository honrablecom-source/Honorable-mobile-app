package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.EditText
import app.honorable.editor.EditorAccessMode
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** UI-thread controller; all shelf and account I/O runs on its single private worker. */
internal class VideoTitleStyleShelf(
    private val activity: Activity,
    private val mode: EditorAccessMode,
    private val appearance: VideoTitleStyle,
    private val isCurrent: () -> Boolean,
    private val busy: (Boolean) -> Unit,
    private val message: (String) -> Unit,
    private val apply: (NamedVideoTitleStyle, Boolean) -> Unit
) {
    private val closed = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private val context = activity.applicationContext
    private var dialog: AlertDialog? = null
    private var store: VideoTitleStyleStore? = null // worker only
    private var owner: String? = null // worker only
    private var deletion: VideoTitleStyleStore.Deletion? = null // worker only
    private var canUndo = false // UI only

    fun close() {
        if (!closed.compareAndSet(false,true)) return
        dialog?.dismiss(); dialog=null
        worker.shutdownNow()
        busy(false)
    }
    private fun valid(): Boolean = !closed.get() && isCurrent()
    private fun requireOwner() {
        check(!closed.get()) { "Title style operation cancelled" }
        check(VideoPrivateOwner.namespace(context,mode)==owner) { "Account changed. Reopen saved title styles." }
    }
    private fun <T> job(action: () -> T, done: (T) -> Unit) {
        if (!valid()) { close(); return }
        busy(true)
        try { worker.execute {
            val result=runCatching { action() }
            activity.runOnUiThread {
                if (!closed.get()) {
                    busy(false)
                    if (!valid()) { close(); return@runOnUiThread }
                    result.onSuccess(done).onFailure { message(it.message ?: "Saved title styles are unavailable."); close() }
                }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { busy(false); close() }
    }
    private fun show(value: AlertDialog) {
        if (!valid()) { close(); return }
        dialog?.dismiss(); dialog=value
        value.setOnCancelListener { close() }
        value.show()
    }
    fun open() {
        job({
            owner=VideoPrivateOwner.namespace(context,mode)
            check(owner!=null) { "Session changed. Reopen this video." }
            // Resolve the trusted Android parent first; reject aliases on the shelf itself.
            val directory=File(context.noBackupFilesDir.canonicalFile,"video-title-styles")
            store=VideoTitleStyleStore(directory,owner!!)
            requireOwner(); store!!.load().also { requireOwner() }
        }) { menu(it) }
    }
    private fun menu(catalog: VideoTitleStyleCatalog) {
        val actions=mutableListOf("Save selected title appearance", "Apply to selected clip", "Apply to all clips", "Delete saved title style")
        if(canUndo) actions.add("Undo last deletion")
        show(AlertDialog.Builder(activity).setTitle("Saved title styles · ${catalog.styles.size}/20")
            .setItems(actions.toTypedArray()) { _, index ->
                if(!valid()) { close(); return@setItems }
                when(index) {
                    0 -> saveName()
                    1,2,3 -> choose(catalog,index)
                    4 -> job({ requireOwner(); store!!.undo(checkNotNull(deletion)){requireOwner()}; deletion=null; store!!.load() }) {
                        canUndo=false; message("Title style deletion undone."); menu(it)
                    }
                }
            }.setNegativeButton("Close") { _,_ -> close() }.create())
    }
    private fun saveName() {
        val name=EditText(activity).apply {
            hint="Style name"; contentDescription="Saved video title style name"; setSingleLine(true)
            setTextColor(Color.BLACK); filters=arrayOf(android.text.InputFilter.LengthFilter(80))
        }
        val value=AlertDialog.Builder(activity).setTitle("Save title appearance")
            .setView(name).setNegativeButton("Cancel") { _,_ -> close() }.setPositiveButton("Save on this device",null).create()
        show(value)
        value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val style=runCatching { NamedVideoTitleStyle(name.text.toString().trim(),appearance) }.getOrElse {
                name.error=it.message; return@setOnClickListener
            }
            value.dismiss()
            job({ requireOwner(); store!!.save(style){requireOwner()}; store!!.load() }) {
                message("Title appearance saved on this device."); menu(it)
            }
        }
    }
    private fun choose(catalog: VideoTitleStyleCatalog, action: Int) {
        if(catalog.styles.isEmpty()) { message("No saved title styles yet. Save a title appearance first."); menu(catalog); return }
        show(AlertDialog.Builder(activity).setTitle(if(action==3) "Delete saved title style" else "Choose title style")
            .setItems(catalog.styles.map { it.name }.toTypedArray()) { _,index -> confirm(catalog.styles[index],action) }
            .setNegativeButton("Cancel") { _,_ -> close() }.create())
    }
    private fun confirm(style: NamedVideoTitleStyle, action: Int) {
        val description=with(style.style) { "${listOf("Top","Middle","Bottom")[position]}, ${Math.round(size*1000)/10.0}% height, ${if(light) "white" else "black"} text, ${if(plate) "with" else "no"} plate." }
        show(AlertDialog.Builder(activity).setTitle(if(action==3) "Delete ${style.name}?" else "Apply ${style.name}?")
            .setMessage(if(action==3) "Removes this local appearance. Current edits stay unchanged. You can undo the deletion in this shelf." else "$description\nReplaces title appearance on ${if(action==2) "all clips" else "the selected clip"}. Words, captions, timing and media stay unchanged. Blank titles remain blank. Project Undo restores the previous appearance.")
            .setNegativeButton("Cancel") { _,_ -> close() }
            .setPositiveButton(if(action==3) "Delete" else "Apply") { _,_ ->
                if(action==3) job({
                    requireOwner(); deletion=store!!.delete(style){requireOwner()}; store!!.load()
                }) { canUndo=true; message("Title style deleted. Undo is available in this shelf."); menu(it) }
                else job({
                    requireOwner(); check(store!!.load().styles.contains(style)) { "Saved styles changed. Reopen the shelf." }; requireOwner(); style
                }) { selected ->
                    try { apply(selected,action==2) } finally { close() }
                }
            }.create())
    }
}
