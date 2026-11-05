package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.nio.ByteBuffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class PhotoMonochromeTest{
 private val scope=PhotoSessionCodec.scope("owner","https://example.test","content://photo/source")
 private val mix=PhotoMonochrome(true,.8f,.15f,.05f)
 private fun legacy(name:String)=Base64.getDecoder().decode(when(name){
  "session1"->"SFBIMQAAAAEAQGM3NmY4NTdjNTk5NzhmZjc4ZTRkMzY0ZDU2MDk0OWVhNTY0Y2M4NzQzNzQyYjUwNmI3MWZiM2ExZjFhYTgyOWYAAAAAP4AAAAAAAAA/gAAAP4AAAAAAAAAAAAAAAAAAAAAA/////wA="
  "session11"->"SFBIMQAAAAsAQGM3NmY4NTdjNTk5NzhmZjc4ZTRkMzY0ZDU2MDk0OWVhNTY0Y2M4NzQzNzQyYjUwNmI3MWZiM2ExZjFhYTgyOWYAAAAAP4AAAAAAAAA/gAAAP4AAAAAAAAAAPoAAAD8AAAA/QAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAC/gAAAAAAAAAAAAAAAAAAAAAAAAAC/gAAAAAAAAAAAAAD/FCQ6///cmT+AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/wAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAPkzMzQEAPhmZmgAAAAAAAAAAAULwAABB8AAAPpmZmkHwAAA/gAAAAAAAAAAAAAAAAAAAAP////8A"
  "preset1"->"SFBQMQAAAAEAAAABAAAABVBsYWluAAAAAD+AAAA/gAAAAAAAAD6AAAA/AAAAP0AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
  "preset4"->"SFBQMQAAAAQAAAABAAAACVNlbGVjdGl2ZQAAAAA/gAAAP4AAAAAAAAA+gAAAPwAAAD9AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAL+AAAAAAAAAAAAAAAAAAAAAAAAAAL+AAAAAAAAAAAAAAP8UJDr//9yZP4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABQvAAAEHwAAA+mZmaQfAAAD+AAAAAAAAA"
  else->error("Unknown fixture")
 })
 @Test fun disabledMixIsBitExactIncludingTransparentHiddenColors(){
  val kernel=PhotoMonochromeKernel(PhotoMonochrome(red=1f,green=0f,blue=0f))
  val random=java.util.Random(91)
  repeat(4096){val pixel=random.nextInt();assertEquals(pixel,kernel.apply(pixel))}
  assertEquals(0x00123456,PhotoMonochromeKernel(mix).apply(0x00123456))
 }
 @Test fun primaryColorsHaveKnownLuminanceAndChannelsCanBeIsolated(){
  val normal=PhotoMonochromeKernel(PhotoMonochrome(enabled=true))
  assertEquals(0xff363636.toInt(),normal.apply(0xffff0000.toInt()))
  assertEquals(0xffb6b6b6.toInt(),normal.apply(0xff00ff00.toInt()))
  assertEquals(0xff121212.toInt(),normal.apply(0xff0000ff.toInt()))
  val green=PhotoMonochromeKernel(PhotoMonochrome(true,0f,1f,0f))
  assertEquals(0xffffffff.toInt(),green.apply(0xff00ff00.toInt()))
  assertEquals(0xff000000.toInt(),green.apply(0xffff00ff.toInt()))
 }
 @Test fun normalizationPreservesGreysWhiteAlphaAndCommonWeightScaling(){
  val a=PhotoMonochromeKernel(PhotoMonochrome(true,.2f,.3f,.5f))
  val b=PhotoMonochromeKernel(PhotoMonochrome(true,.1f,.15f,.25f))
  for(gray in 0..255){val p=(127 shl 24)or(gray shl 16)or(gray shl 8)or gray;assertEquals(p,a.apply(p));assertEquals(p,b.apply(p))}
  val random=java.util.Random(3)
  repeat(4096){val pixel=random.nextInt();assertEquals(a.apply(pixel),b.apply(pixel));assertEquals(pixel ushr 24,a.apply(pixel)ushr 24)}
 }
 @Test fun allZeroWeightsProduceBlackWithoutChangingAlphaOrHiddenTransparentRgb(){
  val kernel=PhotoMonochromeKernel(PhotoMonochrome(true,0f,0f,0f))
  assertEquals(0x7f000000,kernel.apply(0x7fabcdef));assertEquals(0x00123456,kernel.apply(0x00123456))
  assertEquals(0xffffffff.toInt(),PhotoMonochromeKernel(PhotoMonochrome(true,1f,1f,1f)).apply(0xffffffff.toInt()))
 }
 @Test fun invalidWeightsAndCorruptPayloadsFailClosed(){
  for(value in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-.001f,1.001f)){
   assertTrue(runCatching{mix.copy(red=value)}.isFailure);assertTrue(runCatching{mix.copy(green=value)}.isFailure);assertTrue(runCatching{mix.copy(blue=value)}.isFailure)
  }
  val raw=ByteArrayOutputStream().also{DataOutputStream(it).use{out->out.writeBoolean(true);out.writeFloat(Float.NaN);out.writeFloat(0f);out.writeFloat(1f)}}.toByteArray()
  assertTrue(runCatching{PhotoMonochrome.read(DataInputStream(ByteArrayInputStream(raw)))}.isFailure)
  val state=PhotoSessionSnapshot(scope,PhotoEdit(monochrome=mix));val bytes=PhotoSessionCodec.encode(state)
  assertTrue(runCatching{PhotoSessionCodec.decode(bytes.copyOf(bytes.size-1),scope)}.isFailure)
  assertTrue(runCatching{PhotoSessionCodec.decode(bytes+byteArrayOf(0),scope)}.isFailure)
 }
 @Test fun versionTwelveKeepsCurrentUndoRedoPendingAndDisabledCustomSettings(){
  val edit=PhotoEdit(monochrome=mix)
  val disabled=PhotoEdit(monochrome=mix.copy(enabled=false))
  val state=PhotoSessionSnapshot(scope,edit,listOf(PhotoEdit(),disabled),listOf(edit),pendingExport=disabled)
  val bytes=PhotoSessionCodec.encode(state);assertEquals(12,ByteBuffer.wrap(bytes).getInt(4))
  assertEquals(state,PhotoSessionCodec.decode(bytes,scope))
  val historyOnly=PhotoSessionSnapshot(scope,PhotoEdit(),listOf(edit))
  val historyBytes=PhotoSessionCodec.encode(historyOnly);assertEquals(12,ByteBuffer.wrap(historyBytes).getInt(4));assertEquals(historyOnly,PhotoSessionCodec.decode(historyBytes,scope))
  assertEquals(disabled,PhotoSessionCodec.decode(PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,disabled)),scope).edit)
  val rich=edit.copy(layers=PhotoLayers(List(32){PhotoObject("text$it",PhotoObjectType.TEXT,text="界".repeat(200))}))
  val recovery=PhotoSessionSnapshot(scope,rich,List(12){rich},List(12){rich},pendingExport=rich)
  val recoveredBytes=PhotoSessionCodec.encodeForRecovery(recovery);val recovered=PhotoSessionCodec.decode(recoveredBytes,scope)
  assertTrue(recoveredBytes.size<=PhotoSessionCodec.MAX_BYTES);assertEquals(rich,recovered.edit);assertEquals(rich,recovered.pendingExport)
  assertTrue(recovered.undo.size+recovered.redo.size<24)
 }
 @Test fun existingSessionAndPresetBytesRemainExactlyCompatible(){
  for(name in listOf("session1","session11")){
   val bytes=legacy(name);val decoded=PhotoSessionCodec.decode(bytes,scope)
   assertEquals(PhotoMonochrome(),decoded.edit.monochrome);assertArrayEquals(bytes,PhotoSessionCodec.encode(decoded))
  }
  for(name in listOf("preset1","preset4")){
   val bytes=legacy(name);val decoded=PhotoPresetCodec.decode(bytes)
   assertTrue(decoded.entries.all{it.adjustments.monochrome==PhotoMonochrome()});assertArrayEquals(bytes,PhotoPresetCodec.encode(decoded))
  }
 }
 @Test fun namedLookVersionFiveRetainsMixWithoutReplacingFramingOrObjects(){
  val adjustment=PhotoAdjustmentPreset.capture(PhotoEdit(exposure=.5f,monochrome=mix))
  val catalog=PhotoPresetCatalog(listOf(NamedPhotoPreset("Custom mono",adjustment),NamedPhotoPreset("Off remembered",adjustment.copy(monochrome=mix.copy(enabled=false)))))
  val bytes=PhotoPresetCodec.encode(catalog);assertEquals(5,ByteBuffer.wrap(bytes).getInt(4));assertEquals(catalog,PhotoPresetCodec.decode(bytes))
  val target=PhotoEdit(rotation=90,crop=.8f,layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Keep color",color=0xffff0000.toInt()))))
  val result=adjustment.applyTo(target)
  assertEquals(mix,result.monochrome);assertEquals(target.rotation,result.rotation);assertEquals(target.crop,result.crop);assertEquals(target.layers,result.layers)
 }
 @Test fun savedImageAndCreatedCanvasWrappersKeepMixAndProtectedOriginalReferences(){
  val edit=PhotoEdit(monochrome=mix)
  val source="content://fixture/original";val fingerprint="a".repeat(64)
  val bytes=PhotoProjectCodec.encode(PhotoPresetNamespace.GUEST,source,fingerprint,edit)
  assertEquals(edit,PhotoProjectCodec.decode(bytes,PhotoPresetNamespace.GUEST,source,fingerprint))
  val id="12345678-1234-1234-1234-123456789abc"
  val canvas=CanvasProject(id,"Mono composition",fingerprint,edit,listOf(source))
  assertEquals(canvas,CanvasProjectCodec.decode(CanvasProjectCodec.encode(canvas),id))
  assertEquals(listOf(source),canvas.protectedOriginals)
 }
}
