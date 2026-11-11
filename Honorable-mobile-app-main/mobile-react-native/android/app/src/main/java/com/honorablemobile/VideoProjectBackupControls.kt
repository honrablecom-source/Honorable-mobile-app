package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal object VideoProjectBackupIO {
    fun read(context: Context, uri: Uri, request: LibraryDuplicateRequest, current: () -> Unit, use: (InputStream) -> Unit) {
        current(); require(uri.scheme == "content")
        val descriptor = requireNotNull(context.contentResolver.openAssetFileDescriptor(uri, "r", request.signal)) { "A referenced original is unavailable" }
        descriptor.use { asset -> asset.createInputStream().use { input -> request.bind(input); try { current(); use(input); current() } finally { request.detach(input) } } }
    }
    fun temporary(context: Context): File {
        val root = context.cacheDir.canonicalFile; val directory = File(root, "created-images")
        require(directory.canonicalFile == File(root, "created-images") && (directory.isDirectory || directory.mkdirs()))
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("noble-video-backup-") && it.extension == "zip" && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        return File.createTempFile("noble-video-backup-", ".zip", directory)
    }
    fun copyInput(context: Context, uri: Uri, request: LibraryDuplicateRequest, current: () -> Unit): File {
        val file = temporary(context)
        try { FileOutputStream(file).use { output -> read(context, uri, request, current) { input ->
            val budget = VideoProjectBackup.Budget(output, current); val buffer = ByteArray(65536)
            while (true) { current(); val n = input.read(buffer); if (n < 0) break; require(n > 0); budget.write(buffer, 0, n) }; output.fd.sync()
        } }; current(); return file } catch (error: Throwable) { file.delete(); throw error }
    }
    fun create(context: Context, plan: VideoProjectBackup.Plan, request: LibraryDuplicateRequest, current: () -> Unit): VideoProjectBackup.Verified {
        current(); val file = temporary(context)
        try {
            val types = plan.originals.map { current(); context.contentResolver.getType(Uri.parse(it)) }
            val value = VideoProjectBackup.create(file, plan, types, { index, use -> read(context, Uri.parse(plan.originals[index]), request, current, use) }, current)
            // A second bounded read binds the captured archive bytes to each source observed after copying.
            var total = 0L
            value.entries.forEachIndexed { index, entry ->
                val digest = MessageDigest.getInstance("SHA-256"); var bytes = 0L
                read(context, Uri.parse(plan.originals[index]), request, current) { input -> val buffer = ByteArray(65536); while (true) { current(); val n = input.read(buffer); if (n < 0) break; require(n > 0 && n <= VideoProjectBackup.MAX_MEDIA - total); total += n; bytes += n; digest.update(buffer, 0, n) } }
                val sha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                require(bytes == entry.bytes && sha == entry.sha256) { "An original changed while preparing its backup; try again" }
            }
            current(); return value
        } catch (error: Throwable) { file.delete(); throw error }
    }
}
internal class VideoProjectBackupPrepared(val value: VideoProjectBackup.Verified) {
    val token = UUID.randomUUID().toString()
    fun dispose() { value.file.delete() }
}
internal class VideoProjectBackupControls(private val activity: Activity, private val plan: VideoProjectBackup.Plan,
    private val isCurrent: () -> Boolean, private val requireWorkerCurrent: () -> Unit,
    private val share: (File) -> Unit, private val message: (String) -> Unit, private val released: () -> Unit) {
    private val closed = AtomicBoolean(false); private val worker = Executors.newSingleThreadExecutor()
    private val lease = PhotoDifferenceLease<VideoProjectBackupPrepared>({ it.token }, { it.dispose() })
    private var prepared: VideoProjectBackupPrepared? = null
    private var request: LibraryDuplicateRequest? = null; private var dialog: AlertDialog? = null; private var stage: Any? = null
    private fun valid() = !closed.get() && isCurrent()
    private fun owns(token: Any) = valid() && stage === token
    fun close() { if (!closed.compareAndSet(false, true)) return; stage = null; request?.cancel(); request = null; dialog?.dismiss(); dialog = null; prepared = null; lease.clear(); worker.shutdownNow(); released() }
    private fun show(value: AlertDialog, token: Any) {
        if (!valid()) { close(); return }; stage = null; dialog?.dismiss(); dialog = value; stage = token
        value.setOnCancelListener { if (owns(token)) close() }; value.setOnDismissListener { if (!closed.get() && stage === token) close() }; value.show()
    }
    fun open() {
        val prospective = VideoProjectBackupStore(activity.applicationContext)
        VideoProjectBackup.relocate(plan.portable, plan.originals.indices.map { prospective.mediaUri("00000000-0000-0000-0000-000000000000", it).toString() }) // Refuse snapshots that cannot fit recovery after durable URI relocation.
        val token = Any(); val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12); setBackgroundColor(Color.WHITE) }
        val help = TextView(activity).apply { setTextColor(Color.BLACK); text = "Back up this exact editable snapshot with ALL ${plan.originals.size} referenced original video/audio/subtitle files, including unused sources in the source pool. The ZIP preserves original bytes and embedded metadata, including any private information. Titles, captions, settings, trim and playhead are included. Reference URI fields are neutralized; user text and embedded media metadata are not scrubbed. No account access, credits or privileges transfer.\n\nPreparing is local. After a complete checksum/file/text review, you can explicitly share the ZIP to a chosen app, which can retain it. Save it there: the prepared ZIP is temporary. Open it on a compatible Noble Android install through Library → Video backups to restore a separate local guest copy. Current edits, saves and original files stay unchanged.\n\nLimits:65unique media references,256MiB total original bytes,257MiB ZIP, bounded project metadata and checked90second work per stage. Large or unavailable originals refuse the whole backup; choose a smaller project. No rendered appearance/device compatibility guarantee. Blocked providers can delay cancellation." }
        val consent = CheckBox(activity).apply { text = "Include all referenced originals and their embedded metadata"; setTextColor(Color.BLACK); buttonTintList = android.content.res.ColorStateList.valueOf(Color.BLACK); minHeight = (48 * activity.resources.displayMetrics.density).toInt(); isChecked = false }
        panel.addView(help); panel.addView(consent)
        val value = AlertDialog.Builder(activity).setTitle("Back up editable video project").setView(ScrollView(activity).apply { addView(panel) }).setNegativeButton("Cancel") { _, _ -> if (owns(token)) close() }.setPositiveButton("Prepare backup", null).create()
        show(value, token)
        if (!closed.get()) value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!owns(token) || request != null) return@setOnClickListener
            if (!consent.isChecked) { value.setTitle("Choose whether to include originals first"); return@setOnClickListener }
            job("Preparing complete local backup", { request, current -> VideoProjectBackupPrepared(VideoProjectBackupIO.create(activity.applicationContext, plan, request, current)) }, { result -> lease.offer(result); prepared = result; review(result) }, { it.dispose() })
        }
    }
    private fun <T> job(title: String, action: (LibraryDuplicateRequest, () -> Unit) -> T, done: (T) -> Unit, abandon: (T) -> Unit) {
        if (!valid() || request != null) return
        val running = LibraryDuplicateRequest(UUID.randomUUID().toString()); request = running; val token = Any()
        show(AlertDialog.Builder(activity).setTitle(title).setMessage("Local bounded work with a checked90second deadline. Original media/current edits stay. Blocked providers can delay physical cancellation.").setNegativeButton("Stop") { _, _ -> if (stage === token) close() }.create(), token)
        if (closed.get()) return
        try { worker.execute {
            val began = System.nanoTime(); var lastOwner = began - 100_000_000L
            fun current() { running.requireActive(); check(!closed.get() && !Thread.currentThread().isInterrupted); val now = System.nanoTime(); check(now - began < 90_000_000_000L) { "Backup work timed out; choose fewer or smaller originals" }; if (now - lastOwner >= 100_000_000L) { requireWorkerCurrent(); lastOwner = now } }
            var produced: T? = null
            val result = runCatching { current(); action(running, ::current).also { produced = it; current() } }
            if (result.isFailure) produced?.let(abandon)
            if (closed.get()) { result.getOrNull()?.let(abandon); return@execute }
            activity.runOnUiThread {
                if (request !== running || !owns(token)) { result.getOrNull()?.let(abandon); close(); return@runOnUiThread }
                request = null
                result.onSuccess { value -> runCatching { done(value) }.onFailure { abandon(value); message("Could not show the backup review. Original media and edits were kept."); close() } }
                    .onFailure { message(it.message ?: "Backup failed. Original media and edits were kept."); close() }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { request = null; close() }
    }
    private fun review(value: VideoProjectBackupPrepared) {
        val token = Any(); val body = TextView(activity).apply { setTextColor(Color.BLACK); setPadding(20, 12, 20, 12); text = VideoProjectBackup.description(value.value.draft, value.value.entries) + "\n\nZIP ${value.value.bytes} bytes\nSHA-256 ${value.value.sha256}\n\nSharing grants read access to this prepared ZIP only. Other apps can retain full original media and metadata. Originals/current project stay unchanged." }
        show(AlertDialog.Builder(activity).setTitle("Review complete project backup").setView(ScrollView(activity).apply { setBackgroundColor(Color.WHITE); addView(body) }).setNegativeButton("Cancel") { _, _ -> if (owns(token)) close() }.setPositiveButton("Share reviewed backup") { _, _ -> if (owns(token) && prepared === value) {
            job("Verifying reviewed backup", { _, current -> VideoProjectBackup.inspect(value.value.file, current, value.value.sha256); value }, { checked ->
                if (prepared !== checked) { close(); return@job }
                val taken = lease.take(checked.token) ?: run { close(); return@job }; prepared = null
                runCatching { share(taken.value.file) }.onSuccess { message("Backup share opened. Save the ZIP in your chosen app to keep it.") }.onFailure { taken.dispose(); message("Could not share the backup. Original media and edits were kept.") }; close()
            }, { /* existing lease is disposed by close() */ })
        } }.create(), token)
    }
}
