package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoPlaybackTimeTest {
    @Test fun mixedSpeedsShareOneMicrosecondDurationAndOneFinalRounding() {
        val clips=listOf(VideoSegment(0,1000,speed=1.5f),VideoSegment(0,1000,speed=2f),VideoSegment(0,1000,speed=.5f))
        val before=clips.toList()
        assertEquals(3_166_666L,VideoPlaybackTime.durationUs(clips))
        assertEquals(3166L,VideoPlaybackTime.durationMs(clips))
        assertEquals(before,clips)
    }
    @Test fun subMillisecondRemaindersAccumulateWithoutRoundingEachClip() {
        val clips=List(3){VideoSegment(0,1000,speed=1.5f)}
        assertEquals(1_999_998L,VideoPlaybackTime.durationUs(clips))
        assertEquals(1999L,VideoPlaybackTime.durationMs(clips))
        assertEquals(666L,VideoPlaybackTime.durationMs(clips.take(1)))
    }
    @Test fun invalidCountsAndOverflowAreRejectedInsteadOfWrappingNegative() {
        val clip=VideoSegment(0,1000)
        assertTrue(runCatching{VideoPlaybackTime.durationUs(emptyList())}.isFailure)
        assertTrue(runCatching{VideoPlaybackTime.durationUs(List(1001){clip})}.isFailure)
        val huge=VideoSegment(0,Long.MAX_VALUE)
        val failure=runCatching{VideoPlaybackTime.durationUs(listOf(huge,huge))}.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("duration is too large")==true)
    }
}
