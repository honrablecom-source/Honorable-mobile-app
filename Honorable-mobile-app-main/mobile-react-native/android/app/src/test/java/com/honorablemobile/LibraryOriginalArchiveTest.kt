package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class LibraryOriginalArchiveTest {
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun unpack(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; assertFalse(result.containsKey(entry.name)); result[entry.name] = zip.readBytes(); zip.closeEntry() }
        }
        return result
    }
    @Test fun independentZipReaderRecoversEveryOriginalInOrderAndChecksumsMatch() {
        val originals = listOf(byteArrayOf(0, 1, -1, 42), ByteArray(80000) { (it * 17).toByte() })
        val output = ByteArrayOutputStream()
        val result = LibraryOriginalArchive.create(output, listOf("image/jpeg", "video/mp4"), { index, use -> use(ByteArrayInputStream(originals[index])) })
        val files = unpack(output.toByteArray())
        assertEquals(listOf("originals/001.jpg", "originals/002.mp4", "manifest.json"), files.keys.toList())
        originals.forEachIndexed { index, source ->
            val entry = result.entries[index]; assertArrayEquals(source, files[entry.name]); assertEquals(source.size.toLong(), entry.bytes)
            assertEquals(digest(source), entry.sha256)
        }
        val manifest = String(files.getValue("manifest.json"), Charsets.UTF_8)
        assertTrue(manifest.contains("\"originalMetadataIncluded\":true"))
        result.entries.forEach { assertTrue(manifest.contains("\"sha256\":\"${it.sha256}\"")); assertTrue(manifest.contains("\"bytes\":${it.bytes}")) }
        assertEquals(output.size().toLong(), result.bytes); assertEquals(digest(output.toByteArray()), result.sha256)
    }
    @Test fun untrustedMimeCannotInjectPathsOrManifestContent() {
        val output = ByteArrayOutputStream()
        val result = LibraryOriginalArchive.create(output, listOf("../../private\"\n", null), { _, use -> use(ByteArrayInputStream(byteArrayOf(7))) })
        assertEquals(listOf("originals/001.bin", "originals/002.bin"), result.entries.map { it.name })
        assertTrue(result.entries.all { it.mime == "application/octet-stream" })
        assertFalse(String(unpack(output.toByteArray()).getValue("manifest.json")).contains("private"))
    }
    @Test fun maximumCountIsDistinctAndStable() {
        val output = ByteArrayOutputStream(); var reads = 0
        val result = LibraryOriginalArchive.create(output, List(20) { "image/png" }, { index, use -> reads++; use(ByteArrayInputStream(byteArrayOf(index.toByte()))) })
        assertEquals(20, reads); assertEquals(20, result.entries.size); assertEquals(21, unpack(output.toByteArray()).size)
        assertEquals("originals/020.png", result.entries.last().name)
    }
    @Test fun emptyOrMissingOriginalCannotProduceSuccess() {
        for (missing in listOf(false, true)) {
            try { LibraryOriginalArchive.create(ByteArrayOutputStream(), listOf(null), { _, use -> if (!missing) use(ByteArrayInputStream(byteArrayOf())) }); fail("Accepted missing/empty original") }
            catch (_: IllegalStateException) { }
        }
    }
    @Test fun sourceFailurePropagatesInsteadOfPublishingTruncatedArchive() {
        try { LibraryOriginalArchive.create(ByteArrayOutputStream(), listOf(null), { _, _ -> throw IOException("Synthetic provider failure") }); fail("Accepted source failure") }
        catch (error: IOException) { assertEquals("Synthetic provider failure", error.message) }
    }
    @Test fun destinationFailureLatchesWithoutWritingAnotherFooter() {
        var calls = 0
        val output = object : OutputStream() { override fun write(value: Int) { calls++; throw IOException("Synthetic destination failure") } }
        try { LibraryOriginalArchive.create(output, listOf(null), { _, use -> use(ByteArrayInputStream(byteArrayOf(1))) }); fail("Accepted destination failure") }
        catch (error: IOException) { assertEquals("Synthetic destination failure", error.message) }
        assertEquals(1, calls)
    }
    @Test fun cancellationRefusesResultAndStopsBeforeOpeningNextOriginal() {
        var reads = 0; var cancelled = false
        try {
            LibraryOriginalArchive.create(ByteArrayOutputStream(), listOf(null, null), { _, use -> reads++; use(ByteArrayInputStream(byteArrayOf(1))); cancelled = true }, { check(!cancelled) { "Synthetic cancel" } })
            fail("Returned after cancel")
        } catch (error: IllegalStateException) { assertEquals("Synthetic cancel", error.message) }
        assertEquals(1, reads)
    }
    @Test fun invalidCountsRefuseBeforeReadingOrWriting() {
        for (count in listOf(0, 21)) {
            val output = ByteArrayOutputStream()
            try { LibraryOriginalArchive.create(output, List(count) { null }, { _, _ -> fail("Read invalid selection") }); fail("Accepted invalid selection") }
            catch (_: IllegalArgumentException) { }
            assertEquals(0, output.size())
        }
    }
    @Test fun exactReadBoundaryIsRefusedWithoutRequiringAnUnboundedByteArray() {
        var remaining = 64L * 1024 * 1024
        val source = object : InputStream() {
            override fun read(): Int { if (remaining == 0L) return -1; remaining--; return 42 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining == 0L) return -1
                val count = minOf(length.toLong(), remaining).toInt()
                bytes.fill(42, offset, offset + count); remaining -= count; return count
            }
        }
        val destination = object : OutputStream() {
            override fun write(value: Int) { }
            override fun write(bytes: ByteArray, offset: Int, length: Int) { }
        }
        try { LibraryOriginalArchive.create(destination, listOf(null), { _, use -> use(source) }); fail("Accepted exact source limit") }
        catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("read limit")) }
        assertEquals(0L, remaining)
    }
    @Test fun zeroProgressSourceCannotHangOrProduceSuccess() {
        val source = object : InputStream() {
            override fun read() = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int) = 0
        }
        try { LibraryOriginalArchive.create(ByteArrayOutputStream(), listOf(null), { _, use -> use(source) }); fail("Accepted stalled source") }
        catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("no progress")) }
    }
}
