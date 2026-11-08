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
    private val stages = VideoTitleShelfStages()
    private var workerReceipt: Any? = null // worker only

    fun close() {
        if (!closed.compareAndSet(false,true)) return
        stages.close()
        val previous=dialog; dialog=null; previous?.dismiss()
        worker.shutdownNow()
        busy(false)
    }
    private fun valid(): Boolean = !closed.get() && isCurrent()
    private fun requireOwner() {
        check(!closed.get() && workerReceipt?.let { stages.permits(it) } == true) { "Title style operation cancelled" }
        check(VideoPrivateOwner.namespace(context,mode)==owner) { "Account changed. Reopen saved title styles." }
    }
    private fun active(value: AlertDialog): Boolean = dialog===value && value.isShowing && stages.owns(value) && valid()
    private fun closeStage(value: AlertDialog) { if(dialog===value && stages.owns(value))close() }
    private fun <T> job(action: () -> T, done: (T) -> Unit) = job(null,action,done)
    private fun <T> job(from: AlertDialog?, action: () -> T, done: (T) -> Unit) {
        if(from!=null && !active(from))return
        if(!stages.canBegin(from))return
        if (!valid()) { close(); return }
        val receipt=stages.begin(from)?:return
        val previous=dialog;dialog=null
        busy(true);previous?.dismiss()
        try { worker.execute {
            val result=runCatching {
                check(stages.permits(receipt)) { "Title style operation cancelled" }
                workerReceipt=receipt
                try { action() } finally { workerReceipt=null }
            }
            activity.runOnUiThread {
                if (!stages.finish(receipt))return@runOnUiThread
                busy(false)
                if (!valid()) { close(); return@runOnUiThread }
                result.onSuccess(done).onFailure { message(it.message ?: "Saved title styles are unavailable."); close() }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) {
            if(stages.finish(receipt)){busy(false);close()}
        }
    }
    private fun show(value: AlertDialog, onDismiss: () -> Unit = {}): Boolean {
        if (!valid()) { close(); return false }
        if(!stages.open(value)){value.dismiss();return false}
        val previous=dialog;dialog=value
        value.setOnCancelListener { closeStage(value) }
        value.setOnDismissListener { closeStage(value);onDismiss() }
        value.show()
        if(previous!==value)previous?.dismiss()
        return active(value)
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
        lateinit var value: AlertDialog
        value=AlertDialog.Builder(activity).setTitle("Saved title styles · ${catalog.styles.size}/20")
            .setItems(actions.toTypedArray()) { _, index ->
                if(!active(value))return@setItems
                when(index) {
                    0 -> saveName()
                    1,2,3 -> choose(catalog,index)
                    4 -> job(value,{ requireOwner(); store!!.undo(checkNotNull(deletion)){requireOwner()}; deletion=null; store!!.load() }) {
                        canUndo=false; message("Title style deletion undone."); menu(it)
                    }
                }
            }.setNegativeButton("Close") { _,_ -> closeStage(value) }.create()
        show(value)
    }
    private fun saveName() {
        val name=EditText(activity).apply {
            hint="Style name"; contentDescription="Saved video title style name"; setSingleLine(true)
            setTextColor(Color.BLACK); filters=arrayOf(android.text.InputFilter.LengthFilter(80))
        }
        lateinit var value: AlertDialog
        value=AlertDialog.Builder(activity).setTitle("Save title appearance")
            .setView(name).setNegativeButton("Cancel") { _,_ -> closeStage(value) }.setPositiveButton("Save on this device",null).create()
        if(!show(value){name.text?.clear()})return
        value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!active(value))return@setOnClickListener
            val style=runCatching { NamedVideoTitleStyle(name.text.toString().trim(),appearance) }.getOrElse {
                name.error=it.message; return@setOnClickListener
            }
            job(value,{ requireOwner(); store!!.save(style){requireOwner()}; store!!.load() }) {
                message("Title appearance saved on this device."); menu(it)
            }
        }
    }
    private fun choose(catalog: VideoTitleStyleCatalog, action: Int) {
        if(catalog.styles.isEmpty()) { message("No saved title styles yet. Save a title appearance first."); menu(catalog); return }
        lateinit var value: AlertDialog
        value=AlertDialog.Builder(activity).setTitle(if(action==3) "Delete saved title style" else "Choose title style")
            .setItems(catalog.styles.map { it.name }.toTypedArray()) { _,index ->
                if(!active(value))return@setItems
                catalog.styles.getOrNull(index)?.let { confirm(it,action) }
            }.setNegativeButton("Cancel") { _,_ -> closeStage(value) }.create()
        show(value)
    }
    private fun confirm(style: NamedVideoTitleStyle, action: Int) {
        val description=with(style.style) { "${listOf("Top","Middle","Bottom")[position]}, ${Math.round(size*1000)/10.0}% height, ${if(light) "white" else "black"} text, ${if(plate) "with" else "no"} plate." }
        lateinit var value: AlertDialog
        value=AlertDialog.Builder(activity).setTitle(if(action==3) "Delete ${style.name}?" else "Apply ${style.name}?")
            .setMessage(if(action==3) "Removes this local appearance. Current edits stay unchanged. You can undo the deletion in this shelf." else "$description\nReplaces title appearance on ${if(action==2) "all clips" else "the selected clip"}. Words, captions, timing and media stay unchanged. Blank titles remain blank. Project Undo restores the previous appearance.")
            .setNegativeButton("Cancel") { _,_ -> closeStage(value) }
            .setPositiveButton(if(action==3) "Delete" else "Apply") { _,_ ->
                if(!active(value))return@setPositiveButton
                if(action==3) job(value,{
                    requireOwner(); deletion=store!!.delete(style){requireOwner()}; store!!.load()
                }) { canUndo=true; message("Title style deleted. Undo is available in this shelf."); menu(it) }
                else job(value,{
                    requireOwner(); check(store!!.load().styles.contains(style)) { "Saved styles changed. Reopen the shelf." }; requireOwner(); style
                }) { selected ->
                    try { apply(selected,action==2) } finally { close() }
                }
            }.create()
        show(value)
    }
}
