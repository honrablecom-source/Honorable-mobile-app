package app.honorable.editor
import org.junit.Assert.*
import org.junit.Test
class PhotoQuickStartTest {
 @Test fun startingAdjustmentsPreserveGeometryAndAreReversible(){
  val original=PhotoEdit(rotation=90,crop=.8f,personCutout=true)
  PhotoQuickStart.entries.forEach{val result=PhotoQuickStart.apply(original,it);assertEquals(original.rotation,result.rotation);assertEquals(original.crop,result.crop);assertEquals(original.personCutout,result.personCutout);assertNotEquals(original,result)}
  assertEquals(0f,PhotoQuickStart.apply(original,PhotoQuickStart.MONOCHROME).saturation)
 }
}
