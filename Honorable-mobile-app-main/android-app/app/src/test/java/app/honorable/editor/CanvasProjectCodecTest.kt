package app.honorable.editor
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
class CanvasProjectCodecTest {
 private val id="12345678-1234-1234-1234-123456789abc"
 private val edit=PhotoEdit(layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="After restart"),PhotoObject("shape",PhotoObjectType.ELLIPSE,opacity=.4f))))
 private fun project()=CanvasProject(id,"Local poster","a".repeat(64),edit)
 @Test fun durableCanvasMetadataRetainsIndependentObjects(){val value=project();assertEquals(value,CanvasProjectCodec.decode(CanvasProjectCodec.encode(value),id))}
 @Test fun identityDigestAndSchemaChangesAreRejected(){val bytes=CanvasProjectCodec.encode(project());assertTrue(runCatching{CanvasProjectCodec.decode(bytes,"87654321-1234-1234-1234-123456789abc")}.isFailure);assertTrue(runCatching{CanvasProjectCodec.decode(bytes.clone().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()},id)}.isFailure);assertTrue(runCatching{CanvasProjectCodec.decode(bytes.copyOf(bytes.size-1),id)}.isFailure);assertTrue(runCatching{CanvasProjectCodec.decode(bytes.clone().apply{this[7]=127},id)}.isFailure)}
 @Test fun namesAreLabelsAndCannotChangeProjectIdentity(){val original=project();val renamed=original.copy(name="../a label");assertEquals(original.id,CanvasProjectCodec.decode(CanvasProjectCodec.encode(renamed),id).id);for(name in listOf("", "bad\nname", "x".repeat(41)))assertTrue(runCatching{original.copy(name=name)}.isFailure);assertTrue(runCatching{original.copy(id="../private")}.isFailure)}
 @Test fun boundedRichLayerStateSurvivesCanvasRestartEnvelope(){val rich=project().copy(edit=PhotoEdit(layers=PhotoLayers((0 until 32).map{PhotoObject("object$it",PhotoObjectType.TEXT,text="界".repeat(200))})));val bytes=CanvasProjectCodec.encode(rich);assertTrue(bytes.size<=CanvasProjectCodec.MAX_BYTES);assertEquals(rich,CanvasProjectCodec.decode(bytes,id))}
 private fun privateEnvelope(origins:List<ByteArray>):ByteArray {
  val session=PhotoSessionCodec.encode(PhotoSessionSnapshot(PhotoSessionCodec.scope("guest-created-canvas","local-only",id),edit))
  val body=ByteArrayOutputStream().use{bytes->DataOutputStream(bytes).use{out->
   out.writeUTF(id);out.writeUTF("Local poster");out.writeUTF("a".repeat(64));out.writeInt(origins.size)
   origins.forEach{out.writeInt(it.size);out.write(it)};out.writeInt(session.size);out.write(session)
  };bytes.toByteArray()}
  return ByteArrayOutputStream().use{bytes->DataOutputStream(bytes).use{out->out.writeInt(0x4E43414E);out.writeInt(2);out.writeInt(body.size);out.write(MessageDigest.getInstance("SHA-256").digest(body));out.write(body)};bytes.toByteArray()}
 }
 @Test fun privateOriginalsRoundTripAndTheirMetadataIsChecksumCovered(){
  val uri="content://fixture/original-photo"
  val value=project().copy(protectedOriginals=listOf(uri,"content://fixture/original-video"))
  val bytes=CanvasProjectCodec.encode(value)
  assertEquals(2,ByteBuffer.wrap(bytes).getInt(4));assertEquals(value,CanvasProjectCodec.decode(bytes,id))
  val needle=uri.toByteArray();val at=(0..bytes.size-needle.size).first{index->needle.indices.all{bytes[index+it]==needle[it]}}
  val damaged=bytes.copyOf();damaged[at+needle.lastIndex]=(damaged[at+needle.lastIndex].toInt() xor 1).toByte()
  assertTrue(runCatching{CanvasProjectCodec.decode(damaged,id)}.isFailure)
  assertTrue(runCatching{CanvasProjectCodec.decode(bytes.copyOf(bytes.size-1),id)}.isFailure)
  assertTrue(runCatching{CanvasProjectCodec.decode(bytes+byteArrayOf(0),id)}.isFailure)
  assertTrue(runCatching{CanvasProjectCodec.decode(bytes.copyOf().also{ByteBuffer.wrap(it).putInt(8,Int.MAX_VALUE)},id)}.isFailure)
 }
 @Test fun originalsAreDefensiveValidatedAndRetainedByNameAndEditCopies(){
  val input=mutableListOf("content://fixture/photo","content://fixture/video")
  val value=project().copy(protectedOriginals=input);input.clear()
  assertEquals(2,value.protectedOriginals.size);assertTrue(runCatching{(value.protectedOriginals as MutableList).clear()}.isFailure)
  assertEquals(value.protectedOriginals,value.copy(name="Renamed").protectedOriginals)
  assertEquals(value.protectedOriginals,value.copy(edit=PhotoEdit()).protectedOriginals)
  for(origins in listOf(listOf("file:///private/photo"),listOf("content://fixture/photo#fragment"),listOf("content://fixture/"+"x".repeat(2048)),List(17){"content://fixture/$it"}))assertTrue(runCatching{project().copy(protectedOriginals=origins)}.isFailure)
 }
 @Test fun maximumOriginalUriBudgetAndRichEditsRemainBoundedAndRecoverable(){
  val originals=List(16){index->val prefix="content://fixture/$index/";prefix+"x".repeat(2048-prefix.length)}
  val value=project().copy(protectedOriginals=originals,edit=PhotoEdit(layers=PhotoLayers((0 until 32).map{PhotoObject("object$it",PhotoObjectType.TEXT,text="界".repeat(200))})))
  val bytes=CanvasProjectCodec.encode(value);assertTrue(bytes.size<=CanvasProjectCodec.MAX_BYTES)
  assertEquals(value,CanvasProjectCodec.decode(bytes,id));assertEquals(originals,CanvasProjectCodec.decode(bytes,id).protectedOriginals)
 }
 @Test fun validChecksumsCannotMakeInvalidOriginalMetadataAcceptable(){
  val uri="content://fixture/original".toByteArray()
  for(origins in listOf(emptyList(),listOf(uri,uri),List(17){"content://fixture/$it".toByteArray()},listOf("file:///not-content".toByteArray()),listOf("content://fixture/photo#fragment".toByteArray()),listOf(byteArrayOf(0xC3.toByte())),listOf(ByteArray(2049){'x'.code.toByte()})))assertTrue(runCatching{CanvasProjectCodec.decode(privateEnvelope(origins),id)}.isFailure)
 }
 @Test fun actualPreChangeVersionOneFixtureStillDecodesAndKeepsItsEncoding(){
  val bytes=java.util.Base64.getDecoder().decode("TkNBTgAAAAEAJDEyMzQ1Njc4LTEyMzQtMTIzNC0xMjM0LTEyMzQ1Njc4OWFiYwANTGVnYWN5IHNvdXJjZQBAYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYgAAAHGy53zMBaqbadFTCdsBzysF1c+xyrbpwlwv2uENrUWbD0hQSDEAAAABAEA5Y2M4MGJlMzk3OWQxMmU4NzYxN2VkZTJkNjIxNTYwM2Q1OGNmY2JjYmE1MThkNDIxYzk0ZDczMTk2ZDdkYmE2AAAAAD+AAAAAAAAAP4AAAD+AAAAAAAAAAAAAAAAAAAAAAP////8A")
  val expected=CanvasProject(id,"Legacy source","b".repeat(64),PhotoEdit())
  val decoded=CanvasProjectCodec.decode(bytes,id)
  assertEquals(expected,decoded);assertTrue(decoded.protectedOriginals.isEmpty())
  assertEquals(1,ByteBuffer.wrap(bytes).getInt(4));assertArrayEquals(bytes,CanvasProjectCodec.encode(decoded))
 }
}
