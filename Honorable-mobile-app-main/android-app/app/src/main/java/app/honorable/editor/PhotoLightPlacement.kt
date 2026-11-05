package app.honorable.editor

import kotlin.math.*

/** Map a composed preview point back to the photo where local light is applied.
 * Overlaid text/objects/watermarks do not change the underlying photo coordinate.
 * Color-key transparency is not visibility-tested: changing light can change the key itself. */
object PhotoLightPlacement {
 fun normalizedSourcePoint(width:Int,height:Int,edit:PhotoEdit,x:Float,y:Float):PhotoPoint? {
  val geometry=PhotoToneGeometry(width,height,edit)
  val w=geometry.outputWidth;val h=geometry.outputHeight;val value=edit.composition
  val plan=if(value.needsCanvas)PhotoCompositionPlan.create(w,h,value)else null
  val outputWidth=plan?.width?:w;val outputHeight=plan?.height?:h
  if(!x.isFinite()||!y.isFinite()||x<0||y<0||x>outputWidth||y>outputHeight)return null
  fun unproject(px:Double,py:Double):PhotoPoint? {
   val u=px/w;val v=py/h
   if(v<0||v>1)return null
   val amount=abs(value.perspective.toDouble());val narrow=1-2*amount
   val factor=if(value.perspective>=0)narrow+2*amount*v else 1-2*amount*v
   val sourceX=.5+(u-.5)/factor
   val sourceY=if(value.perspective>=0)v/factor else narrow*v/factor
   if(sourceX<-.000001||sourceX>1.000001||sourceY<-.000001||sourceY>1.000001)return null
   return PhotoPoint((sourceX.coerceIn(0.0,1.0)*w).toFloat(),(sourceY.coerceIn(0.0,1.0)*h).toFloat())
  }
  val content=if(plan==null)PhotoPoint(x,y)else {
   unproject((x-plan.left)/plan.scale.toDouble(),(y-plan.top)/plan.scale.toDouble())?:run{
    if(plan.reflectionHeight<=0||y<plan.reflectionTop||y>minOf(plan.height.toFloat(),plan.reflectionTop+plan.reflectionHeight))return null
    unproject((x-plan.left)/plan.scale.toDouble(),h-(y-plan.reflectionTop)/plan.scale.toDouble())?:return null
   }
  }
  val ix=floor(content.x).toInt().coerceIn(0,w-1);val iy=floor(content.y).toInt().coerceIn(0,h-1)
  val nx=2.0*(ix+.5)/w-1;val ny=2.0*(iy+.5)/h-1
  val inside=when(value.shape){
   1->{val dx=(ix+.5-w/2.0)/(minOf(w,h)/2.0);val dy=(iy+.5-h/2.0)/(minOf(w,h)/2.0);dx*dx+dy*dy<=1}
   2->{val radius=minOf(w,h)*.18;val dx=max(abs(ix+.5-w/2.0)-(w/2.0-radius),0.0);val dy=max(abs(iy+.5-h/2.0)-(h/2.0-radius),0.0);dx*dx+dy*dy<=radius*radius}
   3->abs(nx)+abs(ny)<=1
   4->{val hx=nx*1.25;val hy=-ny*1.25;val a=hx*hx+hy*hy-1;a*a*a-hx*hx*hy*hy*hy<=0}
   else->true
  }
  if(!inside)return null
  if(value.tiles==1&&value.sectors==0)return geometry.normalizedSourcePoint(content.x,content.y)
  var sx=(ix*value.tiles)%w;var sy=(iy*value.tiles)%h
  if(value.sectors>0){
   val vx=2.0*(sx+.5)/w-1;val vy=2.0*(sy+.5)/h-1
   val radius=hypot(vx,vy);val sector=2*PI/value.sectors
   var angle=(atan2(vy,vx)%sector+sector)%sector
   if(angle>sector/2)angle=sector-angle
   sx=floor((radius*cos(angle)+1)*w/2).toInt().coerceIn(0,w-1)
   sy=floor((radius*sin(angle)+1)*h/2).toInt().coerceIn(0,h-1)
  }
  return geometry.normalizedSourcePoint(sx+.5f,sy+.5f)
 }
}
