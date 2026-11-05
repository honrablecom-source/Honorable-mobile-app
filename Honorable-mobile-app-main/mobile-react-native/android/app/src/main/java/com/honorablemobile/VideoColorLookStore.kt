package com.honorablemobile

import android.util.AtomicFile
import app.honorable.editor.PhotoPresetNamespace
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException

/** Private no-backup owner namespaces. Corrupt reads never turn into writable empty shelves. */
internal class VideoColorLookStore(directory: File, namespace: String) {
    init { require(PhotoPresetNamespace.valid(namespace)) }
    private val file = AtomicFile(File(directory, "$namespace.bin"))
    fun load(): VideoColorLookCatalog = synchronized(lock) { read() }
    fun add(look: NamedVideoColorLook, requireCurrent: () -> Unit) = synchronized(lock) { write(read().add(look), requireCurrent) }
    fun remove(look: NamedVideoColorLook, requireCurrent: () -> Unit) = synchronized(lock) { write(read().remove(look), requireCurrent) }
    private fun read(): VideoColorLookCatalog {
        val stream = try { file.openRead() } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw missing
            return VideoColorLookCatalog()
        }
        stream.use { input ->
            val result = ByteArrayOutputStream(); val buffer = ByteArray(1024)
            while (true) {
                val remaining = VideoColorLookCodec.MAX_BYTES + 1 - result.size(); require(remaining > 0)
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                result.write(buffer, 0, count); require(result.size() <= VideoColorLookCodec.MAX_BYTES)
            }
            return VideoColorLookCodec.decode(result.toByteArray())
        }
    }
    private fun write(catalog: VideoColorLookCatalog, requireCurrent: () -> Unit) {
        val bytes = VideoColorLookCodec.encode(catalog); requireCurrent()
        val directory = requireNotNull(file.baseFile.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Video look storage is unavailable" }
        val output = file.startWrite()
        try {
            output.write(bytes); requireCurrent(); file.finishWrite(output)
            check(read() == catalog) { "Saved video look could not be verified" }
        } catch (failure: Throwable) { file.failWrite(output); throw failure }
    }
    companion object { private val lock = Any() }
}
