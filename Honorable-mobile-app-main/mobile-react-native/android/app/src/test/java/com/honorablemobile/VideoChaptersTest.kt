package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoChaptersTest {
    @Test fun chaptersUseEditedOrderTrimDurationAndPlaybackSpeedWithoutMutation() {
        val clips = listOf(VideoSegment(1000, 4000, speed=2f, title="Opening", sourceId="private-source"), VideoSegment(500, 1500, speed=.5f), VideoSegment(0, 1000, title="End"))
        val before = clips.toList()
        val text = VideoChapters.text(clips)
        assertTrue(text.endsWith("00:00:00.000 Opening\n00:00:01.500 Clip 2\n00:00:03.500 End"))
        assertFalse(text.contains("private-source"))
        assertEquals(before, clips)
    }
    @Test fun fractionalClipDurationsAccumulateBeforeMillisecondFormatting() {
        val clip = VideoSegment(0, 1000, speed=1.5f, title="Part")
        assertTrue(VideoChapters.text(listOf(clip,clip,clip,clip)).endsWith("00:00:01.999 Part"))
        assertEquals(clip, clip.copy())
    }
    @Test fun labelsStayOnOneLineAndLongTimelinesKeepHours() {
        val clips = listOf(VideoSegment(0, 3_661_123, title=" First\nsecond "), VideoSegment(0, 1000, title=" \n "))
        val text = VideoChapters.text(clips)
        assertTrue(text.endsWith("00:00:00.000 First second\n01:01:01.123 Clip 2"))
    }
    @Test fun emptyOversizedAndOverflowingTimelinesAreRejected() {
        val clip = VideoSegment(0, 1000)
        assertTrue(runCatching { VideoChapters.text(emptyList()) }.isFailure)
        assertTrue(runCatching { VideoChapters.text(List(1001){clip}) }.isFailure)
        val huge = VideoSegment(0, Long.MAX_VALUE)
        assertTrue(runCatching { VideoChapters.text(listOf(huge,huge)) }.isFailure)
    }
}
