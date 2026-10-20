package app.honorable.editor
import org.junit.Assert.*
import org.junit.Test
class PhotoSessionCodecTest {
 private val scope=PhotoSessionCodec.scope("owner","https://example.test","content://photo/1")
 private fun rejected(block:()->Unit){try{block();fail("Expected rejection")}catch(e:IllegalArgumentException){}catch(e:java.io.IOException){}}
 @Test fun roundTripPreservesHistoryAndFrozenExport(){
  val edit=PhotoEdit(90,.6f,12f,1.2f,.4f,.5f,true)
  val state=PhotoSessionSnapshot(scope,edit,listOf(PhotoEdit()),listOf(edit),true,3,edit.copy(rotation=180))
  assertEquals(state,PhotoSessionCodec.decode(PhotoSessionCodec.encode(state),scope))
 }
 @Test fun maximumHistoryFitsSmallBundle(){
  val state=PhotoSessionSnapshot(scope,PhotoEdit(),List(50){PhotoEdit()},List(50){PhotoEdit()},pendingExport=PhotoEdit())
  val bytes=PhotoSessionCodec.encode(state);assertTrue(bytes.size<=4096)
  assertEquals(state,PhotoSessionCodec.decode(bytes,scope))
 }
 @Test fun identityAndSourceChangesRejectRecovery(){
  val bytes=PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,PhotoEdit()))
  for(other in listOf(PhotoSessionCodec.scope("other","https://example.test","content://photo/1"),PhotoSessionCodec.scope("owner","https://other.test","content://photo/1"),PhotoSessionCodec.scope("owner","https://example.test","content://photo/2")))rejected{PhotoSessionCodec.decode(bytes,other)}
  assertFalse(String(bytes,Charsets.ISO_8859_1).contains("content://"))
 }
 @Test fun malformedDataIsRejected(){
  val bytes=PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,PhotoEdit()))
  for(length in 0 until bytes.size)rejected{PhotoSessionCodec.decode(bytes.copyOf(length),scope)}
  rejected{PhotoSessionCodec.decode(bytes+byteArrayOf(0),scope)}
  rejected{PhotoSessionCodec.decode(ByteArray(4097),scope)}
  rejected{PhotoSessionCodec.decode(bytes.clone().apply{this[7]=2},scope)}
  rejected{PhotoSessionCodec.decode(bytes.clone().apply{this[0]=0},scope)}
 }
 @Test fun invalidScalarsAndHistoryAreRejected(){
  rejected{PhotoEdit(crop=Float.NaN)};rejected{PhotoEdit(rotation=45)}
  rejected{PhotoEdit(exposure=3f)};rejected{PhotoEdit(contrast=-1f)}
  rejected{PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,PhotoEdit(),List(51){PhotoEdit()}))}
  rejected{PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,PhotoEdit(),activeTool=4))}
 }
}
