package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoTrimRangeTest {
    private val clip=VideoSegment(100,5000)
    @Test fun exactMillisecondsAreParsedWithoutFloatingPointRounding() {
        assertEquals(VideoTrimRange(1234,4987),VideoTrimRange.parse("1.234","4.987",clip,5000))
        assertEquals(VideoTrimRange(100,1200),VideoTrimRange.parse(" 0.1 ","1.20",clip,5000))
    }
    @Test fun formattingKeepsThreeDecimalPlaces() {
        assertEquals("0.001",VideoTrimRange.format(1));assertEquals("123.040",VideoTrimRange.format(123040))
        assertEquals("2147483.647",VideoTrimRange.format(Int.MAX_VALUE.toLong()))
    }
    @Test fun invalidPrecisionNotationAndNegativeValuesAreRejected() {
        for(value in listOf("","-1","+1","1,25","1e2","NaN","Infinity","1.",".5","01","1.0001","10000000","9\n1"))assertTrue(value,runCatching{VideoTrimRange.parse(value,"4",clip,5000)}.isFailure)
    }
    @Test fun clipSourceAndIncreasingBoundsAreRequired() {
        for(range in listOf("0" to "1","1" to "5.001","2" to "2","3" to "2"))assertTrue(runCatching{VideoTrimRange.parse(range.first,range.second,clip,5000)}.isFailure)
        assertTrue(runCatching{VideoTrimRange.parse("1","4",clip,4999)}.isFailure)
    }
    @Test fun preciseTrimUsesExistingHistoryAndRetainsIntersectingCaptions() {
        val project=VideoEditProject(5000);project.setCaptions(0,listOf(VideoCaption(0,200,"Early"),VideoCaption(1000,3000,"Keep")))
        val before=project.segments;val range=VideoTrimRange.parse("1.234","2.987",before[0],5000)
        project.trim(0,range.startMs,range.endMs)
        assertEquals(1234L,project.segments[0].startMs);assertEquals(2987L,project.segments[0].endMs)
        assertEquals(listOf(VideoCaption(1000,3000,"Keep")),project.segments[0].captions)
        assertTrue(project.undo());assertEquals(before,project.segments)
    }
    @Test fun maximumSupportedSourceTimeRemainsExact() {
        val max=Int.MAX_VALUE.toLong();val source=VideoSegment(0,max)
        assertEquals(VideoTrimRange(max-1,max),VideoTrimRange.parse("2147483.646","2147483.647",source,max))
        assertTrue(runCatching{VideoTrimRange.parse("0","9999999.999",source,max)}.isFailure)
    }
}
