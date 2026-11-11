package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.text.Normalizer
import java.util.Collections
import java.util.Locale
import java.util.UUID

internal data class LibraryCollectionItem(val id:String,val uri:String,val kind:String) {
    init {
        require(id.toLongOrNull()?.let{it>=0&&it<=9_007_199_254_740_991&&it.toString()==id}==true){"Choose a valid Library original"}
        val bytes=uri.toByteArray(Charsets.UTF_8)
        require(uri.startsWith("content://")&&uri.length>10&&bytes.size<=2048&&uri.none{it.isISOControl()}&&bytes.toString(Charsets.UTF_8)==uri){"Choose a local Library original"}
        require(kind in setOf("IMAGE","VIDEO")){"Choose a photo or video"}
    }
}

internal class LibraryCollection(val id:String,val name:String,items:List<LibraryCollectionItem>) {
    val items:List<LibraryCollectionItem> = Collections.unmodifiableList(items.toList())
    init {
        require(id.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))){"Invalid collection identifier"}
        val bytes=name.toByteArray(Charsets.UTF_8)
        require(name==name.trim()&&name.isNotBlank()&&name.codePointCount(0,name.length)<=40&&bytes.size<=160&&bytes.toString(Charsets.UTF_8)==name&&name.none{it.isISOControl()}){"Use a name of 1–40 characters"}
        require(this.items.size in 1..20&&this.items.map{it.id}.distinct().size==this.items.size&&this.items.map{it.uri}.distinct().size==this.items.size){"Choose 1–20 different originals"}
    }
}

internal class LibraryCollectionCatalog(values:List<LibraryCollection> = emptyList()) {
    val collections:List<LibraryCollection> = Collections.unmodifiableList(values.toList())
    init {
        require(collections.size<=20){"Keep up to 20 named collections"}
        require(collections.map{it.id}.distinct().size==collections.size){"Collection identifiers must be unique"}
        require(collections.map{Normalizer.normalize(it.name,Normalizer.Form.NFKC).lowercase(Locale.ROOT)}.distinct().size==collections.size){"Choose an unused collection name"}
    }
    fun create(name:String,items:List<LibraryCollectionItem>,id:String=UUID.randomUUID().toString())=
        LibraryCollectionCatalog(collections+LibraryCollection(id,name,items))
    fun remove(id:String):LibraryCollectionCatalog {
        require(collections.any{it.id==id}){"Collections changed. Reopen the list"}
        return LibraryCollectionCatalog(collections.filterNot{it.id==id})
    }
}

/** Bounded, owner-bound metadata. No file bytes, thumbnails or credentials. */
internal object LibraryCollectionCodec {
    const val MAX_BYTES=400_000
    fun encode(owner:String,catalog:LibraryCollectionCatalog):ByteArray {
        requireOwner(owner)
        val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use{out->
            out.writeInt(0x4e4c4331);out.writeUTF(owner);out.writeInt(catalog.collections.size)
            catalog.collections.forEach{c->out.writeUTF(c.id);out.writeUTF(c.name);out.writeInt(c.items.size);c.items.forEach{out.writeUTF(it.id);out.writeUTF(it.uri);out.writeUTF(it.kind)}}
        }
        return buffer.toByteArray().also{require(it.size<=MAX_BYTES){"Collection metadata limit reached. Keep fewer originals or collections"}}
    }
    fun decode(owner:String,bytes:ByteArray):LibraryCollectionCatalog {
        requireOwner(owner);require(bytes.size in 1..MAX_BYTES){"Saved collections are damaged or too large"}
        return DataInputStream(ByteArrayInputStream(bytes)).use{input->
            require(input.readInt()==0x4e4c4331&&input.readUTF()==owner){"Saved collections belong to another owner or version"}
            val count=input.readInt();require(count in 0..20){"Invalid collection count"}
            val values=List(count){
                val id=input.readUTF();val name=input.readUTF();val size=input.readInt();require(size in 1..20){"Invalid original count"}
                LibraryCollection(id,name,List(size){LibraryCollectionItem(input.readUTF(),input.readUTF(),input.readUTF())})
            }
            require(input.read()==-1){"Unexpected collection data"};LibraryCollectionCatalog(values)
        }
    }
    private fun requireOwner(owner:String){require(owner.isNotBlank()&&owner.length<=256&&owner.none{it.isISOControl()}&&owner.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==owner)}
}

internal interface LibraryCollectionRecord {
    fun read():ByteArray?
    fun write(bytes:ByteArray)
}

/** Merge under one lock; unreadable metadata is preserved rather than replaced with empty data. */
internal class LibraryCollectionStore(private val owner:String,private val record:LibraryCollectionRecord) {
    fun load(requireCurrent:()->Unit):LibraryCollectionCatalog=synchronized(lock){requireCurrent();read().also{requireCurrent()}}
    fun create(name:String,items:List<LibraryCollectionItem>,requireCurrent:()->Unit):LibraryCollectionCatalog=synchronized(lock){
        requireCurrent();val next=read().create(name,items);val bytes=LibraryCollectionCodec.encode(owner,next);requireCurrent();record.write(bytes);next
    }
    fun remove(id:String,requireCurrent:()->Unit):LibraryCollectionCatalog=synchronized(lock){
        requireCurrent();val next=read().remove(id);val bytes=LibraryCollectionCodec.encode(owner,next);requireCurrent();record.write(bytes);next
    }
    private fun read()=record.read()?.let{LibraryCollectionCodec.decode(owner,it)}?:LibraryCollectionCatalog()
    companion object {private val lock=Any()}
}
