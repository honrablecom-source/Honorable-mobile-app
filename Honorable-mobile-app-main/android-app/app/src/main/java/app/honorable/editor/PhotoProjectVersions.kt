package app.honorable.editor

import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Collections
import java.util.Locale
import java.util.UUID

internal class PhotoVersionScope(val namespace:String,val source:String){
    private val hash:String=PhotoProjectCodec.storageName(namespace,source).removeSuffix(".bin")
    val storageName:String="photo-versions-$hash"
    fun fingerprint()=hash.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
}
internal object PhotoVersionName {
    fun validate(value:String){require(value==value.trim()&&value.isNotBlank()&&value.length<=40&&value.none{it.isISOControl()}&&value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==value){"Use a unique name of 1–40 characters"}}
    fun key(value:String)=Normalizer.normalize(value,Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}
internal class PhotoSavedVersion private constructor(val scopeKey:String,val id:String,val name:String,val fingerprint:String,bytes:ByteArray,originals:List<String>){
    private val data=bytes.copyOf()
    val originals:List<String> = Collections.unmodifiableList(ImportedPhotoOriginals.validate(originals).toList())
    val encodedSize:Int get()=16+2+name.toByteArray(Charsets.UTF_8).size+32+1+this.originals.sumOf{2+it.toByteArray(Charsets.UTF_8).size}+4+data.size
    init{require(UUID.fromString(id).toString()==id);PhotoVersionName.validate(name);require(fingerprint.matches(Regex("[0-9a-f]{64}")));require(data.size in 1..PhotoProjectCodec.MAX_BYTES);require(this.originals==originals){"Saved source list is not canonical"}}
    fun encoded()=data.copyOf()
    fun edit(scope:PhotoVersionScope,currentFingerprint:String):PhotoEdit {
        require(scope.storageName==scopeKey){"Saved version belongs to another original or owner"}
        return PhotoProjectCodec.decode(data,scope.namespace,scope.source,currentFingerprint)
    }
    override fun equals(other:Any?)=other is PhotoSavedVersion&&scopeKey==other.scopeKey&&id==other.id&&name==other.name&&fingerprint==other.fingerprint&&originals==other.originals&&data.contentEquals(other.data)
    override fun hashCode()=31*(31*(31*id.hashCode()+name.hashCode())+fingerprint.hashCode())+data.contentHashCode()
    companion object {
        fun create(scope:PhotoVersionScope,name:String,fingerprint:String,edit:PhotoEdit,originals:List<String>,id:String=UUID.randomUUID().toString())=
            decode(scope,id,name,fingerprint,PhotoProjectCodec.encode(scope.namespace,scope.source,fingerprint,edit),ImportedPhotoOriginals.validate(originals))
        fun decode(scope:PhotoVersionScope,id:String,name:String,fingerprint:String,bytes:ByteArray,originals:List<String>):PhotoSavedVersion=
            PhotoSavedVersion(scope.storageName,id,name,fingerprint,bytes,originals).also{it.edit(scope,fingerprint)}
    }
}
internal class PhotoVersionCatalog(values:List<PhotoSavedVersion> = emptyList()){
    val entries:List<PhotoSavedVersion> = Collections.unmodifiableList(values.toList())
    init{
        require(entries.size<=20){"Up to 20 versions fit in this original's shelf"}
        require(74L+entries.sumOf{it.encodedSize.toLong()}<=PhotoVersionCodec.MAX_BYTES){"Version storage is full. Delete an unused version first."}
        require(entries.map{it.id}.distinct().size==entries.size){"Saved identities conflict"}
        require(entries.map{PhotoVersionName.key(it.name)}.distinct().size==entries.size){"Choose an unused version name"}
        require(entries.map{it.scopeKey}.distinct().size<=1){"Saved originals or owners conflict"}
    }
    fun add(value:PhotoSavedVersion)=PhotoVersionCatalog(entries+value)
    fun remove(value:PhotoSavedVersion):PhotoVersionCatalog{require(entries.singleOrNull{it.id==value.id}==value){"Saved versions changed. Reopen the shelf."};return PhotoVersionCatalog(entries.filterNot{it.id==value.id})}
    override fun equals(other:Any?)=other is PhotoVersionCatalog&&entries==other.entries
    override fun hashCode()=entries.hashCode()
}
internal object PhotoVersionCodec {
    const val MAX_BYTES=512_000
    private const val MAGIC=0x4e505653
    private fun text(out:DataOutputStream,value:String){val bytes=value.toByteArray(Charsets.UTF_8);out.writeShort(bytes.size);out.write(bytes)}
    private fun text(input:DataInputStream,max:Int):String {
        val count=input.readUnsignedShort();require(count in 1..max&&count<=input.available());val bytes=ByteArray(count);input.readFully(bytes)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    fun encode(scope:PhotoVersionScope,catalog:PhotoVersionCatalog):ByteArray {
        val body=ByteArrayOutputStream().also{buffer->DataOutputStream(buffer).use{out->
            out.writeByte(1);out.write(scope.fingerprint());out.writeByte(catalog.entries.size)
            catalog.entries.forEach{entry->
                require(entry.scopeKey==scope.storageName);val id=UUID.fromString(entry.id);out.writeLong(id.mostSignificantBits);out.writeLong(id.leastSignificantBits);text(out,entry.name)
                out.write(entry.fingerprint.chunked(2).map{it.toInt(16).toByte()}.toByteArray());out.writeByte(entry.originals.size);entry.originals.forEach{text(out,it)}
                val data=entry.encoded();out.writeInt(data.size);out.write(data)
            }
        }}.toByteArray()
        return ByteArrayOutputStream().also{buffer->DataOutputStream(buffer).use{out->out.writeInt(MAGIC);out.writeInt(body.size);out.write(MessageDigest.getInstance("SHA-256").digest(body));out.write(body)}}.toByteArray().also{require(it.size<=MAX_BYTES)}
    }
    fun decode(scope:PhotoVersionScope,bytes:ByteArray):PhotoVersionCatalog {
        require(bytes.size in 74..MAX_BYTES){"Saved versions are damaged or too large"}
        DataInputStream(ByteArrayInputStream(bytes)).use{input->
            require(input.readInt()==MAGIC);val size=input.readInt();require(size==input.available()-32)
            val digest=ByteArray(32);input.readFully(digest);val body=ByteArray(size);input.readFully(body)
            require(MessageDigest.isEqual(digest,MessageDigest.getInstance("SHA-256").digest(body))){"Saved versions are damaged"}
            DataInputStream(ByteArrayInputStream(body)).use{data->
                require(data.readUnsignedByte()==1){"Unsupported saved version format"};val owner=ByteArray(32);data.readFully(owner);require(owner.contentEquals(scope.fingerprint())){"Saved versions belong to another original or owner"}
                val count=data.readUnsignedByte();require(count<=20)
                val entries=List(count){
                    val id=UUID(data.readLong(),data.readLong()).toString();val name=text(data,160);val fingerprint=ByteArray(32);data.readFully(fingerprint)
                    val n=data.readUnsignedByte();require(n<=ImportedPhotoOriginals.MAX_SOURCES);val originals=List(n){text(data,2048)}
                    val length=data.readInt();require(length in 1..PhotoProjectCodec.MAX_BYTES&&length<=data.available());val raw=ByteArray(length);data.readFully(raw)
                    PhotoSavedVersion.decode(scope,id,name,fingerprint.joinToString(""){"%02x".format(it)},raw,originals)
                }
                require(data.available()==0){"Unexpected saved version data"};return PhotoVersionCatalog(entries)
            }
        }
    }
}
