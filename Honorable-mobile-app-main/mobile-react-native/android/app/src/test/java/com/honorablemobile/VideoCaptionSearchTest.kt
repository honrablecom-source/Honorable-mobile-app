package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoCaptionSearchTest {
    @Test fun literalCaseAndExplicitLineBreakMatchingKeepOriginalText(){
        val clip=VideoSegment(0,4000,captions=listOf(VideoCaption(0,1000,"École [$]"),VideoCaption(1000,2000,"two\nwords"),VideoCaption(2000,3000,"ECOLE")))
        assertEquals("École [$]",VideoCaptionSearch.find(listOf(clip),"[$]").single().text)
        assertEquals(1,VideoCaptionSearch.find(listOf(clip),"école").size)
        assertTrue(VideoCaptionSearch.find(listOf(clip),"école",true).isEmpty())
        assertEquals("two\nwords",VideoCaptionSearch.find(listOf(clip)," two words ").single().text)
        assertTrue(VideoCaptionSearch.find(listOf(clip),".*").isEmpty())
    }
    @Test fun visibleMatchesUseOriginalCueIndexTrimmedSourceAndEditedOutputTime(){
        val first=VideoSegment(1000,3000,speed=2f,captions=listOf(VideoCaption(0,500,"needle hidden"),VideoCaption(500,1500,"needle first")))
        val second=VideoSegment(5000,7000,speed=.5f,sourceId="second",captions=listOf(VideoCaption(5500,6000,"needle second")))
        val hits=VideoCaptionSearch.find(listOf(first,second),"needle")
        assertEquals(2,hits.size);assertEquals(1,hits[0].cueIndex);assertEquals(1000L,hits[0].sourceMs);assertEquals(0L,hits[0].outputUs)
        assertEquals(1,hits[1].clipIndex);assertEquals("second",hits[1].sourceId);assertEquals(5500L,hits[1].sourceMs);assertEquals(2_000_000L,hits[1].outputUs)
        assertTrue(hits[1].label().contains("Clip 2 · 00:00:02,000 playback"))
    }
    @Test fun repeatedClipsRemainSeparateNavigationTargetsAndNonVisibleCuesAreExcluded(){
        val part=VideoSegment(0,1000,captions=listOf(VideoCaption(0,500,"needle"),VideoCaption(1000,2000,"needle outside")))
        val hits=VideoCaptionSearch.find(listOf(part,part),"needle")
        assertEquals(listOf(0,1),hits.map{it.clipIndex});assertEquals(listOf(0L,1_000_000L),hits.map{it.outputUs})
        assertEquals(listOf(0,0),hits.map{it.cueIndex})
    }
    @Test fun boundedQueriesRejectControlsMalformedUnicodeAndUnboundedProjects(){
        val clip=VideoSegment(0,1000)
        for(query in listOf("  ","x".repeat(81),"a\nb","bad\u0000text","\uD800"))assertTrue(runCatching{VideoCaptionSearch.find(listOf(clip),query)}.isFailure)
        assertTrue(runCatching{VideoCaptionSearch.find(emptyList(),"x")}.isFailure)
        assertTrue(runCatching{VideoCaptionSearch.find(List(1001){clip},"x")}.isFailure)
        assertTrue(VideoCaptionSearch.find(listOf(clip),"nothing").isEmpty())
    }
    @Test fun searchIsReadOnlyAndFindingsCannotMutateProjectOrHistory(){
        val project=VideoEditProject(3000);project.setCaptions(0,listOf(VideoCaption(0,1000,"find this")))
        val before=project.segments.toList();val canUndo=project.canUndo
        val draft=VideoDraft(3000,before,0,0,0,3000);val bytes=VideoDraftCodec.encode(draft)
        val hits=VideoCaptionSearch.find(project.segments,"find")
        assertTrue(runCatching{(hits as MutableList).clear()}.isFailure)
        assertEquals(before,project.segments);assertEquals(canUndo,project.canUndo)
        assertArrayEquals(bytes,VideoDraftCodec.encode(VideoDraft(3000,project.segments,0,0,0,3000)))
        assertTrue(project.undo());assertTrue(project.segments.single().captions.isEmpty())
    }
    @Test fun resultsAndLabelsHandleMaximumCueCountAndUnicodeWithoutSplittingSurrogates(){
        val part=VideoSegment(0,1000,captions=List(200){VideoCaption(it*5L,it*5L+5,"needle $it")})
        assertEquals(1000,VideoCaptionSearch.find(List(5){part},"needle").size)
        assertTrue(runCatching{VideoCaptionSearch.find(List(6){part},"needle")}.isFailure)
        val emoji=VideoSegment(0,1000,captions=listOf(VideoCaption(0,1000,"😀".repeat(100))))
        val hit=VideoCaptionSearch.find(listOf(emoji),"😀".repeat(80)).single()
        val label=hit.label();assertEquals(label,label.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        assertTrue(label.endsWith("😀…"));assertEquals("😀".repeat(100),hit.text)
    }
    @Test fun caseMatchingIsIndependentOfDeviceLocale(){
        val old=java.util.Locale.getDefault()
        try{java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
            val clip=VideoSegment(0,1000,captions=listOf(VideoCaption(0,1000,"TITLE")))
            assertEquals(1,VideoCaptionSearch.find(listOf(clip),"title").size)
            assertTrue(VideoCaptionSearch.find(listOf(clip),"title",true).isEmpty())
        }finally{java.util.Locale.setDefault(old)}
    }
}
