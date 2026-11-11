package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

class PhotoAlbumPdfTest {
    @Test fun mixedWritesProduceExactBytesAndIndependentDigest() {
        val destination = ByteArrayOutputStream(); val out = PhotoAlbumPdf.WriteBudget(destination, 9)
        out.write(255); out.write(byteArrayOf(99, 1, 2, 3, 88), 1, 3); out.write(byteArrayOf(4, 5)); out.write(byteArrayOf(), 0, 0)
        val expected = byteArrayOf(-1, 1, 2, 3, 4, 5)
        assertArrayEquals(expected, destination.toByteArray()); assertEquals(6L, out.bytes)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(expected).joinToString("") { "%02x".format(it) }, out.finish())
        assertEquals(out.finish(), out.finish())
    }

    @Test fun overLimitBlockIsRefusedWithoutPartialWriteAndFailureCannotBeCommitted() {
        val destination = ByteArrayOutputStream(); val out = PhotoAlbumPdf.WriteBudget(destination, 3)
        out.write(byteArrayOf(1, 2))
        try { out.write(byteArrayOf(3, 4)); fail("Accepted oversized block") } catch (_: IllegalStateException) { }
        assertArrayEquals(byteArrayOf(1, 2), destination.toByteArray()); assertEquals(2L, out.bytes)
        try { out.finish(); fail("Committed failed output") } catch (_: IllegalStateException) { }
        try { out.write(3); fail("Retried failed document") } catch (_: IllegalStateException) { }
    }

    @Test fun exactLimitAcceptsCompleteOutputButNotAnotherByte() {
        val destination = ByteArrayOutputStream(); val out = PhotoAlbumPdf.WriteBudget(destination, 2)
        out.write(byteArrayOf(1, 2)); assertEquals(2L, out.bytes); assertEquals(64, out.finish().length)
        try { out.write(3); fail("Wrote after completion") } catch (_: IllegalStateException) { }
        assertArrayEquals(byteArrayOf(1, 2), destination.toByteArray())
    }

    @Test fun invalidRangesAndEmptyOutputCannotBeAccepted() {
        for ((offset, length) in listOf(-1 to 1, 0 to -1, 2 to 1, Int.MAX_VALUE to Int.MAX_VALUE)) {
            val dest = ByteArrayOutputStream(); val out = PhotoAlbumPdf.WriteBudget(dest, 4)
            try { out.write(byteArrayOf(1), offset, length); fail("Accepted invalid range") } catch (_: IndexOutOfBoundsException) { }
            assertEquals(0, dest.size()); try { out.finish(); fail("Committed empty output") } catch (_: IllegalStateException) { }
        }
        try { PhotoAlbumPdf.WriteBudget(ByteArrayOutputStream()).finish(); fail("Committed empty PDF") } catch (_: IllegalStateException) { }
    }

    @Test fun cancellationBeforeWriteDoesNotTouchDestination() {
        val dest = ByteArrayOutputStream(); var cancelled = false
        val out = PhotoAlbumPdf.WriteBudget(dest, 4) { if (cancelled) error("Synthetic cancellation") }
        cancelled = true
        try { out.write(1); fail("Ignored cancellation") } catch (_: IllegalStateException) { }
        assertEquals(0, dest.size()); try { out.finish(); fail("Committed cancelled output") } catch (_: IllegalStateException) { }
    }

    @Test fun destinationFailureIsLatchedEvenIfNativeCallerCatchesIt() {
        val out = PhotoAlbumPdf.WriteBudget(object : OutputStream() { override fun write(value: Int) { throw IOException("Synthetic disk failure") } }, 4)
        try { out.write(1); fail("Swallowed write failure") } catch (_: IOException) { }
        assertEquals(0L, out.bytes); try { out.finish(); fail("Committed partial failed document") } catch (_: IllegalStateException) { }
    }

    @Test fun a4OrientationFollowsCorrectedPhotoAndSquaresUsePortrait() {
        val portrait = PhotoAlbumPdf.page(300, 800)
        val landscape = PhotoAlbumPdf.page(800, 300)
        val square = PhotoAlbumPdf.page(400, 400)
        assertEquals(595, portrait.width); assertEquals(842, portrait.height)
        assertEquals(842, landscape.width); assertEquals(595, landscape.height)
        assertEquals(595, square.width); assertEquals(842, square.height)
    }

    @Test fun completePhotoFitsCenteredWithoutCrop() {
        val page = PhotoAlbumPdf.page(1000, 500)
        assertEquals(24f, page.left, .0001f)
        assertEquals(818f, page.right, .0001f)
        assertEquals(99f, page.top, .0001f)
        assertEquals(496f, page.bottom, .0001f)
        assertEquals(2f, page.imageWidth / page.imageHeight, .0001f)
    }

    @Test fun squareKeepsEqualSidesAndSymmetricWhitespace() {
        val page = PhotoAlbumPdf.page(1024, 1024)
        assertEquals(547f, page.imageWidth, .0001f)
        assertEquals(page.imageWidth, page.imageHeight, .0001f)
        assertEquals(page.left, page.width - page.right, .0001f)
        assertEquals(page.top, page.height - page.bottom, .0001f)
    }

    @Test fun thousandsOfIndependentRatiosStayInsideMarginsAndFillOneAxis() {
        val random = Random(142)
        repeat(2000) {
            val width = random.nextInt(1, 10001)
            val height = random.nextInt(1, 10001)
            val p = PhotoAlbumPdf.page(width, height)
            assertTrue(p.left >= 23.999f && p.top >= 23.999f)
            assertTrue(p.right <= p.width - 23.999f && p.bottom <= p.height - 23.999f)
            assertTrue(p.imageWidth > 0 && p.imageHeight > 0)
            assertEquals(p.left, p.width - p.right, .001f)
            assertEquals(p.top, p.height - p.bottom, .001f)
            assertTrue(kotlin.math.abs(p.imageWidth - (p.width - 48)) < .001 || kotlin.math.abs(p.imageHeight - (p.height - 48)) < .001)
            // Independent cross-products avoid treating a rounded fitted ratio as the reference.
            assertEquals(1.0, p.imageWidth.toDouble() * height / (p.imageHeight.toDouble() * width), .001)
        }
    }

    @Test fun extremeAllowedEdgesAndPixelCapRemainFinite() {
        for ((width, height) in listOf(1 to 32768, 32768 to 1, 10000 to 10000, 32768 to 3051)) {
            val p = PhotoAlbumPdf.page(width, height)
            assertTrue(p.imageWidth.isFinite() && p.imageHeight.isFinite())
            assertTrue(p.imageWidth > 0 && p.imageHeight > 0)
        }
    }

    @Test fun invalidDimensionsRefuseBeforePlanning() {
        for ((width, height) in listOf(0 to 1, 1 to 0, -1 to 1, 32769 to 1, 10001 to 10000, Int.MAX_VALUE to Int.MAX_VALUE)) {
            try { PhotoAlbumPdf.page(width, height); fail("Accepted invalid dimensions") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun albumCountIsBoundedAndDoesNotInventPages() {
        for (count in 2..8) PhotoAlbumPdf.requireCount(count)
        for (count in listOf(Int.MIN_VALUE, -1, 0, 1, 9, Int.MAX_VALUE)) {
            try { PhotoAlbumPdf.requireCount(count); fail("Accepted invalid album count") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
