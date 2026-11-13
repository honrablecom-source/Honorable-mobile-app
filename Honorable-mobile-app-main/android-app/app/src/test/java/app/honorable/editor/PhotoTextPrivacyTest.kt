package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class PhotoTextPrivacyTest {
    private fun source(width: Int = 16, height: Int = 12, pixels: IntArray = IntArray(width * height) { -0x665545 }) = PhotoTextPrivacy.Source.capture(width, height, pixels)
    private fun review(s: PhotoTextPrivacy.Source = source(), boxes: List<PhotoTextPrivacy.Box> = listOf(PhotoTextPrivacy.Box(2, 3, 7, 8))) = PhotoTextPrivacy.Review.create(s, s.width, s.height, boxes)
    private fun reject(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    private fun matte(pixel: Int): Int {
        val a = (pixel ushr 24) / 255.0
        fun channel(shift: Int) = (((pixel ushr shift) and 255) * a + 255 * (1 - a)).roundToInt()
        return -0x1000000 or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    @Test fun privateOpaqueSourceAndFingerprintBindDimensionsAndPixels() {
        val pixels = intArrayOf(0x00112233, 0x804080c0.toInt(), 0xff123456.toInt(), 0x00ffeedd)
        val original = pixels.copyOf(); val s = source(2, 2, pixels); pixels.fill(0)
        val actual = review(s, listOf(PhotoTextPrivacy.Box(0, 0, 1, 1))).render(setOf(1), 0).pixels()
        assertArrayEquals(intArrayOf(-0x1000000, matte(original[1]), original[2], -1), actual)
        assertEquals(s.fingerprint, source(2, 2, original).fingerprint)
        assertNotEquals(s.fingerprint, source(4, 1, original).fingerprint)
        assertNotEquals(s.fingerprint, source(2, 2, IntArray(4)).fingerprint)
        actual.fill(0); assertEquals(-0x1000000, review(s, listOf(PhotoTextPrivacy.Box(0, 0, 1, 1))).render(setOf(1), 0).pixels()[0])
    }
    @Test fun scalingClipsAndRoundsOutwardWithStableUniqueDetectionOrder() {
        val s = source(7, 5)
        val r = PhotoTextPrivacy.Review.create(s, 19, 13, listOf(PhotoTextPrivacy.Box(-99, -4, 5, 4), PhotoTextPrivacy.Box(5, 4, 18, 12), PhotoTextPrivacy.Box(-99, -4, 5, 4), PhotoTextPrivacy.Box(21, 3, 30, 6)))
        assertEquals(listOf(1, 2), r.regions.map { it.id })
        assertEquals(PhotoTextPrivacy.Box(0, 0, 2, 2), r.regions[0].box)
        assertEquals(PhotoTextPrivacy.Box(1, 1, 7, 5), r.regions[1].box)
        try { (r.regions as MutableList).clear(); fail("Review must be immutable") } catch (_: UnsupportedOperationException) {}
        val large = PhotoTextPrivacy.Review.create(s, Int.MAX_VALUE, Int.MAX_VALUE, listOf(PhotoTextPrivacy.Box(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)))
        assertEquals(PhotoTextPrivacy.Box(0, 0, 7, 5), large.regions.single().box)
    }
    @Test fun selectedRegionUnionMatchesIndependentPointContainmentEveryPixel() {
        val width = 31; val height = 23
        val pixels = IntArray(width * height) { i -> ((i * 17 and 255) shl 24) or (i * 7919 and 0xffffff) }
        val detections = listOf(PhotoTextPrivacy.Box(1, 2, 19, 15), PhotoTextPrivacy.Box(15, 4, 28, 21), PhotoTextPrivacy.Box(2, 18, 8, 23))
        val r = review(source(width, height, pixels), detections)
        for (padding in listOf(0, 3, 24)) {
            val image = r.render(setOf(1, 2), padding)
            val actual = image.pixels()
            for (i in actual.indices) {
                val x = i % width; val y = i / width
                val hidden = detections.take(2).any { x.toLong() >= it.left.toLong() - padding && x.toLong() < it.right.toLong() + padding && y.toLong() >= it.top.toLong() - padding && y.toLong() < it.bottom.toLong() + padding }
                assertEquals("at $x,$y with padding $padding", if (hidden) -0x1000000 else matte(pixels[i]), actual[i])
            }
            assertEquals(listOf(1, 2), image.selectedIds)
            actual.fill(0); assertNotEquals(0, image.pixels()[0])
        }
    }
    @Test fun emptyForeignSelectionsAndInvalidInputRefuseWithoutSubstituteDetections() {
        reject { PhotoTextPrivacy.Source.capture(0, 1, intArrayOf()) }
        reject { source(1025, 1, IntArray(1025)) }
        reject { source(2, 2, IntArray(3)) }
        reject { PhotoTextPrivacy.Box(2, 0, 2, 3) }
        reject { PhotoTextPrivacy.Review.create(source(), 0, 12, listOf(PhotoTextPrivacy.Box(1, 1, 2, 2))) }
        reject { review(boxes = emptyList()) }
        reject { review(boxes = List(101) { PhotoTextPrivacy.Box(0, 0, 1, 1) }) }
        reject { review(boxes = listOf(PhotoTextPrivacy.Box(90, 90, 99, 99))) }
        reject { review().render(emptySet(), 4) }; reject { review().render(setOf(2), 4) }
        reject { review().render(setOf(1), -1) }; reject { review().render(setOf(1), 25) }
    }
    @Test fun cancellationIsCheckedDuringCaptureGeometryRasterAndBeforePngPublication() {
        class Stop : RuntimeException()
        fun stopOnLast(operation: ((() -> Unit)) -> Unit) {
            var calls = 0; operation { calls++ }; assertTrue(calls > 1)
            var seen = 0
            try { operation { if (++seen == calls) throw Stop() }; fail("Final current check required") } catch (_: Stop) {}
        }
        stopOnLast { current -> PhotoTextPrivacy.Source.capture(16, 12, IntArray(192) { -1 }, current) }
        stopOnLast { current -> PhotoTextPrivacy.Review.create(source(), 16, 12, listOf(PhotoTextPrivacy.Box(2, 3, 7, 8)), current) }
        stopOnLast { current -> review().render(setOf(1), 4, current) }
        stopOnLast { current -> review().render(setOf(1), 4).png(current) }
        var calls = 0
        try { review().render(setOf(1), 4).png { if (++calls == 4) throw Stop() }; fail("Encoding cancellation required") } catch (_: Stop) {}
    }
    private fun decode(bytes: ByteArray, width: Int, height: Int): IntArray {
        val input = DataInputStream(ByteArrayInputStream(bytes)); val signature = ByteArray(8); input.readFully(signature)
        assertArrayEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10), signature)
        val names = mutableListOf<String>(); var compressed = byteArrayOf()
        while (input.available() > 0) {
            val count = input.readInt(); assertTrue(count >= 0 && count <= PhotoTextPrivacy.MAX_PNG_BYTES)
            val type = ByteArray(4); input.readFully(type); val payload = ByteArray(count); input.readFully(payload)
            val crc = CRC32(); crc.update(type); crc.update(payload); assertEquals(crc.value, input.readInt().toLong() and 0xffffffffL)
            val name = type.toString(Charsets.US_ASCII); names.add(name)
            if (name == "IHDR") {
                val h = DataInputStream(ByteArrayInputStream(payload)); assertEquals(width, h.readInt()); assertEquals(height, h.readInt()); assertEquals(8, h.readUnsignedByte()); assertEquals(2, h.readUnsignedByte()); assertEquals(0, h.readUnsignedByte()); assertEquals(0, h.readUnsignedByte()); assertEquals(0, h.readUnsignedByte()); assertEquals(0, h.available())
            }
            if (name == "IDAT") compressed = payload
            if (name == "IEND") assertEquals(0, count)
        }
        assertEquals(listOf("IHDR", "IDAT", "IEND"), names)
        val raw = InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        assertEquals((1 + width * 3) * height, raw.size)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            assertEquals(0, raw[y * (width * 3 + 1)].toInt())
            for (x in 0 until width) { val at = y * (width * 3 + 1) + 1 + x * 3; pixels[y * width + x] = -0x1000000 or ((raw[at].toInt() and 255) shl 16) or ((raw[at + 1].toInt() and 255) shl 8) or (raw[at + 2].toInt() and 255) }
        }
        return pixels
    }
    @Test fun exactPngContainsOnlyFlattenedRgbAndStandardChunksWithIndependentDecoder() {
        val r = review().render(setOf(1), 2); val bytes = r.png()
        assertTrue(bytes.size < PhotoTextPrivacy.MAX_PNG_BYTES); assertArrayEquals(r.pixels(), decode(bytes, r.width, r.height))
        val decoded = PixelPngFixture.read(ByteArrayInputStream(bytes)); assertNotNull(decoded)
        assertArrayEquals(r.pixels(), decoded.getRGB(0, 0, r.width, r.height, null, 0, r.width))
    }
    @Test fun maximumEntropyGridAndMergedOverlapsRemainExactAndBounded() {
        val random = java.util.Random(193); val pixels = IntArray(1024 * 1024) { -0x1000000 or random.nextInt(0x1000000) }
        val boxes = List(100) { PhotoTextPrivacy.Box(0, 0, 2 + it, 2 + it) }
        val s = source(1024, 1024, pixels); val r = review(s, boxes).render((1..100).toSet(), 0)
        assertEquals(100, review(s, boxes).regions.size)
        val expected = pixels.copyOf()
        for (y in 0..100) for (x in 0..100) expected[y * 1024 + x] = -0x1000000
        assertArrayEquals(expected, r.pixels())
        val png = r.png(); assertTrue(png.size <= PhotoTextPrivacy.MAX_PNG_BYTES)
        assertArrayEquals(r.pixels(), decode(png, 1024, 1024))
    }
    @Test fun independentPythonFixtureUsesUnalignedGeometryAndAlpha() {
        val width = 137; val height = 83
        val pixels = IntArray(width * height) { i -> val x = i % width; val y = i / width; (((x * 19 + y * 7) and 255) shl 24) or (((x * 37 + y * 13) and 255) shl 16) or (((x * 11 + y * 29) and 255) shl 8) or ((x * 7 + y * 43) and 255) }
        val r = PhotoTextPrivacy.Review.create(source(width, height, pixels), 1000, 613, listOf(PhotoTextPrivacy.Box(-13, 17, 423, 310), PhotoTextPrivacy.Box(818, 411, 1007, 630))).render(setOf(2, 1), 3)
        assertEquals(listOf(1, 2), r.selectedIds)
        val expected = IntArray(pixels.size) { i ->
            val x = i % width; val y = i / width
            val hidden = listOf(intArrayOf(-13, 17, 423, 310), intArrayOf(818, 411, 1007, 630)).any { raw ->
                x >= floor(raw[0].coerceIn(0, 1000) * width / 1000.0).toInt() - 3 && x < ceil(raw[2].coerceIn(0, 1000) * width / 1000.0).toInt() + 3 && y >= floor(raw[1].coerceIn(0, 613) * height / 613.0).toInt() - 3 && y < ceil(raw[3].coerceIn(0, 613) * height / 613.0).toInt() + 3
            }
            if (hidden) -0x1000000 else matte(pixels[i])
        }
        assertArrayEquals(expected, r.pixels()); val png = r.png(); assertArrayEquals(expected, decode(png, width, height))
        System.getProperty("noble.privacy.fixtureDir")?.let { folder -> File(folder).apply { mkdirs() }; File(folder, "unaligned-alpha.png").writeBytes(png) }
    }
}
