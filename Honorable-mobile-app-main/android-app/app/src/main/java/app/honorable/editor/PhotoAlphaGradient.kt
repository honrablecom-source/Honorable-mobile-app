package app.honorable.editor

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

/** Per-photo output-relative fade. It never carries a source, pixels or a saved-look identity. */
data class PhotoAlphaGradient(val enabled:Boolean=false,val angle:Float=0f,val start:Float=0f,val end:Float=1f,val reverse:Boolean=false){
 init{
  require(listOf(angle,start,end).all{it.isFinite()}){"Use finite fade settings"}
  require(angle in 0f..360f){"Use a direction from 0 to 360 degrees"}
  require(start in 0f..1f&&end in 0f..1f&&start<end){"Set fade start before fade end"}
 }
 internal fun write(out:DataOutputStream){out.writeBoolean(enabled);out.writeFloat(angle);out.writeFloat(start);out.writeFloat(end);out.writeBoolean(reverse)}
 companion object{internal fun read(input:DataInputStream)=PhotoAlphaGradient(input.readBoolean(),input.readFloat(),input.readFloat(),input.readFloat(),input.readBoolean())}
}

/** Smoothstep coverage along normalized frame axes; color bits retained and alpha never increased. */
class PhotoAlphaGradientKernel(private val value:PhotoAlphaGradient,private val width:Int,private val height:Int){
 init{require(width>0&&height>0)}
 private val radians=Math.toRadians((value.angle%360f).toDouble())
 private val dx=cos(radians);private val dy=sin(radians);private val extent=abs(dx)+abs(dy)
 fun apply(pixel:Int,x:Int,y:Int):Int{
  require(x in 0 until width&&y in 0 until height)
  val alpha=pixel ushr 24
  if(!value.enabled||alpha==0)return pixel
  val position=.5+(((x+.5)/width-.5)*dx+((y+.5)/height-.5)*dy)/extent
  val t=((position-value.start)/(value.end.toDouble()-value.start)).coerceIn(0.0,1.0)
  val smooth=t*t*(3-2*t);val coverage=if(value.reverse)1-smooth else smooth
  val next=(alpha*coverage).roundToInt().coerceIn(0,alpha)
  return (pixel and 0xffffff)or(next shl 24)
 }
}
