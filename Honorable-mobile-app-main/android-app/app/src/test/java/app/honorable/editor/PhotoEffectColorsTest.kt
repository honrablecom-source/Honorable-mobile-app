package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PhotoEffectColorsTest{
 @Test fun copiedPaletteHexAndLegacyBareHexBothWork(){
  assertEquals(0xff18324a.toInt(),PhotoEffectColors.parse("#18324A"))
  assertEquals(0xff18324a.toInt(),PhotoEffectColors.parse("18324a"))
  assertEquals(0xffabcdef.toInt(),PhotoEffectColors.parse(" #aBcDeF "))
 }
 @Test fun invalidOrAlphaBearingInputIsRefused(){
  for(text in listOf("","#","##123456","#12345","#1234567","#80123456","red","#GG0000","#12 3456"))assertTrue("Reject $text",runCatching{PhotoEffectColors.parse(text)}.isFailure)
 }
 @Test fun displayedColorsRoundTripIndependentlyOfLocale(){
  val previous=Locale.getDefault()
  try{Locale.setDefault(Locale.forLanguageTag("tr-TR"));for(color in listOf(0xff000000.toInt(),-1,0xffabcdef.toInt(),0xff00340a.toInt())){
   val text=PhotoEffectColors.format(color);assertTrue(text.matches(Regex("#[0-9A-F]{6}")));assertEquals(color,PhotoEffectColors.parse(text))
  }}finally{Locale.setDefault(previous)}
 }
 @Test fun pastedEndpointsUseExistingEffectKernelAndSavedLookCodec(){
  val effects=PhotoEffects(duotone=1f,dark=PhotoEffectColors.parse("#18324A"),light=PhotoEffectColors.parse("FFD090"))
  val pixels=intArrayOf(0xff000000.toInt(),-1,0x80112233.toInt())
  PhotoEffectsKernel.applyInPlace(pixels,3,1,effects)
  assertEquals(effects.dark,pixels[0]);assertEquals(effects.light,pixels[1]);assertEquals(128,pixels[2] ushr 24)
  val edit=PhotoEdit(effects=effects);val scope=PhotoSessionCodec.scope("test","local","content://synthetic/colors")
  val session=PhotoSessionSnapshot(scope,edit);assertEquals(session,PhotoSessionCodec.decode(PhotoSessionCodec.encode(session),scope))
  val preset=PhotoAdjustmentPreset.capture(edit);assertEquals(effects,preset.applyTo(PhotoEdit()).effects)
 }
}
