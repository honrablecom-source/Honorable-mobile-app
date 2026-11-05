package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoCaptionChoicesTest{
 private val custom=PhotoCaption("Keep me",x=.27f,y=.43f,size=.081f,light=false,plate=false)
 @Test fun unchangedCustomCaptionKeepsItsExactGeometryAndAppearance(){
  val positions=PhotoCaptionChoices.positions(custom);val sizes=PhotoCaptionChoices.sizes(custom)
  assertEquals(3,positions.indexOf(custom.y));assertEquals(3,sizes.indexOf(custom.size))
  assertEquals(custom,PhotoCaptionChoices.updated(custom,custom.text,positions[positions.indexOf(custom.y)],sizes[sizes.indexOf(custom.size)],custom.light,custom.plate))
 }
 @Test fun textAndExplicitVerticalPresetChangesKeepHorizontalPlacement(){
  val updated=PhotoCaptionChoices.updated(custom,"New text",.85f,.06f,true,true)
  assertEquals(custom.copy(text="New text",y=.85f,size=.06f,light=true,plate=true),updated)
  assertEquals(.27f,updated.x,0f)
 }
 @Test fun existingPresetValuesAreNotDuplicatedOrReordered(){
  for(y in listOf(.15f,.5f,.85f))for(size in listOf(.035f,.06f,.10f)){
   val caption=custom.copy(y=y,size=size)
   assertEquals(listOf(.15f,.5f,.85f),PhotoCaptionChoices.positions(caption))
   assertEquals(listOf(.035f,.06f,.10f),PhotoCaptionChoices.sizes(caption))
  }
 }
 @Test fun newCaptionDefaultsAndValidationRemainUnchanged(){
  assertEquals(listOf(.15f,.5f,.85f),PhotoCaptionChoices.positions(null));assertEquals(listOf(.035f,.06f,.10f),PhotoCaptionChoices.sizes(null))
  assertEquals(PhotoCaption("New"),PhotoCaptionChoices.updated(null,"New",.85f,.06f,true,true))
  for(text in listOf("","\u0000","a".repeat(121)))assertTrue(runCatching{PhotoCaptionChoices.updated(custom,text,custom.y,custom.size,true,true)}.isFailure)
 }
 @Test fun modelBoundaryValuesRemainExactCurrentChoices(){
  for(y in listOf(.05f,.95f))for(size in listOf(.025f,.15f)){
   val caption=custom.copy(y=y,size=size)
   assertEquals(y,PhotoCaptionChoices.positions(caption).last(),0f);assertEquals(size,PhotoCaptionChoices.sizes(caption).last(),0f)
   assertEquals(caption,PhotoCaptionChoices.updated(caption,caption.text,y,size,caption.light,caption.plate))
  }
 }
}
