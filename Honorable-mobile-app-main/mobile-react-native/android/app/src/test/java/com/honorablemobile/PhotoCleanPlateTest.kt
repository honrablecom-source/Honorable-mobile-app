package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
class PhotoCleanPlateTest {
 private fun run(frames:List<IntArray>)=PhotoCleanPlate.create(PhotoPixelDifference.Size(frames.first().size,1),frames)
 @Test fun identicalFramesPreserveEveryOriginalBitIncludingTransparentColors(){
  val a=intArrayOf(0x00112233,0x80123456.toInt(),-1,0)
  for(n in listOf(3,5)){val r=run(List(n){a.copyOf()});assertArrayEquals(a,r.pixels);assertEquals(0,r.changedPixels)}
 }
 @Test fun movingMinorityDisappearsAndPersistentMajorityRemains(){
  val bg=0xff123456.toInt();val obj=-1
  val f=List(3){i->IntArray(4){j->if(j==i||j==3)obj else bg}}
  val r=run(f);assertArrayEquals(intArrayOf(bg,bg,bg,obj),r.pixels);assertEquals(1,r.changedPixels)
 }
 @Test fun fiveFramesRejectTwoOutliers(){assertArrayEquals(intArrayOf(0xff000000.toInt()),run(listOf(intArrayOf(-1),intArrayOf(-1),intArrayOf(0xff000000.toInt()),intArrayOf(0xff000000.toInt()),intArrayOf(0xff000000.toInt()))).pixels)}
 @Test fun independentReferenceMatchesRandomPixelsAndNeverInventsColors(){
  val random=Random(118)
  for(n in listOf(3,5))repeat(50){
   val frames=List(n){IntArray(31){random.nextInt()}};val before=frames.map{it.copyOf()};val out=run(frames)
   fun channels(p:Int):List<Int>{val a=p ushr 24;return listOf(a,((p ushr 16 and 255)*a+127)/255,((p ushr 8 and 255)*a+127)/255,((p and 255)*a+127)/255)}
   for(i in 0..30){val c=frames.map{channels(it[i])};val median=(0..3).map{k->c.map{it[k]}.sorted()[n/2]};val best=c.indices.minBy{f->(0..3).sumOf{k->kotlin.math.abs(c[f][k]-median[k])}};assertEquals(frames[best][i],out.pixels[i]);assertTrue(frames.any{it[i]==out.pixels[i]})}
   assertEquals(out.pixels.indices.count{out.pixels[it]!=frames[0][it]},out.changedPixels);frames.indices.forEach{assertArrayEquals(before[it],frames[it])}
  }
 }
 @Test fun invisibleColorsTieInSelectionOrder(){assertEquals(0x00112233,run(listOf(intArrayOf(0x00112233),intArrayOf(0x00abcdef),intArrayOf(0))).pixels[0])}
 @Test fun invalidCountsAndMismatchedGridsFail(){for(n in listOf(0,1,2,4,6))assertThrows(IllegalArgumentException::class.java){PhotoCleanPlate.create(PhotoPixelDifference.Size(1,1),List(n){intArrayOf(0)})};assertThrows(IllegalArgumentException::class.java){run(listOf(intArrayOf(1),intArrayOf(),intArrayOf(1)))}}
 @Test fun cancellationIsCheckedDuringAndAfterWork(){var checks=0;assertThrows(IllegalStateException::class.java){PhotoCleanPlate.create(PhotoPixelDifference.Size(1024,4),List(5){IntArray(4096)}){if(++checks==3)error("cancel")}};assertEquals(3,checks);var end=0;assertThrows(IllegalStateException::class.java){PhotoCleanPlate.create(PhotoPixelDifference.Size(1,1),List(3){intArrayOf(0)}){if(++end==2)error("cancel")}}}
}
