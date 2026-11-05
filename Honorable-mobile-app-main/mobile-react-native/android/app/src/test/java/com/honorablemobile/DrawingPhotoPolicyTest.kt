package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class DrawingPhotoPolicyTest {
    @Test fun onlyExplicitContentSelectionsAndBoundedRequestIdsAreAccepted(){
        assertTrue(DrawingPhotoPolicy.acceptsSource("content"))
        for(value in listOf(null,"file","https","http","data","javascript"))assertFalse(DrawingPhotoPolicy.acceptsSource(value))
        assertTrue(DrawingPhotoPolicy.acceptsRequest(1));assertTrue(DrawingPhotoPolicy.acceptsRequest(1_000_000_000))
        assertFalse(DrawingPhotoPolicy.acceptsRequest(0));assertFalse(DrawingPhotoPolicy.acceptsRequest(-1));assertFalse(DrawingPhotoPolicy.acceptsRequest(Int.MAX_VALUE))
    }
    @Test fun copiesFitWithoutUpscalingOrAspectDistortionAndShrinkMonotonically(){
        assertEquals(1024 to 768,DrawingPhotoPolicy.fitted(4032,3024,1024))
        assertEquals(768 to 1024,DrawingPhotoPolicy.fitted(3024,4032,1024))
        assertEquals(40 to 20,DrawingPhotoPolicy.fitted(40,20,1024))
        assertEquals(1 to 1024,DrawingPhotoPolicy.fitted(1,Int.MAX_VALUE,1024))
        var side=1024;var iterations=0
        while(true){val smaller=DrawingPhotoPolicy.smaller(side)?:break;assertTrue(smaller<side);side=smaller;iterations++}
        assertEquals(64,side);assertTrue(iterations<20)
        assertTrue(runCatching{DrawingPhotoPolicy.fitted(0,10,1024)}.isFailure)
        assertTrue(runCatching{DrawingPhotoPolicy.fitted(10,10,1025)}.isFailure)
    }
}
