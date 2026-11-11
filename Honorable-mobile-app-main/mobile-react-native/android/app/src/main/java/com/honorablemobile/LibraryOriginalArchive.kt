package com.honorablemobile

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Original bytes only. No source names, URIs, paths or EXIF values in the manifest. */
internal object LibraryOriginalArchive {
    const val MAX_ITEMS = 20
    const val MAX_OUTPUT = 257L * 1024 * 1024
    data class Entry(val name: String, val mime: String, val bytes: Long, val sha256: String)
    data class Result(val entries: List<Entry>, val bytes: Long, val sha256: String)
    private val extensions = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp",
        "image/heic" to "heic", "image/heif" to "heif", "image/avif" to "avif", "image/gif" to "gif",
        "video/mp4" to "mp4", "video/webm" to "webm", "video/quicktime" to "mov")
    fun requireCount(count: Int) { require(count in 1..MAX_ITEMS) { "Choose one to twenty originals" } }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }

    fun create(output: OutputStream, mimeTypes: List<String?>,
               read: (Int, (InputStream) -> Unit) -> Unit, current: () -> Unit = {}): Result {
        requireCount(mimeTypes.size)
        val budget = PhotoDifferenceReadBudget(current = current)
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        var failed = false
        val bounded = object : OutputStream() {
            override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                check(!failed) { "Archive output already failed" }
                try {
                    current()
                    require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
                    check(length.toLong() <= MAX_OUTPUT - written) { "Archive is too large" }
                    output.write(bytes, offset, length); digest.update(bytes, offset, length); written += length
                } catch (error: Throwable) { failed = true; throw error }
            }
            override fun flush() { check(!failed); try { current(); output.flush() } catch (error: Throwable) { failed = true; throw error } }
            // The caller owns the destination and deletes every failed temporary archive.
        }
        val entries = mutableListOf<Entry>()
        ZipOutputStream(bounded).use { zip ->
            zip.setLevel(0)
            mimeTypes.forEachIndexed { index, supplied ->
                current()
                val mime = supplied?.takeIf { it in extensions } ?: "application/octet-stream"
                val name = "originals/" + (index + 1).toString().padStart(3, '0') + "." + (extensions[mime] ?: "bin")
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                val fileDigest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                var visited = false
                read(index) { source ->
                    check(!visited) { "Original source supplied twice" }; visited = true
                    val limited = budget.wrap(source)
                    val buffer = ByteArray(32768)
                    while (true) {
                        current()
                        val count = limited.read(buffer)
                        if (count < 0) break
                        check(count > 0) { "Original stream made no progress" }
                        zip.write(buffer, 0, count); fileDigest.update(buffer, 0, count); size += count
                    }
                    limited.finish()
                }
                check(visited && size > 0) { "An original is empty or unavailable" }
                current(); zip.closeEntry()
                entries += Entry(name, mime, size, fileDigest.digest().hex())
            }
            val manifest = "{\"format\":\"noble-originals-v1\",\"originalMetadataIncluded\":true,\"entries\":[" +
                entries.joinToString(",") { "{\"name\":\"${it.name}\",\"mime\":\"${it.mime}\",\"bytes\":${it.bytes},\"sha256\":\"${it.sha256}\"}" } + "]}\n"
            zip.putNextEntry(ZipEntry("manifest.json").apply { time = 0L })
            zip.write(manifest.toByteArray(Charsets.UTF_8)); zip.closeEntry(); current()
        }
        check(!failed); current()
        return Result(entries.toList(), written, digest.digest().hex())
    }
}
