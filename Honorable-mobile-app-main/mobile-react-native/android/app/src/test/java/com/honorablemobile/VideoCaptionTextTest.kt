package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoCaptionTextTest {
    @Test fun trimmedSpedAndAdjacentCuesUseCanonicalPlayback() {
        val cue=VideoCaption(1000,4000,"Hello")
        val clips=listOf(VideoSegment(2000,3000,speed=2f,captions=listOf(cue)),VideoSegment(3000,5000,speed=2f,captions=listOf(cue)))
        val result=VideoCaptionText.create(clips,true)
        assertEquals(1,result.cues)
        assertEquals("[00:00:00,000 --> 00:00:01,000]\nHello\n",result.text)
    }
    @Test fun repeatedClipsWithGapsRemainAndMetadataIsNotAppended() {
        val clip=VideoSegment(0,2000,captions=listOf(VideoCaption(500,1000,"世界 🌍\nLiteral [1]")),captionSourceUri="content://private/subtitle")
        val result=VideoCaptionText.create(listOf(clip,clip))
        assertEquals(2,result.cues)
        assertEquals("世界 🌍\nLiteral [1]\n\n世界 🌍\nLiteral [1]\n",result.text)
        assertFalse(result.text.contains("content://"))
    }
    @Test fun timedOutputRoundsSubmillisecondIntervalsOutward() {
        val clips=listOf(VideoSegment(0,1,speed=2f),VideoSegment(0,3,speed=2f,captions=listOf(VideoCaption(1,2,"short"))))
        assertEquals("[00:00:00,001 --> 00:00:00,002]\nshort\n",VideoCaptionText.create(clips,true).text)
        assertEquals("[00:00:00,000 --> 00:00:00,001]\nx\n",VideoCaptionText.create(listOf(VideoSegment(0,1,speed=2f,captions=listOf(VideoCaption(0,1,"x")))),true).text)
    }
    @Test fun absentHiddenAndOversizedProjectsAreRejected() {
        assertTrue(runCatching{VideoCaptionText.create(emptyList())}.isFailure)
        assertTrue(runCatching{VideoCaptionText.create(listOf(VideoSegment(0,1000)))}.isFailure)
        assertTrue(runCatching{VideoCaptionText.create(listOf(VideoSegment(1000,2000,captions=listOf(VideoCaption(0,500,"hidden")))))}.isFailure)
        val clip=VideoSegment(0,1000,captions=listOf(VideoCaption(0,500,"x")))
        assertTrue(runCatching{VideoCaptionText.create(List(1001){clip})}.isFailure)
        val large=clip.copy(captions=listOf(VideoCaption(0,500,"字".repeat(120))))
        assertTrue(runCatching{VideoCaptionText.create(List(200){large})}.isFailure)
    }
    @Test fun maximumCueCountRemainsBoundedAndComplete() {
        val clip=VideoSegment(0,2000,captions=listOf(VideoCaption(0,500,"x")))
        val result=VideoCaptionText.create(List(1000){clip},true)
        assertEquals(1000,result.cues)
        assertTrue(result.text.toByteArray().size<=VideoCaptionText.MAX_BYTES)
        assertTrue(result.preview().contains("Preview limited"))
        assertFalse(result.text.contains("Preview limited"))
    }
    @Test fun previewNeverSplitsUnicodeAndPreparationLeavesHistoryUntouched() {
        val p=VideoEditProject(2000)
        p.setCaptions(0,listOf(VideoCaption(0,1000,"🌍🌍🌍")))
        val before=p.segments.toList();val undo=p.canUndo;val redo=p.canRedo
        val result=VideoCaptionText.create(p.segments)
        assertTrue(result.preview(1).startsWith("🌍…"))
        assertEquals("🌍🌍🌍\n",result.text)
        assertEquals(before,p.segments);assertEquals(undo,p.canUndo);assertEquals(redo,p.canRedo)
        assertTrue(runCatching{result.preview(0)}.isFailure)
    }
}
