package app.honorable.editor

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException

/** App-private, no-backup shelf. Names are payload labels, never filesystem paths. */
class PhotoPresetStore(directory: File, namespace: String) {
    init { require(PhotoPresetNamespace.valid(namespace)) }
    private val file = AtomicFile(File(directory, "$namespace.bin"))

    fun load(): PhotoPresetCatalog = synchronized(lock) { read() }
    fun add(preset: NamedPhotoPreset) = synchronized(lock) { write(read().add(preset)) }
    fun remove(name: String) = synchronized(lock) { write(read().remove(name)) }

    private fun read(): PhotoPresetCatalog {
        val input = try { file.openRead() } catch (missing: FileNotFoundException) {
            // An unreadable existing shelf must never be treated as an empty one and overwritten.
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw missing
            return PhotoPresetCatalog()
        }
        return input.use {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val remaining = PhotoPresetCodec.MAX_BYTES + 1 - bytes.size()
                require(remaining > 0) { "Saved looks exceed the local storage limit" }
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                bytes.write(buffer, 0, count)
                require(bytes.size() <= PhotoPresetCodec.MAX_BYTES) { "Saved looks exceed the local storage limit" }
            }
            PhotoPresetCodec.decode(bytes.toByteArray())
        }
    }

    private fun write(catalog: PhotoPresetCatalog) {
        val bytes = PhotoPresetCodec.encode(catalog)
        val directory = requireNotNull(file.baseFile.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Saved looks storage is unavailable" }
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }

    companion object { private val lock = Any() }
}
