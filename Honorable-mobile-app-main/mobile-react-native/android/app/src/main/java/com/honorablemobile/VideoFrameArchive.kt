package com.honorablemobile

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** A portable, URI-free record of requested nearest-frame samples, not exact decoded timestamps. */
internal object VideoFrameArchive {
    const val MAX_PNG_BYTES = 2 * 1024 * 1024
    const val MAX_TOTAL_PNG_BYTES = 16 * 1024 * 1024
    const val MAX_ZIP_BYTES = 20 * 1024 * 1024
    val signature: ByteArray get() = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    data class Plan(val fromMs: Long, val toMs: Long, val count: Int) {
        init { require(fromMs >= 0 && toMs > fromMs && toMs <= Int.MAX_VALUE && count in setOf(4, 8, 16) && toMs - fromMs >= count) }
        val times: List<Long> get() = (0 until count).map { fromMs + (2L * it + 1) * (toMs - fromMs) / (2 * count) }
    }
    data class Frame(val png: ByteArray, val width: Int, val height: Int)
    data class Entry(val name: String, val requestedMs: Long, val width: Int, val height: Int, val bytes: Int, val sha256: String)
    class Prepared(val file: File, val plan: Plan, entries: List<Entry>, val digest: String) {
        val entries: List<Entry> = java.util.Collections.unmodifiableList(entries.toList())
        val token = java.util.UUID.randomUUID().toString()
        fun dispose() { file.delete() }
    }
    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun manifest(plan: Plan, entries: List<Entry>): ByteArray = ("{\"version\":1,\"sampling\":\"requested nearest source frame; actual decoded timestamps unavailable\",\"colour\":\"SDR PNG; no HDR/profile preservation\",\"fromMs\":" + plan.fromMs + ",\"toExclusiveMs\":" + plan.toMs + ",\"frames\":[" + entries.joinToString(",") {
        "{\"file\":\"${it.name}\",\"requestedMs\":${it.requestedMs},\"width\":${it.width},\"height\":${it.height},\"bytes\":${it.bytes},\"sha256\":\"${it.sha256}\"}"
    } + "]}\n").toByteArray(Charsets.UTF_8)
    class Budget(private val output: OutputStream, private val current: () -> Unit, private val limit: Int = MAX_ZIP_BYTES) : OutputStream() {
        private var size = 0; private var failed = false
        init { require(limit in 1..MAX_ZIP_BYTES) }
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            check(!failed)
            try { current(); require(offset >= 0 && length >= 0 && offset <= bytes.size - length); check(length <= limit - size) { "Archive exceeds its limit" }; output.write(bytes, offset, length); size += length }
            catch (error: Throwable) { failed = true; throw error }
        }
    }
    private fun fileSha(file: File, current: () -> Unit): String {
        current(); check(file.isFile && file.length() in 1..MAX_ZIP_BYTES.toLong()) { "Archive expired or oversized" }
        val digest = MessageDigest.getInstance("SHA-256"); var count = 0
        file.inputStream().use { input -> val buffer = ByteArray(32768); while (true) { current(); val n = input.read(buffer); if (n < 0) break; check(n <= MAX_ZIP_BYTES - count); count += n; digest.update(buffer, 0, n) } }
        current(); return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun create(file: File, plan: Plan, current: () -> Unit = {}, frame: (Int, Long) -> Frame): Prepared {
        val entries = mutableListOf<Entry>(); var total = 0
        try {
            FileOutputStream(file).use { output ->
                ZipOutputStream(Budget(output, current)).use { zip ->
                    // Finish the ZIP before syncing its still-open descriptor.
                    plan.times.forEachIndexed { index, time ->
                        current(); val value = frame(index, time); current()
                        require(value.width in 1..512 && value.height in 1..512 && value.png.size in 8..MAX_PNG_BYTES && value.png.take(8).toByteArray().contentEquals(signature)) { "Invalid prepared frame" }
                        check(value.png.size <= MAX_TOTAL_PNG_BYTES - total) { "All frames exceed 16 MiB; try fewer samples" }; total += value.png.size
                        if (entries.isNotEmpty()) check(entries.first().width == value.width && entries.first().height == value.height) { "Frame dimensions changed" }
                        val entry = Entry("frame-" + (index + 1).toString().padStart(2, '0') + ".png", time, value.width, value.height, value.png.size, sha(value.png))
                        zip.putNextEntry(ZipEntry(entry.name).apply { this.time = 0 }); zip.write(value.png); zip.closeEntry(); entries += entry
                    }
                    zip.putNextEntry(ZipEntry("manifest.json").apply { time = 0 }); zip.write(manifest(plan, entries)); zip.closeEntry(); zip.finish(); zip.flush(); output.fd.sync()
                }
            }
            current(); return Prepared(file, plan, entries, fileSha(file, current)).also { verify(it, current) }
        } catch (error: Throwable) { file.delete(); throw error }
    }
    fun read(zip: ZipFile, entry: Entry, current: () -> Unit): ByteArray {
        current(); val item = requireNotNull(zip.getEntry(entry.name)); check(item.size == entry.bytes.toLong() && item.size <= MAX_PNG_BYTES)
        val result = java.io.ByteArrayOutputStream()
        zip.getInputStream(item).use { input -> val buffer = ByteArray(32768); while (true) { current(); val n = input.read(buffer); if (n < 0) break; check(n <= entry.bytes - result.size()); result.write(buffer, 0, n) } }
        return result.toByteArray().also { check(it.size == entry.bytes && sha(it) == entry.sha256) { "Prepared frame changed" }; current() }
    }
    fun verify(value: Prepared, current: () -> Unit = {}) {
        check(fileSha(value.file, current) == value.digest) { "Reviewed archive changed" }
        check(value.entries.size == value.plan.count)
        ZipFile(value.file).use { zip ->
            val expected = value.entries.map { it.name } + "manifest.json"
            val actual = zip.entries().toList().map { it.name }; check(actual == expected && actual.distinct().size == actual.size)
            value.entries.forEach { read(zip, it, current) }
            val meta = requireNotNull(zip.getEntry("manifest.json")); val encoded = manifest(value.plan, value.entries); check(meta.size == encoded.size.toLong() && meta.size <= 16384)
            zip.getInputStream(meta).use { input -> val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(1024); while (true) { current(); val n = input.read(buffer); if (n < 0) break; check(n <= encoded.size - output.size()); output.write(buffer, 0, n) }; check(output.toByteArray().contentEquals(encoded)) }
        }
        current()
    }
}
