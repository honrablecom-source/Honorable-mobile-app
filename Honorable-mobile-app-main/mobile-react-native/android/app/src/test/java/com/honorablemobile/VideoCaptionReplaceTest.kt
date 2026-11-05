package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoCaptionReplaceTest {
    private val first = VideoSegment(0,2000,title="Title",speed=2f,volume=.5f,captionSourceUri="content://subtitle/original",captions=listOf(VideoCaption(0,1000,"Hello world"),VideoCaption(1000,2000,"Hello again")))
    private val second = VideoSegment(0,1000,captions=listOf(VideoCaption(0,1000,"HELLO world")))
    @Test fun selectedClipScopePreservesTimingProvenanceAndAllOtherEdits() {
        val source = listOf(first,second)
        val result = VideoCaptionReplace.propose(source,"Hello","Goodbye",0)
        assertEquals(listOf("Goodbye world","Goodbye again"),result[0].captions.map{it.text})
        assertEquals(first,result[0].copy(captions=first.captions))
        assertSame(second,result[1])
        assertEquals("Hello world",source[0].captions[0].text)
    }
    @Test fun projectScopeAndCaseOptionsReplaceLiteralTextWithoutRegexInterpretation() {
        val result = VideoCaptionReplace.propose(listOf(first,second),"hello","Hi",null,true)
        assertEquals(listOf("Hi world","Hi again","Hi world"),result.flatMap{it.captions}.map{it.text})
        val literal = first.copy(captions=listOf(VideoCaption(0,1000,"A.* A.*")))
        assertEquals("$1 $1",VideoCaptionReplace.propose(listOf(literal),"A.*","$1",null).single().captions.single().text)
    }
    @Test fun emptyReplacementDeletesMatchesButRefusesEmptyCuesAtomically() {
        assertEquals("Hello",VideoCaptionReplace.propose(listOf(first)," world","",null)[0].captions[0].text)
        assertTrue(runCatching{VideoCaptionReplace.propose(listOf(first),"Hello world","",null)}.isFailure)
        assertEquals("Hello world",first.captions[0].text)
    }
    @Test fun noMatchInvalidScopeAndMalformedTextAreRejected() {
        for(find in listOf("","x".repeat(241),"bad\ntext","\uD800")) assertTrue(runCatching{VideoCaptionReplace.propose(listOf(first),find,"ok",null)}.isFailure)
        assertTrue(runCatching{VideoCaptionReplace.propose(listOf(first),"hello","ok",null)}.isFailure)
        assertTrue(runCatching{VideoCaptionReplace.propose(listOf(first),"Hello","Hello",null)}.isFailure)
        assertTrue(runCatching{VideoCaptionReplace.propose(listOf(first),"Hello","ok",1)}.isFailure)
        assertTrue(runCatching{VideoCaptionReplace.propose(listOf(first),"Hello","\uD800",null)}.isFailure)
    }
    @Test fun cueAndProjectTextLimitsFailWithoutPartialChanges() {
        assertTrue(runCatching{VideoCaptionReplace.propose(listOf(first),"Hello","x".repeat(240),null)}.isFailure)
        val cues = (0 until 150).map{VideoCaption(it*1000L,it*1000L+500,"a")}
        val clips = List(2){VideoSegment(0,150000,captions=cues)}
        assertTrue(runCatching{VideoCaptionReplace.propose(clips,"a","x".repeat(240),null)}.isFailure)
        assertEquals("a",clips[0].captions[0].text)
    }
    @Test fun proposalUsesExistingUndoAndMatchingSubtitleExport() {
        val source = listOf(first,second)
        val project = VideoEditProject(2000)
        project.restore(source)
        val result = VideoCaptionReplace.propose(source,"world","planet",null)
        project.applyDraft(result)
        assertTrue(VideoCaptions.exportSrt(project.segments).toString(Charsets.UTF_8).contains("Hello planet"))
        assertTrue(project.undo()); assertEquals(source,project.segments)
        assertTrue(project.redo()); assertEquals(result,project.segments)
    }
}
