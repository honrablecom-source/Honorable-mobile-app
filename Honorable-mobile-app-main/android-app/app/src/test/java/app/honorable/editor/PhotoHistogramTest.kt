package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoHistogramTest {
 private fun measure(vararg pixels:Int)=PhotoHistogramAccumulator().apply{add(pixels)}.snapshot()
 @Test fun emptyAndTransparentDataHaveNoInventedTonalRange(){
  for(value in listOf(measure(),measure(0x00ffffff,0x00000000))){
   assertEquals(0L,value.visible);assertNull(value.minimum);assertNull(value.maximum);assertNull(value.mean)
   assertEquals(0L,value.bins.sum());assertEquals(listOf(0L,0L,0L),value.channelFull)
  }
  assertEquals(2L,measure(0x00ffffff,0x00000000).transparent)
 }
 @Test fun blackWhiteAndPrimariesUseEncodedSrgbWeights(){
  val value=measure(0xff000000.toInt(),0xffffffff.toInt(),0xffff0000.toInt(),0xff00ff00.toInt(),0xff0000ff.toInt())
  assertEquals(5L,value.visible);assertEquals(0,value.minimum);assertEquals(255,value.maximum)
  assertEquals((0+255+54+182+18)/5.0,value.mean!!,0.0)
  for(index in listOf(0,15,3,11,1))assertEquals(1L,value.bins[index])
  assertEquals(listOf(3L,3L,3L),value.channelZero);assertEquals(listOf(2L,2L,2L),value.channelFull)
 }
 @Test fun partialAlphaCountsEquallyWhileHiddenRgbIsExcluded(){
  val value=measure(0x01000000,0x7fffffff,0x00ff00ff)
  assertEquals(2L,value.visible);assertEquals(2L,value.partialAlpha);assertEquals(1L,value.transparent)
  assertEquals(127.5,value.mean!!,0.0);assertEquals(listOf(1L,1L,1L),value.channelFull)
 }
 @Test fun everyNeutralToneLandsInExactBinAndRetainsEndpoints(){
  val input=IntArray(256){0xff000000.toInt()or(it shl 16)or(it shl 8)or it}
  val accumulator=PhotoHistogramAccumulator();accumulator.add(input);val value=accumulator.snapshot()
  assertEquals(List(16){16L},value.bins);assertEquals(127.5,value.mean!!,0.0)
  assertEquals(listOf(1L,1L,1L),value.channelZero);assertEquals(listOf(1L,1L,1L),value.channelFull)
 }
 @Test fun rowsOffsetsAndChunksMatchOnePassWithoutChangingInput(){
  val pixels=intArrayOf(0xffffffff.toInt(),0xff000000.toInt(),0x7f112233,0x00ffffff,0xff445566.toInt());val original=pixels.copyOf()
  val all=PhotoHistogramAccumulator().apply{add(pixels)}.snapshot()
  val chunks=PhotoHistogramAccumulator().apply{add(pixels,0,2);add(pixels,2,3);add(pixels,pixels.size,0)}.snapshot()
  assertEquals(all,chunks);assertArrayEquals(original,pixels)
 }
 @Test fun snapshotsDoNotChangeWhenMoreRowsArrive(){
  val accumulator=PhotoHistogramAccumulator();accumulator.add(intArrayOf(0xff000000.toInt()));val old=accumulator.snapshot()
  accumulator.add(intArrayOf(0xffffffff.toInt()))
  assertEquals(1L,old.visible);assertEquals(0L,old.bins[15]);assertEquals(0,old.maximum)
  assertEquals(2L,accumulator.snapshot().visible)
 }
 @Test fun invalidRangesAreRejectedBeforeCountingAnyPixels(){
  val accumulator=PhotoHistogramAccumulator();val input=intArrayOf(0xff000000.toInt())
  for((offset,count)in listOf(-1 to 1,0 to -1,2 to 0,0 to 2,Int.MAX_VALUE to Int.MAX_VALUE)){
   try{accumulator.add(input,offset,count);fail("Invalid range accepted")}catch(expected:IllegalArgumentException){}
  }
  assertEquals(0L,accumulator.snapshot().visible)
 }
 @Test fun repeatedRowsPreserveLongTotalsAndEndpointDenominators(){
  val accumulator=PhotoHistogramAccumulator();val row=IntArray(1000){if(it%2==0)0xffffffff.toInt()else 0x00ffffff}
  repeat(10000){accumulator.add(row)};val value=accumulator.snapshot()
  assertEquals(5000000L,value.visible);assertEquals(5000000L,value.transparent)
  assertEquals(5000000L,value.bins[15]);assertEquals(listOf(5000000L,5000000L,5000000L),value.channelFull)
 }
}
