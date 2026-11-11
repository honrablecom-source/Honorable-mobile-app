package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class PhotoTextLayoutTest {
    private val fitter = PhotoTextLayout.Fitter { _, _, _, ideal -> ideal }
    private fun batch() = PhotoTextLayout.Batch.capture(800, 600, listOf(
        PhotoTextLayout.RawLine("FIRST OCR", PhotoTextLayout.Box(20, 40, 200, 80)),
        PhotoTextLayout.RawLine("SECOND OCR", PhotoTextLayout.Box(300, 500, 790, 590))))
    private fun plan(base: PhotoEdit = PhotoEdit(), corrections: Map<Int, String> = mapOf(2 to "Corrected second", 1 to "Corrected first")) =
        PhotoTextLayout.prepare(base, batch(), corrections, fitter, { "new-$it" })

    @Test fun onlyExplicitCorrectedLinesAreInsertedInSourceOrderWithPaperBelowThem() {
        val plan = plan(corrections = mapOf(2 to "Reviewed exact text"))
        assertEquals(listOf(2), plan.selected.map { it.id }); assertEquals("Reviewed exact text", plan.selected.single().text)
        assertEquals(2, plan.inserted.size); assertEquals(PhotoObjectType.RECTANGLE, plan.inserted[0].type)
        assertEquals(-1, plan.inserted[0].color); assertEquals(1f, plan.inserted[0].width, 0f); assertEquals(1f, plan.inserted[0].height, 0f)
        val text = plan.inserted[1]; assertEquals(PhotoObjectType.TEXT, text.type); assertEquals("Reviewed exact text", text.text)
        assertTrue(text.visible && !text.locked && text.opacity == 1f); assertEquals(-0x1000000, text.color); assertEquals(0, text.textStyle.alignment)
        assertEquals(listOf(1, 2), plan().selected.map { it.id })
    }

    @Test fun oldObjectsAndEveryOtherEditAreKeptExactlyForSingleUndoInsertion() {
        val old = PhotoObject("old", PhotoObjectType.TEXT, text = "Keep me", locked = true, visible = false)
        val base = PhotoEdit(rotation = 90, crop = .5f, brightness = 12f, layers = PhotoLayers(listOf(old)))
        val plan = plan(base)
        assertSame(old, plan.edit.layers.objects[0]); assertEquals(base, plan.edit.copy(layers = base.layers)); assertEquals(1, base.layers.objects.size)
        assertSame(base, plan.base); assertTrue(plan.matches(base)); assertFalse(plan.matches(base.copy()))
        assertEquals(listOf("old", "new-0", "new-1", "new-2"), plan.edit.layers.objects.map { it.id })
    }

    @Test fun layerCapacityReservesPaperAndNeverSilentlyDropsSelectedLines() {
        fun base(count: Int) = PhotoEdit(layers = PhotoLayers(List(count) { PhotoObject("old-$it", PhotoObjectType.RECTANGLE) }))
        assertEquals(32, plan(base(29)).edit.layers.objects.size)
        assertThrows(IllegalArgumentException::class.java) { plan(base(30)) }
        assertEquals(32, plan(base(30), mapOf(1 to "One")).edit.layers.objects.size)
        assertThrows(IllegalArgumentException::class.java) { plan(base(31), mapOf(1 to "One")) }
    }

    @Test fun emptyUnknownAndInvalidCorrectedTextRefusedWithoutChangingBase() {
        val base = PhotoEdit()
        for (selection in listOf(emptyMap(), mapOf(3 to "Unknown"), mapOf(1 to " "), mapOf(1 to "x".repeat(201)), mapOf(1 to "a\nb\nc\nd"), mapOf(1 to "NUL\u0000"), mapOf(1 to "\uD800"))) {
            assertThrows(IllegalArgumentException::class.java) { plan(base, selection) }; assertTrue(base.layers.objects.isEmpty())
        }
        assertEquals("  Café & <type>\nsecond  ", plan(corrections = mapOf(1 to "  Café & <type>\nsecond  ")).selected.single().text)
    }

    @Test fun rawDenseAndMalformedRecognitionRefusedInsteadOfTruncated() {
        val line = PhotoTextLayout.RawLine("Text", PhotoTextLayout.Box(0, 0, 10, 10))
        assertThrows(IllegalArgumentException::class.java) { PhotoTextLayout.Batch.capture(10, 10, List(101) { line }) }
        assertThrows(IllegalArgumentException::class.java) { PhotoTextLayout.Batch.capture(10, 10, listOf(line.copy(text = "x".repeat(1001)))) }
        assertThrows(IllegalArgumentException::class.java) { PhotoTextLayout.Batch.capture(10, 10, List(17) { line.copy(text = "x".repeat(1000)) }) }
        assertThrows(IllegalArgumentException::class.java) { PhotoTextLayout.Batch.capture(10, 10, listOf(line.copy(text = "\uD800"))) }
        assertThrows(IllegalArgumentException::class.java) { PhotoTextLayout.Batch.capture(Int.MAX_VALUE, Int.MAX_VALUE, listOf(line)) }
    }

    @Test fun clippedGeometryStaysFiniteInsideOutputAndOutsideDetectionsFail() {
        for ((w, h) in listOf(1 to 1, 1 to 1000, 1000 to 1, 8192 to 7, 7 to 8192, 800 to 600)) {
            val batch = PhotoTextLayout.Batch.capture(w, h, listOf(PhotoTextLayout.RawLine("A", PhotoTextLayout.Box(-100, -100, w + 100, h + 100))))
            val plan = PhotoTextLayout.prepare(PhotoEdit(), batch, mapOf(1 to "A"), fitter, { "id-$it" })
            val text = plan.inserted[1]
            assertTrue(text.x - text.width / 2 >= -1e-6f && text.x + text.width / 2 <= 1.000001f)
            assertTrue(text.y - text.height / 2 >= -1e-6f && text.y + text.height / 2 <= 1.000001f)
        }
        assertThrows(IllegalArgumentException::class.java) { PhotoTextLayout.Batch.capture(10, 10, listOf(PhotoTextLayout.RawLine("Outside", PhotoTextLayout.Box(12, 0, 15, 8)))) }
    }

    @Test fun unreadableNativeFontPlanIsNotAcceptedOrReplacedWithPartialText() {
        for (size in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, .019f, .201f)) {
            assertThrows(IllegalArgumentException::class.java) {
                PhotoTextLayout.prepare(PhotoEdit(), batch(), mapOf(1 to "All the corrected text"), PhotoTextLayout.Fitter { _, _, _, _ -> size })
            }
        }
    }

    @Test fun duplicateAndMalformedObjectIdsFailWithoutOverwritingExistingObjects() {
        val base = PhotoEdit(layers = PhotoLayers(listOf(PhotoObject("same", PhotoObjectType.RECTANGLE))))
        for (identifier in listOf("same", "bad/id", "", "x".repeat(65))) {
            assertThrows(IllegalArgumentException::class.java) { PhotoTextLayout.prepare(base, batch(), mapOf(1 to "Text"), fitter, { identifier }) }
        }
        assertEquals("same", base.layers.objects.single().id)
        assertThrows(IllegalArgumentException::class.java) { PhotoTextLayout.prepare(PhotoEdit(), batch(), mapOf(1 to "Text"), fitter, { "repeated" }) }
    }

    @Test fun cancellationAndFitterFailureNeverReturnPartiallyBuiltEdits() {
        val base = PhotoEdit(); var calls = 0
        assertThrows(CancellationException::class.java) {
            PhotoTextLayout.prepare(base, batch(), mapOf(1 to "One", 2 to "Two"), fitter, current = { if (++calls == 3) throw CancellationException() })
        }
        assertTrue(base.layers.objects.isEmpty())
        assertThrows(UnsupportedOperationException::class.java) { PhotoTextLayout.prepare(base, batch(), mapOf(1 to "One"), PhotoTextLayout.Fitter { _, _, _, _ -> throw UnsupportedOperationException() }) }
    }

    @Test fun capturedAndPlannedCollectionsAreDefensiveAndImmutable() {
        val raw = mutableListOf(PhotoTextLayout.RawLine("Text", PhotoTextLayout.Box(0, 0, 50, 20)))
        val batch = PhotoTextLayout.Batch.capture(100, 100, raw); raw.clear(); assertEquals(1, batch.lines.size)
        val selected = mutableMapOf(1 to "Corrected")
        val plan = PhotoTextLayout.prepare(PhotoEdit(), batch, selected, fitter); selected[1] = "Changed"
        assertEquals("Corrected", plan.selected.single().text)
        assertThrows(UnsupportedOperationException::class.java) { (plan.inserted as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (batch.lines as MutableList).clear() }
    }
}
