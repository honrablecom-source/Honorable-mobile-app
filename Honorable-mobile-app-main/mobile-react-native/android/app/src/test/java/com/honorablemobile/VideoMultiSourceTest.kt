package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class VideoMultiSourceTest {
    private val second = VideoSource("second", "content://documents/video-2", 30_000)
    private fun multi() = VideoDraft(10_000, listOf(VideoSegment(1_000, 9_000), VideoSegment(20_000, 29_000, 2f, .5f, "second")),
        1, 25_000, 21_000, 28_000, listOf(second))
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }

    @Test fun importedSourceUsesItsOwnDurationAndRemainsUndoable() {
        val p = VideoEditProject(10_000)
        p.appendSource("second", 30_000)
        p.trim(1, 20_000, 29_000); p.setSpeed(1, 2f); p.setVolume(1, .5f)
        p.split(1, 25_000); p.move(2, 0); p.duplicate(0)
        assertEquals(listOf("second", "second", "primary", "second"), p.segments.map { it.sourceId })
        assertTrue(p.segments.filter { it.sourceId == "second" }.all { it.speed == 2f && it.volume == .5f })
        val edited = p.segments
        p.reset(); assertEquals(listOf(VideoSegment(0, 10_000)), p.segments)
        assertTrue(p.undo()); assertEquals(edited, p.segments)
    }
    @Test fun multiSourceDraftRoundTripIncludesSelectedSourcePosition() {
        val draft = multi(); val encoded = VideoDraftCodec.encode(draft)
        assertEquals(2, ByteBuffer.wrap(encoded).getInt(4))
        assertEquals(draft, VideoDraftCodec.decode(encoded, 10_000))
        val p = VideoEditProject(10_000)
        p.applyDraft(draft.segments, draft.sources.associate { it.id to it.durationMs })
        assertEquals(draft.segments, p.segments)
        assertEquals(12_500L, p.totalDurationMs)
        assertTrue(p.undo()); assertEquals("primary", p.segments.single().sourceId)
        assertTrue(p.redo()); assertEquals("second", p.segments.last().sourceId)
    }
    @Test fun legacyDraftRemainsVersionOneAndReadable() {
        val legacy = VideoDraft(10_000, listOf(VideoSegment(0, 10_000)), 0, 100, 0, 10_000)
        val bytes = VideoDraftCodec.encode(legacy)
        assertEquals(1, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(legacy, VideoDraftCodec.decode(bytes, 10_000))
    }
    @Test fun sourceMismatchAndMissingIdsAreRejectedWithoutChangingEdits() {
        val p = VideoEditProject(10_000); val original = p.segments
        rejected { p.applyDraft(listOf(VideoSegment(0, 20_000, sourceId="missing"))) }
        rejected { p.appendSource("primary", 30_000) }
        assertEquals(original, p.segments)
        p.appendSource("second", 30_000); val current = p.segments
        rejected { p.applyDraft(listOf(VideoSegment(0, 31_000, sourceId="second"))) }
        rejected { p.applyDraft(listOf(VideoSegment(0, 5_000)), mapOf("second" to 5_000)) }
        assertEquals(current, p.segments)
    }
    @Test fun corruptMultiSourcePayloadsCannotReplaceProjects() {
        val bytes = VideoDraftCodec.encode(multi())
        for(length in bytes.indices) assertNull(VideoDraftCodec.decode(bytes.copyOf(length), 10_000))
        assertNull(VideoDraftCodec.decode(bytes+byteArrayOf(0), 10_000))
        assertNull(VideoDraftCodec.decode(bytes.clone().apply { ByteBuffer.wrap(this).putInt(4, 99) }, 10_000))
        assertNull(VideoDraftCodec.decode(bytes.clone().apply { ByteBuffer.wrap(this).putInt(72, 99) }, 10_000))
        assertNull(VideoDraftCodec.decode(bytes, 10_001))
    }
    @Test fun sourceAndClipLimitsRemainBounded() {
        val p = VideoEditProject(10_000)
        for(i in 1..31) p.appendSource("s$i", 1000)
        val clips = p.segments
        rejected { p.appendSource("overflow", 1000) }; assertEquals(clips, p.segments)
        val one = VideoEditProject(1000)
        one.restore(List(1000) { VideoSegment(0,1000) })
        rejected { one.split(0, 500) }; assertEquals(1000, one.segments.size)
        rejected { VideoSource("bad", "https://remote.example/video", 1000) }
        rejected { VideoDraft(10_000, multi().segments, 1, 25_000, 20_000, 29_000) }
    }
    @Test fun sceneSelectionCannotCrossToAnotherSource() {
        val a = listOf(VideoSegment(0, 10_000))
        val selection = VideoSceneSelection.capture(a, 0, 0, 10_000)
        assertFalse(selection.stillMatches(listOf(VideoSegment(0,10_000,sourceId="second")),0,0,10_000))
    }
}
