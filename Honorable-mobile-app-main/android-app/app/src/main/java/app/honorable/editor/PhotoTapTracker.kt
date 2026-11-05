package app.honorable.editor

/** Display-pixel movement tolerance. A rejected gesture cannot reactivate until a fresh down. */
class PhotoTapTracker(private val slop:Float){
 init{require(slop.isFinite()&&slop>=0)}
 private var active=false;private var pointer=-1;private var startX=0.0;private var startY=0.0
 fun down(x:Float,y:Float,pointerId:Int){
  active=x.isFinite()&&y.isFinite()&&pointerId>=0
  pointer=pointerId;startX=x.toDouble();startY=y.toDouble()
 }
 private fun matches(x:Float,y:Float,pointerId:Int):Boolean {
  if(!active||pointerId!=pointer||!x.isFinite()||!y.isFinite())return false
  val dx=x.toDouble()-startX;val dy=y.toDouble()-startY
  return dx*dx+dy*dy<=slop.toDouble()*slop
 }
 fun move(x:Float,y:Float,pointerId:Int){if(!matches(x,y,pointerId))cancel()}
 fun up(x:Float,y:Float,pointerId:Int):Boolean {val accepted=matches(x,y,pointerId);cancel();return accepted}
 fun cancel(){active=false;pointer=-1}
}
