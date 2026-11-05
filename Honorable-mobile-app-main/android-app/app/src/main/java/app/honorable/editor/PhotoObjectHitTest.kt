package app.honorable.editor

import kotlin.math.*

/** Finished-canvas pixel coordinates. Text uses its clipped edit box, not individual glyph outlines. */
object PhotoObjectHitTest {
 /** Coordinates relative to drawable bounds after ImageView's inverse matrix and padding removal. */
 fun pickDrawable(layers:PhotoLayers,width:Int,height:Int,drawableWidth:Int,drawableHeight:Int,x:Float,y:Float):PhotoObject? {
  val point=PhotoPreviewCoordinates.bitmapPoint(width,height,drawableWidth,drawableHeight,x,y)?:return null
  return pick(layers,width,height,point.x,point.y)
 }
 fun pick(layers:PhotoLayers,width:Int,height:Int,x:Float,y:Float):PhotoObject? {
  require(width>0&&height>0)
  if(!x.isFinite()||!y.isFinite()||x<0||y<0||x>width||y>height)return null
  return layers.objects.asReversed().firstOrNull{value->
   if(!value.visible||(value.opacity*255).toInt()==0)return@firstOrNull false
   if(value.type!=PhotoObjectType.TEXT&&(value.opacity*(value.color ushr 24)).toInt()==0)return@firstOrNull false
   if(value.type==PhotoObjectType.TEXT&&value.color ushr 24==0&&
    !(value.textStyle.outline>0&&value.textStyle.outlineColor ushr 24>0)&&
    !(value.textStyle.shadow>0&&value.textStyle.shadowColor ushr 24>0))return@firstOrNull false
   val plan=PhotoObjectPlan.create(value,width,height)
   val radians=Math.toRadians(value.rotation.toDouble());val dx=x-plan.centerX;val dy=y-plan.centerY
   val localX=dx*cos(radians)+dy*sin(radians);val localY=-dx*sin(radians)+dy*cos(radians)
   val halfWidth=plan.width/2.0;val halfHeight=plan.height/2.0
   if(value.type==PhotoObjectType.ELLIPSE)(localX/halfWidth).pow(2)+(localY/halfHeight).pow(2)<=1.000001
   else abs(localX)<=halfWidth+.0001&&abs(localY)<=halfHeight+.0001
  }
 }
}
