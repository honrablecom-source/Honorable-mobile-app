package com.honorablemobile

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal object VideoSubtitleDocuments {
    fun readBounded(input: InputStream): ByteArray = ByteArrayOutputStream().use { output ->
        val buffer = ByteArray(4096)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Subtitle operation cancelled")
            val count = input.read(buffer)
            if (count < 0) break
            require(count > 0) { "Cannot read subtitle data" }
            require(output.size() + count <= VideoCaptions.MAX_SRT_BYTES) { "Choose an SRT file up to 128 KB" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    /** Verify URI/inode separation from all project originals before truncating a new sidecar. */
    fun writeCopy(context: Context, destination: Uri, bytes: ByteArray, originals: Set<Uri>) {
        require(bytes.size in 1..VideoCaptions.MAX_SRT_BYTES && originals.isNotEmpty())
        require(destination !in originals) { "Subtitle copy cannot replace an imported original" }
        val resolver = context.contentResolver
        var separate = false
        try {
            resolver.openFileDescriptor(destination, "rw").use { output ->
                requireNotNull(output)
                val target = Os.fstat(output.fileDescriptor)
                for (original in originals) resolver.openFileDescriptor(original, "r").use { input ->
                    if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Subtitle operation cancelled")
                    requireNotNull(input) { "An original is unavailable" }
                    val source = Os.fstat(input.fileDescriptor)
                    require(source.st_ino != 0L && target.st_ino != 0L)
                    require(source.st_dev != target.st_dev || source.st_ino != target.st_ino) { "Subtitle copy cannot replace an imported original" }
                }
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Subtitle operation cancelled")
                separate = true
                Os.ftruncate(output.fileDescriptor, 0)
                ParcelFileDescriptor.AutoCloseOutputStream(output.dup()).use { it.write(bytes); it.flush() }
            }
            val saved = resolver.openInputStream(destination)?.use { readBounded(it) } ?: error("Cannot verify subtitle copy")
            require(saved.contentEquals(bytes)) { "Saved subtitles differ from the exported copy" }
        } catch (error: Throwable) {
            val removed = separate && runCatching { DocumentsContract.deleteDocument(resolver, destination) }.getOrDefault(false)
            throw IllegalStateException("${error.message ?: "Subtitle export failed"}${if (separate && !removed) ". Check Files for a partial copy." else ""}", error)
        }
    }
}
