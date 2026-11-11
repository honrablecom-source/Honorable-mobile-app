package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random

class VideoTimeSliceTest {
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Test fun sampleCentersAreOrderedDistinctAndInsideTheHalfOpenSourceWindow() {
        assertEquals(listOf(150L, 250L, 350L, 450L), VideoTimeSlice.Plan(100, 500, 4, VideoTimeSlice.Axis.COLUMNS).times)
        val random = Random(160)
        repeat(1000) {
            val count = listOf(4, 8, 16)[random.nextInt(3)]; val start = random.nextInt(100000).toLong(); val end = start + random.nextInt(count, 100000)
            val times = VideoTimeSlice.Plan(start, end, count, VideoTimeSlice.Axis.ROWS).times
            assertEquals(count, times.distinct().size); assertTrue(times.all { it in start until end }); assertTrue(times.zipWithNext().all { it.first < it.second })
            times.forEachIndexed { index, time -> val exact = start + ((index + .5) * (end - start) / count); assertTrue(time <= exact && exact - time < 1.0) }
        }
    }
    @Test fun unevenRowsAndColumnsCopyEveryArgbBitFromIndependentBandOracleWithoutMutation() {
        val random = Random(1601)
        for (axis in VideoTimeSlice.Axis.entries) repeat(100) {
            val count = listOf(4, 8, 16)[random.nextInt(3)]; val width = random.nextInt(count, 71); val height = random.nextInt(count, 71)
            val inputs = List(count) { IntArray(width * height) { random.nextInt() } }; val originals = inputs.map { it.copyOf() }
            val seen = mutableListOf<Int>(); val plan = VideoTimeSlice.Plan(100, 1100, count, axis)
            val actual = VideoTimeSlice.render(plan, width, height, { index, time -> seen += index; assertEquals(plan.times[index], time); inputs[index] })
            for (y in 0 until height) for (x in 0 until width) {
                val position = if (axis == VideoTimeSlice.Axis.COLUMNS) x else y; val dimension = if (axis == VideoTimeSlice.Axis.COLUMNS) width else height
                val selected = ((position + 1) * count - 1) / dimension
                assertEquals(originals[selected][y * width + x], actual[y * width + x])
            }
            assertEquals((0 until count).toList(), seen); inputs.forEachIndexed { index, pixels -> assertArrayEquals(originals[index], pixels) }
        }
    }
    @Test fun maximumOutputAndTransparentInputsKeepExactSpatialCoordinates() {
        val plan = VideoTimeSlice.Plan(0, 16000, 16, VideoTimeSlice.Axis.COLUMNS)
        val result = VideoTimeSlice.render(plan, 512, 512, { index, _ -> IntArray(512 * 512) { pixel -> ((index * 17) shl 24) or pixel } })
        for (y in 0 until 512) for (x in 0 until 512) assertEquals(((x / 32 * 17) shl 24) or (y * 512 + x), result[y * 512 + x])
    }
    @Test fun dimensionsPlansAndFrameLengthRefuseWithoutInventedPixels() {
        for (count in listOf(0, 3, 5, 17)) assertThrows(IllegalArgumentException::class.java) { VideoTimeSlice.Plan(0, 100, count, VideoTimeSlice.Axis.ROWS) }
        for ((a, b) in listOf(-1L to 100L, 0L to 0L, 0L to 3L, 0L to Int.MAX_VALUE.toLong() + 1)) assertThrows(IllegalArgumentException::class.java) { VideoTimeSlice.Plan(a, b, 4, VideoTimeSlice.Axis.COLUMNS) }
        val plan = VideoTimeSlice.Plan(0, 100, 4, VideoTimeSlice.Axis.COLUMNS)
        assertThrows(IllegalArgumentException::class.java) { VideoTimeSlice.render(plan, 3, 10, { _, _ -> fail("Read invalid geometry"); intArrayOf() }) }
        assertThrows(IllegalArgumentException::class.java) { VideoTimeSlice.render(plan, 513, 10, { _, _ -> intArrayOf() }) }
        assertThrows(IllegalArgumentException::class.java) { VideoTimeSlice.render(plan, 10, 10, { _, _ -> IntArray(99) }) }
    }
    @Test fun cancellationAndFrameFailureStopWithoutReturningPartialArt() {
        val plan = VideoTimeSlice.Plan(0, 1000, 4, VideoTimeSlice.Axis.COLUMNS); var checks = 0; var frames = 0
        assertThrows(IllegalStateException::class.java) { VideoTimeSlice.render(plan, 32, 32, { _, _ -> frames++; IntArray(1024) }, { if (++checks == 10) error("Synthetic cancel") }) }
        assertEquals(1, frames)
        assertThrows(IOException::class.java) { VideoTimeSlice.render(plan, 16, 16, { index, _ -> if (index == 1) throw IOException("Synthetic frame failure"); IntArray(256) }) }
    }
    @Test fun sizeIsBoundedAspectPreservingAndDoesNotUpscaleSmallSources() {
        assertEquals(320 to 180, VideoTimeSlice.size(320, 180)); assertEquals(512 to 288, VideoTimeSlice.size(3840, 2160))
        assertEquals(288 to 512, VideoTimeSlice.size(2160, 3840)); assertEquals(512 to 1, VideoTimeSlice.size(100000, 1))
        assertThrows(IllegalArgumentException::class.java) { VideoTimeSlice.size(0, 100) }
    }
    @Test fun boundedOutputHasIndependentDigestAndOneUseFinalization() {
        val output = ByteArrayOutputStream(); val bounded = VideoTimeSlice.OutputBudget(output)
        bounded.write(255); bounded.write(byteArrayOf(1, 2, 3, 4), 1, 2)
        assertArrayEquals(byteArrayOf(-1, 2, 3), output.toByteArray()); assertEquals(hash(output.toByteArray()), bounded.finish())
        assertThrows(IllegalStateException::class.java) { bounded.finish() }; assertThrows(IllegalStateException::class.java) { bounded.write(1) }
    }
    @Test fun overLimitInvalidRangeDestinationAndCancellationFailuresLatch() {
        val over = VideoTimeSlice.OutputBudget(ByteArrayOutputStream(), limit = 2); over.write(byteArrayOf(1, 2))
        assertThrows(IllegalStateException::class.java) { over.write(3) }; assertThrows(IllegalStateException::class.java) { over.finish() }
        val invalid = VideoTimeSlice.OutputBudget(ByteArrayOutputStream()); assertThrows(IllegalArgumentException::class.java) { invalid.write(byteArrayOf(1), 1, 1) }; assertThrows(IllegalStateException::class.java) { invalid.finish() }
        val broken = VideoTimeSlice.OutputBudget(object : OutputStream() { override fun write(value: Int) { throw IOException("Synthetic output failure") } })
        assertThrows(IOException::class.java) { broken.write(1) }; assertThrows(IllegalStateException::class.java) { broken.finish() }
        var cancel = false; val cancelled = VideoTimeSlice.OutputBudget(ByteArrayOutputStream(), { check(!cancel) }); cancelled.write(1); cancel = true
        assertThrows(IllegalStateException::class.java) { cancelled.finish() }; cancel = false; assertThrows(IllegalStateException::class.java) { cancelled.write(2) }
    }
    @Test fun actualPreparedFileVerificationRefusesChangedMissingAndOversizedContent() {
        val directory = Files.createTempDirectory("noble-time-slice-test-").toFile()
        try {
            val file = directory.resolve("owned.png"); val bytes = byteArrayOf(1, 2, 3); file.writeBytes(bytes)
            val prepared = VideoTimeSlicePrepared(file, hash(bytes), 4, 4, VideoTimeSlice.Plan(0, 100, 4, VideoTimeSlice.Axis.ROWS), null)
            assertArrayEquals(bytes, VideoTimeSliceImage.bytes(prepared) {})
            file.writeBytes(byteArrayOf(1, 2, 4)); assertThrows(IllegalStateException::class.java) { VideoTimeSliceImage.bytes(prepared) {} }
            file.outputStream().use { it.write(ByteArray(VideoTimeSlice.MAX_BYTES.toInt() + 1)) }; assertThrows(IllegalStateException::class.java) { VideoTimeSliceImage.bytes(prepared) {} }
            file.delete(); assertThrows(IllegalStateException::class.java) { VideoTimeSliceImage.bytes(prepared) {} }
        } finally { directory.deleteRecursively() }
    }
    @Test fun actualPreviewLeaseDisposesReplacementButTakenHandoffSurvivesClose() {
        val directory = Files.createTempDirectory("noble-time-slice-lease-").toFile()
        try {
            fun image(name: String) = VideoTimeSlicePrepared(directory.resolve(name).apply { writeBytes(byteArrayOf(7)) }, hash(byteArrayOf(7)), 4, 4, VideoTimeSlice.Plan(0, 100, 4, VideoTimeSlice.Axis.ROWS), null)
            val first = image("first.png"); val second = image("second.png")
            val lease = PhotoDifferenceLease<VideoTimeSlicePrepared>({ it.token }, { it.dispose() })
            lease.offer(first); lease.offer(second); assertFalse(first.file.exists()); assertTrue(second.file.exists())
            assertNull(lease.take(first.token)); assertSame(second, lease.take(second.token)); lease.clear(); assertTrue(second.file.exists())
            assertNull(lease.take(second.token)); second.dispose(); assertFalse(second.file.exists())
        } finally { directory.deleteRecursively() }
    }
}
