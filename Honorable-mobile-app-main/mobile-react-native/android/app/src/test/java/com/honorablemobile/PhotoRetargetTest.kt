package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.math.abs
class PhotoRetargetTest {
 private fun reference(pixels:IntArray,w:Int,h:Int,remove:Int):IntArray {
  var width=w;var current=pixels.copyOf()
  fun components(p:Int):IntArray{val a=p ushr 24;return intArrayOf(a,((p ushr 16 and 255)*a+127)/255,((p ushr 8 and 255)*a+127)/255,((p and 255)*a+127)/255)}
  repeat(remove){
   val energy=IntArray(width*h){i->val x=i%width;val y=i/width;fun at(xx:Int,yy:Int)=components(current[yy.coerceIn(0,h-1)*width+xx.coerceIn(0,width-1)])
    val left=at(x-1,y);val right=at(x+1,y);val up=at(x,y-1);val down=at(x,y+1);(0..3).sumOf{k->abs(left[k]-right[k])+abs(up[k]-down[k])}}
   var best:IntArray?=null;var bestCost=Int.MAX_VALUE
   fun walk(path:IntArray,y:Int){if(y==h){val cost=(0 until h).sumOf{row->energy[row*width+path[row]]};var wins=cost<bestCost;if(cost==bestCost){val prior=best!!;for(row in h-1 downTo 0)if(path[row]!=prior[row]){wins=path[row]<prior[row];break}};if(wins){best=path.copyOf();bestCost=cost};return};for(x in 0 until width)if(y==0||abs(x-path[y-1])<=1){path[y]=x;walk(path,y+1)}}
   walk(IntArray(h),0);val seam=best!!;current=(0 until h).flatMap{y->(0 until width).filter{it!=seam[y]}.map{x->current[y*width+x]}}.toIntArray();width--
  };return current
 }
 @Test fun bruteForceAllConnectedPathsMatchMinimumEnergyAndTiePolicy(){val rng=Random(136);repeat(80){val w=2+rng.nextInt(5);val h=1+rng.nextInt(4);val remove=1+rng.nextInt(minOf(3,w-1));val input=IntArray(w*h){rng.nextInt()};val before=input.copyOf();val result=PhotoRetarget.narrow(PhotoPixelDifference.Size(w,h),input,remove);assertArrayEquals(reference(input,w,h,remove),result.pixels);assertEquals(w-remove,result.width);assertEquals(h,result.height);assertEquals(remove,result.removedColumns);assertArrayEquals(before,input)}}
 @Test fun flatHiddenRgbUsesStableLeftmostSeamsAndPreservesRemainingArgbBits(){val input=IntArray(15){it+1};val r=PhotoRetarget.narrow(PhotoPixelDifference.Size(5,3),input,2);assertArrayEquals(intArrayOf(3,4,5,8,9,10,13,14,15),r.pixels);assertArrayEquals(IntArray(15){it+1},input)}
 @Test fun rowOrderAndExactSourcePixelProvenanceAreNeverInvented(){val rng=Random(137);repeat(40){val w=10;val h=8;val input=IntArray(w*h){rng.nextInt()};val r=PhotoRetarget.narrow(PhotoPixelDifference.Size(w,h),input,4);for(y in 0 until h){var previous=-1;for(x in 0 until r.width){val p=r.pixels[y*r.width+x];val found=(previous+1 until w).firstOrNull{i->input[y*w+i]==p};assertNotNull(found);previous=found!!}}}}
 @Test fun constantOpaqueFramesKeepAlphaAndDimensions(){val input=IntArray(30){0x80335577.toInt()};val r=PhotoRetarget.narrow(PhotoPixelDifference.Size(6,5),input,3);assertEquals(3,r.width);assertEquals(5,r.height);assertTrue(r.pixels.all{it==input[0]})}
 @Test fun percentRoundsUpFromDecodedWidthAndNeverDeletesLastColumn(){for((w,p,removed)in listOf(Triple(1024,12,123),Triple(100,1,1),Triple(5,12,1),Triple(2,12,1))){val r=PhotoRetarget.percent(PhotoPixelDifference.Size(w,1),IntArray(w){it},p);assertEquals(removed,r.removedColumns);assertEquals(w-removed,r.width)}}
 @Test fun maximumImageGridAndMaximumSeamCountUseBoundedArrays(){val grid=IntArray(1024*1024){0xff334455.toInt()};val r=PhotoRetarget.narrow(PhotoPixelDifference.Size(1024,1024),grid,1);assertEquals(1023*1024,r.pixels.size);assertTrue(r.pixels.all{it==grid[0]});assertEquals(896,PhotoRetarget.narrow(PhotoPixelDifference.Size(1024,1),IntArray(1024),128).width)}
 @Test fun invalidPercentCountsAndGridBindingsFail(){for(p in listOf(0,13,-1))assertThrows(IllegalArgumentException::class.java){PhotoRetarget.percent(PhotoPixelDifference.Size(2,1),intArrayOf(1,2),p)};for(n in listOf(0,-1,2,129))assertThrows(IllegalArgumentException::class.java){PhotoRetarget.narrow(PhotoPixelDifference.Size(2,1),intArrayOf(1,2),n)};assertThrows(IllegalArgumentException::class.java){PhotoRetarget.narrow(PhotoPixelDifference.Size(3,2),intArrayOf(1),1)};assertThrows(IllegalArgumentException::class.java){PhotoRetarget.percent(PhotoPixelDifference.Size(1,1),intArrayOf(1),10)}}
 @Test fun cancellationChecksBeforeAllocationDuringPathsAndRowRemoval(){for(stop in listOf(1,3,8,15)){var calls=0;val input=IntArray(200){it};assertThrows(IllegalStateException::class.java){PhotoRetarget.narrow(PhotoPixelDifference.Size(20,10),input,2){if(++calls==stop)error("cancel")}};assertArrayEquals(IntArray(200){it},input)}}
}
