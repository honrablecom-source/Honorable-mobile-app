package app.honorable.editor

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class PhotoProjectCodecTest {
    private val namespace=PhotoPresetNamespace.GUEST
    private val source="content://synthetic/photo"
    private val fingerprint=PhotoProjectCodec.fingerprint(ByteArrayInputStream("synthetic photo bytes".toByteArray()))
    private val edit=PhotoEdit(exposure=.5f,layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Editable project"))))
    @Test fun savedProjectRetainsLayerAndAdjustmentStateWithoutRawSourceOrOwner(){
        val bytes=PhotoProjectCodec.encode(namespace,source,fingerprint,edit)
        assertEquals(edit,PhotoProjectCodec.decode(bytes,namespace,source,fingerprint))
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains(source))
        assertTrue(PhotoProjectCodec.storageName(namespace,source).matches(Regex("[0-9a-f]{64}\\.bin")))
    }
    @Test fun sourceOwnerAndOriginalChangesRejectRecovery(){
        val bytes=PhotoProjectCodec.encode(namespace,source,fingerprint,edit)
        assertTrue(runCatching{PhotoProjectCodec.decode(bytes,namespace,"content://other/photo",fingerprint)}.isFailure)
        assertTrue(runCatching{PhotoProjectCodec.decode(bytes,PhotoPresetNamespace.account("owner","https://example.test"),source,fingerprint)}.isFailure)
        assertTrue(runCatching{PhotoProjectCodec.decode(bytes,namespace,source,"0".repeat(64))}.isFailure)
        assertNotEquals(PhotoProjectCodec.storageName(namespace,source),PhotoProjectCodec.storageName(PhotoPresetNamespace.account("owner","https://example.test"),source))
    }
    @Test fun corruptTruncatedFutureAndOversizedProjectsFailClosed(){
        val bytes=PhotoProjectCodec.encode(namespace,source,fingerprint,edit)
        assertTrue(runCatching{PhotoProjectCodec.decode(bytes.copyOf(bytes.size-1),namespace,source,fingerprint)}.isFailure)
        assertTrue(runCatching{PhotoProjectCodec.decode(bytes.clone().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()},namespace,source,fingerprint)}.isFailure)
        assertTrue(runCatching{PhotoProjectCodec.decode(bytes.clone().apply{this[7]=2},namespace,source,fingerprint)}.isFailure)
        assertTrue(runCatching{PhotoProjectCodec.decode(ByteArray(PhotoProjectCodec.MAX_BYTES+1),namespace,source,fingerprint)}.isFailure)
        assertTrue(runCatching{PhotoProjectCodec.storageName("../owner",source)}.isFailure)
        assertTrue(runCatching{PhotoProjectCodec.storageName(namespace,"file:///private")}.isFailure)
    }
    @Test fun richObjectProjectsStayInsideTheBoundedEnvelope(){
        val rich=edit.copy(layers=PhotoLayers((0 until 32).map{PhotoObject("text$it",PhotoObjectType.TEXT,text="界".repeat(200))}))
        val bytes=PhotoProjectCodec.encode(namespace,source,fingerprint,rich)
        assertTrue(bytes.size<=PhotoProjectCodec.MAX_BYTES);assertEquals(rich,PhotoProjectCodec.decode(bytes,namespace,source,fingerprint))
    }
    @Test fun hashingHandlesZeroLengthReadsAndCancellationWithoutAcceptingEmptySources(){
        val unusual=object:InputStream(){var emitted=false;override fun read()=if(emitted)-1 else 65.also{emitted=true};override fun read(buffer:ByteArray,offset:Int,length:Int)=if(emitted)-1 else 0}
        assertEquals(PhotoProjectCodec.fingerprint(ByteArrayInputStream(byteArrayOf(65))),PhotoProjectCodec.fingerprint(unusual))
        assertTrue(runCatching{PhotoProjectCodec.fingerprint(ByteArrayInputStream(ByteArray(0)))}.isFailure)
        assertTrue(runCatching{PhotoProjectCodec.fingerprint(ByteArrayInputStream(byteArrayOf(1))){error("Editor changed")}}.isFailure)
    }
    @Test fun failedAccessTransactionsRollBackOnlyNewPermissions(){
        var granted=false;var released=0
        assertTrue(runCatching{PhotoProjectAccess.withDurableRead({granted},{granted=true},{released++;granted=false},{}){error("Save failed")}}.isFailure)
        assertFalse(granted);assertEquals(1,released)
        granted=true
        assertTrue(runCatching{PhotoProjectAccess.withDurableRead({granted},{error("Already granted")},{released++},{}){error("Save failed")}}.isFailure)
        assertTrue(granted);assertEquals(1,released)
        assertEquals("saved",PhotoProjectAccess.withDurableRead({granted},{},{released++},{}){"saved"})
        assertEquals(1,released)
    }
}
