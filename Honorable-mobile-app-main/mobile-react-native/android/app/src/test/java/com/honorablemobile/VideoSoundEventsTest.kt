package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoSoundEventsTest {
    private val labels=List(521){"Sound $it"}
    private fun fails(action:()->Unit){try{action();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    private fun rows(samples:Int)=Array(VideoSoundEvents.frames(samples)){FloatArray(521).also{it[10]=.6f;it[20]=.2f;it[30]=.2f}}
    @Test fun frameCountsIncludeActualStftTailAndEveryApproximateInterval(){
        for((samples,frames)in listOf(8000 to 1,15360 to 1,15600 to 1,15601 to 2,23040 to 2,23280 to 2,23281 to 3,48000 to 6,320000 to 41)){
            assertEquals(frames,VideoSoundEvents.frames(samples))
            val result=VideoSoundEvents.propose(rows(samples),samples,1234,labels)
            assertEquals(frames,result.size);assertEquals(1234L,result.first().startMs);assertEquals(1234+samples*1000L/16000,result.last().endMs)
            assertTrue(result.zipWithNext().all{(a,b)->a.endMs==b.startMs})
        }
    }
    @Test fun allTopThreeLabelsAndScoresKeptWithStableTieOrderAndNoImplicitThreshold(){
        val input=rows(48000);val output=VideoSoundEvents.propose(input,48000,0,labels)
        assertEquals(listOf(10,20,30),output.first().choices.map{it.index});assertEquals(.2f,output.first().choices[1].score)
        input[0][10]=0f;assertEquals(.6f,output.first().choices.first().score)
        val zero=VideoSoundEvents.propose(Array(1){FloatArray(521)},8000,0,labels)
        assertEquals(listOf(0,1,2),zero.single().choices.map{it.index})
    }
    @Test fun explicitSelectionMergesOnlyAdjacentSameClassAndNeverFillsUncheckedGaps(){
        val windows=VideoSoundEvents.propose(rows(48000),48000,1000,labels)
        val cues=VideoSoundEvents.selected(windows,mapOf(0 to 10,1 to 10,3 to 10,4 to 20,5 to 20))
        assertEquals(listOf(VideoCaption(1000,1960,"[Sound 10]"),VideoCaption(2440,2920,"[Sound 10]"),VideoCaption(2920,4000,"[Sound 20]")),cues)
        fails{VideoSoundEvents.selected(windows,emptyMap())};fails{VideoSoundEvents.selected(windows,mapOf(6 to 10))};fails{VideoSoundEvents.selected(windows,mapOf(0 to 11))}
    }
    @Test fun invalidScoresShapesBoundsAndCancellationRefuseWholeProposal(){
        fails{VideoSoundEvents.frames(7999)};fails{VideoSoundEvents.frames(320001)}
        fails{VideoSoundEvents.propose(rows(48000),48000,Long.MAX_VALUE,labels)}
        fails{VideoSoundEvents.propose(rows(48000),48000,Int.MAX_VALUE.toLong(),labels)}
        fails{VideoSoundEvents.propose(arrayOf(FloatArray(520)),8000,0,labels)}
        fails{VideoSoundEvents.propose(rows(48000).drop(1).toTypedArray(),48000,0,labels)}
        for(bad in listOf(Float.NaN,Float.POSITIVE_INFINITY,-.1f,1.01f))fails{val r=rows(8000);r[0][100]=bad;VideoSoundEvents.propose(r,8000,0,labels)}
        try{VideoSoundEvents.propose(rows(48000),48000,0,labels){throw java.util.concurrent.CancellationException()};fail()}catch(_:java.util.concurrent.CancellationException){}
    }
}
