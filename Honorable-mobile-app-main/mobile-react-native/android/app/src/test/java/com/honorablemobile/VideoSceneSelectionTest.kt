package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoSceneSelectionTest {
    @Test fun samplesStayInsidePendingTrimAndSelectedClip() {
        val clips = listOf(VideoSegment(0, 40_000), VideoSegment(60_000, 120_000))
        val selection = VideoSceneSelection.capture(clips, 1, 72_000, 92_000)
        assertEquals(listOf(74_000L, 78_000L, 82_000L, 86_000L, 90_000L), selection.sampleTimes())
        assertEquals(72_000L to 77_000L, selection.suggestedRange(73_000))
        assertEquals(87_000L to 92_000L, selection.suggestedRange(91_000))
    }
    @Test fun longAndShortSelectionsUseBoundedSamples() {
        val clips = listOf(VideoSegment(0, 900_000))
        assertEquals(12, VideoSceneSelection.capture(clips, 0, 0, 900_000).sampleTimes().size)
        val short = VideoSceneSelection.capture(clips, 0, 2_000, 2_001)
        assertEquals(listOf(2_000L), short.sampleTimes())
        assertEquals(2_000L to 2_001L, short.suggestedRange(500_000))
    }
    @Test fun lateResultCannotOverwriteTrimClipOrProjectChanges() {
        val clips = listOf(VideoSegment(0, 20_000), VideoSegment(30_000, 50_000))
        val s = VideoSceneSelection.capture(clips, 0, 2_000, 12_000)
        assertTrue(s.stillMatches(clips, 0, 2_000, 12_000))
        assertFalse(s.stillMatches(clips, 0, 3_000, 12_000))
        assertFalse(s.stillMatches(clips, 1, 30_000, 50_000))
        assertFalse(s.stillMatches(clips.map { it.copy(speed = 2f) }, 0, 2_000, 12_000))
        assertFalse(s.stillMatches(clips.reversed(), 0, 2_000, 12_000))
    }
    @Test fun captureOwnsItsListAndClampsToRealClipBounds() {
        val clips = mutableListOf(VideoSegment(1_555, 15_500))
        val s = VideoSceneSelection.capture(clips, 0, 1_000, 16_000)
        clips.clear()
        assertEquals(1_555L, s.startMs)
        assertEquals(15_500L, s.endMs)
        assertTrue(s.sampleTimes().all { it >= 1_555 && it < 15_500 })
    }
    @Test fun emptyOrNonOverlappingSelectionsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { VideoSceneSelection.capture(emptyList(), 0, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { VideoSceneSelection.capture(listOf(VideoSegment(0, 5_000)), 0, 6_000, 7_000) }
    }
}
