package app.honorable.editor

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

/** Hue-range selection excludes neutral pixels; every adjustment preserves original alpha. */
data class PhotoSelectiveColor(val enabled:Boolean=false,val hue:Float=0f,val radius:Float=30f,val softness:Float=.3f,val shift:Float=0f,val saturation:Float=1f,val exposure:Float=0f){
 init{require(listOf(hue,radius,softness,shift,saturation,exposure).all{it.isFinite()});require(hue in 0f..360f&&radius in 1f..180f&&softness in 0f..1f&&shift in -180f..180f&&saturation in 0f..2f&&exposure in -2f..2f)}
 val isIdentity get()=!enabled||(shift==0f&&saturation==1f&&exposure==0f)
 internal fun write(out:DataOutputStream){out.writeBoolean(enabled);for(v in listOf(hue,radius,softness,shift,saturation,exposure))out.writeFloat(v)}
 companion object{internal fun read(input:DataInputStream)=PhotoSelectiveColor(input.readBoolean(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat())}
}
/** Allocation-free per pixel, deterministic hue wrap and feather, instantiated once per render. */
class PhotoSelectiveColorKernel(private val edit:PhotoSelectiveColor){
 private val gain=2f.pow(edit.exposure)
 fun apply(pixel:Int):Int{
  if(edit.isIdentity||pixel ushr 24==0)return pixel
  val r=((pixel ushr 16)and 255)/255f;val g=((pixel ushr 8)and 255)/255f;val b=(pixel and 255)/255f
  val high=maxOf(r,g,b);val low=minOf(r,g,b);val delta=high-low
  if(delta<.01f||high==0f)return pixel
  var hue=when(high){r->60*((g-b)/delta%6);g->60*((b-r)/delta+2);else->60*((r-g)/delta+4)}
  if(hue<0)hue+=360
  val difference=abs(hue-edit.hue)%360;val distance=min(difference,360-difference)
  if(distance>edit.radius)return pixel
  val inner=edit.radius*(1-edit.softness)
  val linear=if(distance<=inner||edit.softness==0f)1f else ((edit.radius-distance)/(edit.radius-inner)).coerceIn(0f,1f)
  val weight=linear*linear*(3-2*linear)
  if(weight==0f)return pixel
  val shifted=((hue+edit.shift)%360+360)%360;val sector=(shifted/60).toInt();val f=shifted/60-sector
  val value=(high*gain).coerceIn(0f,1f);val saturation=(delta/high*edit.saturation).coerceIn(0f,1f)
  val p=value*(1-saturation);val q=value*(1-f*saturation);val t=value*(1-(1-f)*saturation)
  val nr=when(sector){0,5->value;1->q;2,3->p;else->t}
  val ng=when(sector){0->t;1,2->value;3->q;else->p}
  val nb=when(sector){0,1->p;2->t;3,4->value;else->q}
  fun channel(original:Float,changed:Float)=((original+(changed-original)*weight)*255).roundToInt().coerceIn(0,255)
  return (pixel and -0x1000000)or(channel(r,nr) shl 16)or(channel(g,ng) shl 8)or channel(b,nb)
 }
}
