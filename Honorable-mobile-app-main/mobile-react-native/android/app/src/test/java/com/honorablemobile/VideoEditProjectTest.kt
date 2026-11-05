package com.honorablemobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEditProjectTest {
    @Test fun splitReorderDeleteAndHistoryPreserveSourceRanges() {
        val project = VideoEditProject(10_000)
        project.split(0, 4_000)
        assertEquals(listOf(VideoSegment(0, 4_000), VideoSegment(4_000, 10_000)), project.segments)
        project.move(1, 0)
        assertEquals(VideoSegment(4_000, 10_000), project.segments.first())
        project.delete(1)
        assertEquals(listOf(VideoSegment(4_000, 10_000)), project.segments)
        assertTrue(project.undo())
        assertEquals(2, project.segments.size)
        assertTrue(project.redo())
        assertEquals(1, project.segments.size)
        project.reset()
        assertEquals(listOf(VideoSegment(0, 10_000)), project.segments)
        assertFalse(project.canRedo)
    }

    @Test fun speedAndVolumeSurviveSplitTrimAndRestore() {
        val project = VideoEditProject(10_000)
        project.setSpeed(0, 2f)
        project.setVolume(0, 0f)
        project.split(0, 4_000)
        project.trim(1, 5_000, 9_000)
        assertEquals(4_000L, project.totalDurationMs)
        assertEquals(listOf(0f, 0f), project.segments.map { it.volume })
        assertEquals(listOf(2f, 2f), project.segments.map { it.speed })
        val restored = VideoEditProject(10_000)
        restored.restore(project.segments)
        assertEquals(project.segments, restored.segments)
    }

    @Test fun duplicatedClipKeepsItsEditsAndIsUndoable() {
        val project = VideoEditProject(10_000)
        project.trim(0, 2_000, 8_000)
        project.setSpeed(0, 2f)
        project.setVolume(0, .5f)
        project.duplicate(0)
        assertEquals(listOf(VideoSegment(2_000, 8_000, 2f, .5f), VideoSegment(2_000, 8_000, 2f, .5f)), project.segments)
        assertEquals(6_000L, project.totalDurationMs)
        project.setVolume(1, 0f)
        assertEquals(.5f, project.segments[0].volume)
        assertTrue(project.undo())
        assertTrue(project.undo())
        assertEquals(1, project.segments.size)
        assertTrue(project.redo())
        assertEquals(2, project.segments.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoreRejectsRangesBeyondSource() {
        VideoEditProject(1_000).restore(listOf(VideoSegment(0, 2_000)))
    }
    @Test fun manySpeedAdjustedClipsKeepFractionalRemainders() {
        val project=VideoEditProject(1000)
        project.restore(List(1000){VideoSegment(0,1000,speed=1.5f)})
        assertEquals(666_666L,project.totalDurationMs)
    }
    @Test fun overflowingTimelineRestoreIsRejectedBeforeSourceCatalogOrHistoryChanges() {
        val project=VideoEditProject(1000)
        val before=project.segments
        val huge=VideoSegment(0,Long.MAX_VALUE,sourceId="huge")
        assertTrue(runCatching{project.restore(listOf(huge,huge),mapOf("huge" to Long.MAX_VALUE))}.isFailure)
        assertEquals(before,project.segments);assertFalse(project.canUndo)
        project.appendSource("huge",2000)
        assertEquals(2000L,project.segments.last().endMs)
    }

}
