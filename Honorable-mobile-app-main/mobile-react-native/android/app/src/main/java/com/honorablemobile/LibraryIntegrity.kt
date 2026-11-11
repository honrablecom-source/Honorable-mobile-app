package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Collections
import java.util.UUID

internal data class LibraryIntegrityEntry(val original:LibraryCollectionItem,val bytes:Long,val sha256:String,val label:String="") {
    init {require(bytes in 0 until LibraryDuplicateHash.FILE_LIMIT&&sha256.matches(Regex("[a-f0-9]{64}"))){"Invalid recorded fingerprint"};require(label.length<=120&&label.toByteArray(Charsets.UTF_8).size<=480&&label.none{it.isISOControl()}&&label.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==label)}
}
internal class LibraryIntegrityBaseline(val id:String,val name:String,val recordedAt:Long,values:List<LibraryIntegrityEntry>) {
    val entries:List<LibraryIntegrityEntry> = Collections.unmodifiableList(values.toList())
    init {LibraryCollection(id,name,entries.map{it.original});require(recordedAt in 1..8_640_000_000_000_000){"Invalid recording time"}}
    companion object {
        fun create(name:String,items:List<LibraryCollectionItem>,report:LibraryDuplicateHash.Report,now:Long,id:String=UUID.randomUUID().toString(),labels:Map<String,String> = emptyMap()):LibraryIntegrityBaseline {
            require(report.skipped.isEmpty()&&report.checked.size==items.size&&report.checked.map{it.id}.toSet()==items.map{it.id}.toSet()&&report.checked.map{it.id}.distinct().size==items.size){"Every selected original must be fully checked before keeping a baseline"}
            val hashes=report.checked.associateBy{it.id}
            return LibraryIntegrityBaseline(id,name,now,items.map{item->val hash=hashes.getValue(item.id);LibraryIntegrityEntry(item,hash.bytes,hash.sha256,labels[item.id].orEmpty())})
        }
    }
}
internal class LibraryIntegrityCatalog(values:List<LibraryIntegrityBaseline> = emptyList()) {
    val baselines:List<LibraryIntegrityBaseline> = Collections.unmodifiableList(values.toList())
    init {require(baselines.size<=10){"Keep up to 10 integrity baselines"};LibraryCollectionCatalog(baselines.map{LibraryCollection(it.id,it.name,it.entries.map{entry->entry.original})})}
    fun add(value:LibraryIntegrityBaseline)=LibraryIntegrityCatalog(baselines+value)
    fun requireNew(name:String,items:List<LibraryCollectionItem>){require(baselines.size<10){"Keep up to 10 integrity baselines"};LibraryCollectionCatalog(baselines.map{LibraryCollection(it.id,it.name,it.entries.map{e->e.original})}).create(name,items)}
    fun find(id:String)=requireNotNull(baselines.singleOrNull{it.id==id}){"Baseline changed or was removed. Reopen the shelf"}
    fun remove(id:String):LibraryIntegrityCatalog {find(id);return LibraryIntegrityCatalog(baselines.filterNot{it.id==id})}
}
internal object LibraryIntegrityCodec {
    const val MAX_BYTES=400_000
    fun encode(owner:String,catalog:LibraryIntegrityCatalog):ByteArray {
        requireOwner(owner)
        val bytes=ByteArrayOutputStream().also{buffer->DataOutputStream(buffer).use{out->
            out.writeInt(0x4e4c4931);out.writeUTF(owner);out.writeInt(catalog.baselines.size)
            catalog.baselines.forEach{b->out.writeUTF(b.id);out.writeUTF(b.name);out.writeLong(b.recordedAt);out.writeInt(b.entries.size);b.entries.forEach{e->out.writeUTF(e.original.id);out.writeUTF(e.original.uri);out.writeUTF(e.original.kind);out.writeLong(e.bytes);out.writeUTF(e.sha256);out.writeUTF(e.label)}}
        }}.toByteArray()
        require(bytes.size<=MAX_BYTES){"Integrity metadata limit reached"};return bytes
    }
    fun decode(owner:String,bytes:ByteArray):LibraryIntegrityCatalog {
        requireOwner(owner);require(bytes.size in 1..MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use{input->
            require(input.readInt()==0x4e4c4931&&input.readUTF()==owner){"Integrity data belongs to another owner or version"}
            val count=input.readInt();require(count in 0..10)
            val values=List(count){val id=input.readUTF();val name=input.readUTF();val at=input.readLong();val size=input.readInt();require(size in 1..20)
                LibraryIntegrityBaseline(id,name,at,List(size){LibraryIntegrityEntry(LibraryCollectionItem(input.readUTF(),input.readUTF(),input.readUTF()),input.readLong(),input.readUTF(),input.readUTF())})}
            require(input.read()==-1);LibraryIntegrityCatalog(values)
        }
    }
    private fun requireOwner(owner:String){require(owner.isNotBlank()&&owner.length<=256&&owner.none{it.isISOControl()}&&owner.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==owner)}
}
internal interface LibraryIntegrityRecord {fun read():ByteArray?;fun write(bytes:ByteArray)}
internal class LibraryIntegrityStore(private val owner:String,private val record:LibraryIntegrityRecord){
    fun load(guard:()->Unit):LibraryIntegrityCatalog=synchronized(lock){guard();read().also{guard()}}
    fun save(value:LibraryIntegrityBaseline,guard:()->Unit):LibraryIntegrityCatalog=synchronized(lock){guard();val next=read().add(value);val bytes=LibraryIntegrityCodec.encode(owner,next);guard();record.write(bytes);next}
    fun remove(id:String,guard:()->Unit):LibraryIntegrityCatalog=synchronized(lock){guard();val next=read().remove(id);val bytes=LibraryIntegrityCodec.encode(owner,next);guard();record.write(bytes);next}
    private fun read()=record.read()?.let{LibraryIntegrityCodec.decode(owner,it)}?:LibraryIntegrityCatalog()
    companion object {private val lock=Any()}
}
internal object LibraryIntegrityVerification {
    enum class Status { MATCH, CHANGED, MISSING, UNAVAILABLE, FILE_LIMIT, TOTAL_LIMIT, LENGTH_CHANGED }
    data class Item(val id:String,val status:Status,val previousBytes:Long,val currentBytes:Long?=null,val label:String="")
    fun compare(baseline:LibraryIntegrityBaseline,report:LibraryDuplicateHash.Report,missing:Set<String>):List<Item>{
        val ids=baseline.entries.map{it.original.id}.toSet()
        require(missing.all{it in ids}&&report.checked.all{it.id in ids&&it.id !in missing}&&report.skipped.all{it.id in ids&&it.id !in missing})
        val accounted=report.checked.map{it.id}+report.skipped.map{it.id}+missing
        require(accounted.size==ids.size&&accounted.toSet()==ids){"Verification coverage changed"}
        val hashes=report.checked.associateBy{it.id};val skipped=report.skipped.associateBy{it.id}
        return Collections.unmodifiableList(baseline.entries.map{entry->
            val id=entry.original.id;val current=hashes[id]
            val status=when{ id in missing->Status.MISSING;current!=null->if(current.bytes==entry.bytes&&current.sha256==entry.sha256)Status.MATCH else Status.CHANGED
                else->Status.valueOf(skipped.getValue(id).reason.name)}
            Item(id,status,entry.bytes,current?.bytes,entry.label)
        })
    }
}
