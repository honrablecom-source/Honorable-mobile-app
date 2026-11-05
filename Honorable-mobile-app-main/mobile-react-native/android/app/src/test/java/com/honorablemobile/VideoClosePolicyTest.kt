package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoClosePolicyTest {
    private fun capture(project:VideoEditProject?,duration:Long=8000,selected:Int=0,from:Long?=0,to:Long?=8000,busy:Boolean=false,recovery:Boolean=false,source:String="content://synthetic/video")=
        VideoCloseState.capture(project,duration,selected,from,to,busy,recovery,source)
    @Test fun untouchedOriginalCanLeaveWithoutConfirmation() {
        val project=VideoEditProject(8000)
        assertFalse(capture(project).needsConfirmation())
        assertFalse(capture(null).needsConfirmation())
        assertFalse(capture(VideoEditProject(8001),duration=8001,to=8001).needsConfirmation())
    }
    @Test fun editedFieldsAndAdditionalClipsRequireConfirmation() {
        val original=VideoSegment(0,8000)
        for(clip in listOf(original.copy(title="Keep words"),original.copy(speed=2f),original.copy(volume=.5f),original.copy(titleStyle=VideoTitleStyle(0)),original.copy(captions=listOf(VideoCaption(0,1000,"Text"))),original.copy(layout=VideoLayout(rotation=90)))) {
            val project=VideoEditProject(8000);project.restore(listOf(clip))
            assertTrue(capture(project).needsConfirmation());assertFalse(project.canUndo)
        }
        val project=VideoEditProject(8000);project.split(0,4000)
        assertTrue(capture(project,to=4000).needsConfirmation())
    }
    @Test fun pendingInvalidOrUnavailableRangeIsConservativelyConfirmed() {
        val project=VideoEditProject(8000)
        assertTrue(capture(project,from=1000).needsConfirmation())
        assertTrue(capture(project,to=7000).needsConfirmation())
        assertTrue(capture(project,from=7000,to=1000).needsConfirmation())
        assertTrue(capture(project,from=null,to=null).needsConfirmation())
        assertTrue(capture(project,selected=2).needsConfirmation())
    }
    @Test fun busyAndPendingRecoveryAreConfirmedEvenBeforeProjectLoads() {
        assertTrue(capture(null,busy=true).needsConfirmation())
        assertTrue(capture(null,recovery=true).needsConfirmation())
        assertTrue(capture(VideoEditProject(8000),busy=true).needsConfirmation())
    }
    @Test fun restoredEditedProjectAndSoundtrackAreConfirmedWithoutHistory() {
        val project=VideoEditProject(8000)
        project.restore(listOf(VideoSegment(2000,6000)))
        assertFalse(project.canUndo);assertTrue(capture(project,from=2000,to=6000).needsConfirmation())
        project.restore(listOf(VideoSegment(0,8000)),soundtrack=VideoSoundtrack("content://synthetic/music",8000))
        assertFalse(project.canUndo);assertTrue(capture(project).needsConfirmation())
    }
    @Test fun undoToUnchangedOriginalCanLeaveAndMatchesOriginalToken() {
        val project=VideoEditProject(8000);val before=capture(project)
        project.split(0,4000);assertFalse(before.stillMatches(capture(project,to=4000)))
        project.undo();assertFalse(capture(project).needsConfirmation());assertTrue(before.stillMatches(capture(project)))
    }
    @Test fun staleProjectSourceSelectionRangeBusyOrRecoveryCannotConfirmOldState() {
        val project=VideoEditProject(8000);val before=capture(project)
        for(changed in listOf(capture(VideoEditProject(8000)),capture(project,source="content://synthetic/other"),capture(project,selected=1),capture(project,from=1000),capture(project,to=7000),capture(project,busy=true),capture(project,recovery=true),capture(project,duration=8001)))assertFalse(before.stillMatches(changed))
        project.setSoundtrack(VideoSoundtrack("content://synthetic/audio",8000))
        assertFalse(before.stillMatches(capture(project)))
    }
    @Test fun capturedCaptionValuesAreDetachedFromMutableInput() {
        val cues=mutableListOf(VideoCaption(0,1000,"Keep"));val project=VideoEditProject(8000)
        project.restore(listOf(VideoSegment(0,8000,captions=cues)))
        val before=capture(project);cues.clear()
        assertFalse(before.stillMatches(capture(project)));assertTrue(before.needsConfirmation())
    }
}
