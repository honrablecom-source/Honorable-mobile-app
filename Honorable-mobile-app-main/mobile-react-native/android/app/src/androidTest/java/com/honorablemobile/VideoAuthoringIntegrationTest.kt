package com.honorablemobile

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

class VideoAuthoringIntegrationTest {
    @Test fun steppedRampAndSourceTimecodesReachTheSharedComposition() {
        val clip=VideoSegment(0,8000)
        val ramp=VideoAuthoringTools.speedRamp(listOf(clip),0,true)
        val coded=VideoAuthoringTools.sourceTimecodes(ramp,0,1000)
        val composition=VideoCompositionExporter.composition(Uri.parse("content://synthetic/video"),coded)
        assertEquals(coded.size,composition.sequences.single().editedMediaItems.size)
        assertTrue(composition.effects.videoEffects.any{it is androidx.media3.effect.OverlayEffect})
        assertTrue(VideoCaptions.resolve(coded).isNotEmpty())
    }
    @Test fun captionDuckingKeepsAllSourceRangesInCompositionOrder() {
        val clip=VideoSegment(0,3000,captions=listOf(VideoCaption(1000,2000,"Words")))
        val pieces=VideoAuthoringTools.captionDucking(listOf(clip),0,.25f)
        val items=VideoCompositionExporter.composition(Uri.parse("content://synthetic/video"),pieces).sequences.single().editedMediaItems
        assertEquals(3,items.size)
        assertEquals(listOf(0L,1000L,2000L),items.map{it.mediaItem.clippingConfiguration.startPositionMs})
        assertEquals(listOf(1000L,2000L,3000L),items.map{it.mediaItem.clippingConfiguration.endPositionMs})
    }
}
