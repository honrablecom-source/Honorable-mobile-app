package com.honorablemobile

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class LibraryIntegrityTest {
    private val owner="synthetic-private-owner"
    private fun id(i:Int)=String.format("00000000-0000-0000-0000-%012d",i)
    private fun item(i:Int=1)=LibraryCollectionItem(i.toString(),"content://synthetic/$i","IMAGE")
    private fun report(items:List<LibraryCollectionItem>,contents:(LibraryCollectionItem)->String={"abc"})=LibraryDuplicateHash.inspect(items,{LibraryDuplicateHash.Source(ByteArrayInputStream(contents(it).toByteArray()))},minimumItems=1)
    private fun baseline(i:Int=1,name:String="Before")=LibraryIntegrityBaseline.create(name,listOf(item(i)),report(listOf(item(i))),1234,id(i),mapOf(i.toString() to "Photo $i.jpg"))
    private class Record:LibraryIntegrityRecord {
        var bytes:ByteArray?=null;var writes=0
        override fun read()=bytes?.copyOf()
        override fun write(bytes:ByteArray){this.bytes=bytes.copyOf();writes++}
    }
    @Test fun singleOriginalHashingIsExplicitAndDoesNotWeakenDuplicateDefaults(){
        assertEquals(1,report(listOf(item())).checked.size)
        assertThrows(IllegalArgumentException::class.java){LibraryDuplicateHash.inspect(listOf(item()),{LibraryDuplicateHash.Source(ByteArrayInputStream(byteArrayOf()))})}
    }
    @Test fun capturesExactBindingsHashesLabelsAndInputOrder(){
        val items=listOf(item(2),item(1));val hashes=report(items)
        val reversed=LibraryDuplicateHash.Report(hashes.checked.reversed(),emptyList(),hashes.bytesRead)
        val b=LibraryIntegrityBaseline.create("旅行 🌅",items,reversed,1000,id(1),mapOf("2" to "Clip two"))
        assertEquals(listOf("2","1"),b.entries.map{it.original.id});assertEquals("Clip two",b.entries[0].label)
        assertEquals(hashes.checked[0].sha256,b.entries[0].sha256);assertEquals(3L,b.entries[0].bytes)
    }
    @Test fun refusesPartialSkippedForeignAndDuplicateHashCoverage(){
        val items=listOf(item(1),item(2));val hash=report(items).checked
        for(r in listOf(
            LibraryDuplicateHash.Report(hash.take(1),emptyList(),3),
            LibraryDuplicateHash.Report(hash,listOf(LibraryDuplicateHash.Skipped("1",LibraryDuplicateHash.Reason.UNAVAILABLE)),6),
            LibraryDuplicateHash.Report(listOf(hash[0],hash[0]),emptyList(),6),
            LibraryDuplicateHash.Report(listOf(hash[0],hash[1].copy(id="3")),emptyList(),6))) {
            assertThrows(IllegalArgumentException::class.java){LibraryIntegrityBaseline.create("No partial",items,r,1000,id(1))}
        }
    }
    @Test fun validatesFingerprintAndLabelBounds(){
        for(bytes in listOf(-1L,LibraryDuplicateHash.FILE_LIMIT))assertThrows(IllegalArgumentException::class.java){LibraryIntegrityEntry(item(),bytes,"a".repeat(64))}
        for(hash in listOf("","a".repeat(63),"A".repeat(64),"z".repeat(64)))assertThrows(IllegalArgumentException::class.java){LibraryIntegrityEntry(item(),1,hash)}
        for(label in listOf("a".repeat(121),"bad\nlabel","\uD800"))assertThrows(IllegalArgumentException::class.java){LibraryIntegrityEntry(item(),1,"a".repeat(64),label)}
        assertEquals(0L,LibraryIntegrityEntry(item(),0,"a".repeat(64)).bytes)
    }
    @Test fun validatesBaselineNamesTimesAndRepeatedIdentities(){
        val e=baseline().entries[0]
        for(time in listOf(0L,-1L,8_640_000_000_000_001L))assertThrows(IllegalArgumentException::class.java){LibraryIntegrityBaseline(id(1),"Before",time,listOf(e))}
        for(name in listOf(""," Before","a".repeat(41)))assertThrows(IllegalArgumentException::class.java){LibraryIntegrityBaseline(id(1),name,1,listOf(e))}
        assertThrows(IllegalArgumentException::class.java){LibraryIntegrityBaseline(id(1),"Before",1,listOf(e,e))}
    }
    @Test fun catalogNamesAndCapacityCannotOverwriteExistingBaselines(){
        val c=LibraryIntegrityCatalog(listOf(baseline()))
        assertThrows(IllegalArgumentException::class.java){c.add(baseline(2,"Ｂｅｆｏｒｅ"))}
        assertThrows(IllegalArgumentException::class.java){c.requireNew("before",listOf(item(2)))}
        val full=LibraryIntegrityCatalog((1..10).map{baseline(it,"Baseline $it")})
        assertThrows(IllegalArgumentException::class.java){full.add(baseline(11,"Extra"))}
        assertThrows(IllegalArgumentException::class.java){full.requireNew("Extra",listOf(item(11)))}
    }
    @Test fun codecRoundTripsOwnerSourceDigestTimeAndLabels(){
        val original=baseline();val bytes=LibraryIntegrityCodec.encode(owner,LibraryIntegrityCatalog(listOf(original)))
        val decoded=LibraryIntegrityCodec.decode(owner,bytes).find(original.id)
        assertEquals(original.entries,decoded.entries);assertEquals(original.recordedAt,decoded.recordedAt);assertEquals(original.name,decoded.name)
    }
    @Test fun codecRejectsWrongOwnerTruncationTrailingDataAndInvalidOwnerUnicode(){
        val bytes=LibraryIntegrityCodec.encode(owner,LibraryIntegrityCatalog(listOf(baseline())))
        assertThrows(IllegalArgumentException::class.java){LibraryIntegrityCodec.decode("another-owner",bytes)}
        assertThrows(IllegalArgumentException::class.java){LibraryIntegrityCodec.decode(owner,bytes+byteArrayOf(0))}
        for(size in bytes.indices)assertThrows(Exception::class.java){LibraryIntegrityCodec.decode(owner,bytes.copyOf(size))}
        assertThrows(IllegalArgumentException::class.java){LibraryIntegrityCodec.encode("\uD800",LibraryIntegrityCatalog())}
    }
    @Test fun matchingAndChangedBytesAreDistinctAndNeverUpdateTheBaseline(){
        val b=baseline();val before=b.entries.toList()
        assertEquals(LibraryIntegrityVerification.Status.MATCH,LibraryIntegrityVerification.compare(b,report(listOf(item())),emptySet()).single().status)
        assertEquals(LibraryIntegrityVerification.Status.CHANGED,LibraryIntegrityVerification.compare(b,report(listOf(item())){"abd"},emptySet()).single().status)
        assertEquals(LibraryIntegrityVerification.Status.CHANGED,LibraryIntegrityVerification.compare(b,report(listOf(item())){"longer"},emptySet()).single().status)
        val wrongLength=LibraryDuplicateHash.Report(listOf(report(listOf(item())).checked[0].copy(bytes=4)),emptyList(),4)
        assertEquals(LibraryIntegrityVerification.Status.CHANGED,LibraryIntegrityVerification.compare(b,wrongLength,emptySet()).single().status)
        assertEquals(before,b.entries)
    }
    @Test fun missingAndUnreadableAreNeverTreatedAsMatchingOrChanged(){
        val b=baseline()
        val missing=LibraryIntegrityVerification.compare(b,LibraryDuplicateHash.Report(emptyList(),emptyList(),0),setOf("1")).single()
        assertEquals(LibraryIntegrityVerification.Status.MISSING,missing.status);assertNull(missing.currentBytes)
        for(reason in LibraryDuplicateHash.Reason.values()){
            val r=LibraryDuplicateHash.Report(emptyList(),listOf(LibraryDuplicateHash.Skipped("1",reason)),0)
            val result=LibraryIntegrityVerification.compare(b,r,emptySet()).single()
            assertEquals(reason.name,result.status.name);assertNull(result.currentBytes)
        }
    }
    @Test fun verificationRequiresExactlyOneAccountedResultPerOriginal(){
        val b=baseline();val hash=report(listOf(item())).checked[0]
        for(r in listOf(LibraryDuplicateHash.Report(emptyList(),emptyList(),0),LibraryDuplicateHash.Report(listOf(hash,hash),emptyList(),6),LibraryDuplicateHash.Report(listOf(hash.copy(id="2")),emptyList(),3))){
            assertThrows(IllegalArgumentException::class.java){LibraryIntegrityVerification.compare(b,r,emptySet())}
        }
        assertThrows(IllegalArgumentException::class.java){LibraryIntegrityVerification.compare(b,report(listOf(item())),setOf("1"))}
    }
    @Test fun ownsImmutableBaselinesCatalogsAndVerificationResults(){
        val values=baseline().entries.toMutableList();val b=LibraryIntegrityBaseline(id(1),"Before",1,values);values.clear()
        assertEquals(1,b.entries.size);assertThrows(UnsupportedOperationException::class.java){(b.entries as MutableList).clear()}
        val c=LibraryIntegrityCatalog(listOf(b));assertThrows(UnsupportedOperationException::class.java){(c.baselines as MutableList).clear()}
        val rows=LibraryIntegrityVerification.compare(b,report(listOf(item())),emptySet());assertThrows(UnsupportedOperationException::class.java){(rows as MutableList).clear()}
    }
    @Test fun storesMergeLatestBaselinesAndRefuseDuplicateNamesWithoutWriting(){
        val record=Record();val a=LibraryIntegrityStore(owner,record);val b=LibraryIntegrityStore(owner,record)
        a.save(baseline(1,"One")){};b.save(baseline(2,"Two")){}
        val before=record.bytes!!.copyOf();assertThrows(IllegalArgumentException::class.java){a.save(baseline(3,"one")){}}
        assertEquals(listOf("One","Two"),a.load{}.baselines.map{it.name});assertArrayEquals(before,record.bytes);assertEquals(2,record.writes)
    }
    @Test fun corruptStorageIsPreservedAndStaleGuardsNeverCommit(){
        val corrupt=Record().apply{bytes=byteArrayOf(1,2,3)};val store=LibraryIntegrityStore(owner,corrupt)
        assertThrows(Exception::class.java){store.load{}};assertThrows(Exception::class.java){store.save(baseline()){}}
        assertArrayEquals(byteArrayOf(1,2,3),corrupt.bytes);assertEquals(0,corrupt.writes)
        val record=Record();var calls=0
        assertThrows(IllegalStateException::class.java){LibraryIntegrityStore(owner,record).save(baseline()){calls++;if(calls==2)error("owner changed")}}
        assertNull(record.bytes);assertEquals(0,record.writes)
    }
    @Test fun deletionRemovesOnlyTheNamedBaselineAndRefusesReplay(){
        val record=Record();val store=LibraryIntegrityStore(owner,record);val one=baseline(1,"One");val two=baseline(2,"Two")
        store.save(one){};store.save(two){};store.remove(one.id){}
        assertEquals(two.entries,store.load{}.find(two.id).entries)
        val before=record.bytes!!.copyOf();assertThrows(IllegalArgumentException::class.java){store.remove(one.id){}}
        assertArrayEquals(before,record.bytes);assertEquals(3,record.writes)
    }
    @Test fun oversizedMetadataIsRejectedWithoutReplacingExistingData(){
        val values=(1..10).map{c->LibraryIntegrityBaseline(id(c),"Baseline $c",1,(1..20).map{i->LibraryIntegrityEntry(LibraryCollectionItem((c*100+i).toString(),"content://synthetic/$c/$i/"+"x".repeat(1900),"IMAGE"),1,"a".repeat(64),"label".repeat(20))})}
        assertThrows(IllegalArgumentException::class.java){LibraryIntegrityCodec.encode(owner,LibraryIntegrityCatalog(values))}
        assertThrows(IllegalArgumentException::class.java){LibraryIntegrityCodec.decode(owner,ByteArray(LibraryIntegrityCodec.MAX_BYTES+1))}
    }
}
