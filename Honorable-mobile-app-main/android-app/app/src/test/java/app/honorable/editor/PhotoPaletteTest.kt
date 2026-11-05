package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoPaletteTest {
 private fun measure(vararg pixels:Int)=PhotoPaletteAccumulator().apply{add(pixels)}.snapshot()
 @Test fun emptyAndHiddenRgbProduceNoColors(){
  val empty=measure();assertTrue(empty.swatches.isEmpty());assertEquals("",empty.hexText());assertEquals(0L,empty.totalAlphaWeight)
  val hidden=measure(0x00ffffff,0x00ff0000);assertTrue(hidden.swatches.isEmpty());assertEquals(2L,hidden.transparent);assertEquals(0L,hidden.visible)
 }
 @Test fun exactSolidColorRetainsRgbAndAlphaWeight(){
  val result=measure(0xff123456.toInt(),0xff123456.toInt())
  assertEquals(listOf(PhotoPaletteSwatch(0xff123456.toInt(),510L)),result.swatches)
  assertEquals("#123456",result.hexText());assertEquals(2L,result.visible);assertEquals(510L,result.totalAlphaWeight)
 }
 @Test fun sourceOpacityWeightsRankingAndDenominator(){
  val result=measure(0x01ff0000,0xff0000ff.toInt(),0x0000ff00)
  assertEquals(listOf("#0000FF","#FF0000"),result.swatches.map{it.hex()})
  assertEquals(listOf(255L,1L),result.swatches.map{it.alphaWeight});assertEquals(256L,result.totalAlphaWeight)
  assertEquals(2L,result.visible);assertEquals(1L,result.transparent)
 }
 @Test fun quantizationAveragesWithinBucketUsingAlpha(){
  val result=measure(0x01000000,0x03070707)
  assertEquals(listOf(PhotoPaletteSwatch(0xff050505.toInt(),4L)),result.swatches)
  val separate=measure(0xff070707.toInt(),0xff080808.toInt())
  assertEquals(listOf("#070707","#080808"),separate.swatches.map{it.hex()})
 }
 @Test fun rankingTieBreaksDeterministicallyAndTruncationKeepsTotalCoverage(){
  val pixels=IntArray(12){0xff000000.toInt()or(it*8)}
  val all=measure(*pixels);assertEquals(8,all.swatches.size)
  assertEquals((0..7).map{String.format(java.util.Locale.ROOT,"#0000%02X",it*8)},all.swatches.map{it.hex()})
  assertEquals(12*255L,all.totalAlphaWeight);assertEquals(8*255L,all.swatches.sumOf{it.alphaWeight})
  assertEquals(all,measure(*pixels.reversedArray()))
  assertEquals("#000000",PhotoPaletteAccumulator().apply{add(pixels)}.snapshot(1).hexText())
 }
 @Test fun chunkedRowsMatchOnePassWithoutMutatingPixels(){
  val pixels=intArrayOf(0xffffffff.toInt(),0xff010101.toInt(),0x7f112233,0x00ffffff,0xff445566.toInt());val original=pixels.copyOf()
  val chunks=PhotoPaletteAccumulator().apply{add(pixels,0,2);add(pixels,2,3);add(pixels,pixels.size,0)}.snapshot()
  assertEquals(measure(*pixels),chunks);assertArrayEquals(original,pixels)
 }
 @Test fun snapshotsAreDetachedAndHexExportContainsOnlyVisibleSwatches(){
  val acc=PhotoPaletteAccumulator();acc.add(intArrayOf(0xffff0000.toInt()));val old=acc.snapshot()
  acc.add(intArrayOf(0xff0000ff.toInt(),0x0000ff00));assertEquals("#FF0000",old.hexText())
  assertEquals("#0000FF\n#FF0000",acc.snapshot().hexText());assertEquals(255L,old.totalAlphaWeight)
 }
 @Test fun invalidRangesAndLimitsAreRejected(){
  val acc=PhotoPaletteAccumulator();val input=intArrayOf(0xffffffff.toInt())
  for((offset,count)in listOf(-1 to 1,0 to -1,2 to 0,0 to 2,Int.MAX_VALUE to Int.MAX_VALUE)){
   try{acc.add(input,offset,count);fail("Invalid range accepted")}catch(expected:IllegalArgumentException){}
  }
  for(limit in listOf(0,9,Int.MAX_VALUE))try{acc.snapshot(limit);fail("Invalid limit accepted")}catch(expected:IllegalArgumentException){}
  assertEquals(0L,acc.snapshot().totalAlphaWeight)
 }
}
