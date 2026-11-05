package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoSoundtrackReportTest {
    private val music=VideoSoundtrack("content://private/music",10000,startMs=1000,endMs=3500)
    @Test fun loopingExplainsRepeatedExcerptAndPartialFinalPass(){
        val report=VideoSoundtrackReport.inspect(listOf(VideoSegment(0,7000)),music)
        assertEquals(3L,report.passes);assertEquals(2000000L,report.lastPassUs);assertEquals(0L,report.uncoveredUs)
        assertTrue(report.text().contains("3 playback passes"));assertTrue(report.text().contains("final pass 2.000 s of 2.500 s"))
        assertFalse(report.text().contains("content://"))
    }
    @Test fun exactRepeatedBoundaryHasAFullFinalPass(){
        val report=VideoSoundtrackReport.inspect(listOf(VideoSegment(0,5000)),music)
        assertEquals(2L,report.passes);assertEquals(2500000L,report.lastPassUs)
        assertEquals(5000000L,report.projectUs);assertEquals(2500000L,report.excerptUs)
    }
    @Test fun onceDistinguishesTruncationFromAProjectTailWithoutSoundtrack(){
        val track=music.copy(loop=false)
        val tail=VideoSoundtrackReport.inspect(listOf(VideoSegment(0,7000)),track)
        assertEquals(1L,tail.passes);assertEquals(4500000L,tail.uncoveredUs)
        assertTrue(tail.text().contains("No soundtrack for the final 4.500 s"))
        val truncated=VideoSoundtrackReport.inspect(listOf(VideoSegment(0,1000)),track)
        assertEquals(1000000L,truncated.lastPassUs);assertEquals(0L,truncated.uncoveredUs)
        assertTrue(truncated.text().contains("project end cuts it to 1.000 s"))
    }
    @Test fun canonicalMicrosecondsAccumulateBeforeDisplayingAndMuteIsExplicit(){
        val clips=List(3){VideoSegment(0,1,speed=2f)}
        val track=VideoSoundtrack("content://private/music",1,volume=0f)
        val report=VideoSoundtrackReport.inspect(clips,track)
        assertEquals(1500L,report.projectUs);assertEquals(2L,report.passes);assertEquals(500L,report.lastPassUs)
        assertTrue(report.text().contains("Muted (0%)"));assertTrue(report.text().contains("Original clip audio is separate"))
        assertEquals(1L,VideoSoundtrackReport.inspect(listOf(VideoSegment(0,1,speed=2f)),track).passes)
    }
    @Test fun reportLeavesActualProjectAndSoundtrackHistoryUnchanged(){
        val p=VideoEditProject(7000);p.setSoundtrack(music);val before=p.segments
        VideoSoundtrackReport.inspect(p.segments,music).text()
        assertSame(before,p.segments);assertEquals(music,p.soundtrack);assertTrue(p.canUndo);assertFalse(p.canRedo)
        assertTrue(p.undo());assertNull(p.soundtrack);assertTrue(p.canRedo)
    }
    @Test fun repeatBoundsAndInvalidTimelinesUseExistingCanonicalGuard(){
        val tiny=VideoSoundtrack("content://private/music",1)
        assertEquals(1000L,VideoSoundtrackReport.inspect(listOf(VideoSegment(0,1000)),tiny).passes)
        assertTrue(runCatching{VideoSoundtrackReport.inspect(listOf(VideoSegment(0,1001)),tiny)}.isFailure)
        assertTrue(runCatching{VideoSoundtrackReport.inspect(emptyList(),music)}.isFailure)
        assertTrue(runCatching{VideoSoundtrackReport.inspect(List(1001){VideoSegment(0,1)},music)}.isFailure)
    }
    @Test fun displayedTotalsAvoidDoublePrecisionDriftAtCanonicalLongLimit(){
        val report=VideoSoundtrackReport.inspect(listOf(VideoSegment(0,Long.MAX_VALUE)),music.copy(loop=false))
        assertEquals(Long.MAX_VALUE,report.projectUs)
        assertTrue(report.text().startsWith("Project 9223372036854.776 s"))
        assertEquals(Long.MAX_VALUE-2500000L,report.uncoveredUs)
    }
}
