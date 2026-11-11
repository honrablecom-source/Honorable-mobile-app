package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.math.abs
class PhotoExposureBlendTest {
 private fun blend(a:Int,b:Int)=PhotoExposureBlend.create(PhotoPixelDifference.Size(1,1),intArrayOf(a),intArrayOf(b)).pixels[0]
 private fun gray(v:Int)=0xff000000.toInt() or (v shl 16) or (v shl 8) or v
 @Test fun identicalVisiblePixelsKeepExactAlphaAndColor(){val rng=Random(140);repeat(1000){val p=(rng.nextInt(1,256) shl 24) or rng.nextInt(0x1000000);assertEquals(p,blend(p,p))}}
 @Test fun wellExposedMidtoneDominatesClippedDarkAndLightBrackets(){assertEquals(gray(128),blend(gray(0),gray(128)));assertEquals(gray(128),blend(gray(255),gray(128)));assertEquals(gray(128),blend(gray(0),gray(255)))}
 @Test fun hiddenTransparentColorsDoNotDarkenObservedPixelAndEmptyOutputIsCanonical(){for(p in listOf(0x01223344,0x80335577.toInt(),0xffabcdef.toInt())){assertEquals(p,blend(0x00ff00ff,p));assertEquals(p,blend(p,0x00ff00ff))};assertEquals(0,blend(0x00112233,0x00ffffff))}
 @Test fun independentDoubleReferenceAndSwapSymmetryMatchRandomAlphaFrames(){val rng=Random(141);repeat(2000){val x=rng.nextInt();val y=rng.nextInt();fun weight(p:Int):Double{val l=((p ushr 16 and 255)*77+(p ushr 8 and 255)*150+(p and 255)*29+128)/256;val c=256-abs(2*l-255);return c.toDouble()*c*(p ushr 24)}
  val wx=weight(x);val wy=weight(y);val out=blend(x,y);assertEquals(out,blend(y,x));if(wx+wy==0.0)assertEquals(0,out)else{val ax=x ushr 24;val ay=y ushr 24;val expectedA=kotlin.math.floor((wx*ax+wy*ay)/(wx+wy)+.5).toInt();assertEquals(expectedA,out ushr 24);for(shift in listOf(0,8,16)){val expected=kotlin.math.floor((wx*ax*(x ushr shift and 255)+wy*ay*(y ushr shift and 255))/(wx*ax+wy*ay)+.5).toInt();assertEquals(expected,out ushr shift and 255)}}}}
 @Test fun inputsNeverMutateAndChangedCountUsesStoredOutputValues(){val a=intArrayOf(gray(0),gray(128),0x00112233);val b=intArrayOf(gray(128),gray(128),0);val beforeA=a.copyOf();val beforeB=b.copyOf();val r=PhotoExposureBlend.create(PhotoPixelDifference.Size(3,1),a,b);assertArrayEquals(intArrayOf(gray(128),gray(128),0),r.pixels);assertEquals(2,r.changedPixels);assertArrayEquals(beforeA,a);assertArrayEquals(beforeB,b)}
 @Test fun outputChannelsAndAlphaStayInsideObservedSourceBounds(){val rng=Random(142);repeat(1000){val a=rng.nextInt();val b=rng.nextInt();val p=blend(a,b);if(a ushr 24!=0&&b ushr 24!=0)for(shift in listOf(0,8,16,24)){val x=a ushr shift and 255;val y=b ushr shift and 255;assertTrue((p ushr shift and 255) in minOf(x,y)..maxOf(x,y))}}}
 @Test fun largestGridUsesSafeLongWeightedProducts(){val a=IntArray(1024*1024){0x80abcdef.toInt()};val b=IntArray(a.size){0xffffffff.toInt()};val r=PhotoExposureBlend.create(PhotoPixelDifference.Size(1024,1024),a,b);assertEquals(a.size,r.pixels.size);assertTrue(r.pixels.all{it==r.pixels[0]});assertTrue(r.pixels[0] ushr 24 in 128..255)}
 @Test fun invalidBindingsAndCancellationNeverReturnPartialOutput(){assertThrows(IllegalArgumentException::class.java){PhotoExposureBlend.create(PhotoPixelDifference.Size(2,1),intArrayOf(1),intArrayOf(1,2))};var calls=0;val a=IntArray(4096);assertThrows(IllegalStateException::class.java){PhotoExposureBlend.create(PhotoPixelDifference.Size(1024,4),a,a){if(++calls==4)error("cancel")}};assertEquals(4,calls);assertArrayEquals(IntArray(4096),a)}
}
