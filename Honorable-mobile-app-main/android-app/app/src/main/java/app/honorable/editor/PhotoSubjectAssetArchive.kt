package app.honorable.editor

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Only a generated-asset export format, never an importer or an external-media resolver. */
object PhotoSubjectAssetArchive {
    const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    const val MAX_PNG_BYTES = 32L * 1024 * 1024
    const val MAX_ZIP_BYTES = MAX_PNG_BYTES + 65536
    data class Entry(val subject: Int, val x: Int, val y: Int, val width: Int, val height: Int,
                     val visiblePixels: Int, val softPixels: Int, val bytes: Long, val sha256: String) {
        val name get() = "subjects/" + subject.toString().padStart(3, '0') + ".png"
    }
    class Encoded internal constructor(val file: File, val entry: Entry, internal val sourceSha256: String)
    class Prepared internal constructor(val file: File, val sha256: String, val bytes: Long,
                                       val entries: List<Entry>, val manifest: String)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun fileSha(file: File, maximum: Long, current: () -> Unit): Pair<String, Long> {
        current(); require(file.isFile && file.canonicalFile == file.absoluteFile && file.length() in 1..maximum)
        val digest = MessageDigest.getInstance("SHA-256"); var total = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(16384)
            while (true) { current(); val n = input.read(buffer); if (n < 0) break; require(n > 0 && n <= maximum - total); digest.update(buffer, 0, n); total += n }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } to total
    }
    private fun read(file: File, maximum: Long, current: () -> Unit): ByteArray {
        current(); require(file.isFile && file.canonicalFile == file.absoluteFile && file.length() in 1..maximum)
        return file.inputStream().use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(16384); var total = 0L
            while (true) { current(); val n = input.read(buffer); if (n < 0) break; require(n > 0 && n <= maximum - total); out.write(buffer, 0, n); total += n }
            out.toByteArray()
        }
    }
    /** CRC, exact dimensions, bounded zlib output and valid row filters; no original EXIF/text chunks. */
    private fun png(bytes: ByteArray, width: Int, height: Int, current: () -> Unit) {
        require(bytes.size in 57..MAX_IMAGE_BYTES && bytes.take(8) == listOf<Byte>(-119, 80, 78, 71, 13, 10, 26, 10)) { "Invalid cutout PNG" }
        val view = ByteBuffer.wrap(bytes); var offset = 8; var header = false; var ended = false; var channels = 0
        val data = ByteArrayOutputStream()
        while (offset < bytes.size) {
            current(); require(offset <= bytes.size - 12 && !ended)
            val count = view.getInt(offset); require(count >= 0 && count <= bytes.size - offset - 12)
            val kind = bytes.copyOfRange(offset + 4, offset + 8).toString(Charsets.US_ASCII)
            val crc = CRC32().apply { update(bytes, offset + 4, count + 4) }
            require(crc.value == (view.getInt(offset + 8 + count).toLong() and 0xffffffffL)) { "Cutout PNG CRC changed" }
            if (!header) require(kind == "IHDR")
            when (kind) {
                "IHDR" -> {
                    require(!header && count == 13 && view.getInt(offset + 8) == width && view.getInt(offset + 12) == height)
                    require(bytes[offset + 16].toInt() == 8 && bytes[offset + 18].toInt() == 0 && bytes[offset + 19].toInt() == 0 && bytes[offset + 20].toInt() == 0)
                    channels = when (bytes[offset + 17].toInt()) { 2 -> 3; 6 -> 4; else -> error("Use true-color cutout PNG") }; header = true
                }
                "IDAT" -> { require(header && count <= MAX_IMAGE_BYTES - data.size()); data.write(bytes, offset + 8, count) }
                "IEND" -> { require(count == 0 && data.size() > 0 && offset + 12 == bytes.size); ended = true }
                "sRGB", "sBIT", "gAMA", "cHRM" -> require(header && count <= 32)
                else -> error("Unexpected cutout PNG metadata")
            }
            offset += count + 12
        }
        require(header && ended && width in 1..PhotoSubjectAssets.MAX_EDGE && height in 1..PhotoSubjectAssets.MAX_EDGE)
        val row = width * channels + 1; val expected = row.toLong() * height; var total = 0L
        val inflater = Inflater()
        try {
            inflater.setInput(data.toByteArray()); val output = ByteArray(16384)
            while (!inflater.finished()) {
                current(); val n = inflater.inflate(output)
                require(n > 0 || inflater.finished()) { "Incomplete cutout PNG data" }
                require(n <= expected - total) { "Cutout PNG expands beyond its image bounds" }
                for (i in 0 until n) if ((total + i) % row == 0L) require(output[i].toInt() in 0..4) { "Invalid cutout PNG row filter" }
                total += n
            }
            require(total == expected && inflater.remaining == 0) { "Cutout PNG pixels are incomplete or have trailing data" }
        } finally { inflater.end() }
    }
    fun capture(file: File, cutout: PhotoSubjectAssets.Cutout, current: () -> Unit = {}): Encoded {
        val bytes = read(file, MAX_IMAGE_BYTES.toLong(), current); png(bytes, cutout.width, cutout.height, current)
        return Encoded(file, Entry(cutout.subject, cutout.x, cutout.y, cutout.width, cutout.height, cutout.visiblePixels, cutout.softPixels, bytes.size.toLong(), sha(bytes)), cutout.sourceSha256)
    }
    private fun manifest(source: PhotoSubjectAssets.Source, entries: List<Entry>) = buildString {
        append("{\"format\":\"Noble AI subject assets\",\"version\":1,\"model\":\"ML Kit Subject Segmentation beta\",\"grid\":{\"width\":${source.width},\"height\":${source.height},\"argbSha256\":\"${source.sha256}\"},\"subjects\":[")
        entries.forEachIndexed { index, e ->
            if (index > 0) append(',')
            append("{\"subject\":${e.subject},\"file\":\"${e.name}\",\"x\":${e.x},\"y\":${e.y},\"width\":${e.width},\"height\":${e.height},\"visiblePixels\":${e.visiblePixels},\"softPixels\":${e.softPixels},\"bytes\":${e.bytes},\"sha256\":\"${e.sha256}\"}")
        }
        append("],\"note\":\"Cropped soft masks from an opaque edited preview, at most 1024 pixels per edge. Not full-resolution originals. Nearby subjects may merge; inspect edges. Faint pixels can retain background detail; not privacy redaction. No full original photo file, URI, filename or EXIF is included. Grid fingerprint uses big-endian width,height,ARGB words.\"}")
    }
    fun create(file: File, source: PhotoSubjectAssets.Source, selected: List<Encoded>, current: () -> Unit = {}): Prepared {
        require(file.isFile && file.canonicalFile == file.absoluteFile && file.length() == 0L) { "Create a fresh private asset-pack output" }
        try {
            current(); require(selected.size in 1..PhotoSubjectAssets.MAX_SUBJECTS && selected.map { it.entry.subject }.distinct().size == selected.size) { "Select each desired subject once" }
            val sorted = selected.sortedBy { it.entry.subject }; var total = 0L; var pixels = 0L
            sorted.forEach { value ->
                val e = value.entry; current(); require(value.sourceSha256 == source.sha256) { "Cutout belongs to another preview" }
                require(e.subject in 1..PhotoSubjectAssets.MAX_SUBJECTS && e.x >= 0 && e.y >= 0 && e.width in 1..source.width && e.height in 1..source.height &&
                    e.x.toLong() + e.width <= source.width && e.y.toLong() + e.height <= source.height && e.visiblePixels in 1..e.width * e.height && e.softPixels in 0..e.visiblePixels)
                require(e.bytes in 1..MAX_IMAGE_BYTES.toLong() && e.bytes <= MAX_PNG_BYTES - total && e.sha256.matches(Regex("[a-f0-9]{64}")))
                val area = e.width.toLong() * e.height; require(area <= PhotoSubjectAssets.MAX_MASK_PIXELS - pixels); pixels += area; total += e.bytes
            }
            val entries = Collections.unmodifiableList(sorted.map { it.entry }); val json = manifest(source, entries); val metadata = json.toByteArray(Charsets.UTF_8); require(metadata.size <= 16384)
            FileOutputStream(file).use { raw ->
                var written = 0L; var failed = false
                val bounded = object : OutputStream() {
                    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        check(!failed)
                        try { current(); require(offset >= 0 && length >= 0 && offset <= bytes.size - length && length <= MAX_ZIP_BYTES - written); raw.write(bytes, offset, length); written += length }
                        catch (error: Throwable) { failed = true; throw error }
                    }
                    override fun flush() = raw.flush()
                }
                ZipOutputStream(bounded).use { zip ->
                    zip.setLevel(0)
                    sorted.forEach { value ->
                        current(); val bytes = read(value.file, MAX_IMAGE_BYTES.toLong(), current)
                        require(bytes.size.toLong() == value.entry.bytes && sha(bytes) == value.entry.sha256) { "Reviewed cutout changed" }
                        png(bytes, value.entry.width, value.entry.height, current)
                        zip.putNextEntry(ZipEntry(value.entry.name).apply { time = 0 }); zip.write(bytes); zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry("manifest.json").apply { time = 0 }); zip.write(metadata); zip.closeEntry(); zip.finish(); zip.flush(); raw.fd.sync()
                }
            }
            val fingerprint = fileSha(file, MAX_ZIP_BYTES, current)
            return Prepared(file, fingerprint.first, fingerprint.second, entries, json).also { verify(it, current) }
        } catch (error: Throwable) { file.delete(); throw error }
    }
    fun verify(value: Prepared, current: () -> Unit = {}) {
        val fingerprint = fileSha(value.file, MAX_ZIP_BYTES, current)
        require(fingerprint.second == value.bytes && fingerprint.first == value.sha256) { "Reviewed asset pack changed" }
        ZipFile(value.file).use { zip ->
            val expected = value.entries.map { it.name } + "manifest.json"
            require(zip.entries().toList().map { it.name } == expected && zip.comment == null)
            for (name in expected) {
                current(); val entry = requireNotNull(zip.getEntry(name)); val image = value.entries.firstOrNull { it.name == name }
                val size = image?.bytes ?: value.manifest.toByteArray(Charsets.UTF_8).size.toLong(); require(entry.size == size)
                val metadata = if (image == null) ByteArrayOutputStream() else null
                val digest = MessageDigest.getInstance("SHA-256"); val crc = CRC32(); var total = 0L
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(16384)
                    while (true) { current(); val n = input.read(buffer); if (n < 0) break; require(n > 0 && n <= size - total); metadata?.write(buffer, 0, n); digest.update(buffer, 0, n); crc.update(buffer, 0, n); total += n }
                }
                require(total == size && crc.value == entry.crc)
                if (image != null) require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == image.sha256)
                else require(requireNotNull(metadata).toByteArray().contentEquals(value.manifest.toByteArray(Charsets.UTF_8)))
            }
        }
    }
}
