package com.honorablemobile

import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class VideoEdlTest {
    private fun cut(from:Long=0,to:Long=1000,source:String="primary",speed:Float=1f)=VideoEdl.Cut(source,from,to,speed)
    private fun events(text:String)=text.lines().filter{it.matches(Regex("^\\d{3} .*"))}.map{it.trim().split(Regex("\\s+"))}
    private fun frame(text:String,fps:Int):Long {
        val parts=text.split(':').map{it.toLong()}
        assertEquals(4,parts.size)
        assertTrue(parts[0] in 0..23&&parts[1] in 0..59&&parts[2] in 0..59&&parts[3] in 0 until fps.toLong())
        return ((parts[0]*60+parts[1])*60+parts[2])*fps+parts[3]
    }
    @Test fun exportsSimpleSourceAndRecordBoundaries(){
        val r=VideoEdl.prepare(listOf(cut(1000,3000)),25)
        assertTrue(r.text.startsWith("TITLE: NOBLE VIDEO CUTS\nFCM: NON-DROP FRAME\n"))
        assertEquals(listOf("001","N001","V","C","00:00:01:00","00:00:03:00","00:00:00:00","00:00:02:00"),events(r.text).single())
        assertEquals(50L,r.frames);assertEquals(0,r.adjustedCuts);assertEquals(1,r.events)
    }
    @Test fun mapsSourcesInFirstUseOrderAndRetainsRepeatedCuts(){
        val r=VideoEdl.prepare(listOf(cut(source="second"),cut(source="primary"),cut(source="second")),30)
        assertEquals(mapOf("second" to "N001","primary" to "N002"),r.reels)
        assertEquals(listOf("N001","N002","N001"),events(r.text).map{it[1]})
        assertEquals(listOf("001","002","003"),events(r.text).map{it[0]})
        assertEquals("00:00:03:00",events(r.text).last().last())
        assertTrue(r.text.contains("* NOBLE SOURCE N001 = second\n"))
    }
    @Test fun snapsInwardAndKeepsRecordCutsContiguous(){
        val r=VideoEdl.prepare(listOf(cut(1,1099),cut(1201,2239)),25)
        val rows=events(r.text)
        assertEquals(1L,frame(rows[0][4],25));assertEquals(27L,frame(rows[0][5],25))
        assertEquals(31L,frame(rows[1][4],25));assertEquals(55L,frame(rows[1][5],25))
        assertEquals(rows[0][7],rows[1][6]);assertEquals(50L,r.frames);assertEquals(2,r.adjustedCuts)
    }
    @Test fun supportsAllDocumentedIntegerFrameRates(){
        for(fps in listOf(24,25,30)){
            val r=VideoEdl.prepare(listOf(cut(0,1000)),fps)
            assertEquals(fps.toLong(),r.frames);assertEquals(0,r.adjustedCuts)
            assertTrue(r.text.contains("* NOBLE FRAME RATE: $fps\n"))
        }
    }
    @Test fun rejectsUnsupportedRateAndClipCounts(){
        for(rate in listOf(0,23,29,50,60))assertThrows(IllegalArgumentException::class.java){VideoEdl.prepare(listOf(cut()),rate)}
        assertThrows(IllegalArgumentException::class.java){VideoEdl.prepare(emptyList(),25)}
        assertThrows(IllegalArgumentException::class.java){VideoEdl.prepare(List(1000){cut()},25)}
        assertEquals(999,VideoEdl.prepare(List(999){cut()},25).events)
    }
    @Test fun rejectsSpeedChangesInsteadOfExportingWrongTiming(){
        for(speed in listOf(.5f,1.5f,2f,0f,-1f,Float.NaN,Float.POSITIVE_INFINITY)){
            assertThrows(IllegalArgumentException::class.java){VideoEdl.prepare(listOf(cut(speed=speed)),25)}
        }
    }
    @Test fun rejectsNoWholeFrameInsteadOfDroppingClips(){
        for(c in listOf(cut(1,39),cut(0,1),cut(41,79))){
            assertThrows(IllegalArgumentException::class.java){VideoEdl.prepare(listOf(c),25)}
        }
        assertEquals(1L,VideoEdl.prepare(listOf(cut(40,80)),25).frames)
    }
    @Test fun refusesInvalidAndTwentyFourHourSourceBounds(){
        for(c in listOf(cut(-1,1000),cut(100,100),cut(200,100),cut(0,86_400_000),cut(0,Long.MAX_VALUE))){
            assertThrows(IllegalArgumentException::class.java){VideoEdl.prepare(listOf(c),25)}
        }
        assertTrue(VideoEdl.prepare(listOf(cut(86_399_000,86_399_999)),30).text.contains("23:59:59:"))
    }
    @Test fun refusesRecordTimecodeWrap(){
        val c=cut(0,43_200_000)
        assertThrows(IllegalArgumentException::class.java){VideoEdl.prepare(listOf(c,c),25)}
    }
    @Test fun rejectsSourceCommentInjectionAndTooManySources(){
        for(id in listOf("","bad\n001 injected","x y","content://private/media","a".repeat(65),"résumé")){
            assertThrows(IllegalArgumentException::class.java){VideoEdl.prepare(listOf(cut(source=id)),25)}
        }
        assertThrows(IllegalArgumentException::class.java){VideoEdl.prepare(List(33){cut(source="src_$it")},25)}
        assertEquals(32,VideoEdl.prepare(List(32){cut(source="src_$it")},25).reels.size)
    }
    @Test fun doesNotMutateInputsAndOwnsItsMapping(){
        val input=mutableListOf(cut(source="one"),cut(source="two"));val before=input.toList()
        val r=VideoEdl.prepare(input,25);assertEquals(before,input)
        input.clear();assertEquals(2,r.events);assertEquals(2,r.reels.size)
        assertThrows(UnsupportedOperationException::class.java){(r.reels as MutableMap)["three"]="N003"}
    }
    @Test fun usesAsciiTimecodesRegardlessOfDeviceLocale(){
        val old=Locale.getDefault()
        try{val expected=VideoEdl.prepare(listOf(cut()),25).text;Locale.setDefault(Locale.forLanguageTag("ar-EG"));assertEquals(expected,VideoEdl.prepare(listOf(cut()),25).text)}finally{Locale.setDefault(old)}
    }
    @Test fun generatedCutsStayInsideEveryRequestedRangeWithContiguousOutput(){
        val random=Random(109)
        for(fps in listOf(24,25,30))repeat(30){
            val input=List(20){val start=random.nextLong(0,3_000_000);cut(start,start+random.nextLong(200,20_000),"src_${it%4}")}
            val r=VideoEdl.prepare(input,fps);var cursor=0L
            for((i,row) in events(r.text).withIndex()){
                assertEquals(8,row.size)
                val from=frame(row[4],fps);val to=frame(row[5],fps)
                assertTrue(from*1000>=input[i].startMs*fps)
                assertTrue(to*1000<=input[i].endMs*fps)
                assertTrue(to>from);assertEquals(cursor,frame(row[6],fps))
                cursor+=to-from;assertEquals(cursor,frame(row[7],fps))
            }
            assertEquals(cursor,r.frames)
        }
    }
}
