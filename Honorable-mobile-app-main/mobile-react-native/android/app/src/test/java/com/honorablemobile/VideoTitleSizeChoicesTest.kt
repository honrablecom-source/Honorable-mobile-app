package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoTitleSizeChoicesTest{
 private val custom=VideoTitleStyle(1,.067f,false,false)
 @Test fun unchangedCustomStyleRetainsExactSizeAndAppearance(){
  val values=VideoTitleSizeChoices.values(custom);assertEquals(3,values.indexOf(custom.size))
  val applied=VideoTitleStyle(custom.position,values[values.indexOf(custom.size)],custom.light,custom.plate)
  assertEquals(custom,applied)
 }
 @Test fun presetsRemainStableAndDoNotGainDuplicateChoices(){
  val presets=listOf(.035f,.055f,.08f)
  for(size in presets)assertEquals(presets,VideoTitleSizeChoices.values(custom.copy(size=size)))
 }
 @Test fun validBoundarySizesRemainSelectableWithoutQuantization(){
  for(size in listOf(.025f,.12f,.03501f,.07999f)){
   val style=custom.copy(size=size);val values=VideoTitleSizeChoices.values(style)
   assertEquals(size,values.last(),0f);assertEquals(size,values[values.indexOf(size)],0f)
  }
 }
 @Test fun unchangedCustomApplyDoesNotInsertAnEmptyHistoryStep(){
  val project=VideoEditProject(2000);project.setTitleStyle(0,custom);project.setVolume(0,.5f)
  val values=VideoTitleSizeChoices.values(custom)
  project.setTitleStyle(0,custom.copy(size=values[values.indexOf(custom.size)]))
  assertTrue(project.undo());assertEquals(1f,project.segments.single().volume,0f);assertEquals(custom,project.segments.single().titleStyle)
  assertTrue(project.undo());assertEquals(VideoTitleStyle(),project.segments.single().titleStyle)
 }
 @Test fun savedDraftKeepsTheExactCustomChoice(){
  val values=VideoTitleSizeChoices.values(custom);val style=custom.copy(size=values[values.indexOf(custom.size)])
  val draft=VideoDraft(2000,listOf(VideoSegment(0,2000,title="Title",titleStyle=style)),0,0,0,2000)
  val restored=VideoDraftCodec.decode(VideoDraftCodec.encode(draft),2000)!!
  assertEquals(draft,restored);assertEquals(.067f,restored.segments.single().titleStyle.size,0f)
 }
}
