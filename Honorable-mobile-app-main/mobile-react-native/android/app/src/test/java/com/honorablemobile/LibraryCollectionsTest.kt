package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class LibraryCollectionsTest {
    private val owner="synthetic-private-owner"
    private fun item(id:Int=1,uri:String="content://synthetic/$id",kind:String="IMAGE")=LibraryCollectionItem(id.toString(),uri,kind)
    private fun id(i:Int)=String.format("00000000-0000-0000-0000-%012d",i)
    private fun catalog()=LibraryCollectionCatalog().create("Trip",listOf(item(),item(2,kind="VIDEO")),id(1))
    private class Record:LibraryCollectionRecord {
        var bytes:ByteArray?=null;var writes=0
        override fun read()=bytes?.copyOf()
        override fun write(bytes:ByteArray){this.bytes=bytes.copyOf();writes++}
    }
    @Test fun roundTripsExactOriginalIdentityOrderNameAndOwner(){
        val before=catalog();val decoded=LibraryCollectionCodec.decode(owner,LibraryCollectionCodec.encode(owner,before))
        assertEquals("Trip",decoded.collections.single().name);assertEquals(id(1),decoded.collections.single().id)
        assertEquals(before.collections.single().items,decoded.collections.single().items)
    }
    @Test fun acceptsUnicodeNamesAndRejectsConfusableDuplicates(){
        val c=LibraryCollectionCatalog().create("Ｔｒｉｐ",listOf(item()),id(1))
        assertThrows(IllegalArgumentException::class.java){c.create("trip",listOf(item(2)),id(2))}
        val unicode=LibraryCollectionCatalog().create("旅行 🌅",listOf(item()),id(1))
        assertEquals("旅行 🌅",LibraryCollectionCodec.decode(owner,LibraryCollectionCodec.encode(owner,unicode)).collections[0].name)
    }
    @Test fun validatesNamesWithoutTruncation(){
        for(name in listOf(""," "," Trip","Trip ","bad\nname","a".repeat(41),"\uD800")){
            assertThrows(IllegalArgumentException::class.java){LibraryCollectionCatalog().create(name,listOf(item()),id(1))}
        }
        assertEquals(40,LibraryCollectionCatalog().create("a".repeat(40),listOf(item()),id(1)).collections[0].name.length)
    }
    @Test fun validatesExactOriginalBindings(){
        for(i in listOf("-1","01","1.5","9007199254740992","bad"))assertThrows(IllegalArgumentException::class.java){LibraryCollectionItem(i,"content://synthetic/one","IMAGE")}
        for(uri in listOf("https://example.test/image","file:///private","content://","content://bad\npath","content://"+"x".repeat(2049),"content://\uD800"))assertThrows(IllegalArgumentException::class.java){item(uri=uri)}
        assertThrows(IllegalArgumentException::class.java){item(kind="AUDIO")}
    }
    @Test fun enforcesOriginalAndCollectionLimits(){
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCatalog().create("Empty",emptyList(),id(1))}
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCatalog().create("Big",List(21){item(it)},id(1))}
        val collections=List(20){LibraryCollection(id(it),"Collection $it",List(20){j->item(it*20+j)})}
        assertEquals(20,LibraryCollectionCatalog(collections).collections.size)
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCatalog(collections).create("Extra",listOf(item(1000)),id(1000))}
    }
    @Test fun rejectsDuplicateIdsAndUrisInsteadOfSubstituting(){
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCatalog().create("Duplicate",listOf(item(),item()),id(1))}
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCatalog().create("Duplicate",listOf(item(),item(2,uri="content://synthetic/1")),id(1))}
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCatalog(listOf(catalog().collections[0],LibraryCollection(id(1),"Other",listOf(item(3)))))}
    }
    @Test fun ownsUnmodifiableLists(){
        val originals=mutableListOf(item());val collection=LibraryCollection(id(1),"Trip",originals);originals.clear()
        assertEquals(1,collection.items.size)
        assertThrows(UnsupportedOperationException::class.java){(collection.items as MutableList).clear()}
        val values=mutableListOf(collection);val catalog=LibraryCollectionCatalog(values);values.clear()
        assertEquals(1,catalog.collections.size)
        assertThrows(UnsupportedOperationException::class.java){(catalog.collections as MutableList).clear()}
    }
    @Test fun codecRefusesWrongOwnerTrailingDataAndTruncation(){
        val bytes=LibraryCollectionCodec.encode(owner,catalog())
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCodec.decode("other-owner",bytes)}
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCodec.decode(owner,bytes+byteArrayOf(0))}
        for(length in bytes.indices)assertThrows(Exception::class.java){LibraryCollectionCodec.decode(owner,bytes.copyOf(length))}
    }
    @Test fun refusesOversizedEncodedMetadata(){
        val values=List(20){c->LibraryCollection(id(c),"Collection $c",List(20){j->item(c*20+j,"content://synthetic/$c/$j/"+"x".repeat(1900))})}
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCodec.encode(owner,LibraryCollectionCatalog(values))}
        assertThrows(IllegalArgumentException::class.java){LibraryCollectionCodec.decode(owner,ByteArray(LibraryCollectionCodec.MAX_BYTES+1))}
    }
    @Test fun storesMergeLatestContentsAcrossInstances(){
        val record=Record();val a=LibraryCollectionStore(owner,record);val b=LibraryCollectionStore(owner,record)
        a.create("First",listOf(item())){};b.create("Second",listOf(item(2))){}
        assertEquals(listOf("First","Second"),a.load{}.collections.map{it.name});assertEquals(2,record.writes)
    }
    @Test fun duplicateFailurePreservesExactStorageBytes(){
        val record=Record();val store=LibraryCollectionStore(owner,record);store.create("Trip",listOf(item())){}
        val bytes=record.bytes!!.copyOf()
        assertThrows(IllegalArgumentException::class.java){store.create("trip",listOf(item(2))) {}}
        assertArrayEquals(bytes,record.bytes);assertEquals(1,record.writes)
    }
    @Test fun corruptStorageIsNeverReplacedWithEmptyCatalog(){
        val record=Record().apply{bytes=byteArrayOf(1,2,3)};val store=LibraryCollectionStore(owner,record)
        assertThrows(Exception::class.java){store.load{}}
        assertThrows(Exception::class.java){store.create("New",listOf(item())){}}
        assertArrayEquals(byteArrayOf(1,2,3),record.bytes);assertEquals(0,record.writes)
    }
    @Test fun staleAdmissionAndPrewriteGuardsNeverWrite(){
        val record=Record();val store=LibraryCollectionStore(owner,record)
        assertThrows(IllegalStateException::class.java){store.create("Trip",listOf(item())){error("stale")}}
        var calls=0
        assertThrows(IllegalStateException::class.java){store.create("Trip",listOf(item())){calls++;if(calls==2)error("source changed")}}
        assertNull(record.bytes);assertEquals(0,record.writes)
    }
    @Test fun metadataDeletionPreservesOtherReferencesAndRefusesStaleId(){
        val record=Record();val store=LibraryCollectionStore(owner,record)
        val first=store.create("First",listOf(item())){}.collections[0]
        store.create("Second",listOf(item(2))){}
        val remaining=store.remove(first.id){}
        assertEquals(listOf(item(2)),remaining.collections.single().items)
        val before=record.bytes!!.copyOf();assertThrows(IllegalArgumentException::class.java){store.remove(first.id){}}
        assertArrayEquals(before,record.bytes);assertEquals(3,record.writes)
    }
}
