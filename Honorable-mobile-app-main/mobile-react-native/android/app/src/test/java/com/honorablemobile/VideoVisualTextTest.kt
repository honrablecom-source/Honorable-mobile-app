package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class VideoVisualTextTest {
    private fun reject(action:()->Unit){try{action();fail("Expected refusal")}catch(_:IllegalArgumentException){}}
    @Test fun sourceBinsCoverFullRangeAndRequestMidpointsWithoutPlaybackRetiming(){
        val bins=VideoVisualText.windows(1234,2445)
        assertEquals(listOf(VideoVisualText.Window(1234,1734),VideoVisualText.Window(1734,2234),VideoVisualText.Window(2234,2445)),bins)
        assertEquals(listOf(1484L,1984L,2339L),bins.map{it.requestedMs})
    }
    @Test fun identicalNeighborOcrMergesButBlankAndChangedTextKeepBoundaries(){
        assertEquals(listOf(VideoCaption(1000,2000,"Hello"),VideoCaption(2500,3000,"Hello"),VideoCaption(3000,3500,"World")),VideoVisualText.captions(1000,3500,listOf(" Hello ","Hello","","Hello","World")))
    }
    @Test fun lineBreakCleanupDoesNotSilentlyDropWordsOrDenseText(){
        assertEquals("First\nSecond",VideoVisualText.captions(0,500,listOf(" First\r\n\r\nSecond ")).single().text)
        reject{VideoVisualText.captions(0,500,listOf("A\nB\nC\nD"))};reject{VideoVisualText.captions(0,500,listOf("x".repeat(241)))}
        reject{VideoVisualText.captions(0,500,listOf("Bad\u0000text"))}
    }
    @Test fun noRecognitionMeansNoInventedCuesAndMissingFramesRefuseAll(){
        assertTrue(VideoVisualText.captions(0,1000,listOf(" ","\n")).isEmpty())
        reject{VideoVisualText.captions(0,1000,listOf("Incomplete"))}
    }
    @Test fun boundsCropAndMaxFrameCountAreFinite(){
        assertEquals(20,VideoVisualText.windows(0,10000).size);reject{VideoVisualText.windows(0,10001)};reject{VideoVisualText.windows(-1,500)};reject{VideoVisualText.windows(0,499)}
        assertEquals(49..82,VideoVisualText.Crop(60,100).pixels(83));assertEquals(0..82,VideoVisualText.Crop(0,100).pixels(83))
        reject{VideoVisualText.Crop(90,95)};reject{VideoVisualText.Crop(-1,100)}
    }
    @Test fun cancellationNeverReturnsPartialCaptionDraft(){
        var calls=0
        try{VideoVisualText.captions(0,1500,listOf("A","B","C")){if(++calls==2)throw CancellationException()};fail()}catch(_:CancellationException){}
    }
}
