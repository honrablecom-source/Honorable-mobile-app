package com.honorablemobile

import app.honorable.editor.PhotoPresetNamespace
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors

class VideoProjectVersionStoreTest {
    private class Record:VideoVersionRecord {
        var bytes:ByteArray?=null;var writes=0;var failWrite=false;var failVerification=false
        override fun read():ByteArray? { if(failVerification){failVerification=false;throw IOException("Synthetic read failure")};return bytes?.copyOf() }
        override fun write(bytes:ByteArray){writes++;if(failWrite)throw IOException("Synthetic disk failure");this.bytes=bytes.copyOf()}
    }
    private val scope=VideoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/primary")
    private fun entry(name:String)=VideoProjectVersion.create(name,VideoDraft(8000,listOf(VideoSegment(0,8000,title=name)),0,0,0,8000))
    private fun store(record:Record,scope:VideoVersionScope=this.scope)=VideoProjectVersionStore(scope,8000,record)
    private fun reject(block:()->Unit){try{block();fail("Expected refusal")}catch(_:Exception){}}
    @Test fun saveReloadDeleteUndoAndStaleChoicePreserveIndependentVersions() {
        val record=Record();val store=store(record);val a=entry("A");val b=entry("B")
        store.save(a){};store.save(b){};assertEquals(listOf(a,b),store(record).load().entries)
        val deletion=store.delete(a){};assertEquals(listOf(b),store.load().entries)
        store.undo(deletion){};assertEquals(listOf(a,b),store.load().entries)
        val removed=store.delete(a){};store.save(entry("C")){};reject{store.undo(removed){}}
        reject{store.requireEntry(a)};assertEquals(b,store.requireEntry(b));assertEquals(listOf("B","C"),store.load().entries.map{it.name})
    }
    @Test fun cancellationBeforeCommitAndWriteFailureKeepTheEarlierCatalog() {
        val record=Record();val store=store(record);store.save(entry("Before")){};val before=record.bytes!!.copyOf();var calls=0
        reject{store.save(entry("Cancel")){calls++;check(calls<2)}};assertEquals(2,calls);assertArrayEquals(before,record.bytes)
        record.failWrite=true;reject{store.save(entry("Fail")){}};assertArrayEquals(before,record.bytes)
    }
    @Test fun corruptOrWrongScopeDataCannotBeTurnedIntoAnEmptyShelfOrOverwritten() {
        val record=Record();record.bytes=byteArrayOf(1,2,3);val before=record.bytes!!.copyOf()
        reject{store(record).load()};reject{store(record).save(entry("New")){}};assertArrayEquals(before,record.bytes);assertEquals(0,record.writes)
        record.bytes=null;store(record).save(entry("Owned")){};val saved=record.bytes!!.copyOf();val other=VideoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/other")
        reject{store(record,other).load()};reject{store(record,other).save(entry("Wrong")){}};assertArrayEquals(saved,record.bytes)
    }
    @Test fun deletionUndoCannotCrossOriginalOrOwner() {
        val a=Record();val first=store(a);val version=entry("A");first.save(version){};val deletion=first.delete(version){}
        val b=Record();val other=store(b,VideoVersionScope(PhotoPresetNamespace.account("other","https://example.invalid"),"content://synthetic/primary"))
        reject{other.undo(deletion){}};assertTrue(other.load().entries.isEmpty());assertEquals(0,b.writes)
    }
    @Test fun concurrentStoreInstancesSerializeReadModifyWriteWithoutLostVersions() {
        val record=Record();val executor=Executors.newFixedThreadPool(4)
        try{(0 until 20).map{i->executor.submit{store(record).save(entry("Version $i")){}}}.forEach{it.get()}}
        finally{executor.shutdownNow()}
        assertEquals(20,store(record).load().entries.size)
    }
    @Test fun verificationFailureDoesNotClaimSuccessOrBlindlyRollbackCommittedData() {
        val record=object:VideoVersionRecord {
            var bytes:ByteArray?=null;var verify=false
            override fun read():ByteArray?{if(verify){verify=false;throw IOException("Synthetic verify failure")};return bytes}
            override fun write(bytes:ByteArray){this.bytes=bytes.copyOf();verify=true}
        }
        val store=VideoProjectVersionStore(scope,8000,record);val version=entry("Possibly committed")
        reject{store.save(version){}};assertEquals(listOf(version),store.load().entries)
    }
    @Test fun productionJsonBase64EnvelopeIsStrictAndFitsPreservingEncryptedReadBound() {
        assertNull(VideoVersionRecordCodec.decode(null))
        val bytes=VideoProjectVersionCodec.encode(scope,VideoProjectVersions(8000,listOf(entry("A"))))
        val json=VideoVersionRecordCodec.encode(bytes);val before=json.toString();assertArrayEquals(bytes,VideoVersionRecordCodec.decode(json));assertEquals(before,json.toString())
        for(value in listOf<Any>(JSONObject.NULL,true,12,"","!invalid!","A".repeat(720001)))reject{VideoVersionRecordCodec.decode(JSONObject().put("versions",value))}
        reject{VideoVersionRecordCodec.decode(JSONObject())}
        val maximum=VideoVersionRecordCodec.encode(ByteArray(VideoProjectVersionCodec.MAX_BYTES)).toString().toByteArray().size
        val encryptedBase64Bound=((maximum+64L+2)/3)*4+128
        assertTrue(encryptedBase64Bound<1_048_576)
    }
}
