package app.honorable.editor

import android.util.Base64
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhotoVersionStoreTest {
    private val scope=PhotoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/photo")
    private val fingerprint="a".repeat(64)
    private fun entry(name:String="First")=PhotoSavedVersion.create(scope,name,fingerprint,PhotoEdit(exposure=.25f),emptyList())
    private class Record:PhotoVersionRecord {
        var data:ByteArray?=null;var writes=0;var failRead=false;var failWrite=false;var failAfterCommit=false
        override fun read():ByteArray?{if(failRead)throw IOException("Synthetic read failure");return data?.copyOf()}
        override fun write(bytes:ByteArray){if(failWrite)throw IOException("Synthetic write failure");data=bytes.copyOf();writes++;if(failAfterCommit)failRead=true}
    }
    @Test fun saveIsNewOnlyAndDuplicateNamesNeverOverwrite(){
        val record=Record();val store=PhotoVersionStore(scope,record);assertEquals(PhotoVersionCatalog(),store.load());val first=entry();store.save(first){}
        val bytes=record.data!!.copyOf();assertEquals(listOf(first),store.load().entries)
        assertTrue(runCatching{store.save(entry("ＦＩＲＳＴ")){} }.isFailure);assertArrayEquals(bytes,record.data)
    }
    @Test fun exactDeletionAndUndoPreserveOtherEntriesAndRefuseChangedSnapshots(){
        val record=Record();val store=PhotoVersionStore(scope,record);val first=entry();val second=entry("Second");store.save(first){};store.save(second){}
        val ticket=store.delete(first){};assertEquals(listOf(second),store.load().entries);store.undo(ticket){};assertEquals(listOf(first,second),store.load().entries)
        val changed=PhotoSavedVersion.create(scope,"Changed",fingerprint,PhotoEdit(),emptyList(),first.id)
        assertTrue(runCatching{store.delete(changed){} }.isFailure);assertTrue(runCatching{store.requireEntry(changed)}.isFailure)
        val newer=store.delete(first){};store.save(entry("Third")){};val before=record.data!!.copyOf()
        assertTrue(runCatching{store.undo(newer){} }.isFailure);assertArrayEquals(before,record.data)
    }
    @Test fun cancellationAndWriteFailureLeavePriorBytesAndDoNotPretendToSave(){
        val record=Record();val store=PhotoVersionStore(scope,record);var checks=0
        assertTrue(runCatching{store.save(entry()){checks++;check(checks<2)}}.isFailure);assertNull(record.data);assertEquals(0,record.writes)
        store.save(entry()){};val before=record.data!!.copyOf();record.failWrite=true
        assertTrue(runCatching{store.save(entry("Second")){} }.isFailure);assertArrayEquals(before,record.data)
    }
    @Test fun corruptUnreadableAndOtherScopeRecordsNeverBecomeEmptyWritableShelves(){
        val record=Record();val store=PhotoVersionStore(scope,record);record.data=byteArrayOf(1,2,3)
        assertTrue(runCatching{store.save(entry()){} }.isFailure);assertArrayEquals(byteArrayOf(1,2,3),record.data);assertEquals(0,record.writes)
        record.data=PhotoVersionCodec.encode(scope,PhotoVersionCatalog(listOf(entry())));val other=PhotoVersionStore(PhotoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/other"),record)
        assertTrue(runCatching{other.load()}.isFailure);record.failRead=true;assertTrue(runCatching{store.load()}.isFailure);assertEquals(0,record.writes)
    }
    @Test fun failedReadbackCanFollowACompletedCommitAndDoesNotBlindlyRollItBack(){
        val record=Record();val store=PhotoVersionStore(scope,record);val value=entry();record.failAfterCommit=true
        assertTrue(runCatching{store.save(value){} }.isFailure);assertNotNull(record.data);assertEquals(1,record.writes)
        record.failRead=false;assertEquals(listOf(value),store.load().entries)
    }
    @Test fun processSerializedWritersDoNotLoseConcurrentVersions(){
        val record=Record();val pool=Executors.newFixedThreadPool(4)
        try{val jobs=List(20){n->pool.submit{PhotoVersionStore(scope,record).save(entry("Version $n")){} }};jobs.forEach{it.get(10,TimeUnit.SECONDS)};assertEquals(20,PhotoVersionStore(scope,record).load().entries.size)}finally{pool.shutdownNow()}
    }
    @Test fun deletionUndoTicketCannotWriteAnotherOriginalEvenWhenBothShelvesAreEmpty(){
        val firstRecord=Record();val first=PhotoVersionStore(scope,firstRecord);val value=entry();first.save(value){};val ticket=first.delete(value){}
        val otherRecord=Record();val other=PhotoVersionStore(PhotoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/other"),otherRecord)
        assertTrue(runCatching{other.undo(ticket){} }.isFailure);assertNull(otherRecord.data);assertEquals(0,otherRecord.writes)
    }
    @Test fun nativeEnvelopeIsStrictAndMaximumCipherEnvelopeFitsPreservingReadBound(){
        val valid=PhotoVersionCodec.encode(scope,PhotoVersionCatalog(listOf(entry())));assertArrayEquals(valid,PhotoVersionRecordCodec.decode(PhotoVersionRecordCodec.encode(valid)));assertNull(PhotoVersionRecordCodec.decode(null))
        for(value in listOf(JSONObject(),JSONObject().put("photoVersions",7),JSONObject().put("photoVersions","bad!"),PhotoVersionRecordCodec.encode(valid).put("extra",true),JSONObject().put("photoVersions",Base64.encodeToString(valid,Base64.NO_WRAP)+"\n")))assertTrue(runCatching{PhotoVersionRecordCodec.decode(value)}.isFailure)
        val inner=PhotoVersionRecordCodec.encode(ByteArray(PhotoVersionCodec.MAX_BYTES)).toString().toByteArray(Charsets.UTF_8)
        val outer=JSONObject().put("iv",Base64.encodeToString(ByteArray(12),Base64.NO_WRAP)).put("data",Base64.encodeToString(ByteArray(inner.size+16),Base64.NO_WRAP)).toString().toByteArray(Charsets.UTF_8)
        assertTrue(outer.size<1_048_576)
    }
}
