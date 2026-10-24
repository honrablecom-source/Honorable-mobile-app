package com.honorablemobile

import android.net.Uri
import androidx.media3.effect.OverlayEffect
import org.junit.Assert.*
import org.junit.Test

class VideoGradeCompositionTest {
    @Test fun colorRunsBeforeTitleAndTimelineFadeInTheSharedComposition(){
        val grade=VideoGrade(.4f,1.2f,.7f)
        val composition=VideoCompositionExporter.composition(Uri.parse("content://synthetic/video"),listOf(VideoSegment(0,1000,title="Title",fadeEdges=true,grade=grade)))
        val effects=composition.sequences.single().editedMediaItems.single().effects.videoEffects
        assertEquals(2,effects.size);assertTrue(effects[0] is VideoColorEffect);assertTrue(effects[1] is OverlayEffect)
        assertArrayEquals(grade.matrix(),(effects[0] as VideoColorEffect).getMatrix(0,false),.00001f)
        assertTrue(composition.effects.videoEffects.single() is VideoTimelineFade)
    }
    @Test fun identityClipsSkipTheColorPassAndAdjacentClipsKeepTheirOwnGrade(){
        val grade=VideoGrade(saturation=0f)
        val composition=VideoCompositionExporter.composition(Uri.parse("content://synthetic/video"),listOf(VideoSegment(0,1000),VideoSegment(1000,2000,grade=grade)))
        val items=composition.sequences.single().editedMediaItems
        assertTrue(items[0].effects.videoEffects.isEmpty())
        assertArrayEquals(grade.matrix(),(items[1].effects.videoEffects.single() as VideoColorEffect).getMatrix(0,false),.00001f)
    }
    @Test fun hdrAndSdrUseTheirOwnMatricesWithoutFrameTimeAllocation(){
        val effect=VideoColorEffect(VideoGrade(saturation=0f))
        assertNotEquals(effect.getMatrix(0,false)[0],effect.getMatrix(0,true)[0])
        assertSame(effect.getMatrix(0,false),effect.getMatrix(999,false))
        assertFalse(effect.isNoOp(1080,1920));assertTrue(VideoColorEffect(VideoGrade()).isNoOp(1080,1920))
    }
}
