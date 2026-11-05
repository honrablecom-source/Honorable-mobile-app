package app.honorable.editor

import java.util.Locale

/** Approximate encoded RGB palette, grouped by five bits per channel, weighted by source alpha. */
data class PhotoPaletteSwatch(val color:Int,val alphaWeight:Long){
 fun hex()=String.format(Locale.ROOT,"#%06X",color and 0xffffff)
}
data class PhotoPalette(val swatches:List<PhotoPaletteSwatch>,val totalAlphaWeight:Long,val visible:Long,val transparent:Long){
 fun hexText()=swatches.joinToString("\n"){it.hex()}
}

/** Fixed32768 buckets; retains numeric sums only, never source pixels. */
class PhotoPaletteAccumulator {
 private val weights=LongArray(32768)
 private val reds=LongArray(32768);private val greens=LongArray(32768);private val blues=LongArray(32768)
 private var total=0L;private var visible=0L;private var transparent=0L
 fun add(pixels:IntArray,offset:Int=0,count:Int=pixels.size-offset){
  require(offset>=0&&count>=0&&offset<=pixels.size&&count<=pixels.size-offset)
  for(index in offset until offset+count){
   val pixel=pixels[index];val alpha=pixel ushr 24
   if(alpha==0){transparent++;continue}
   val r=(pixel ushr 16)and 255;val g=(pixel ushr 8)and 255;val b=pixel and 255
   val bucket=((r ushr 3)shl 10)or((g ushr 3)shl 5)or(b ushr 3)
   val weight=alpha.toLong();weights[bucket]+=weight
   reds[bucket]+=r*weight;greens[bucket]+=g*weight;blues[bucket]+=b*weight
   total+=weight;visible++
  }
 }
 fun snapshot(limit:Int=8):PhotoPalette {
  require(limit in 1..8)
  val swatches=weights.indices.asSequence().filter{weights[it]>0}
   .sortedWith(compareByDescending<Int>{weights[it]}.thenBy{it}).take(limit).map{index->
    val weight=weights[index]
    fun channel(sums:LongArray)=((sums[index]+weight/2)/weight).toInt()
    PhotoPaletteSwatch(0xff000000.toInt()or(channel(reds)shl 16)or(channel(greens)shl 8)or channel(blues),weight)
   }.toList()
  return PhotoPalette(swatches,total,visible,transparent)
 }
}
