package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Synthetic model outputs test math/ownership only, not AI detection accuracy. */
class PhotoSubjectAssetsTest {
    private fun source(width: Int = 4, height: Int = 3) = PhotoSubjectAssets.Source.capture(width, height, IntArray(width * height) { 0xff000000.toInt() or (it * 1297) })
    @Test fun independentSubjectMasksUseTheirOwnSourceRectangleAndKeepVisibleSourceColors() {
        val input = source(); val masks = listOf(PhotoSubjectAssets.Mask(1, 0, 2, 2, floatArrayOf(0f, .5f, 1f, .25f)), PhotoSubjectAssets.Mask(0, 2, 3, 1, floatArrayOf(1f, 0f, .75f)))
        val result = PhotoSubjectAssets.prepare(input, masks)
        assertEquals(2, result.size)
        assertArrayEquals(intArrayOf(0, 0x80000000.toInt() or (input.pixels()[2] and 0xffffff), input.pixels()[5], 0x40000000 or (input.pixels()[6] and 0xffffff)), result[0].pixels())
        assertArrayEquals(intArrayOf(input.pixels()[8], 0, 0xbf000000.toInt() or (input.pixels()[10] and 0xffffff)), result[1].pixels())
        assertEquals(3, result[0].visiblePixels); assertEquals(2, result[0].softPixels); assertEquals(2, result[1].subject)
        assertEquals(listOf(1, 0, 2, 2), listOf(result[0].x, result[0].y, result[0].width, result[0].height))
    }
    @Test fun capturedPixelsMasksAndReturnedCopiesCannotMutateThePreparedAssets() {
        val pixels = intArrayOf(0xff123456.toInt()); val values = floatArrayOf(1f)
        val input = PhotoSubjectAssets.Source.capture(1, 1, pixels); val mask = PhotoSubjectAssets.Mask(0, 0, 1, 1, values)
        pixels[0] = -1; values[0] = 0f; input.pixels()[0] = 0
        val result = PhotoSubjectAssets.prepare(input, listOf(mask)); result[0].pixels()[0] = 0
        assertArrayEquals(intArrayOf(0xff123456.toInt()), result.single().pixels())
        assertThrows(UnsupportedOperationException::class.java) { (result as MutableList).clear() }
    }
    @Test fun sourceFingerprintHasAnIndependentExplicitDimensionAndArgbEncoding() {
        val input = source(); val bytes = ByteBuffer.allocate(8 + 12 * 4).putInt(4).putInt(3)
        input.pixels().forEach { bytes.putInt(it) }
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expected, input.sha256)
        assertNotEquals(expected, PhotoSubjectAssets.Source.capture(3, 4, input.pixels()).sha256)
    }
    @Test fun badDimensionsTransparencyConfidenceGeometryAndEmptyCandidatesFailClosed() {
        for (pixels in listOf(intArrayOf(0), intArrayOf(0x80123456.toInt()))) assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssets.Source.capture(1, 1, pixels) }
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssets.Source.capture(1025, 1, IntArray(1025) { -1 }) }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.01f, 1.01f)) assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssets.Mask(0, 0, 1, 1, floatArrayOf(value)) }
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssets.Mask(0, 0, 2, 2, floatArrayOf(1f)) }
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssets.prepare(source(), listOf(PhotoSubjectAssets.Mask(Int.MAX_VALUE, 0, 1, 1, floatArrayOf(1f)))) }
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssets.prepare(source(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssets.prepare(source(), listOf(PhotoSubjectAssets.Mask(0, 0, 1, 1, floatArrayOf(0f)))) }
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssets.prepare(source(), List(13) { PhotoSubjectAssets.Mask(0, 0, 1, 1, floatArrayOf(1f)) }) }
    }
    @Test fun pixelBudgetAndCancellationRefuseACompleteCandidateSetWithoutTruncation() {
        val input = source(1024, 1024); val mask = PhotoSubjectAssets.Mask(0, 0, 1024, 1024, FloatArray(1024 * 1024) { 1f })
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssets.prepare(input, List(5) { mask }) }
        var checks = 0
        assertThrows(IllegalStateException::class.java) { PhotoSubjectAssets.prepare(source(), listOf(PhotoSubjectAssets.Mask(0, 0, 4, 3, FloatArray(12) { 1f }))) { if (++checks == 4) error("Synthetic cancellation") } }
        assertEquals(4, checks)
    }
    @Test fun allConfidenceByteStepsAndTheFullPixelBudgetHaveStableAlphaAndNoHiddenRgb() {
        val input = source(256, 1); val result = PhotoSubjectAssets.prepare(input, listOf(PhotoSubjectAssets.Mask(0, 0, 256, 1, FloatArray(256) { it / 255f }))).single().pixels()
        for (index in 0..255) { assertEquals(index, result[index] ushr 24); assertEquals(if (index == 0) 0 else input.pixels()[index] and 0xffffff, result[index] and 0xffffff) }
        val large = source(1024, 1024); val mask = PhotoSubjectAssets.Mask(0, 0, 1024, 1024, FloatArray(1024 * 1024) { 1f })
        val maximum = PhotoSubjectAssets.prepare(large, List(4) { mask }); assertEquals(4, maximum.size)
        maximum.forEach { assertArrayEquals(large.pixels(), it.pixels()) }
    }
}
