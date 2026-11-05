package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.nio.ByteBuffer
import java.io.*

class PhotoAlphaGradientTest {
 private val scope=PhotoSessionCodec.scope("owner","https://example.test","content://photo/source")
 private val fade=PhotoAlphaGradient(true,45f,.1f,.9f,true)
 private fun rich()=PhotoEdit(exposure=.5f,monochrome=PhotoMonochrome(true,.7f,.2f,.1f),layers=PhotoLayers(listOf(PhotoObject("legacy",PhotoObjectType.TEXT,text="Legacy"))),regions=PhotoRegions(listOf(PhotoRegion("region",PhotoRegionEffect.GRAYSCALE))))
 @Test fun disabledFadeAndHiddenTransparentRgbAreBitExact(){
  val kernel=PhotoAlphaGradientKernel(PhotoAlphaGradient(),17,13);val random=java.util.Random(31)
  repeat(4096){index->val pixel=random.nextInt();assertEquals(pixel,kernel.apply(pixel,index%17,index/17%13))}
  assertEquals(0x00123456,PhotoAlphaGradientKernel(fade,1,1).apply(0x00123456,0,0))
 }
 @Test fun horizontalStopsAndReverseHaveKnownCoverageWithoutRecoloring(){
  for(reverse in listOf(false,true)){
   val kernel=PhotoAlphaGradientKernel(PhotoAlphaGradient(true,0f,.125f,.875f,reverse),4,1)
   val expected=if(reverse)listOf(255,189,66,0)else listOf(0,66,189,255)
   for(x in 0..3){val result=kernel.apply(0xff123456.toInt(),x,0);assertEquals(expected[x],result ushr 24);assertEquals(0x123456,result and 0xffffff)}
  }
 }
 @Test fun cardinalDirectionsFollowTheFinishedPhotoAxes(){
  val directions=listOf(90f to listOf(0,66,189,255),270f to listOf(255,189,66,0))
  for((angle,expected)in directions){val k=PhotoAlphaGradientKernel(PhotoAlphaGradient(true,angle,.125f,.875f),1,4);for(y in 0..3)assertEquals(expected[y],k.apply(-1,0,y) ushr 24)}
  val k=PhotoAlphaGradientKernel(PhotoAlphaGradient(true,180f,.125f,.875f),4,1);assertEquals(255,k.apply(-1,0,0) ushr 24);assertEquals(0,k.apply(-1,3,0) ushr 24)
 }
 @Test fun singletonUsesCenterCoverageAndMultipliesExistingAlpha(){
  assertEquals(0x40112233,PhotoAlphaGradientKernel(PhotoAlphaGradient(true,37f,.25f,.75f),1,1).apply(0x80112233.toInt(),0,0))
 }
 @Test fun alphaNeverIncreasesAndEveryColorBitIsRetained(){
  val random=java.util.Random(81)
  for(reverse in listOf(false,true)){
   val k=PhotoAlphaGradientKernel(fade.copy(reverse=reverse),17,13)
   repeat(4096){index->val pixel=random.nextInt();val result=k.apply(pixel,index%17,index/17%13);assertEquals(pixel and 0xffffff,result and 0xffffff);assertTrue(result ushr 24<=pixel ushr 24)}
  }
 }
 @Test fun normalizedPixelCentersMatchAcrossPreviewScaleAndFullTurn(){
  for(angle in listOf(0f,45f,90f,225f,360f)){
   val small=PhotoAlphaGradientKernel(fade.copy(angle=angle),5,7);val large=PhotoAlphaGradientKernel(fade.copy(angle=angle),15,21)
   for(y in 0..6)for(x in 0..4)assertEquals(small.apply(-1,x,y),large.apply(-1,3*x+1,3*y+1))
  }
  val zero=PhotoAlphaGradientKernel(fade.copy(angle=0f),5,7);val full=PhotoAlphaGradientKernel(fade.copy(angle=360f),5,7)
  for(y in 0..6)for(x in 0..4)assertEquals(zero.apply(-1,x,y),full.apply(-1,x,y))
 }
 @Test fun invalidParametersAndCoordinatesFailClosed(){
  for(value in listOf(Float.NaN,Float.POSITIVE_INFINITY,-1f,361f))assertTrue(runCatching{PhotoAlphaGradient(angle=value)}.isFailure)
  for((start,end)in listOf(0f to 0f,.8f to .2f,-.1f to 1f,0f to 1.1f,Float.NaN to 1f,0f to Float.NaN))assertTrue(runCatching{PhotoAlphaGradient(start=start,end=end)}.isFailure)
  assertTrue(runCatching{PhotoAlphaGradientKernel(fade,0,1)}.isFailure)
  assertTrue(runCatching{PhotoAlphaGradientKernel(fade,1,1).apply(-1,1,0)}.isFailure)
 }
 @Test fun gradientScalarStorageRetainsDisabledSettingsAndRejectsMalformedFloats(){
  val value=PhotoAlphaGradient(false,153.5f,.11f,.81f,true);val bytes=ByteArrayOutputStream().apply{DataOutputStream(this).use{value.write(it)}}.toByteArray()
  assertEquals(value,PhotoAlphaGradient.read(DataInputStream(ByteArrayInputStream(bytes))))
  val malformed=bytes.clone();ByteBuffer.wrap(malformed).putFloat(1,Float.NaN)
  assertTrue(runCatching{PhotoAlphaGradient.read(DataInputStream(ByteArrayInputStream(malformed)))}.isFailure)
 }
 @Test fun richSessionHistoryAndPendingExportRetainFadeWithVersion13(){
  val edit=rich().copy(alphaGradient=fade)
  val undo=List(50){PhotoEdit(alphaGradient=fade.copy(angle=it.toFloat()))}
  val redo=List(50){PhotoEdit(alphaGradient=fade.copy(angle=(it+50).toFloat(),reverse=false))}
  val value=PhotoSessionSnapshot(scope,edit,undo,redo,true,2,edit)
  val bytes=PhotoSessionCodec.encodeForRecovery(value);assertEquals(13,ByteBuffer.wrap(bytes).getInt(4));assertEquals(value,PhotoSessionCodec.decode(bytes,scope))
 }
 @Test fun disabledSettingsHistoryAndPendingAlsoSelectVersion13(){
  val disabled=PhotoEdit(alphaGradient=fade.copy(enabled=false));val plain=PhotoSessionSnapshot(scope,PhotoEdit())
  for(value in listOf(plain.copy(edit=disabled),plain.copy(undo=listOf(disabled)),plain.copy(redo=listOf(disabled)),plain.copy(pendingExport=disabled))){
   val bytes=PhotoSessionCodec.encode(value);assertEquals(13,ByteBuffer.wrap(bytes).getInt(4));assertEquals(value,PhotoSessionCodec.decode(bytes,scope))
  }
 }
 @Test fun actualPreviousVersionFixturesDecodeAndReencodeByteExactly(){
  val v12=Base64.getDecoder().decode("SFBIMQAAAAwAQGM3NmY4NTdjNTk5NzhmZjc4ZTRkMzY0ZDU2MDk0OWVhNTY0Y2M4NzQzNzQyYjUwNmI3MWZiM2ExZjFhYTgyOWYAAAAAP4AAAAAAAAA/gAAAP4AAAD8AAAAAPoAAAD8AAAA/QAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAC/gAAAAAAAAAAAAAAAAAAAAAAAAAC/gAAAAAAAAAAAAAD/FCQ6///cmT+AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/wAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAPkzMzQEAPhmZmgAAAAEABmxlZ2FjeQAAAAI/AAAAPwAAAD6zMzM+TMzNAAAAAD+AAAD/AAAAAAZMZWdhY3k9dcKPAQAAAAAAAAAAAAAAAAAAAQAAAAAAAAAA/////wAAAAA8I9cKPCPXCpkAAAAAAAABAAZyZWdpb24AAAACAAAAAD8AAAA/AAAAPszMzT6ZmZo/AAAAPczMzQEAAAAAAEHwAAA+mZmaAAAAAD+AAAAAAAAAAT8zMzM+TMzNPczMzQAAAAEAAAAAP4AAAAAAAAA/gAAAP4AAAAAAAAAAPoAAAD8AAAA/QAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAC/gAAAAAAAAAAAAAAAAAAAAAAAAAC/gAAAAAAAAAAAAAD/FCQ6///cmT+AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/wAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAPkzMzQEAPhmZmgAAAAAAAAAAAAAAAABB8AAAPpmZmgAAAAA/gAAAAAAAAAA+WbPQPzcXWT2T3ZgAAAABAAAAAD+AAAA/gAAAP4AAAD+AAAAAAAAAAD6AAAA/AAAAP0AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAv4AAAAAAAAAAAAAAAAAAAAAAAAAAv4AAAAAAAAAAAAAA/xQkOv//3Jk/gAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP8AAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAD5MzM0BAD4ZmZoAAAAAAAAAAAAAAAAAQfAAAD6ZmZoAAAAAP4AAAAAAAAAAPlmz0D83F1k9k92YAQAAAAIBAAAAAD+AAAAAAAAAP4AAAD+AAAA/AAAAAD6AAAA/AAAAP0AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAv4AAAAAAAAAAAAAAAAAAAAAAAAAAv4AAAAAAAAAAAAAA/xQkOv//3Jk/gAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP8AAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAD5MzM0BAD4ZmZoAAAABAAZsZWdhY3kAAAACPwAAAD8AAAA+szMzPkzMzQAAAAA/gAAA/wAAAAAGTGVnYWN5PXXCjwEAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAP////8AAAAAPCPXCjwj1wqZAAAAAAAAAQAGcmVnaW9uAAAAAgAAAAA/AAAAPwAAAD7MzM0+mZmaPwAAAD3MzM0BAAAAAABB8AAAPpmZmgAAAAA/gAAAAAAAAAE/MzMzPkzMzT3MzM0=")
  val expected=PhotoSessionSnapshot(scope,rich(),listOf(PhotoEdit()),listOf(PhotoEdit(brightness=1f)),true,2,rich())
  assertEquals(expected,PhotoSessionCodec.decode(v12,scope));assertArrayEquals(v12,PhotoSessionCodec.encode(expected))
  val v1=Base64.getDecoder().decode("SFBIMQAAAAEAQGM3NmY4NTdjNTk5NzhmZjc4ZTRkMzY0ZDU2MDk0OWVhNTY0Y2M4NzQzNzQyYjUwNmI3MWZiM2ExZjFhYTgyOWYAAAAAP4AAAAAAAAA/gAAAP4AAAAAAAAAAAAAAAAAAAAAA/////wA=");val plain=PhotoSessionSnapshot(scope,PhotoEdit());assertEquals(plain,PhotoSessionCodec.decode(v1,scope));assertArrayEquals(v1,PhotoSessionCodec.encode(plain))
 }
 @Test fun photoProjectAndCanvasWrappersRetainFadeAndOriginalProvenance(){
  val edit=rich().copy(alphaGradient=fade);val source="content://fixture/photo";val digest=PhotoProjectCodec.fingerprint(ByteArrayInputStream("fixture".toByteArray()))
  val encoded=PhotoProjectCodec.encode(PhotoPresetNamespace.GUEST,source,digest,edit);assertEquals(edit,PhotoProjectCodec.decode(encoded,PhotoPresetNamespace.GUEST,source,digest))
  val project=CanvasProject("12345678-1234-1234-1234-123456789abc","Fade","a".repeat(64),edit,listOf(source));assertEquals(project,CanvasProjectCodec.decode(CanvasProjectCodec.encode(project),project.id))
 }
 @Test fun savedGlobalLooksPreserveTheTargetPhotoFadeInsteadOfTransferringIt(){
  val from=PhotoEdit(exposure=1f,alphaGradient=fade);val target=PhotoEdit(alphaGradient=fade.copy(angle=90f,reverse=false))
  val applied=PhotoAdjustmentPreset.capture(from).applyTo(target);assertEquals(1f,applied.exposure,0f);assertEquals(target.alphaGradient,applied.alphaGradient)
 }
 @Test fun sessionOwnerTruncationTrailingBytesAndFutureVersionsRemainRejected(){
  val bytes=PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,PhotoEdit(alphaGradient=fade)))
  assertTrue(runCatching{PhotoSessionCodec.decode(bytes,"a".repeat(64))}.isFailure)
  for(value in listOf(bytes.copyOf(bytes.size-1),bytes+byteArrayOf(0),bytes.clone().apply{ByteBuffer.wrap(this).putInt(4,14)}))assertTrue(runCatching{PhotoSessionCodec.decode(value,scope)}.isFailure)
 }
}
