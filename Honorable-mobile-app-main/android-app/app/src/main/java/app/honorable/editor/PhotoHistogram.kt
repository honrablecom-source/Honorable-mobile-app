package app.honorable.editor

/** Encoded sRGB luma distribution, not linear-light luminance or a clipping diagnosis.
 * Nonzero-alpha pixels count equally; fully transparent RGB is excluded. No pixels retained. */
data class PhotoHistogram(val bins:List<Long>,val visible:Long,val transparent:Long,val partialAlpha:Long,
 val minimum:Int?,val maximum:Int?,val mean:Double?,val channelZero:List<Long>,val channelFull:List<Long>)

class PhotoHistogramAccumulator {
 private val bins=LongArray(16)
 private val zero=LongArray(3);private val full=LongArray(3)
 private var visible=0L;private var transparent=0L;private var partial=0L;private var sum=0L
 private var minimum=255;private var maximum=0
 fun add(pixels:IntArray,offset:Int=0,count:Int=pixels.size-offset){
  require(offset>=0&&count>=0&&offset<=pixels.size&&count<=pixels.size-offset)
  for(index in offset until offset+count){
   val pixel=pixels[index];val alpha=pixel ushr 24
   if(alpha==0){transparent++;continue}
   if(alpha<255)partial++
   val red=(pixel ushr 16)and 255;val green=(pixel ushr 8)and 255;val blue=pixel and 255
   val value=(2126*red+7152*green+722*blue+5000)/10000
   bins[value/16]++;visible++;sum+=value
   minimum=minOf(minimum,value);maximum=maxOf(maximum,value)
   if(red==0)zero[0]++;if(green==0)zero[1]++;if(blue==0)zero[2]++
   if(red==255)full[0]++;if(green==255)full[1]++;if(blue==255)full[2]++
  }
 }
 fun snapshot()=PhotoHistogram(bins.toList(),visible,transparent,partial,
  minimum.takeIf{visible>0},maximum.takeIf{visible>0},if(visible>0)sum.toDouble()/visible else null,zero.toList(),full.toList())
}
