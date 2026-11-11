package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.honorable.editor.EditorAccessMode
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Standalone import/list/delete entry; imported edit state always opens a separate guest workspace. */
class VideoProjectBackupsActivity: Activity() {
    @Volatile private var foreground = false
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: VideoProjectBackupStore
    private var request: LibraryDuplicateRequest? = null
    private var dialog: AlertDialog? = null
    private var stage: Any? = null
    private var picked: Uri? = null
    private var awaitingPicker = false
    private var preview: ImportPreview? = null
    private var notice = ""
    private var viewGeneration = 0L
    private class ImportPreview(val file: File, val staged: VideoProjectBackupStore.Staged) {
        fun dispose(store: VideoProjectBackupStore) { try { store.abandon(staged) } finally { file.delete() } }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (intent.getStringExtra(EditorAccessMode.EXTRA) != EditorAccessMode.GUEST_BASIC.name) { finish(); return }
        awaitingPicker = state?.getBoolean("backupPickerPending") == true
        store = VideoProjectBackupStore(applicationContext)
        window.statusBarColor = Color.WHITE; window.navigationBarColor = Color.WHITE
    }
    private fun valid() = foreground && !isDestroyed && !isFinishing
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun text(value: String) = TextView(this).apply { text = value; setTextColor(Color.BLACK); setPadding(dp(12), dp(8), dp(12), dp(8)) }
    private fun button(label: String, action: () -> Unit): Button {
        val generation = viewGeneration
        return Button(this).apply { text = label; minHeight = dp(48); setTextColor(Color.BLACK); setOnClickListener { if (valid() && request == null && !awaitingPicker && dialog?.isShowing != true && generation == viewGeneration) action() } }
    }
    private fun clearPreview() { val value = preview; preview = null; value?.dispose(store) }
    private fun show(value: AlertDialog, token: Any, dismiss: () -> Unit = {}) {
        if (!valid()) { dismiss(); value.dismiss(); return }
        stage = null; dialog?.dismiss(); dialog = value; stage = token
        value.setOnCancelListener { if (stage === token) { stage = null; dialog = null; dismiss() } }
        value.setOnDismissListener { if (stage === token) { stage = null; dialog = null; dismiss() } }
        value.show()
    }
    private fun <T> job(title: String, action: (LibraryDuplicateRequest, () -> Unit) -> T, done: (T) -> Unit, abandon: (T) -> Unit = {}) {
        if (!valid() || request != null) return
        val running = LibraryDuplicateRequest(UUID.randomUUID().toString()); request = running; val token = Any()
        show(AlertDialog.Builder(this).setTitle(title).setMessage("Local bounded work with a checked90second deadline. Original ZIP/media and other projects stay unchanged. Blocked decoders/providers can delay physical cancellation.")
            .setNegativeButton("Stop") { _, _ -> if (request === running) { running.cancel(); request = null; stage = null; notice = "Import work stopped. Original ZIP and existing projects were kept."; home() } }.create(), token) { if (request === running) { running.cancel(); request = null } }
        try { worker.execute {
            val began = System.nanoTime()
            fun current() { running.requireActive(); check(foreground && !isDestroyed && !isFinishing && !Thread.currentThread().isInterrupted); check(System.nanoTime() - began < 90_000_000_000L) { "Backup work timed out; use a smaller supported backup" } }
            var produced: T? = null
            val result = runCatching { current(); action(running, ::current).also { produced = it; current() } }
            if (result.isFailure) produced?.let { runCatching { abandon(it) } }
            runOnUiThread {
                if (request !== running || !valid()) { result.getOrNull()?.let { runCatching { abandon(it) } }; return@runOnUiThread }
                request = null; stage = null; dialog?.dismiss(); dialog = null
                result.onSuccess { value -> runCatching { done(value) }.onFailure { runCatching { abandon(value) }; showFailure("Could not open this backup step. Original ZIP/media and existing edits were kept.") } }
                    .onFailure { showFailure(it.message ?: "Backup could not be opened. Original ZIP/media and existing edits were kept.") }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { request = null }
    }
    private fun showFailure(message: String) {
        stage = null; runCatching { dialog?.dismiss() }; dialog = null; runCatching { clearPreview() }
        if (!valid()) return
        viewGeneration++
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); addView(text(message)); addView(button("Return to backup list") { home() }); addView(button("Back to Library") { finish() }) }
        setContentView(ScrollView(this).apply { addView(panel) })
    }
    private fun home() {
        if (!valid() || request != null) return
        clearPreview()
        job("Reading imported copies", { _, current -> current(); store.list().also { current() } }, { rows ->
            viewGeneration++
            val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(Color.WHITE) }
            panel.addView(text("Video project backups")); panel.addView(text("Open a Noble video backup as a separate local guest copy with its own media. No account access or privileges transfer. Existing projects and the original ZIP stay unchanged. Imported copies remain private on this install; removing the app removes them. Keep the ZIP elsewhere for a durable backup. Up to8copies/512MiB private storage; each backup≤256MiB media/257MiB ZIP. Only compatible Noble Android backups are supported."))
            if (notice.isNotEmpty()) panel.addView(text(notice))
            panel.addView(button("Choose Noble video backup ZIP") { awaitingPicker = true; runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 7301) }.onFailure { awaitingPicker = false; notice = "A document picker is unavailable."; home() } })
            panel.addView(text("Imported snapshots"))
            if (rows.isEmpty()) panel.addView(text("No imported copies yet."))
            rows.forEachIndexed { index, row ->
                val document = row.document
                panel.addView(text(if (row.error || document == null) "Imported copy${index+1} is unreadable. Existing files are preserved; you can remove only this private copy." else "Imported copy${index+1}: ${document.draft.segments.size}clips, ${document.bytes}media bytes. Opens the original imported snapshot; later guest edits you saved can be restored inside the editor."))
                if (document != null) panel.addView(button("Open imported snapshot${index+1}") { job("Checking imported media checksums", { _, current -> store.verify(row.id, current) }, { launch(it); notice = "Imported snapshot opened separately." }) })
                panel.addView(button("Delete private copy${index+1}") {
                    val token = Any(); show(AlertDialog.Builder(this).setTitle("Delete this imported copy?").setMessage("Delete only this imported snapshot and its private copied media. Edits referencing these copied files will no longer open. Your original ZIP, original media and other copies stay unchanged.")
                        .setNegativeButton("Keep copy", null).setPositiveButton("Delete this copy") { _, _ -> if (stage === token && valid()) { stage = null; job("Deleting this private copy", { _, current -> current(); store.delete(row.id); current() }, { notice = "Private imported copy deleted. Original ZIP and original media were kept."; home() }) } }.create(), token)
                })
            }
            panel.addView(button("Clear interrupted imports") { val token = Any(); show(AlertDialog.Builder(this).setTitle("Clear interrupted imports?").setMessage("Remove only unfinished private import staging files that no active import owns. Completed imported copies, original ZIP files and media stay unchanged.").setNegativeButton("Keep", null).setPositiveButton("Clear interrupted") { _, _ -> if (stage === token && valid()) { stage = null; job("Clearing interrupted copies", { _, current -> current(); store.clearInterrupted(); current() }, { notice = "Interrupted import staging cleared; completed copies kept."; home() }) } }.create(), token) })
            panel.addView(button("Back to Library") { finish() })
            setContentView(ScrollView(this).apply { setBackgroundColor(Color.WHITE); addView(panel) })
        })
    }
    private fun beginImport(uri: Uri) {
        job("Verifying and preparing private copy", { request, current ->
            val file = VideoProjectBackupIO.copyInput(applicationContext, uri, request, current); var staged: VideoProjectBackupStore.Staged? = null
            try {
                val verified = VideoProjectBackup.inspect(file, current); staged = store.stage(verified, current)
                ImportPreview(file, staged).also { current() }
            } catch (error: Throwable) { staged?.let { store.abandon(it) }; file.delete(); throw error }
        }, { value -> preview = value; reviewImport(value) }, { it.dispose(store) })
    }
    private fun reviewImport(value: ImportPreview) {
        val token = Any(); val original = value.staged.verified
        val body = text(VideoProjectBackup.description(original.draft, original.entries) + "\n\nZIP ${original.bytes}bytes\nSHA-256 ${original.sha256}\n\nAll media payload checksums/CRC and project references passed. Staged videos/audio metadata match recorded durations; subtitle references passed the existing source-time SRT validation policy. This is not a complete playback or appearance check. Open keeps a separate private guest copy; no current editor or saved project is replaced. No changes to original ZIP/media. Cancel deletes the temporary import. Imported copies and metadata remain unencrypted guest-local storage on this install; the app's provider is private and read-only. Keep your external ZIP to survive uninstall.")
        val view = AlertDialog.Builder(this).setTitle("Review imported project and media").setView(ScrollView(this).apply { setBackgroundColor(Color.WHITE); addView(body) }).setNegativeButton("Cancel import") { _, _ -> if (stage === token) { clearPreview(); notice = "Temporary import cancelled. Original ZIP and projects kept."; home() } }.setPositiveButton("Keep and open guest copy") { _, _ -> if (stage === token && valid() && preview === value) {
            preview = null; stage = null
            job("Keeping complete private copy", { _, current ->
                try { store.commit(value.staged, current) } catch (error: Throwable) { store.abandon(value.staged); throw error } finally { value.file.delete() }
            }, { document -> runCatching { launch(document) }.onFailure { notice = "Complete private copy was kept, but the editor could not open. Open it from Imported snapshots."; home() } }, { store.discardNew(it) })
        } }.create()
        show(view, token) { if (preview === value) { clearPreview(); notice = "Temporary import closed; original ZIP and existing projects kept."; home() } }
    }
    private fun launch(document: VideoProjectBackupStore.Document) {
        check(valid())
        startActivity(Intent(this, HonorableVideoEditorActivity::class.java).putExtra("uri", document.primary).putExtra(EditorAccessMode.EXTRA, EditorAccessMode.GUEST_BASIC.name)
            .putExtra(VideoProjectBackupStore.EXTRA, document.id).putExtra(VideoProjectBackupStore.DRAFT_EXTRA, VideoDraftCodec.encode(document.draft)))
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7301 || !awaitingPicker) return
        awaitingPicker = false; picked = if (resultCode == RESULT_OK) data?.data?.takeIf { it.scheme == "content" } else null
        if (picked == null) notice = "No backup selected. Existing copies stay unchanged."
        val selected = picked
        if (selected != null && valid() && request == null) { picked = null; beginImport(selected) }
    }
    override fun onSaveInstanceState(state: Bundle) { state.putBoolean("backupPickerPending", awaitingPicker); super.onSaveInstanceState(state) }
    override fun onResume() { super.onResume(); foreground = true; if (!::store.isInitialized) return; val selected = picked; picked = null; if (selected != null) beginImport(selected) else home() }
    override fun onPause() { foreground = false; request?.cancel(); request = null; stage = null; runCatching { dialog?.dismiss() }; dialog = null; if (::store.isInitialized) clearPreview(); super.onPause() }
    override fun onDestroy() { request?.cancel(); request = null; if (::store.isInitialized) clearPreview(); worker.shutdownNow(); super.onDestroy() }
}
