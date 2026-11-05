package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoSelectiveColorTest{
 private fun adjustment()=PhotoSelectiveColor(enabled=true,hue=0f,radius=20f,softness=0f,shift=120f)
 @Test fun adjustsSelectedHueAndKeepsOtherColorsNeutralsAndAlpha(){
  val kernel=PhotoSelectiveColorKernel(adjustment())
  assertEquals(0x7f00ff00,kernel.apply(0x7fff0000));assertEquals(0xff0000ff.toInt(),kernel.apply(0xff0000ff.toInt()));assertEquals(0xff777777.toInt(),kernel.apply(0xff777777.toInt()))
  assertEquals(0x00123456,kernel.apply(0x00123456));assertEquals(0xffff0000.toInt(),PhotoSelectiveColorKernel(PhotoSelectiveColor()).apply(0xffff0000.toInt()))
 }
 @Test fun circularSelectionWrapAndSoftEdges(){
  val red=0xffff0000.toInt();assertEquals(0xff00ff00.toInt(),PhotoSelectiveColorKernel(adjustment().copy(hue=360f)).apply(red))
  val near=0xffff3300.toInt();val hard=PhotoSelectiveColorKernel(adjustment()).apply(near);val soft=PhotoSelectiveColorKernel(adjustment().copy(softness=1f)).apply(near)
  assertNotEquals(near,hard);assertNotEquals(near,soft);assertNotEquals(hard,soft)
  assertEquals(near,PhotoSelectiveColorKernel(adjustment().copy(radius=1f)).apply(near))
 }
 @Test fun saturationExposureAndInputBounds(){
  val red=0xff800000.toInt();assertEquals(0xffff0000.toInt(),PhotoSelectiveColorKernel(adjustment().copy(shift=0f,exposure=1f)).apply(red))
  assertEquals(0xff808080.toInt(),PhotoSelectiveColorKernel(adjustment().copy(shift=0f,saturation=0f)).apply(red))
  assertThrows(IllegalArgumentException::class.java){adjustment().copy(hue=Float.NaN)};assertThrows(IllegalArgumentException::class.java){adjustment().copy(radius=0f)};assertThrows(IllegalArgumentException::class.java){adjustment().copy(exposure=3f)}
 }
 @Test fun sessionElevenAndPresetFourPreserveSelectionAndExistingTargetRegions(){
  val selective=adjustment();val edit=PhotoEdit(selectiveColor=selective);val scope="b".repeat(64)
  val snapshot=PhotoSessionSnapshot(scope,edit,undo=listOf(PhotoEdit()),pendingExport=edit);val bytes=PhotoSessionCodec.encode(snapshot)
  assertEquals(11,java.io.DataInputStream(bytes.inputStream()).run{readInt();readInt()});assertEquals(snapshot,PhotoSessionCodec.decode(bytes,scope))
  val shelf=PhotoPresetCatalog(listOf(NamedPhotoPreset("Selective",PhotoAdjustmentPreset.capture(edit))));val encoded=PhotoPresetCodec.encode(shelf)
  assertEquals(4,java.io.DataInputStream(encoded.inputStream()).run{readInt();readInt()});val restored=PhotoPresetCodec.decode(encoded).entries.single().adjustments
  val target=PhotoEdit(regions=PhotoRegions(listOf(PhotoRegion("keep",PhotoRegionEffect.BLUR))))
  assertEquals(selective,restored.applyTo(target).selectiveColor);assertEquals(target.regions,restored.applyTo(target).regions)
 }
 @Test fun defaultAndLegacyPresetsRetainTheirOriginalVersions(){
  val legacy=PhotoPresetCatalog(listOf(NamedPhotoPreset("Default",PhotoAdjustmentPreset())));val bytes=PhotoPresetCodec.encode(legacy)
  assertEquals(1,java.io.DataInputStream(bytes.inputStream()).run{readInt();readInt()});assertEquals(PhotoSelectiveColor(),PhotoPresetCodec.decode(bytes).entries.single().adjustments.selectiveColor)
  val effect=PhotoPresetCatalog(listOf(NamedPhotoPreset("Effects",PhotoAdjustmentPreset(effects=PhotoEffects(negative=true)))));assertEquals(3,java.io.DataInputStream(PhotoPresetCodec.encode(effect).inputStream()).run{readInt();readInt()})
  val maximum=PhotoPresetCatalog(List(20){index->NamedPhotoPreset((0x4e00+index).toChar().toString()+"界".repeat(39),PhotoAdjustmentPreset(creative=PhotoCreative(grain=1f),effects=PhotoEffects(negative=true),selectiveColor=adjustment()))})
  val maximumBytes=PhotoPresetCodec.encode(maximum);assertTrue(maximumBytes.size<=PhotoPresetCodec.MAX_BYTES);assertEquals(maximum,PhotoPresetCodec.decode(maximumBytes))

 }
}
