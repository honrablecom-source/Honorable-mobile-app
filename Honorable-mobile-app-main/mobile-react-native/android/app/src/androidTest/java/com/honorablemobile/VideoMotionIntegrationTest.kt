package com.honorablemobile

import android.graphics.Matrix
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class VideoMotionIntegrationTest{
 @Test fun timeVaryingEffectIsNotOptimizedAwayAtIdentityStart(){
  val clip=VideoSegment(0,10_000,motion=VideoMotion(true,endZoom=3f,anchorEndMs=10_000,ease=MotionEase.LINEAR))
  val effect=VideoMotionEffect(listOf(clip));assertFalse(effect.isNoOp(1920,1080));assertTrue(effect.getMatrix(0).isIdentity)
  val values=FloatArray(9);effect.getMatrix(5_000_000).getValues(values);assertEquals(2f,values[Matrix.MSCALE_X],0f);assertEquals(2f,values[Matrix.MSCALE_Y],0f)
 }
 @Test fun sharedCompositionKeepsMotionBeforeFadeAndCaptionsAndOutOfAudio(){
  val uri=Uri.parse("content://synthetic/video");val clip=VideoSegment(0,10_000,motion=VideoMotion(true,endZoom=2f,anchorEndMs=10_000),fadeEdges=true,captions=listOf(VideoCaption(0,1000,"Caption")))
  val visual=VideoCompositionExporter.composition(uri,listOf(clip)).effects.videoEffects
  assertTrue(visual.first() is VideoMotionEffect);assertTrue(visual[1] is VideoTimelineFade);assertEquals(3,visual.size)
  val audio=VideoCompositionExporter.audioComposition(mapOf("primary" to uri),listOf(clip),mapOf("primary" to 10_000L),null,setOf("primary"))
  assertTrue(audio.effects.videoEffects.isEmpty())
 }
}
