package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoRefinementToolsTest {
    private val a=VideoSegment(1000,3000,title="A",volume=.5f,grade=VideoGrade(saturation=.6f))
    private val b=VideoSegment(0,1000,title="B")
    @Test fun sortingIsStableAndRetainsEveryOwnedEdit(){
        val original=listOf(a,b,a.copy(title="A2"))
        assertEquals(listOf(b,a,a.copy(title="A2")),VideoRefinementTools.sort(original,VideoSortOrder.SOURCE_TIME))
        assertEquals(listOf(b,a,a.copy(title="A2")),VideoRefinementTools.sort(original,VideoSortOrder.SHORTEST_FIRST))
        assertEquals(listOf(a,a.copy(title="A2"),b),VideoRefinementTools.sort(original,VideoSortOrder.LONGEST_FIRST))
        assertEquals(a,original.first())
    }
    @Test fun duplicateRemovalKeepsDifferentSourceOrRenderEdits(){
        val styled=a.copy(volume=.25f);val other=a.copy(sourceId="other")
        assertEquals(listOf(a,styled,other),VideoRefinementTools.removeExactDuplicates(listOf(a,a,styled,other,a)))
    }
    @Test fun matchingCutsJoinOnlyWhenFramingAudioAndEffectsAgree(){
        val first=VideoSegment(0,1000);val second=first.copy(startMs=1000,endMs=3000)
        assertEquals(listOf(VideoSegment(0,3000)),VideoRefinementTools.joinMatchingCuts(listOf(first,second)))
        for(changed in listOf(second.copy(volume=.5f),second.copy(fadeEdges=true),second.copy(audioFade=true),second.copy(grade=VideoGrade(exposure=.5f)),second.copy(layout=VideoLayout(rotation=90)),second.copy(title="Different"),second.copy(sourceId="other")))assertEquals(listOf(first,changed),VideoRefinementTools.joinMatchingCuts(listOf(first,changed)))
    }
    @Test fun joiningRetainsCrossCutCaptionsAndSubtitleProvenance(){
        val crossing=VideoCaption(500,1500,"Crossing")
        val first=VideoSegment(0,1000,captions=listOf(crossing),captionSourceUri="content://subtitle/document")
        val second=first.copy(startMs=1000,endMs=2000,captions=listOf(crossing,VideoCaption(1600,1900,"Later")))
        val result=VideoRefinementTools.joinMatchingCuts(listOf(first,second)).single()
        assertEquals(listOf(crossing,VideoCaption(1600,1900,"Later")),result.captions)
        assertEquals(first.captionSourceUri,result.captionSourceUri)
    }
    @Test fun captionHoldsClipToSourceRangeAndShareGapsWithoutOverlap(){
        val clip=VideoSegment(1000,5000,speed=2f,captions=listOf(VideoCaption(0,1500,"First"),VideoCaption(2500,3500,"Middle"),VideoCaption(4500,6000,"Last")),captionSourceUri="content://subtitle/document")
        val result=VideoRefinementTools.extendCaptionHolds(listOf(clip),0,1000,1000).single()
        assertEquals(listOf(VideoCaption(1000,2000,"First"),VideoCaption(2000,4000,"Middle"),VideoCaption(4000,5000,"Last")),result.captions)
        assertEquals(clip.speed,result.speed);assertEquals(clip.captionSourceUri,result.captionSourceUri)
        assertTrue(runCatching{VideoRefinementTools.extendCaptionHolds(listOf(clip),0,0,0)}.isFailure)
        assertTrue(runCatching{VideoRefinementTools.extendCaptionHolds(listOf(clip),0,2001,0)}.isFailure)
    }
    @Test fun captionCaseKeepsTimingAndRejectsUnicodeExpansionAtomically(){
        val cue=VideoCaption(0,1000,"Hello\nworld");val clip=VideoSegment(0,2000,captions=listOf(cue))
        assertEquals(cue.copy(text="HELLO\nWORLD"),VideoRefinementTools.captionCase(listOf(clip),0,CaptionLetterCase.UPPER).single().captions.single())
        val long=clip.copy(captions=listOf(cue.copy(text="ß".repeat(200))))
        assertTrue(runCatching{VideoRefinementTools.captionCase(listOf(long),0,CaptionLetterCase.UPPER)}.isFailure)
        assertEquals("ß".repeat(200),long.captions.single().text)
    }
    @Test fun reportAccountsForPlaybackSpeedAndDoesNotClaimMediaAnalysis(){
        val clip=VideoSegment(0,1000,speed=2f,captions=listOf(VideoCaption(0,500,"Caption")))
        val report=VideoRefinementTools.report(listOf(clip,clip,VideoSegment(1000,1200,volume=0f)))
        assertEquals(1_200_000L,report.durationUs);assertEquals(500_000L,report.captionCoverageUs);assertEquals(2,report.visibleCaptions)
        assertEquals(1,report.duplicateClips);assertEquals(1,report.mutedClips);assertEquals(1,report.shortClips)
        assertTrue(report.text().contains("not image quality or audio loudness"));assertFalse(report.text().contains("content://"))
    }
    @Test fun proposalsUseExistingProjectUndoRecoveryAndSoundtrackAuthority(){
        val original=listOf(a,b,a);val music=VideoSoundtrack("content://audio/music",5000)
        for(proposal in listOf(VideoRefinementTools.sort(original,VideoSortOrder.SOURCE_TIME),VideoRefinementTools.removeExactDuplicates(original))){
            val project=VideoEditProject(3000);project.restore(original,soundtrack=music)
            project.applyDraft(proposal,soundtrack=music);assertEquals(proposal,project.segments);assertEquals(music,project.soundtrack)
            assertTrue(project.undo());assertEquals(original,project.segments);assertEquals(music,project.soundtrack)
            assertTrue(project.redo());assertEquals(proposal,project.segments)
            val draft=VideoDraft(3000,proposal,0,0,proposal.first().startMs,proposal.first().endMs,soundtrack=music)
            assertEquals(draft,VideoDraftCodec.decode(VideoDraftCodec.encode(draft),3000))
        }
    }
}
