package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoClipWindowTest {
    private val clip=VideoSegment(2000,4000,speed=1.5f,volume=.5f,title="Keep title",fadeEdges=true,grade=VideoGrade(.2f,1.2f,.8f),audioFade=true,
        captions=listOf(VideoCaption(1000,1500,"before"),VideoCaption(2500,3500,"current"),VideoCaption(6500,7000,"later")),
        captionSourceUri="content://fixture/subtitles",motion=VideoMotion(enabled=true,endZoom=2f,anchorStartMs=0,anchorEndMs=10000))
    @Test fun differentFootageKeepsSpanSpeedAndEveryOtherClipSetting(){
        val moved=VideoClipWindow.move(clip,6000,10000)
        assertEquals(clip.copy(startMs=6000,endMs=8000),moved)
        assertEquals(clip.durationUs,moved.durationUs)
        assertEquals(8000L,VideoClipWindow.latestStart(clip,10000))
        assertEquals(0L,VideoClipWindow.move(clip,0,10000).startMs)
        assertEquals(10000L,VideoClipWindow.move(clip,8000,10000).endMs)
        assertEquals(2000L,clip.startMs)
    }
    @Test fun existingCaptionsAndMotionStayAtSourceTimeRatherThanBeingRetimedOrLost(){
        val moved=VideoClipWindow.move(clip,6000,10000)
        assertEquals(clip.captions,moved.captions);assertEquals(clip.motion,moved.motion)
        assertEquals(listOf("current"),VideoCaptions.resolve(listOf(clip)).map{it.text})
        assertEquals(listOf("later"),VideoCaptions.resolve(listOf(moved)).map{it.text})
        assertEquals(clip.motion.atSource(6500.0),moved.motion.atSource(6500.0))
        assertNotEquals(clip.motion.atSource(clip.startMs.toDouble()),moved.motion.atSource(moved.startMs.toDouble()))
    }
    @Test fun actualProjectUndoRedoKeepsNeighborPositionsAndSoundtrack(){
        val project=VideoEditProject(10000)
        val before=listOf(VideoSegment(0,1000,title="Neighbor before"),clip.copy(sourceId="second"),VideoSegment(9000,10000,title="Neighbor after"))
        val music=VideoSoundtrack("content://fixture/music",20000,loop=false,startMs=5000,endMs=10000)
        project.restore(before,mapOf("second" to 12000L),music)
        val neighborTime=VideoFilmstripScale.outputTime(before,2,9000);val duration=project.totalDurationMs
        project.slipSourceWindow(1,6000)
        val moved=project.segments.toList()
        assertEquals(before[0],moved[0]);assertEquals(before[2],moved[2]);assertEquals(music,project.soundtrack)
        assertEquals(neighborTime,VideoFilmstripScale.outputTime(moved,2,9000));assertEquals(duration,project.totalDurationMs)
        assertTrue(project.undo());assertEquals(before,project.segments);assertEquals(music,project.soundtrack)
        assertTrue(project.redo());assertEquals(moved,project.segments)
    }
    @Test fun slippedMultiSourceWindowRecoversThroughDraftAndGuestEnvelope(){
        val project=VideoEditProject(10000);project.restore(listOf(VideoSegment(0,1000),clip.copy(sourceId="second")),mapOf("second" to 12000L))
        project.slipSourceWindow(1,6000)
        val draft=VideoDraft(10000,project.segments,1,6000,6000,8000,listOf(VideoSource("second","content://fixture/two",12000)))
        assertEquals(draft,VideoDraftCodec.decode(VideoDraftCodec.encode(draft),10000))
        val root="content://fixture/one"
        assertEquals(draft,GuestVideoProjectCodec.decode(GuestVideoProjectCodec.encode(root,draft),root,10000))
    }
    @Test fun outOfSourceUnchangedAndFullSourceWindowsAreRejected(){
        for(start in listOf(-1L,2000L,8001L,Long.MAX_VALUE))assertTrue(runCatching{VideoClipWindow.move(clip,start,10000)}.isFailure)
        assertTrue(runCatching{VideoClipWindow.move(clip,0,3999)}.isFailure)
        val full=VideoSegment(0,1000)
        assertEquals(0L,VideoClipWindow.latestStart(full,1000))
        assertTrue(runCatching{VideoClipWindow.move(full,0,1000)}.isFailure)
        assertTrue(runCatching{VideoClipWindow.move(full,1,1000)}.isFailure)
    }
    @Test fun rejectedProposalsDoNotMutateProjectOrClearAvailableRedo(){
        val project=VideoEditProject(10000);project.restore(listOf(clip))
        val before=project.segments
        for(action in listOf<()->Unit>({project.slipSourceWindow(-1,1000)},{project.slipSourceWindow(1,1000)},{project.slipSourceWindow(0,9000)},{project.slipSourceWindow(0,2000)}))assertTrue(runCatching(action).isFailure)
        assertEquals(before,project.segments);assertFalse(project.canUndo)
        project.slipSourceWindow(0,6000);val moved=project.segments;assertTrue(project.undo());assertTrue(project.canRedo)
        assertTrue(runCatching{project.slipSourceWindow(0,9000)}.isFailure)
        assertTrue(project.canRedo);assertTrue(project.redo());assertEquals(moved,project.segments)
    }
    @Test fun numericLimitChecksRejectBeforeAdditionCanOverflow(){
        val small=VideoSegment(100,1100)
        val latest=Long.MAX_VALUE-1000
        assertEquals(latest,VideoClipWindow.latestStart(small,Long.MAX_VALUE))
        val moved=VideoClipWindow.move(small,latest,Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE,moved.endMs);assertEquals(small.durationUs,moved.durationUs)
        assertTrue(runCatching{VideoClipWindow.move(small,latest+1,Long.MAX_VALUE)}.isFailure)
    }
}
