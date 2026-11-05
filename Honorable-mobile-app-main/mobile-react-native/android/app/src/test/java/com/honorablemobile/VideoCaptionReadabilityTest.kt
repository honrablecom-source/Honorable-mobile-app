package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoCaptionReadabilityTest {
    private fun clip(text:String,end:Long=3000)=VideoSegment(0,end,captions=listOf(VideoCaption(0,end,text)))
    @Test fun usesEditedPlaybackAfterTrimsAndSpeedRatherThanSourceCueDuration(){
        val cue=VideoCaption(0,4000,"x".repeat(30))
        val source=VideoSegment(1000,2000,speed=2f,captions=listOf(cue))
        val result=VideoCaptionReadability.inspect(listOf(VideoSegment(0,1000),source))
        val finding=result.findings.single()
        assertEquals(1_000_000L,finding.cue.startUs);assertEquals(1_500_000L,finding.cue.endUs)
        assertEquals(60.0,finding.charactersPerSecond,0.001)
        assertEquals(listOf(VideoCaptionReadability.Hint.FAST,VideoCaptionReadability.Hint.BRIEF),finding.hints)
    }
    @Test fun mergesAdjacentIdenticalCuesExactlyAsRenderingAndIgnoresTrimmedOutCues(){
        val part=clip("Hello",500)
        val hidden=VideoSegment(1000,2000,captions=listOf(VideoCaption(0,500,"hidden")))
        val report=VideoCaptionReadability.inspect(listOf(part,part,hidden))
        assertEquals(1,report.visibleCues);assertTrue(report.findings.isEmpty())
        assertTrue(report.text().contains("No threshold hints found"))
    }
    @Test fun thresholdsAreStrictAndCountUnicodeCodePointsIncludingSpaces(){
        assertTrue(VideoCaptionReadability.inspect(listOf(clip("x".repeat(20),1000))).findings.isEmpty())
        assertEquals(listOf(VideoCaptionReadability.Hint.FAST),VideoCaptionReadability.inspect(listOf(clip("x".repeat(21),1000))).findings.single().hints)
        assertTrue(VideoCaptionReadability.inspect(listOf(clip("😀".repeat(42),3000))).findings.isEmpty())
        assertEquals(listOf(VideoCaptionReadability.Hint.LONG_LINE),VideoCaptionReadability.inspect(listOf(clip("😀".repeat(43),3000))).findings.single().hints)
        assertEquals(21,VideoCaptionReadability.inspect(listOf(clip("x ".repeat(10)+"x",1000))).findings.single().characters)
        assertEquals(listOf(VideoCaptionReadability.Hint.BRIEF),VideoCaptionReadability.inspect(listOf(clip("Hi",999))).findings.single().hints)
    }
    @Test fun countsExplicitLinesWithoutPretendingToMeasureFontWrapping(){
        val report=VideoCaptionReadability.inspect(listOf(clip("one\ntwo\nthree")))
        val finding=report.findings.single()
        assertEquals(11,finding.characters)
        assertEquals(listOf(VideoCaptionReadability.Hint.MANY_LINES),finding.hints)
        assertTrue(report.text().contains("not measured screen wrapping"))
        assertTrue(report.text().contains("not a language or accessibility certification"))
    }
    @Test fun reportsEmptyTimelineCaptionsAndPreservesActualProjectHistoryAndDraft(){
        val project=VideoEditProject(3000)
        assertEquals(0,VideoCaptionReadability.inspect(project.segments).visibleCues)
        assertTrue(VideoCaptionReadability.inspect(project.segments).text().contains("No captions are visible"))
        project.setCaptions(0,listOf(VideoCaption(0,500,"Please review this text")))
        val before=project.segments.toList();val undo=project.canUndo
        val draft=VideoDraft(3000,before,0,0,0,3000)
        val bytes=VideoDraftCodec.encode(draft)
        val report=VideoCaptionReadability.inspect(project.segments)
        assertTrue(report.text().contains("00:00:00,000 → 00:00:00,500"))
        assertEquals(before,project.segments);assertEquals(undo,project.canUndo)
        assertArrayEquals(bytes,VideoDraftCodec.encode(VideoDraft(3000,project.segments,0,0,0,3000)))
        assertTrue(project.undo());assertTrue(project.segments.single().captions.isEmpty())
        assertTrue(runCatching{(report.findings as MutableList).clear()}.isFailure)
        assertTrue(runCatching{(report.findings.single().hints as MutableList).clear()}.isFailure)
    }
    @Test fun reportNumbersIncludeUnflaggedCuesAndUsesStableDecimalFormatting(){
        val clip=VideoSegment(0,4000,captions=listOf(VideoCaption(0,2000,"OK"),VideoCaption(3000,3500,"Review")))
        val old=java.util.Locale.getDefault()
        try{java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val report=VideoCaptionReadability.inspect(listOf(clip))
            assertEquals(2,report.findings.single().number)
            assertTrue(report.text().contains("0.50 s · 12.0 characters/s"))
        }finally{java.util.Locale.setDefault(old)}
    }
    @Test fun acceptsMaximumCueCountAndRejectsExcessBeforeResolving(){
        val part=VideoSegment(0,1000,captions=List(200){VideoCaption(it*5L,it*5L+5,"cue $it")})
        val report=VideoCaptionReadability.inspect(List(5){part})
        assertEquals(1000,report.visibleCues);assertEquals(1000,report.findings.size)
        assertTrue(report.text().contains("Cue 1000"))
        assertTrue(runCatching{VideoCaptionReadability.inspect(List(6){part})}.isFailure)
        assertTrue(runCatching{VideoCaptionReadability.inspect(emptyList())}.isFailure)
    }
}
