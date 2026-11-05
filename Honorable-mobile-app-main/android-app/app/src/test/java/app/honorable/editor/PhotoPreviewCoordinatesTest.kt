package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoPreviewCoordinatesTest {
 @Test fun scaledDrawableMapsToOriginalBitmapWithoutRejectingVisiblePoints(){
  assertEquals(PhotoPoint(375f,250f),PhotoPreviewCoordinates.bitmapPoint(500,500,1000,1000,750f,500f))
  assertEquals(PhotoPoint(125f,250f),PhotoPreviewCoordinates.bitmapPoint(500,500,250,250,62.5f,125f))
 }
 @Test fun independentAxesAndFractionalPositionsPreserveRelativeLocation(){
  val point=PhotoPreviewCoordinates.bitmapPoint(101,79,303,158,75.75f,118.5f)!!
  assertEquals(25.25f,point.x,0f);assertEquals(59.25f,point.y,0f)
  assertEquals(PhotoPoint(101f,79f),PhotoPreviewCoordinates.bitmapPoint(101,79,303,158,303f,158f))
 }
 @Test fun outsideAndNonfinitePointsAreRefused(){
  for((x,y)in listOf(-1f to 50f,101f to 50f,50f to -1f,50f to 101f,Float.NaN to 50f,50f to Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY to 0f))assertNull(PhotoPreviewCoordinates.bitmapPoint(50,50,100,100,x,y))
  assertEquals(PhotoPoint(0f,0f),PhotoPreviewCoordinates.bitmapPoint(50,50,100,100,0f,0f))
 }
 @Test fun invalidDimensionsAreRejected(){
  for(dimensions in listOf(listOf(0,1,1,1),listOf(1,0,1,1),listOf(1,1,0,1),listOf(1,1,1,0),listOf(-1,1,1,1))){
   try{PhotoPreviewCoordinates.bitmapPoint(dimensions[0],dimensions[1],dimensions[2],dimensions[3],0f,0f);fail("Invalid dimensions accepted")}catch(expected:IllegalArgumentException){}
  }
 }
 @Test fun densityMappingRetainsSourceLightPositionAcrossCropRotationAndMirrors(){
  for(rotation in listOf(0,90,180,270))for(crop in listOf(.5f,1f))for(mirrorX in listOf(false,true))for(mirrorY in listOf(false,true)){
   val edit=PhotoEdit(rotation=rotation,crop=crop,finish=PhotoFinish(ratio=.8f,mirrorX=mirrorX,mirrorY=mirrorY))
   val geometry=PhotoToneGeometry(101,79,edit)
   val sx=geometry.left+geometry.cropWidth*.33f;val sy=geometry.top+geometry.cropHeight*.61f
   val output=geometry.outputPoint(sx,sy)
   val mapped=PhotoPreviewCoordinates.bitmapPoint(geometry.outputWidth,geometry.outputHeight,geometry.outputWidth*3,geometry.outputHeight*3,output.x*3,output.y*3)!!
   val source=geometry.normalizedSourcePoint(mapped.x,mapped.y)
   assertEquals(sx/101,source.x,.00001f);assertEquals(sy/79,source.y,.00001f)
  }
 }
}
