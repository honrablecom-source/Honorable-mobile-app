package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoObjectColorInputTest {
 @Test fun standardPaletteCodesAndPlainDigitsAreEquivalent(){
  for(code in listOf("000000","FFFFFF","123456","abcdef","aBcDeF"))assertEquals(PhotoObjectColorInput.parse(code,0xff000000.toInt()),PhotoObjectColorInput.parse("#$code",0xff000000.toInt()))
  assertEquals(0xffabcdef.toInt(),PhotoObjectColorInput.parse("#aBcDeF",0xff000000.toInt()))
 }
 @Test fun outerWhitespaceIsAcceptedWithoutChangingColor(){
  assertEquals(0x7f123456,PhotoObjectColorInput.parse(" \t#123456\n",0x7f000000))
  assertEquals(0x00123456,PhotoObjectColorInput.parse(" 123456 ",0x00000000))
 }
 @Test fun existingAlphaIsPreservedForEveryPossibleValue(){
  for(alpha in 0..255)assertEquals((alpha shl 24)or 0x123456,PhotoObjectColorInput.parse("#123456",(alpha shl 24)or 0xabcdef))
 }
 @Test fun malformedAndAmbiguousColorsAreRejected(){
  for(value in listOf("","#","12345","1234567","12345678","#12345678","##123456","0x123456","#12 3456","red","#GG0000","１２３４５６","#123456\n#654321","123456 extra","#fff"))assertNull("Must reject $value",PhotoObjectColorInput.parse(value,0xff000000.toInt()))
 }
 @Test fun everyExtractedPaletteCodeRoundTripsToItsRgb(){
  val palette=PhotoPaletteAccumulator().apply{add(intArrayOf(0xff123456.toInt(),0xffabcdef.toInt(),0x7f00ff00))}.snapshot()
  for(swatch in palette.swatches)assertEquals(swatch.color,PhotoObjectColorInput.parse(swatch.hex(),0xff000000.toInt()))
  assertNull(PhotoObjectColorInput.parse(palette.hexText(),0xff000000.toInt()))
 }
}
