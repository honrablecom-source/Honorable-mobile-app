package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoRollCutTest {
    @Test fun sameSourceSameSpeedKeepsSourceContiguityAndCombinedDuration(){
        val clips=listOf(VideoSegment(1000,3000,title="left"),VideoSegment(3000,5000,title="right"))
        val changed=VideoRollCut.propose(clips,0,500,mapOf("primary" to 10000L))
        assertEquals(clips[0].copy(endMs=3500),changed[0]);assertEquals(clips[1].copy(startMs=3500),changed[1])
        assertEquals(VideoPlaybackTime.durationUs(clips),VideoPlaybackTime.durationUs(changed))
        assertEquals(3000L,clips[0].endMs);assertTrue(runCatching{(changed as MutableList).clear()}.isFailure)
    }
    @Test fun differentSourcesAndSpeedsUseTheirOwnSourceDeltasWithoutDiscardingMetadata(){
        val motion=VideoMotion(enabled=true,endZoom=2f,anchorEndMs=10000)
        val left=VideoSegment(1000,7000,speed=2f,volume=.5f,title="left",grade=VideoGrade(.2f,1.2f,.8f),motion=motion,captions=listOf(VideoCaption(5000,6000,"caption")))
        val right=VideoSegment(5000,7000,speed=.5f,sourceId="second",volume=0f,title="right",fadeEdges=true,audioFade=true)
        val changed=VideoRollCut.propose(listOf(left,right),0,-1000,mapOf("primary" to 10000L,"second" to 10000L))
        assertEquals(left.copy(endMs=5000),changed[0]);assertEquals(right.copy(startMs=4500),changed[1])
        assertEquals(7_000_000L,VideoPlaybackTime.durationUs(changed))
        assertEquals(left.captions,changed[0].captions);assertEquals(left.motion,changed[0].motion)
    }
    @Test fun allSupportedSpeedPairsKeepExactMicrosecondsAtBothOffsetDirections(){
        for(a in listOf(.5f,1f,1.5f,2f))for(b in listOf(.5f,1f,1.5f,2f))for(delta in listOf(-1000L,-500L,-2L,2L,500L,1000L)){
            val clips=listOf(VideoSegment(3000,7000,speed=a),VideoSegment(4000,8000,speed=b,sourceId="second"))
            val after=VideoRollCut.propose(clips,0,delta,mapOf("primary" to 20000L,"second" to 20000L))
            assertEquals("$a/$b/$delta",VideoPlaybackTime.durationUs(clips),VideoPlaybackTime.durationUs(after))
            assertEquals(delta*1000,after[0].durationUs-clips[0].durationUs)
        }
    }
    @Test fun signedOffsetParsingAndSourceHandleBoundsRejectInvalidInput(){
        assertEquals(500L,VideoRollCut.offset("+0.500"));assertEquals(-250L,VideoRollCut.offset(" -0.250 "));assertEquals(2L,VideoRollCut.offset("0.002"))
        for(value in listOf("","0","-0.000","0.001","0.003","1e2","1,5","NaN","++1","1.0001"))assertTrue(value,runCatching{VideoRollCut.offset(value)}.isFailure)
        val clips=listOf(VideoSegment(0,1000),VideoSegment(0,1000,sourceId="second"));val sources=mapOf("primary" to 1000L,"second" to 1000L)
        for(delta in listOf(-500L,500L,0L,1L,Long.MAX_VALUE))assertTrue(runCatching{VideoRollCut.propose(clips,0,delta,sources)}.isFailure)
        assertTrue(runCatching{VideoRollCut.propose(clips,1,500,sources)}.isFailure)
        assertTrue(runCatching{VideoRollCut.propose(clips,-1,500,sources)}.isFailure)
        assertTrue(runCatching{VideoRollCut.propose(emptyList(),0,500,sources)}.isFailure)
    }
    @Test fun canonicalProjectPreservesLaterClockSoundtrackUndoRedoAndFailureRedo(){
        val project=VideoEditProject(20000)
        val clips=listOf(VideoSegment(0,1000),VideoSegment(1000,7000,speed=2f),VideoSegment(5000,7000,speed=.5f,sourceId="second"),VideoSegment(1000,3000))
        val music=VideoSoundtrack("content://fixture/music",10000,loop=false,startMs=1000,endMs=8000)
        project.restore(clips,mapOf("second" to 12000L),music)
        val afterTime=VideoFilmstripScale.outputTime(clips,3,1000);val total=project.totalDurationMs
        project.rollCut(1,-1000);val rolled=project.segments.toList()
        assertEquals(clips[0],rolled[0]);assertEquals(clips[3],rolled[3]);assertEquals(music,project.soundtrack)
        assertEquals(afterTime,VideoFilmstripScale.outputTime(rolled,3,1000));assertEquals(total,project.totalDurationMs)
        assertTrue(project.undo());assertEquals(clips,project.segments);assertTrue(project.canRedo)
        assertTrue(runCatching{project.rollCut(1,1)}.isFailure);assertTrue(project.canRedo)
        assertTrue(project.redo());assertEquals(rolled,project.segments);assertEquals(music,project.soundtrack)
    }
    @Test fun rolledRangesAndExistingSourceMetadataSurviveDraftAndGuestRecovery(){
        val project=VideoEditProject(20000)
        project.restore(listOf(VideoSegment(1000,7000,speed=2f,title="left"),VideoSegment(5000,7000,speed=.5f,sourceId="second",captions=listOf(VideoCaption(4800,6000,"known")))) ,mapOf("second" to 12000L))
        project.rollCut(0,-1000)
        val left=project.segments[0]
        val draft=VideoDraft(20000,project.segments,0,left.startMs,left.startMs,left.endMs,listOf(VideoSource("second","content://fixture/two",12000)))
        assertEquals(draft,VideoDraftCodec.decode(VideoDraftCodec.encode(draft),20000))
        val primary="content://fixture/one"
        assertEquals(draft,GuestVideoProjectCodec.decode(GuestVideoProjectCodec.encode(primary,draft),primary,20000))
    }
    @Test fun timestampOverflowAndUnboundedSequencesFailBeforeMutation(){
        val near=VideoSegment(Long.MAX_VALUE-1000,Long.MAX_VALUE-100)
        val pair=listOf(near,VideoSegment(2000,4000,sourceId="second"))
        assertTrue(runCatching{VideoRollCut.propose(pair,0,500,mapOf("primary" to Long.MAX_VALUE,"second" to 10000L))}.isFailure)
        assertEquals(Long.MAX_VALUE-100,near.endMs)
        assertTrue(runCatching{VideoRollCut.propose(List(1001){VideoSegment(0,1000)},0,2,mapOf("primary" to 10000L))}.isFailure)
    }
    @Test fun availableOffsetBoundsAreExactQuantizedAndProtectAtLeastOneSourceMillisecond(){
        val clips=listOf(VideoSegment(1000,7000,speed=2f),VideoSegment(5000,7000,speed=.5f,sourceId="second"))
        val sources=mapOf("primary" to 10000L,"second" to 10000L)
        val limits=VideoRollCut.bounds(clips,0,sources)
        assertEquals(-2998L to 1500L,limits)
        for(delta in listOf(limits.first,limits.second))VideoRollCut.propose(clips,0,delta,sources)
        assertTrue(runCatching{VideoRollCut.propose(clips,0,limits.first-2,sources)}.isFailure)
        assertTrue(runCatching{VideoRollCut.propose(clips,0,limits.second+2,sources)}.isFailure)
        assertEquals(0L to 0L,VideoRollCut.bounds(listOf(VideoSegment(0,1000),VideoSegment(0,1000,sourceId="second")),0,mapOf("primary" to 1000L,"second" to 1000L)))
    }
}
