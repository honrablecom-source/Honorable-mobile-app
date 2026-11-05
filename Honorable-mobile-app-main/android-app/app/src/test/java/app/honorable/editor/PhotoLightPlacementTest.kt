package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PhotoLightPlacementTest {
 private fun assertPoint(expected:PhotoPoint,actual:PhotoPoint?){assertNotNull(actual);assertEquals(expected.x,actual!!.x,.00001f);assertEquals(expected.y,actual.y,.00001f)}
 @Test fun identityRetainsContinuousPhotoCoordinates(){
  assertPoint(PhotoPoint(.25f,.75f),PhotoLightPlacement.normalizedSourcePoint(1000,800,PhotoEdit(),250f,600f))
 }
 @Test fun paddingFrameAndShadowAreRemovedBeforeSourceMapping(){
  for(composition in listOf(PhotoComposition(padding=.2f),PhotoComposition(frame=.1f),PhotoComposition(shadow=.8f),PhotoComposition(padding=.15f,frame=.05f,shadow=.5f))){
   val edit=PhotoEdit(composition=composition);val plan=PhotoCompositionPlan.create(1000,800,composition)
   assertPoint(PhotoPoint(.25f,.75f),PhotoLightPlacement.normalizedSourcePoint(1000,800,edit,plan.left+250*plan.scale,plan.top+600*plan.scale))
   assertNull(PhotoLightPlacement.normalizedSourcePoint(1000,800,edit,0f,0f))
  }
 }
 @Test fun positiveAndNegativePerspectiveInvertKnownTrapezoidPoints(){
  for(amount in listOf(-.4f,-.2f,.2f,.4f)){
   val edit=PhotoEdit(composition=PhotoComposition(perspective=amount));val p=PhotoCompositionPlan.create(1000,800,edit.composition)
   // Mid-height target widths follow the projective transform, not linear trapezoid interpolation.
   val a=abs(amount.toDouble());val u=.25;val v=.6
   val factor=if(amount>=0)(1-2*a)/(1-2*a*v)else 1/(1+2*a/(1-2*a)*v)
   val projectedX=(.5+(u-.5)*factor)*1000
   val projectedY=if(amount>=0)(1-2*a)*v/(1-2*a*v)*800 else v/(1-2*a+2*a*v)*800
   assertPoint(PhotoPoint(u.toFloat(),v.toFloat()),PhotoLightPlacement.normalizedSourcePoint(1000,800,edit,(p.left+projectedX*p.scale).toFloat(),(p.top+projectedY*p.scale).toFloat()))
   assertNull(PhotoLightPlacement.normalizedSourcePoint(1000,800,edit,p.left,p.top+(if(amount>=0)0f else 800*p.scale)))
  }
 }
 @Test fun reflectionMapsVisibleMirroredContentBackToThePhoto(){
  val edit=PhotoEdit(composition=PhotoComposition(padding=.1f,reflection=.5f));val p=PhotoCompositionPlan.create(1000,800,edit.composition)
  val x=p.left+250*p.scale;val y=p.reflectionTop+100*p.scale
  assertPoint(PhotoPoint(.25f,.875f),PhotoLightPlacement.normalizedSourcePoint(1000,800,edit,x,y))
  assertNull(PhotoLightPlacement.normalizedSourcePoint(1000,800,edit,x,p.reflectionTop+p.reflectionHeight+1))
 }
 @Test fun repeatedAndKaleidoscopeCoordinatesMatchTheActualCompositionKernel(){
  val w=31;val h=23;val pixels=IntArray(w*h){0xff000000.toInt()or it}
  for(tiles in listOf(1,2,6))for(sectors in listOf(0,2,5,12))for(shape in 0..4){
   val composition=PhotoComposition(tiles=tiles,sectors=sectors,shape=shape);val kernel=PhotoCompositionKernel(pixels,w,h,composition);val edit=PhotoEdit(composition=composition)
   for(y in 0 until h)for(x in 0 until w){
    val pixel=kernel.sample(x,y);val point=PhotoLightPlacement.normalizedSourcePoint(w,h,edit,x+.5f,y+.5f)
    if(pixel==0)assertNull(point)else {
     assertNotNull(point);val sx=floor(point!!.x*w).toInt().coerceIn(0,w-1);val sy=floor(point.y*h).toInt().coerceIn(0,h-1)
     assertEquals("tiles=$tiles sectors=$sectors shape=$shape at $x,$y",pixel,pixels[sy*w+sx])
    }
   }
  }
 }
 @Test fun cropRotationMirrorsAndPaddedLayoutComposeCorrectly(){
  for(rotation in listOf(0,90,180,270))for(mirrorX in listOf(false,true))for(mirrorY in listOf(false,true)){
   val edit=PhotoEdit(rotation=rotation,crop=.6f,finish=PhotoFinish(ratio=.8f,mirrorX=mirrorX,mirrorY=mirrorY),composition=PhotoComposition(padding=.2f))
   val g=PhotoToneGeometry(101,79,edit);val sx=g.left+g.cropWidth*.3f;val sy=g.top+g.cropHeight*.7f;val at=g.outputPoint(sx,sy);val p=PhotoCompositionPlan.create(g.outputWidth,g.outputHeight,edit.composition)
   assertPoint(PhotoPoint(sx/101,sy/79),PhotoLightPlacement.normalizedSourcePoint(101,79,edit,p.left+at.x*p.scale,p.top+at.y*p.scale))
  }
 }
 @Test fun invalidAndOutsidePointsDoNotYieldClampedPlacements(){
  for((x,y)in listOf(-1f to 50f,101f to 50f,Float.NaN to 50f,50f to Float.POSITIVE_INFINITY))assertNull(PhotoLightPlacement.normalizedSourcePoint(100,100,PhotoEdit(),x,y))
 }
 @Test fun reflectionAndPerspectiveInvertTheSamePhotoPoint(){
  for(amount in listOf(-.2f,.2f)){
   val edit=PhotoEdit(composition=PhotoComposition(perspective=amount,reflection=.5f));val p=PhotoCompositionPlan.create(1000,800,edit.composition)
   val u=.3;val v=.85;val a=abs(amount.toDouble())
   val factor=if(amount>=0)(1-2*a)/(1-2*a*v)else 1/(1+2*a/(1-2*a)*v)
   val px=(.5+(u-.5)*factor)*1000;val py=if(amount>=0)(1-2*a)*v/(1-2*a*v)*800 else v/(1-2*a+2*a*v)*800
   assertPoint(PhotoPoint(u.toFloat(),v.toFloat()),PhotoLightPlacement.normalizedSourcePoint(1000,800,edit,(p.left+px*p.scale).toFloat(),(p.reflectionTop+(800-py)*p.scale).toFloat()))
  }
 }
 @Test fun paddedRepeatedCutoutsComposeWithCropRotationAndMirrors(){
  for(rotation in listOf(0,90,180,270)){
   val edit=PhotoEdit(rotation=rotation,crop=.7f,finish=PhotoFinish(mirrorX=true),composition=PhotoComposition(padding=.2f,tiles=3,sectors=5,shape=1))
   val g=PhotoToneGeometry(101,79,edit);val w=g.outputWidth;val h=g.outputHeight;val p=PhotoCompositionPlan.create(w,h,edit.composition)
   val pixels=IntArray(w*h){0xff000000.toInt()or it};val kernel=PhotoCompositionKernel(pixels,w,h,edit.composition)
   for(y in 0 until h step 4)for(x in 0 until w step 4){
    val sample=kernel.sample(x,y);val actual=PhotoLightPlacement.normalizedSourcePoint(101,79,edit,p.left+(x+.5f)*p.scale,p.top+(y+.5f)*p.scale)
    if(sample==0)assertNull(actual)else {val index=sample and 0xffffff;assertPoint(g.normalizedSourcePoint(index%w+.5f,index/w+.5f),actual)}
   }
  }
 }
 @Test fun actualLocalLightAdmissionRejectsRecognitionAndOtherBusyWorkBeforeUi(){
  val type=Class.forName("sun.misc.Unsafe");val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
  fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
  val invoke=PhotoEditorActivity::class.java.getDeclaredMethod("showLocalLight").apply{isAccessible=true}
  for(name in listOf("readingText","photoProjectBusy","enhancing","cuttingOut","activityClosed")){
   val activity=allocate(PhotoEditorActivity::class.java)
   PhotoEditorActivity::class.java.getDeclaredField("bitmap").apply{isAccessible=true}.set(activity,allocate(android.graphics.Bitmap::class.java))
   PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,true)
   invoke.invoke(activity)
  }
 }

}
