package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class PhotoNeuralUpscaleTest {
    private fun nearest() = PhotoNeuralUpscale.Network { w, h, input, current ->
        val result = FloatArray(w * h * 12)
        for (c in 0..2) for (y in 0 until h * 2) {
            current()
            for (x in 0 until w * 2) result[c * w * h * 4 + y * w * 2 + x] = input[c * w * h + y / 2 * w + x / 2]
        }
        result
    }

    @Test fun sourceOwnsMattePixelsAndIgnoresHiddenRgb() {
        val pixels = intArrayOf(0x00123456, 0x80102030.toInt(), 0xffaabbcc.toInt())
        val source = PhotoNeuralUpscale.Source.capture(3, 1, pixels)
        assertArrayEquals(intArrayOf(-1, 0xff878f97.toInt(), 0xffaabbcc.toInt()), source.pixels())
        pixels.fill(0)
        val leaked = source.pixels(); leaked.fill(0)
        assertEquals(-1, source.pixels()[0])
        val other = PhotoNeuralUpscale.Source.capture(3, 1, intArrayOf(0x00ffffff, 0x80102030.toInt(), 0xffaabbcc.toInt()))
        assertEquals(source.fingerprint, other.fingerprint)
    }

    @Test fun dimensionsAndOverflowRejectedBeforeCopy() {
        for (pair in listOf(0 to 1, 1 to 0, 1025 to 1, Int.MAX_VALUE to Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { PhotoNeuralUpscale.Source.capture(pair.first, pair.second, intArrayOf()) }
        }
        assertThrows(IllegalArgumentException::class.java) { PhotoNeuralUpscale.Source.capture(2, 3, IntArray(5)) }
    }

    @Test fun officialSymmetricBandIncludesEdgeAndAddsWindowWhenDivisible() {
        assertEquals(16, PhotoNeuralUpscale.padded(8))
        assertEquals(8, PhotoNeuralUpscale.padded(1))
        assertEquals(1032, PhotoNeuralUpscale.padded(1024))
        assertArrayEquals(intArrayOf(0, 1, 2, 2, 1, 0, 0, 1, 2), IntArray(9) { PhotoNeuralUpscale.symmetric(it, 3) })
        assertEquals(0, PhotoNeuralUpscale.symmetric(7, 1))
    }

    @Test fun normalizedRgbTensorUsesTrueWholeImageEdges() {
        val source = PhotoNeuralUpscale.Source.capture(3, 1, intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt()))
        var called = false
        PhotoNeuralUpscale.reconstruct(source, PhotoNeuralUpscale.Network { w, h, rgb, check ->
            called = true; assertEquals(8, w); assertEquals(8, h)
            val expected = intArrayOf(0, 1, 2, 2, 1, 0, 0, 1)
            for (c in 0..2) for (y in 0 until h) for (x in 0 until w) assertEquals(if (expected[x] == c) 1f else 0f, rgb[c * w * h + y * w + x], 0f)
            nearest().infer(w, h, rgb, check)
        })
        assertTrue(called)
    }

    @Test fun everyPlanCoversEachSourcePixelOnceWithAlignedBoundedContext() {
        val sizes = listOf(1, 8, 127, 128, 129, 376, 377, 511, 512, 1023, 1024)
        for (w in sizes) for (h in sizes) {
            val coverage = IntArray(w * h)
            for (tile in PhotoNeuralUpscale.tiles(w, h)) {
                assertTrue(tile.left % 8 == 0 && tile.top % 8 == 0 && tile.width % 8 == 0 && tile.height % 8 == 0)
                assertTrue(tile.width in 8..384 && tile.height in 8..384)
                assertTrue(tile.left >= 0 && tile.top >= 0 && tile.right <= PhotoNeuralUpscale.padded(w) && tile.bottom <= PhotoNeuralUpscale.padded(h))
                assertTrue(tile.left == 0 || tile.coreLeft - tile.left >= 128)
                assertTrue(tile.top == 0 || tile.coreTop - tile.top >= 128)
                assertTrue(tile.right == PhotoNeuralUpscale.padded(w) || tile.right - tile.coreRight >= 128)
                assertTrue(tile.bottom == PhotoNeuralUpscale.padded(h) || tile.bottom - tile.coreBottom >= 128)
                for (y in tile.coreTop until tile.coreBottom) for (x in tile.coreLeft until tile.coreRight) coverage[y * w + x]++
            }
            assertTrue("Coverage for $w x $h", coverage.all { it == 1 })
        }
    }

    @Test fun nonSquareAndThinImagesAssembleEveryRgbPixelWithoutCropOrSeams() {
        for ((w, h) in listOf(389 to 277, 1 to 1024, 1024 to 1, 7 to 9)) {
            val input = IntArray(w * h) { i -> -0x1000000 or (((i * 17) and 255) shl 16) or (((i * 31) and 255) shl 8) or ((i * 47) and 255) }
            val source = PhotoNeuralUpscale.Source.capture(w, h, input)
            val result = PhotoNeuralUpscale.reconstruct(source, nearest())
            assertEquals(w * 2, result.width); assertEquals(h * 2, result.height); assertEquals(source.fingerprint, result.sourceFingerprint)
            val output = result.pixels()
            for (y in 0 until h * 2) for (x in 0 until w * 2) assertEquals(input[y / 2 * w + x / 2], output[y * w * 2 + x])
            output.fill(0); assertEquals(input[0], result.pixels()[0])
        }
    }

    @Test fun invalidModelPixelsIncludingDiscardedHaloFailWithoutFallback() {
        val source = PhotoNeuralUpscale.Source.capture(389, 3, IntArray(389 * 3) { -1 })
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            var calls = 0
            assertThrows(IllegalArgumentException::class.java) {
                PhotoNeuralUpscale.reconstruct(source, PhotoNeuralUpscale.Network { w, h, input, check ->
                    calls++; nearest().infer(w, h, input, check).also { it[it.lastIndex] = bad }
                })
            }
            assertEquals(1, calls)
        }
        assertThrows(IllegalArgumentException::class.java) { PhotoNeuralUpscale.reconstruct(source, PhotoNeuralUpscale.Network { _, _, _, _ -> FloatArray(3) }) }
    }

    @Test fun finiteOutOfRangeModelValuesClampOnlyAtFinalRgbConversion() {
        val source = PhotoNeuralUpscale.Source.capture(1, 1, intArrayOf(-1))
        val result = PhotoNeuralUpscale.reconstruct(source, PhotoNeuralUpscale.Network { w, h, _, _ ->
            FloatArray(w * h * 12) { index -> when (index / (w * h * 4)) { 0 -> -0.1f; 1 -> .5f; else -> 1.1f } }
        })
        assertArrayEquals(IntArray(4) { 0xff0080ff.toInt() }, result.pixels())
    }

    @Test fun cancellationAfterInferencePreventsAnyResult() {
        val source = PhotoNeuralUpscale.Source.capture(389, 3, IntArray(389 * 3) { -1 })
        var stopped = false; var calls = 0
        assertThrows(CancellationException::class.java) {
            PhotoNeuralUpscale.reconstruct(source, PhotoNeuralUpscale.Network { w, h, input, _ ->
                calls++; nearest().infer(w, h, input) {}.also { stopped = true }
            }, { if (stopped) throw CancellationException() })
        }
        assertEquals(1, calls)
    }

    @Test fun errorAndPhaseCancellationNeverProduceInterpolationSubstitute() {
        val source = PhotoNeuralUpscale.Source.capture(1, 1, intArrayOf(-1))
        assertThrows(UnsupportedOperationException::class.java) { PhotoNeuralUpscale.reconstruct(source, PhotoNeuralUpscale.Network { _, _, _, _ -> throw UnsupportedOperationException("Model unavailable") }) }
        var calls = 0
        assertThrows(CancellationException::class.java) {
            PhotoNeuralUpscale.reconstruct(source, PhotoNeuralUpscale.Network { _, _, _, _ -> calls++; error("Should not run") }, phase = { _, _ -> throw CancellationException() })
        }
        assertEquals(0, calls)
    }
}
