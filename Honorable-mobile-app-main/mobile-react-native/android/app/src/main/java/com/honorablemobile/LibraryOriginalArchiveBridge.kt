package com.honorablemobile

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.ImportedPhotoOriginals
import app.honorable.search.MediaRecord
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.LifecycleState
import com.facebook.react.bridge.WritableMap
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal class LibraryOriginalArchivePreview(val owner: String, val originals: List<LibraryCollectionItem>, val file: File,
                                           val result: LibraryOriginalArchive.Result) {
    val token = UUID.randomUUID().toString()
    fun dispose() { file.delete() }
    fun map(): WritableMap = Arguments.createMap().apply {
        putString("token", token); putDouble("bytes", result.bytes.toDouble()); putString("sha256", result.sha256)
        putArray("entries", Arguments.createArray().apply { result.entries.forEach { entry -> pushMap(Arguments.createMap().apply {
            putString("name", entry.name); putString("mime", entry.mime); putDouble("bytes", entry.bytes.toDouble()); putString("sha256", entry.sha256)
        }) } })
    }
}

internal object LibraryOriginalArchiveBridge {
    fun requireCurrent(context: ReactApplicationContext, owner: String, originals: List<LibraryCollectionItem>, records: () -> List<MediaRecord>) {
        val activity = context.currentActivity
        check(activity != null && !activity.isDestroyed && !activity.isFinishing && context.lifecycleState == LifecycleState.RESUMED) { "Open Library again before preparing an archive" }
        check(VideoPrivateOwner.namespace(context, EditorAccessMode.ACCOUNT) == owner) { "Account changed. Prepare the archive again" }
        val current = records().associateBy { it.id.toString() }
        check(originals.all { original -> current[original.id]?.let { it.uri == original.uri && it.kind.name == original.kind } == true }) { "An original changed. Select the files again" }
    }
    fun prepare(context: ReactApplicationContext, items: ReadableArray, request: LibraryDuplicateRequest, records: () -> List<MediaRecord>): LibraryOriginalArchivePreview {
        LibraryOriginalArchive.requireCount(items.size())
        val originals = (0 until items.size()).map { index ->
            val item = requireNotNull(items.getMap(index))
            LibraryCollectionItem(requireNotNull(item.getString("id")), requireNotNull(item.getString("uri")), requireNotNull(item.getString("kind")))
        }
        require(originals.all { it.kind in setOf("IMAGE", "VIDEO") } && originals.map { it.id }.distinct().size == originals.size && originals.map { it.uri }.distinct().size == originals.size) { "Choose different photo or video originals" }
        ImportedPhotoOriginals.validate(originals.map { it.uri })
        val owner = requireNotNull(VideoPrivateOwner.namespace(context, EditorAccessMode.ACCOUNT)) { "Sign in to prepare an archive" }
        val started = System.nanoTime()
        fun active() { request.requireActive(); check(System.nanoTime() - started < 60_000_000_000L) { "Archive preparation timed out. Try fewer or smaller originals" } }
        fun current() { active(); requireCurrent(context, owner, originals, records) }
        current()
        val directory = File(context.cacheDir, "created-images")
        check(directory.canonicalFile == File(context.cacheDir.canonicalFile, "created-images"))
        check(directory.isDirectory || directory.mkdirs())
        directory.listFiles()?.filter { it.name.startsWith("noble-original-archive-") && it.extension == "zip" && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val file = File.createTempFile("noble-original-archive-", ".zip", directory)
        try {
            val types = originals.map { original -> current(); context.contentResolver.getType(Uri.parse(original.uri)) }
            val result = file.outputStream().use { output ->
                val completed = LibraryOriginalArchive.create(output, types, { index, use ->
                    current()
                    val asset = context.contentResolver.openAssetFileDescriptor(Uri.parse(originals[index].uri), "r", request.signal) ?: error("An original is unavailable")
                    asset.use { descriptor -> descriptor.createInputStream().use { input ->
                        request.bind(input)
                        try { use(input); current() } finally { request.detach(input) }
                    } }
                }, ::active)
                current(); output.fd.sync(); completed
            }
            current(); check(file.isFile && file.length() == result.bytes && result.bytes in 1..LibraryOriginalArchive.MAX_OUTPUT) { "Archive preparation did not complete" }
            return LibraryOriginalArchivePreview(owner, originals.toList(), file, result)
        } catch (error: OutOfMemoryError) { file.delete(); throw IllegalStateException("Not enough memory to prepare this archive", error) }
        catch (error: Throwable) { file.delete(); throw error }
    }

    fun share(context: ReactApplicationContext, preview: LibraryOriginalArchivePreview, request: LibraryDuplicateRequest, records: () -> List<MediaRecord>) {
        val started = System.nanoTime()
        fun active() { request.requireActive(); check(System.nanoTime() - started < 60_000_000_000L) { "Archive verification timed out. Prepare again" } }
        fun current() { active(); requireCurrent(context, preview.owner, preview.originals, records) }
        current()
        check(preview.file.isFile && preview.file.length() == preview.result.bytes && preview.result.bytes in 1..LibraryOriginalArchive.MAX_OUTPUT) { "Archive expired. Prepare another copy" }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        preview.file.inputStream().use { input ->
            request.bind(input)
            try {
                val buffer = ByteArray(32768)
                while (true) { active(); val count = input.read(buffer); if (count < 0) break; total += count; check(total <= LibraryOriginalArchive.MAX_OUTPUT); digest.update(buffer, 0, count) }
            } finally { request.detach(input) }
        }
        check(total == preview.result.bytes && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == preview.result.sha256) { "Archive changed. Prepare another copy" }
        current()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".creation", preview.file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Noble original archive", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share original archive").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        current(); context.startActivity(chooser)
    }
}
