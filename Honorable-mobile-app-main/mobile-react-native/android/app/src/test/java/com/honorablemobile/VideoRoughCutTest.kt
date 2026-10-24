package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class VideoRoughCutTest {
    private fun rejected(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    @Test fun fiveLongClipsCreateThirtySecondDraftAroundDetectedMoments(){
        val clips=List(5){VideoSegment(5000,25_000,sourceId="s$it",title="Scene $it",fadeEdges=true)}
        val result=VideoRoughCut.assemble(clips,List(5){15_000},30_000)
        assertEquals(30_000L,result.sumOf{it.durationMs})
        for(i in clips.indices){assertEquals(12_000L,result[i].startMs);assertEquals(18_000L,result[i].endMs);assertEquals(clips[i].sourceId,result[i].sourceId);assertEquals(clips[i].title,result[i].title);assertTrue(result[i].fadeEdges)}
    }
    @Test fun shortClipsAreKeptAndUnusedDurationRedistributed(){
        val clips=listOf(VideoSegment(0,2000),VideoSegment(0,30_000),VideoSegment(0,30_000))
        val result=VideoRoughCut.assemble(clips,listOf(0,15_000,15_000),30_000)
        assertEquals(2000L,result[0].durationMs);assertEquals(14_000L,result[1].durationMs);assertEquals(30_000L,result.sumOf{it.durationMs})
    }
    @Test fun speedVolumeAndBoundaryConstraintsSurviveMixedPlaybackRates(){
        val clips=listOf(VideoSegment(10_000,30_000,.5f,.3f),VideoSegment(20_000,80_000,1.5f,.7f),VideoSegment(0,80_000,2f,0f))
        val result=VideoRoughCut.assemble(clips,listOf(-1,Long.MAX_VALUE,5000),15_001)
        assertTrue(abs(15_001-result.sumOf{it.durationMs})<=3)
        for(i in clips.indices){assertEquals(clips[i].speed,result[i].speed,0f);assertEquals(clips[i].volume,result[i].volume,0f);assertTrue(result[i].startMs>=clips[i].startMs);assertTrue(result[i].endMs<=clips[i].endMs)}
    }
    @Test fun targetsLongerThanFootageDoNotInventOrExtendFrames(){
        val clips=listOf(VideoSegment(1000,3000),VideoSegment(0,4000))
        assertEquals(clips,VideoRoughCut.assemble(clips,listOf(1500,2000),30_000))
    }
    @Test fun invalidPlansAndUnsupportedBudgetsFailWithoutChangingInput(){
        val clips=listOf(VideoSegment(0,10_000))
        rejected{VideoRoughCut.assemble(clips,emptyList(),30_000)};rejected{VideoRoughCut.assemble(clips,listOf(1),61_000)}
        rejected{VideoRoughCut.assemble(List(9){clips[0]},List(9){1},30_000)}
        assertEquals(listOf(VideoSegment(0,10_000)),clips)
    }
    @Test fun samplesStayInsideSelectedSourceRangesAndRejectTimestampOverflow(){
        val clip=VideoSegment(5000,9000);assertEquals(listOf(5500L,6500L,7500L,8500L),VideoRoughCut.sampleTimes(clip))
        rejected{VideoRoughCut.sampleTimes(VideoSegment(0,Long.MAX_VALUE))}
    }
    @Test fun focusScoresUseRecognizedSubjectsAndIgnoreGenericOrInvalidLabels(){
        assertEquals(0f,VideoRoughCut.score(listOf("Photography" to .99f,"Person" to Float.NaN),RoughCutFocus.PEOPLE),0f)
        val labels=listOf("Person" to .8f,"Sky" to .7f)
        assertTrue(VideoRoughCut.score(labels,RoughCutFocus.PEOPLE)>VideoRoughCut.score(labels,RoughCutFocus.BALANCED))
    }
    @Test fun applyingDraftIsOneUndoableEditAndKeepsMusicAndCompatibleDrafts(){
        val p=VideoEditProject(30_000);p.appendSource("second",30_000)
        val track=VideoSoundtrack("content://audio/test",15_000);p.setSoundtrack(track)
        val before=p.segments;val next=VideoRoughCut.assemble(before,listOf(15_000,15_000),15_000)
        p.applyDraft(next,soundtrack=track);assertEquals(track,p.soundtrack);assertTrue(p.undo());assertEquals(before,p.segments);assertEquals(track,p.soundtrack)
        assertTrue(p.redo());assertEquals(next,p.segments)
    }
}
