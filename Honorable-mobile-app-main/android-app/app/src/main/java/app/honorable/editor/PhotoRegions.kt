package app.honorable.editor

import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Collections
import kotlin.math.*

enum class PhotoRegionEffect { PIXELATE, BLUR, GRAYSCALE }
enum class PhotoRegionShape { RECTANGLE, ELLIPSE }
/** Finished-image coordinates; region edits precede annotation objects and never modify originals. */
data class PhotoRegion(val id:String,val effect:PhotoRegionEffect,val shape:PhotoRegionShape=PhotoRegionShape.RECTANGLE,
    val x:Float=.5f,val y:Float=.5f,val width:Float=.4f,val height:Float=.3f,val strength:Float=.5f,val feather:Float=.1f,val enabled:Boolean=true) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(listOf(x,y,width,height,strength,feather).all{it.isFinite()})
        require(x in 0f..1f&&y in 0f..1f&&width in .02f..1f&&height in .02f..1f&&strength in .01f..1f&&feather in 0f.. .5f)
    }
    fun coverage(px:Float,py:Float):Float {
        val dx=abs(px-x)/(width/2);val dy=abs(py-y)/(height/2)
        val distance=if(shape==PhotoRegionShape.ELLIPSE)1-sqrt(dx*dx+dy*dy)else min(1-dx,1-dy)
        return if(distance<0)0f else if(feather==0f)1f else (distance/feather).coerceIn(0f,1f)
    }
}
class PhotoRegions(values:List<PhotoRegion> = emptyList()) {
    val values:List<PhotoRegion> = Collections.unmodifiableList(values.toList())
    init{require(this.values.size<=8){"Use up to eight local effect regions"};require(this.values.map{it.id}.distinct().size==this.values.size)}
    fun add(value:PhotoRegion)=PhotoRegions(values+value)
    fun update(value:PhotoRegion):PhotoRegions{require(values.any{it.id==value.id});return PhotoRegions(values.map{if(it.id==value.id)value else it})}
    fun delete(id:String):PhotoRegions{require(values.any{it.id==id});return PhotoRegions(values.filterNot{it.id==id})}
    override fun equals(other:Any?)=other is PhotoRegions&&values==other.values
    override fun hashCode()=values.hashCode()
    internal fun write(out:DataOutputStream){out.writeInt(values.size);values.forEach{out.writeUTF(it.id);out.writeInt(it.effect.ordinal);out.writeInt(it.shape.ordinal);for(v in listOf(it.x,it.y,it.width,it.height,it.strength,it.feather))out.writeFloat(v);out.writeBoolean(it.enabled)}}
    companion object{
        internal fun read(input:DataInputStream):PhotoRegions{val size=input.readInt();require(size in 0..8);return PhotoRegions(List(size){
            val id=input.readUTF();val effect=input.readInt();val shape=input.readInt();require(effect in PhotoRegionEffect.entries.indices&&shape in PhotoRegionShape.entries.indices)
            PhotoRegion(id,PhotoRegionEffect.entries[effect],PhotoRegionShape.entries[shape],input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readBoolean())
        })}
    }
}
/** Crop-bounded processing, original alpha retained, identical kernel for preview/export. */
object PhotoRegionKernel {
    fun applyInPlace(pixels:IntArray,width:Int,height:Int,regions:PhotoRegions){
        require(width>0&&height>0&&width.toLong()*height==pixels.size.toLong())
        for(region in regions.values){
            if(!region.enabled)continue
            val left=floor((region.x-region.width/2)*width).toInt().coerceIn(0,width)
            val top=floor((region.y-region.height/2)*height).toInt().coerceIn(0,height)
            val right=ceil((region.x+region.width/2)*width).toInt().coerceIn(left,width)
            val bottom=ceil((region.y+region.height/2)*height).toInt().coerceIn(top,height)
            val w=right-left;val h=bottom-top;if(w==0||h==0)continue
            val local=IntArray(w*h);for(y in 0 until h)pixels.copyInto(local,y*w,(top+y)*width+left,(top+y)*width+right)
            when(region.effect){
                PhotoRegionEffect.PIXELATE->PhotoEffectsKernel.applyInPlace(local,w,h,PhotoEffects(pixelate=(2+region.strength*62).roundToInt()))
                PhotoRegionEffect.BLUR->PhotoEffectsKernel.applyInPlace(local,w,h,PhotoEffects(blur=(1+region.strength*19).roundToInt()))
                PhotoRegionEffect.GRAYSCALE->for(i in local.indices){val p=local[i];val gray=(.2126*((p ushr 16)and 255)+.7152*((p ushr 8)and 255)+.0722*(p and 255)).roundToInt();local[i]=(p and -0x1000000)or(gray shl 16)or(gray shl 8)or gray}
            }
            for(y in 0 until h)for(x in 0 until w){
                val coverage=region.coverage((left+x+.5f)/width,(top+y+.5f)/height)*(if(region.effect==PhotoRegionEffect.GRAYSCALE)region.strength else 1f)
                if(coverage<=0f)continue
                val index=(top+y)*width+left+x;val original=pixels[index];val changed=local[y*w+x]
                fun channel(shift:Int)=(((original ushr shift)and 255)*(1-coverage)+((changed ushr shift)and 255)*coverage).roundToInt().coerceIn(0,255)
                pixels[index]=(original and -0x1000000)or(channel(16) shl 16)or(channel(8) shl 8)or channel(0)
            }
        }
    }
}
