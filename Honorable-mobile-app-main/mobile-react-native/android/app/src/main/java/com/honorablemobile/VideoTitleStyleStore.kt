package com.honorablemobile

import app.honorable.editor.PhotoPresetNamespace

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** Names/title appearance only, in the caller's app-private no-backup directory. Never stores source media. */
internal class VideoTitleStyleStore(directory: File, private val namespace: String) {
    private val directory = directory.absoluteFile
    private val file: File

    init {
        require(PhotoPresetNamespace.valid(namespace)) { "Invalid title style shelf" }
        file = File(this.directory, "$namespace.bin")
        checkPath()
    }

    class Deletion internal constructor(
        internal val shelf: String,
        internal val before: VideoTitleStyleCatalog,
        internal val after: VideoTitleStyleCatalog
    )

    fun load(): VideoTitleStyleCatalog = synchronized(lock) { read() }

    fun save(style: NamedVideoTitleStyle, requireCurrent: () -> Unit) = synchronized(lock) {
        requireCurrent()
        write(read().add(style), requireCurrent)
    }

    fun delete(style: NamedVideoTitleStyle, requireCurrent: () -> Unit): Deletion = synchronized(lock) {
        requireCurrent()
        val before = read()
        val after = before.remove(style)
        write(after, requireCurrent)
        Deletion(file.path, before, after)
    }

    /** Refuse to replace any edits made to the shelf since deletion, including in another editor. */
    fun undo(deletion: Deletion, requireCurrent: () -> Unit) = synchronized(lock) {
        requireCurrent()
        require(deletion.shelf == file.path && read() == deletion.after) {
            "Saved title styles changed. Reopen the shelf; Undo is no longer available."
        }
        write(deletion.before, requireCurrent)
    }

    private fun checkPath() {
        check(directory.canonicalFile == directory && file.canonicalFile == file) {
            "Title style storage is unavailable"
        }
        check(!file.exists() || file.isFile) { "Title style storage is unavailable" }
    }

    private fun read(): VideoTitleStyleCatalog {
        checkPath()
        if (!file.exists()) return VideoTitleStyleCatalog()
        return file.inputStream().use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val remaining = VideoTitleStyleCodec.MAX_BYTES + 1 - bytes.size()
                check(remaining > 0) { "Saved title style data is too large" }
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                bytes.write(buffer, 0, count)
                check(bytes.size() <= VideoTitleStyleCodec.MAX_BYTES) { "Saved title style data is too large" }
            }
            VideoTitleStyleCodec.decode(bytes.toByteArray())
        }
    }

    private fun write(catalog: VideoTitleStyleCatalog, requireCurrent: () -> Unit) {
        val bytes = VideoTitleStyleCodec.encode(catalog)
        checkPath()
        check(directory.isDirectory || directory.mkdirs()) { "Title style storage is unavailable" }
        val temporary = File.createTempFile("title-style-", ".pending", directory)
        try {
            FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
            checkPath()
            requireCurrent()
            check(temporary.renameTo(file)) { "Unable to save title styles. Your previous shelf is preserved." }
            check(read() == catalog) { "Saved title styles could not be verified. Reopen the shelf." }
        } finally {
            temporary.delete()
        }
    }

    companion object { private val lock = Any() }
}
