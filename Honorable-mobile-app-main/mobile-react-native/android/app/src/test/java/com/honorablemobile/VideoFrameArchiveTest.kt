package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.random.Random

class VideoFrameArchiveTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    // Deliberately synthetic bytes: these tests verify archive integrity, not Android PNG decoding.
    private fun png(index: Int, size: Int = 128): ByteArray = Random(index).nextBytes(size).also { VideoFrameArchive.signature.copyInto(it) }
    private fun temp() = Files.createTempFile("noble-archive-test-", ".zip").toFile()
    private val plan = VideoFrameArchive.Plan(100, 1100, 4)
    private fun frame(index: Int) = VideoFrameArchive.Frame(png(index), 320, 180)
    @Test fun thousandPlansUseDistinctHalfOpenCenterSamplesWithoutLongOverflow() {
        val random = Random(162)
        repeat(1000) {
            val n = listOf(4, 8, 16)[random.nextInt(3)]; val from = random.nextLong(0, Int.MAX_VALUE.toLong() - 10000); val to = from + random.nextInt(n, 10000)
            val times = VideoFrameArchive.Plan(from, to, n).times
            assertEquals(n, times.distinct().size); assertTrue(times.all { it in from until to })
            times.forEachIndexed { index, value -> val oracle = from + (index + .5) * (to - from) / n; assertTrue(value <= oracle && oracle - value < 1.0) }
        }
        for ((a, b) in listOf(-1L to 5L, 0L to 3L, 1L to 1L, 0L to Int.MAX_VALUE.toLong() + 1, Long.MAX_VALUE to Long.MIN_VALUE)) assertThrows(IllegalArgumentException::class.java) { VideoFrameArchive.Plan(a, b, 4) }
        for (n in listOf(0, 1, 3, 5, 32)) assertThrows(IllegalArgumentException::class.java) { VideoFrameArchive.Plan(0, 1000, n) }
    }
    @Test fun everyArchivedByteTimestampDimensionAndChecksumMatchesIndependentInputs() {
        for (count in listOf(4, 8, 16)) {
            val file = temp(); val p = VideoFrameArchive.Plan(100, 16100, count); val inputs = List(count) { png(it, 1000 + it * 73) }; var seen = 0
            try {
                val value = VideoFrameArchive.create(file, p) { index, time -> assertEquals(seen++, index); assertEquals(p.times[index], time); VideoFrameArchive.Frame(inputs[index], 512, 288) }
                assertEquals(count, seen); assertEquals(sha(file.readBytes()), value.digest)
                ZipFile(file).use { zip ->
                    assertEquals(count + 1, zip.size())
                    inputs.forEachIndexed { index, bytes ->
                        val name = "frame-" + (index + 1).toString().padStart(2, '0') + ".png"
                        assertArrayEquals(bytes, zip.getInputStream(zip.getEntry(name)).use { it.readBytes() })
                        assertEquals(VideoFrameArchive.Entry(name, p.times[index], 512, 288, bytes.size, sha(bytes)), value.entries[index])
                    }
                    val manifest = zip.getInputStream(zip.getEntry("manifest.json")).use { it.readBytes().toString(Charsets.UTF_8) }
                    assertTrue(manifest.startsWith("{\"version\":1,")); assertTrue(manifest.contains("\"fromMs\":100,\"toExclusiveMs\":16100"))
                    assertEquals(count, Regex("\"requestedMs\":").findAll(manifest).count()); assertEquals(count, Regex("\"sha256\":").findAll(manifest).count())
                    assertFalse(manifest.contains("content://")); assertFalse(manifest.contains(file.name)); assertTrue(manifest.endsWith("]}\n"))
                }
                assertThrows(UnsupportedOperationException::class.java) { (value.entries as MutableList).clear() }
                VideoFrameArchive.verify(value)
                System.getProperty("noble.archiveEvidence")?.let { directory -> File(directory).mkdirs(); file.copyTo(File(directory, "synthetic-$count.zip"), overwrite = true) }
            } finally { file.delete() }
        }
    }
    @Test fun wholeFileTamperingMissingAndOversizeNeverPassReviewVerification() {
        val file = temp()
        try {
            val value = VideoFrameArchive.create(file, plan) { index, _ -> frame(index) }
            val original = file.readBytes(); file.appendBytes(byteArrayOf(1)); assertThrows(IllegalStateException::class.java) { VideoFrameArchive.verify(value) }
            file.writeBytes(original); file.delete(); assertThrows(IllegalStateException::class.java) { VideoFrameArchive.verify(value) }
            java.io.RandomAccessFile(file, "rw").use { it.setLength(VideoFrameArchive.MAX_ZIP_BYTES.toLong() + 1) }; assertThrows(IllegalStateException::class.java) { VideoFrameArchive.verify(value) }
        } finally { file.delete() }
    }
    @Test fun forgedFrameMetadataAndManifestOrNameSetAreRejectedEvenWithMatchingZipDigest() {
        val file = temp()
        try {
            val value = VideoFrameArchive.create(file, plan) { index, _ -> frame(index) }
            val wrong = value.entries.mapIndexed { index, entry -> if (index == 0) entry.copy(sha256 = "0".repeat(64)) else entry }
            assertThrows(IllegalStateException::class.java) { VideoFrameArchive.verify(VideoFrameArchive.Prepared(file, plan, wrong, value.digest)) }
            val changedPlan = VideoFrameArchive.Plan(200, 1200, 4)
            assertThrows(IllegalStateException::class.java) { VideoFrameArchive.verify(VideoFrameArchive.Prepared(file, changedPlan, value.entries, value.digest)) }
            java.util.zip.ZipOutputStream(file.outputStream()).use { zip -> zip.putNextEntry(java.util.zip.ZipEntry("../escape.png")); zip.write(png(1)); zip.closeEntry() }
            assertThrows(IllegalStateException::class.java) { VideoFrameArchive.verify(VideoFrameArchive.Prepared(file, plan, value.entries, sha(file.readBytes()))) }
        } finally { file.delete() }
    }
    @Test fun badFrameSignaturesDimensionsAndIndividualLimitsDeleteWholePartialArchive() {
        val cases = listOf(VideoFrameArchive.Frame(byteArrayOf(1), 2, 2), VideoFrameArchive.Frame(ByteArray(100), 2, 2), VideoFrameArchive.Frame(png(1), 513, 2), VideoFrameArchive.Frame(png(1), 2, 0), VideoFrameArchive.Frame(png(1, VideoFrameArchive.MAX_PNG_BYTES + 1), 2, 2))
        cases.forEach { invalid -> val file = temp(); assertThrows(IllegalArgumentException::class.java) { VideoFrameArchive.create(file, plan) { _, _ -> invalid } }; assertFalse(file.exists()) }
        val file = temp(); assertThrows(IllegalStateException::class.java) { VideoFrameArchive.create(file, plan) { index, _ -> frame(index).copy(width = 320 + index) } }; assertFalse(file.exists())
    }
    @Test fun aggregateLimitCountsUncompressedPngBytesEvenWhenZipWouldBeTiny() {
        val file = temp(); val p = VideoFrameArchive.Plan(0, 16000, 16); val bytes = ByteArray(1024 * 1024).also { VideoFrameArchive.signature.copyInto(it) }
        try {
            val value = VideoFrameArchive.create(file, p) { _, _ -> VideoFrameArchive.Frame(bytes, 512, 512) }
            assertEquals(VideoFrameArchive.MAX_TOTAL_PNG_BYTES, value.entries.sumOf { it.bytes }); assertTrue(file.length() < 100000); VideoFrameArchive.verify(value)
        } finally { file.delete() }
        val tooMuch = temp(); var frames = 0
        assertThrows(IllegalStateException::class.java) { VideoFrameArchive.create(tooMuch, p) { _, _ -> frames++; VideoFrameArchive.Frame(ByteArray(2 * 1024 * 1024).also { VideoFrameArchive.signature.copyInto(it) }, 512, 512) } }
        assertEquals(9, frames); assertFalse(tooMuch.exists())
    }
    @Test fun decoderFailureEarlyAndLateCancellationDeleteWithoutReturningPartialZip() {
        for (failing in listOf(0, 2, 3)) { val file = temp(); assertThrows(IOException::class.java) { VideoFrameArchive.create(file, plan) { index, _ -> if (index == failing) throw IOException("Synthetic decoder failure"); frame(index) } }; assertFalse(file.exists()) }
        for (cancelAt in listOf(1, 15, 70)) { val file = temp(); var checks = 0; assertThrows(IllegalStateException::class.java) { VideoFrameArchive.create(file, plan, { if (++checks >= cancelAt) error("Synthetic cancellation") }) { index, _ -> frame(index) } }; assertFalse(file.exists()) }
        val late = temp(); var allFrames = 0
        assertThrows(IllegalStateException::class.java) { VideoFrameArchive.create(late, plan, { if (allFrames == 4) error("Cancelled after all frames decoded") }) { index, _ -> allFrames++; frame(index) } }; assertEquals(4, allFrames); assertFalse(late.exists())
        val file = temp(); try { val value = VideoFrameArchive.create(file, plan) { index, _ -> frame(index) }; assertThrows(IllegalStateException::class.java) { VideoFrameArchive.verify(value) { error("Synthetic stale owner") } } } finally { file.delete() }
    }
    @Test fun boundedZipWriterLatchesOverflowDestinationAndCancellation() {
        val output = ByteArrayOutputStream(); val budget = VideoFrameArchive.Budget(output, {}, 3); budget.write(255); budget.write(byteArrayOf(1, 2)); assertArrayEquals(byteArrayOf(-1, 1, 2), output.toByteArray())
        assertThrows(IllegalStateException::class.java) { budget.write(1) }; assertThrows(IllegalStateException::class.java) { budget.write(byteArrayOf()) }
        val broken = VideoFrameArchive.Budget(object : java.io.OutputStream() { override fun write(value: Int) { throw IOException("Synthetic storage failure") } }, {})
        assertThrows(IOException::class.java) { broken.write(1) }; assertThrows(IllegalStateException::class.java) { broken.write(1) }
        val cancelled = VideoFrameArchive.Budget(ByteArrayOutputStream(), { error("cancelled") }); assertThrows(IllegalStateException::class.java) { cancelled.write(1) }; assertThrows(IllegalStateException::class.java) { cancelled.write(1) }
        assertThrows(IllegalArgumentException::class.java) { VideoFrameArchive.Budget(output, {}, 0) }
    }
    @Test fun existingOneUseLeaseKeepsSharedFileAndDeletesCancelledOrReplacedFiles() {
        val first = temp(); val second = temp(); val third = temp()
        val lease = PhotoDifferenceLease<VideoFrameArchive.Prepared>({ it.token }, { it.dispose() })
        fun prepared(file: File) = VideoFrameArchive.Prepared(file, plan, emptyList(), "synthetic")
        val a = prepared(first); val b = prepared(second); val c = prepared(third)
        try {
            lease.offer(a); lease.offer(b); assertFalse(first.exists()); assertNull(lease.take(a.token)); assertTrue(second.exists())
            assertSame(b, lease.take(b.token)); assertNull(lease.take(b.token)); lease.clear(); assertTrue(second.exists())
            lease.offer(c); lease.clear(); assertFalse(third.exists()); assertTrue(second.exists())
        } finally { first.delete(); second.delete(); third.delete() }
    }
}
