package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test

class VideoFilmstripScaleTest {
    private val clips=listOf(VideoSegment(2000,6000,speed=2f),VideoSegment(1000,4000))
    @Test fun widthsRepresentOutputDurationAfterSpeed(){
        val s=VideoFilmstripScale(clips,400f)
        assertEquals(5_000_000,s.durationUs);assertEquals(160f,s.x(s.start(1)),.01f);assertEquals(400f,s.width,.01f)
        assertEquals(0,s.clipAt(159f));assertEquals(1,s.clipAt(160f))
    }
    @Test fun seekingUsesExistingSourceAndSpeedMapping(){
        val s=VideoFilmstripScale(clips,400f);val p=VideoTimeline.locate(clips,s.time(80f))
        assertEquals(0,p.clipIndex);assertEquals(4000,p.sourceTimeMs)
        assertEquals(1_000_000,VideoFilmstripScale.outputTime(clips,0,4000));assertEquals(3_000_000,VideoFilmstripScale.outputTime(clips,1,2000))
    }
    @Test fun clampedEndpointsNeverSeekPastTheLastSourceFrame(){
        val s=VideoFilmstripScale(clips,400f)
        assertEquals(0,s.time(-100f));assertEquals(s.durationUs-1,s.time(10000f));assertEquals(0f,s.x(-10),0f)
        assertEquals(3999,VideoTimeline.locate(clips,s.time(10000f)).sourceTimeMs)
    }
    @Test fun longTimelineAndDensityHaveBoundedCoordinates(){
        val s=VideoFilmstripScale(listOf(VideoSegment(0,14_400_000)),1170f,3f)
        assertEquals(48000f,s.width,0f);assertEquals(1170f,VideoFilmstripScale(clips,1170f,3f).width,0f)
    }
    @Test fun longProjectKeepsEveryClipBoundarySelectable(){
        val clips=List(1000){VideoSegment(0,1000)}
        val s=VideoFilmstripScale(clips,390f)
        assertEquals(999_000_000L,s.start(999));assertEquals(1_000_000_000L,s.start(1000))
        assertEquals(999,s.clipAt(s.x(s.start(999))))
        assertEquals(999,s.clipAt(s.width));assertEquals(16000f,s.width,0f)
    }
    @Test fun invalidCoordinatesAreRejected(){
        for(make in listOf<()->Unit>({VideoFilmstripScale(emptyList(),390f)},{VideoFilmstripScale(clips,Float.NaN)},{VideoFilmstripScale(clips,390f,0f)},{VideoFilmstripScale(clips,390f).time(Float.NaN)})){
            try{make();fail("Expected invalid coordinate rejection")}catch(_:IllegalArgumentException){}
        }
    }
}
