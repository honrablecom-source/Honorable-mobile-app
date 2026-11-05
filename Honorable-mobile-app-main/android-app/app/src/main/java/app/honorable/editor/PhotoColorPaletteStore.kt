package app.honorable.editor

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** Names/colors only, in the caller's app-private no-backup directory. Never stores source media. */
class PhotoColorPaletteStore(directory: File, private val namespace: String) {
    private val directory = directory.absoluteFile
    private val file: File

    init {
        require(PhotoPresetNamespace.valid(namespace)) { "Invalid palette shelf" }
        file = File(this.directory, "$namespace.bin")
        checkPath()
    }

    class Deletion internal constructor(
        internal val shelf: String,
        internal val before: PhotoColorPalettes,
        internal val after: PhotoColorPalettes
    )

    fun load(): PhotoColorPalettes = synchronized(lock) { read() }

    fun save(palette: PhotoColorPalette, requireCurrent: () -> Unit) = synchronized(lock) {
        requireCurrent()
        write(read().add(palette), requireCurrent)
    }

    fun delete(palette: PhotoColorPalette, requireCurrent: () -> Unit): Deletion = synchronized(lock) {
        requireCurrent()
        val before = read()
        val after = before.remove(palette)
        write(after, requireCurrent)
        Deletion(file.path, before, after)
    }

    /** Refuse to replace any edits made to the shelf since deletion, including in another editor. */
    fun undo(deletion: Deletion, requireCurrent: () -> Unit) = synchronized(lock) {
        requireCurrent()
        require(deletion.shelf == file.path && read() == deletion.after) {
            "Saved palettes changed. Reopen the shelf; Undo is no longer available."
        }
        write(deletion.before, requireCurrent)
    }

    private fun checkPath() {
        check(directory.canonicalFile == directory && file.canonicalFile == file) {
            "Palette storage is unavailable"
        }
        check(!file.exists() || file.isFile) { "Palette storage is unavailable" }
    }

    private fun read(): PhotoColorPalettes {
        checkPath()
        if (!file.exists()) return PhotoColorPalettes()
        return file.inputStream().use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val remaining = PhotoColorPaletteCodec.MAX_BYTES + 1 - bytes.size()
                check(remaining > 0) { "Saved palette data is too large" }
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                bytes.write(buffer, 0, count)
                check(bytes.size() <= PhotoColorPaletteCodec.MAX_BYTES) { "Saved palette data is too large" }
            }
            PhotoColorPaletteCodec.decode(bytes.toByteArray())
        }
    }

    private fun write(catalog: PhotoColorPalettes, requireCurrent: () -> Unit) {
        val bytes = PhotoColorPaletteCodec.encode(catalog)
        checkPath()
        check(directory.isDirectory || directory.mkdirs()) { "Palette storage is unavailable" }
        val temporary = File.createTempFile("palette-", ".pending", directory)
        try {
            FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
            checkPath()
            requireCurrent()
            check(temporary.renameTo(file)) { "Unable to save palettes. Your previous shelf is preserved." }
        } finally {
            temporary.delete()
        }
    }

    companion object { private val lock = Any() }
}
