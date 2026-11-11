package app.honorable.editor

import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.TextPaint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoTextLayoutTypographyTest {
    @Test fun completeTitleShrinksToFitInsteadOfEllipsizingAtIdealFont() {
        val text = "VERY LONG TITLE"
        val frame = PhotoTextLayout.Frame(0f, 0f, 100f, 22f)
        val size = requireNotNull(PhotoTextLayoutTypography.fit(text, frame, 100f, .2f))
        assertTrue(size in .02f.. .2f && size < .2f)
        val paint = TextPaint().apply { textSize = (size * 100).coerceAtLeast(1f); typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        assertTrue("The complete single-line title must fit", paint.measureText(text) <= frame.width + .001f)
    }

    @Test fun impossibleFullTextIsRefusedAtMinimumAndNativeOnePixelFontIsRespected() {
        assertNull(PhotoTextLayoutTypography.fit("Cannot fit this complete title", PhotoTextLayout.Frame(0f, 0f, 1f, 1f), 1f, .2f))
        assertNull(PhotoTextLayoutTypography.fit("W".repeat(200), PhotoTextLayout.Frame(0f, 0f, 2f, 2f), 100f, .2f))
    }

    @Test fun measuredNormalizedFrameExactlyMatchesActualObjectPlan() {
        val batch = PhotoTextLayout.Batch.capture(1137, 821, listOf(PhotoTextLayout.RawLine("FRAME", PhotoTextLayout.Box(99, 43, 438, 101))))
        var measured: PhotoTextLayout.Frame? = null
        val plan = PhotoTextLayout.prepare(PhotoEdit(), batch, mapOf(1 to "FRAME"), PhotoTextLayout.Fitter { text, frame, unit, ideal ->
            measured = frame; PhotoTextLayoutTypography.fit(text, frame, unit, ideal)
        })
        val actual = PhotoObjectPlan.create(plan.inserted[1], 1137, 821)
        val frame = requireNotNull(measured)
        assertEquals(actual.width, frame.width, 0f); assertEquals(actual.height, frame.height, 0f)
        assertEquals(actual.centerX - actual.width / 2, frame.left, 0f); assertEquals(actual.centerY - actual.height / 2, frame.top, 0f)
    }

    @Test fun actualLayerRendererShowsBlackEditableTextAndWhitePaperOverRetainedOldObjects() {
        val old = PhotoObject("old-red", PhotoObjectType.RECTANGLE, width = 1f, height = 1f, color = 0xffff0000.toInt())
        val base = PhotoEdit(layers = PhotoLayers(listOf(old)))
        val batch = PhotoTextLayout.Batch.capture(400, 240, listOf(PhotoTextLayout.RawLine("HELLO", PhotoTextLayout.Box(30, 50, 300, 110))))
        val plan = PhotoTextLayout.prepare(base, batch, mapOf(1 to "HELLO"), PhotoTextLayoutTypography)
        val bitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xff00ff00.toInt()); PhotoLayersRenderer.draw(bitmap, plan.edit.layers)
            val pixels = IntArray(400 * 240); bitmap.getPixels(pixels, 0, 400, 0, 0, 400, 240)
            assertTrue(pixels.any { it == -0x1000000 }); assertTrue(pixels.any { it == -1 })
            assertFalse(pixels.any { it == 0xffff0000.toInt() || it == 0xff00ff00.toInt() })
            assertSame(old, plan.edit.layers.objects.first()); assertEquals(1, base.layers.objects.size)
            assertEquals("HELLO", plan.edit.layers.objects.last().text)
        } finally { bitmap.recycle() }
    }
}
