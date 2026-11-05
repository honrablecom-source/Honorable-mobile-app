package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoExportSnapshotTest {
    @Test fun pickerPreparationAndCancellationPreserveTimelineAndHistory() {
        val project=VideoEditProject(10000)
        project.split(0,5000)
        val before=project.segments.toList()
        val proposal=VideoExportSnapshot.clips(project.segments,0,1000,4000)
        assertEquals(before,project.segments)
        assertEquals(1000L,proposal[0].startMs);assertEquals(4000L,proposal[0].endMs)
        assertSame(before[1],proposal[1])
        // Discarding the proposal represents Cancel or a failed picker launch.
        assertTrue(project.undo()); assertEquals(listOf(VideoSegment(0,10000)),project.segments)
        assertFalse(project.canUndo)
        assertTrue(project.redo());assertEquals(before,project.segments)
    }
    @Test fun proposalPreservesSettingsAndSourceTimeCaptionOverlap() {
        val cues=listOf(VideoCaption(0,1000,"before"),VideoCaption(1000,2500,"overlap"),VideoCaption(2500,3000,"inside"),VideoCaption(4000,5000,"after"))
        val original=VideoSegment(0,6000,speed=2f,volume=.25f,title="My words",fadeEdges=true,titleStyle=VideoTitleStyle(0),audioFade=true,captions=cues,captionSourceUri="content://captions/original")
        val proposal=VideoExportSnapshot.clips(listOf(original),0,2000,4000).single()
        assertEquals(listOf(cues[1],cues[2]),proposal.captions)
        assertEquals(original,proposal.copy(startMs=0,endMs=6000,captions=cues))
        assertEquals(cues,original.captions)
    }
    @Test fun invalidRangesFailWithoutProjectOrHistoryChanges() {
        val project=VideoEditProject(5000)
        for(range in listOf(-1L to 1000L,1000L to 1000L,2000L to 1000L,0L to 5001L)) {
            try { VideoExportSnapshot.clips(project.segments,0,range.first,range.second);fail("Invalid range accepted") } catch (_:IllegalArgumentException) {}
            assertEquals(listOf(VideoSegment(0,5000)),project.segments);assertFalse(project.canUndo)
        }
        try { VideoExportSnapshot.clips(project.segments,1,0,1000);fail("Missing selection accepted") } catch (_:IllegalArgumentException) {}
    }
}
