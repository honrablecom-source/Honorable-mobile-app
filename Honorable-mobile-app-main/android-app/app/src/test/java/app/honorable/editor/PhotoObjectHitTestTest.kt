package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PhotoObjectHitTestTest {
 private val back=PhotoObject("back",PhotoObjectType.RECTANGLE,width=.6f,height=.6f)
 private val front=PhotoObject("front",PhotoObjectType.RECTANGLE,width=.4f,height=.4f)
 private fun pick(vararg objects:PhotoObject,x:Float=50f,y:Float=50f)=PhotoObjectHitTest.pick(PhotoLayers(objects.toList()),100,100,x,y)
 @Test fun overlapPicksFrontmostObjectAndLeavesOrderUnchanged(){
  val layers=PhotoLayers(listOf(back,front));assertSame(front,PhotoObjectHitTest.pick(layers,100,100,50f,50f));assertEquals(listOf(back,front),layers.objects)
  assertSame(back,PhotoObjectHitTest.pick(layers,100,100,25f,50f))
 }
 @Test fun hiddenAndFullyTransparentObjectsDoNotStealThePick(){
  for(invisible in listOf(front.copy(visible=false),front.copy(opacity=0f),front.copy(color=0x00123456),front.copy(color=0x01000000,opacity=.5f)))assertSame(back,pick(back,invisible))
 }
 @Test fun lockedObjectsRemainPickableForUnlocking(){val locked=front.copy(locked=true);assertSame(locked,pick(back,locked))}
 @Test fun ellipseUsesCurvedBoundaryRatherThanItsRectangularCorners(){
  val ellipse=front.copy(type=PhotoObjectType.ELLIPSE)
  assertSame(ellipse,pick(ellipse,x=70f,y=50f));assertNull(pick(ellipse,x=65f,y=65f));assertSame(back,pick(back,ellipse,x=65f,y=65f))
 }
 @Test fun quarterTurnUsesPixelAspectRatioOnNonSquareOutput(){
  val rotated=front.copy(width=.4f,height=.2f,rotation=90f);val layers=PhotoLayers(listOf(rotated))
  assertSame(rotated,PhotoObjectHitTest.pick(layers,1000,500,500f,400f))
  assertNull(PhotoObjectHitTest.pick(layers,1000,500,600f,250f))
 }
 @Test fun arbitraryPositiveAndNegativeRotationUseInverseCanvasTransform(){
  for(degrees in listOf(-135f,-45f,45f,135f)){
   val value=front.copy(width=.4f,height=.2f,rotation=degrees);val angle=Math.toRadians(degrees.toDouble())
   fun at(localX:Double)=PhotoObjectHitTest.pick(PhotoLayers(listOf(value)),200,100,(100+localX*cos(angle)).toFloat(),(50+localX*sin(angle)).toFloat())
   assertSame(value,at(35.0));assertNull(at(45.0))
  }
 }
 @Test fun textUsesItsBoxIncludingVisibleOutlineAndShadow(){
  val text=front.copy(type=PhotoObjectType.TEXT,text="Hi",height=.2f)
  assertSame(text,pick(text,x=68f,y=58f))
  assertNull(pick(text.copy(color=0x00000000)))
  val outlined=text.copy(color=0x00000000,textStyle=PhotoTextStyle(outline=.01f));assertSame(outlined,pick(outlined))
  val shadow=text.copy(color=0x00000000,textStyle=PhotoTextStyle(shadow=.01f));assertSame(shadow,pick(shadow))
 }
 @Test fun invalidAndOutsidePointsCannotSelectOrMutateAnything(){
  for((x,y)in listOf(-1f to 50f,101f to 50f,50f to -1f,50f to 101f,Float.NaN to 50f,50f to Float.POSITIVE_INFINITY))assertNull(pick(front,x=x,y=y))
  assertNull(pick());assertNull(pick(front,x=0f,y=0f))
  try{PhotoObjectHitTest.pick(PhotoLayers(listOf(front)),0,100,0f,0f);fail("Invalid canvas accepted")}catch(expected:IllegalArgumentException){}
 }
 @Test fun drawableDensityScalingMapsBackToFinishedBitmapCoordinates(){
  val layers=PhotoLayers(listOf(front))
  for(scale in listOf(1,2,3)){
   assertSame(front,PhotoObjectHitTest.pickDrawable(layers,100,100,100*scale,100*scale,65f*scale,50f*scale))
   assertNull(PhotoObjectHitTest.pickDrawable(layers,100,100,100*scale,100*scale,75f*scale,50f*scale))
  }
  try{PhotoObjectHitTest.pickDrawable(layers,100,100,0,100,0f,0f);fail("Invalid drawable accepted")}catch(expected:IllegalArgumentException){}
 }

}
