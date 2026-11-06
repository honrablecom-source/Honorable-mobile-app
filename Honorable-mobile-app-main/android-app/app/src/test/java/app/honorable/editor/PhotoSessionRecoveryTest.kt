package app.honorable.editor

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class PhotoSessionRecoveryTest {
    private val scope="a".repeat(64)
    private val edit=PhotoEdit(exposure=.25f,finish=PhotoFinish(caption=PhotoCaption("Keep text")),layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Keep object"))))
    private val snapshot=PhotoSessionSnapshot(scope,edit,listOf(PhotoEdit()),listOf(PhotoEdit(rotation=90)),true,2,edit.copy(crop=.8f))
    @Test fun validRecoveryRetainsWholeSnapshotAndOriginalEncoding(){
        val bytes=PhotoSessionCodec.encode(snapshot);val pending=PhotoSessionRecovery(bytes)
        assertEquals(snapshot,pending.decode(scope).getOrThrow());assertArrayEquals(bytes,pending.bytesForState())
    }
    @Test fun incomingAndOutgoingArraysCannotMutateRetainedRecovery(){
        val bytes=PhotoSessionCodec.encode(snapshot);val original=bytes.copyOf();val pending=PhotoSessionRecovery(bytes)
        bytes.fill(0);pending.bytesForState().fill(0)
        assertArrayEquals(original,pending.bytesForState());assertEquals(snapshot,pending.decode(scope).getOrThrow())
    }
    @Test fun damagedTruncatedFutureTrailingAndOversizedDataNeverBecomesAnEmptySession(){
        val valid=PhotoSessionCodec.encode(snapshot)
        for(bytes in listOf(byteArrayOf(),valid.copyOf(7),valid.copyOf(valid.size-1),valid.copyOf().also{it[7]=99},valid+byteArrayOf(0),ByteArray(PhotoSessionCodec.MAX_BYTES+1){7})){
            val pending=PhotoSessionRecovery(bytes);assertTrue(pending.decode(scope).isFailure);assertArrayEquals(bytes,pending.bytesForState())
            assertTrue(pending.decode(scope).isFailure);assertArrayEquals(bytes,pending.bytesForState())
        }
    }
    @Test fun unavailableOrWrongScopeKeepsBytesForCorrectLaterRetry(){
        val bytes=PhotoSessionCodec.encode(snapshot);val pending=PhotoSessionRecovery(bytes)
        assertTrue(pending.decode(null).isFailure);assertTrue(pending.decode("b".repeat(64)).isFailure)
        assertArrayEquals(bytes,pending.bytesForState());assertEquals(snapshot,pending.decode(scope).getOrThrow())
    }
    @Test fun decodedHistoryMutationDoesNotChangeTheRetainedPayloadOrNextAttempt(){
        val pending=PhotoSessionRecovery(PhotoSessionCodec.encode(snapshot));val decoded=pending.decode(scope).getOrThrow()
        (decoded.undo as MutableList).clear();(decoded.redo as MutableList).clear()
        assertEquals(snapshot,pending.decode(scope).getOrThrow())
    }
    @Test fun supportedLegacyMetadataRemainsExactUntilApplication(){
        val stream=ByteArrayOutputStream();DataOutputStream(stream).use{out->
            out.writeInt(0x48504831);out.writeInt(1);out.writeUTF(scope)
            out.writeInt(0);out.writeFloat(1f);out.writeFloat(0f);out.writeFloat(1f);out.writeFloat(1f);out.writeFloat(0f);out.writeBoolean(false)
            out.writeInt(0);out.writeInt(0);out.writeBoolean(false);out.writeInt(-1);out.writeBoolean(false)
        }
        val bytes=stream.toByteArray();val pending=PhotoSessionRecovery(bytes)
        assertEquals(PhotoSessionSnapshot(scope,PhotoEdit()),pending.decode(scope).getOrThrow());assertArrayEquals(bytes,pending.bytesForState())
    }
}
