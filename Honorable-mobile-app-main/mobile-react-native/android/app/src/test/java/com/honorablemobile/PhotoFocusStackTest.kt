package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.math.abs
class PhotoFocusStackTest {
 private val black=0xff000000.toInt()
 private fun gray(v:Int)=black or (v shl 16) or (v shl 8) or v
 @Test fun identicalBracketsAndFlatTiesPreserveFirstExactColor(){
  val size=PhotoPixelDifference.Size(3,2);val a=intArrayOf(0x00112233,0x80123456.toInt(),-1,0,black,gray(127))
  for(n in 2..5){val r=PhotoFocusStack.create(size,List(n){a.copyOf()});assertArrayEquals(a,r.pixels);assertEquals(0,r.changedPixels);assertEquals(6,r.contributions[0])}
  assertArrayEquals(IntArray(6){gray(12)},PhotoFocusStack.create(size,listOf(IntArray(6){gray(12)},IntArray(6){gray(200)})).pixels)
 }
 @Test fun complementarySharpRegionsAreSelectedFromDifferentFrames(){
  val size=PhotoPixelDifference.Size(20,10)
  val a=IntArray(200){i->val x=i%20;val y=i/20;if(x<8)gray(if((x+y)%2==0)0 else 255)else gray(127)}
  val b=IntArray(200){i->val x=i%20;val y=i/20;if(x>11)gray(if((x+y)%2==0)0 else 255)else gray(127)}
  val r=PhotoFocusStack.create(size,listOf(a,b));for(y in 0..9){for(x in 0..5)assertEquals(a[y*20+x],r.pixels[y*20+x]);for(x in 14..19)assertEquals(b[y*20+x],r.pixels[y*20+x])};assertTrue(r.contributions.all{it>0});assertEquals(200,r.contributions.sum());assertTrue(r.changedPixels>0)
 }
 @Test fun independentBruteNeighborhoodReferenceMatchesRandomAlphaAndBorders(){
  val random=Random(120)
  for(n in 2..5)repeat(12){val w=1+random.nextInt(9);val h=1+random.nextInt(8);val f=List(n){IntArray(w*h){random.nextInt()}};val before=f.map{it.copyOf()};val r=PhotoFocusStack.create(PhotoPixelDifference.Size(w,h),f)
   fun lum(p:Int):Int{val a=p ushr 24;val l=((p ushr 16 and 255)*77+(p ushr 8 and 255)*150+(p and 255)*29+128) ushr 8;return(l*a+127)/255}
   fun energy(frame:IntArray,x:Int,y:Int):Int{fun at(xx:Int,yy:Int)=lum(frame[yy.coerceIn(0,h-1)*w+xx.coerceIn(0,w-1)])
    return abs(4*at(x,y)-at(x-1,y)-at(x+1,y)-at(x,y-1)-at(x,y+1))}
   val contributions=IntArray(n)
   for(y in 0 until h)for(x in 0 until w){val scores=f.map{frame->var total=0;for(yy in (y-2).coerceAtLeast(0)..(y+2).coerceAtMost(h-1))for(xx in (x-2).coerceAtLeast(0)..(x+2).coerceAtMost(w-1))total+=energy(frame,xx,yy);total};val winner=scores.indices.maxBy{scores[it]};contributions[winner]++;assertEquals(f[winner][y*w+x],r.pixels[y*w+x])}
   assertArrayEquals(contributions,r.contributions);assertEquals(r.pixels.indices.count{r.pixels[it]!=f[0][it]},r.changedPixels);f.indices.forEach{assertArrayEquals(before[it],f[it])}
  }
 }
 @Test fun transparentHiddenRgbDoesNotCreateFocusContrast(){val a=intArrayOf(0x00123456,0x00ffffff,0);val b=intArrayOf(0,0,0x00abcdef);assertArrayEquals(a,PhotoFocusStack.create(PhotoPixelDifference.Size(3,1),listOf(a,b)).pixels)}
 @Test fun rejectsCountsAndGridMismatch(){for(n in listOf(0,1,6))assertThrows(IllegalArgumentException::class.java){PhotoFocusStack.create(PhotoPixelDifference.Size(1,1),List(n){intArrayOf(0)})};assertThrows(IllegalArgumentException::class.java){PhotoFocusStack.create(PhotoPixelDifference.Size(2,1),listOf(intArrayOf(1,2),intArrayOf(1)))}}
 @Test fun cooperativelyCancelsEveryPhase(){for(stop in listOf(1,3,8,15,22)){var calls=0;assertThrows(IllegalStateException::class.java){PhotoFocusStack.create(PhotoPixelDifference.Size(20,10),List(3){IntArray(200)}){if(++calls==stop)error("cancel")}}}}
 @Test fun maximumGridIntegralDoesNotOverflow(){val w=1024;val sharp=IntArray(w*w){i->gray(if((i%w+i/w)%2==0)0 else 255)};val flat=IntArray(w*w){gray(127)};val r=PhotoFocusStack.create(PhotoPixelDifference.Size(w,w),listOf(flat,sharp));assertArrayEquals(sharp,r.pixels);assertEquals(w*w,r.contributions[1])}
}
