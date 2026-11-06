package com.honorablemobile

import app.honorable.editor.PhotoPresetNamespace
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Collections
import java.util.Locale
import java.util.UUID

internal object VideoVersionName {
    fun requireValid(name:String) {
        require(name==name.trim()&&name.isNotEmpty()&&name.codePointCount(0,name.length)<=40&&name.none{it.isISOControl()}){"Use a version name of 1–40 characters"}
        val bytes=name.toByteArray(Charsets.UTF_8)
        require(bytes.size<=160&&bytes.toString(Charsets.UTF_8)==name){"Use a valid version name"}
    }
    fun key(name:String):String=Normalizer.normalize(name,Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}

internal class VideoVersionScope(owner:String,source:String) {
    private val digest:ByteArray
    init {
        require(PhotoPresetNamespace.valid(owner)){"Invalid version owner"}
        val bytes=source.toByteArray(Charsets.UTF_8)
        require(source.startsWith("content://")&&bytes.size<=2048&&source.none{it.isISOControl()}&&bytes.toString(Charsets.UTF_8)==source){"Choose an original video document"}
        val encoded=ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { it.writeUTF(owner);it.writeUTF(source) } }.toByteArray()
        digest=MessageDigest.getInstance("SHA-256").digest(encoded)
    }
    val storageName:String get()="video-versions-"+digest.joinToString(""){"%02x".format(it)}
    fun fingerprint():ByteArray=digest.copyOf()
}

internal class VideoProjectVersion private constructor(val id:String,val name:String,val sourceDurationMs:Long,bytes:ByteArray) {
    private val data=bytes.copyOf()
    val byteCount:Int get()=data.size
    init {
        require(UUID.fromString(id).toString()==id){"Invalid saved version identity"}
        VideoVersionName.requireValid(name)
        require(data.size in 1..256_000&&sourceDurationMs in 1..Int.MAX_VALUE.toLong()){"Invalid saved version size"}
        require(VideoDraftCodec.decode(data,sourceDurationMs)!=null){"Saved version is damaged or incompatible"}
    }
    fun draft():VideoDraft=requireNotNull(VideoDraftCodec.decode(data.copyOf(),sourceDurationMs)){"Saved version is unavailable"}
    fun encoded():ByteArray=data.copyOf()
    override fun equals(other:Any?):Boolean=other is VideoProjectVersion&&id==other.id&&name==other.name&&sourceDurationMs==other.sourceDurationMs&&data.contentEquals(other.data)
    override fun hashCode():Int=31*(31*(31*id.hashCode()+name.hashCode())+sourceDurationMs.hashCode())+data.contentHashCode()
    companion object {
        fun snapshot(draft:VideoDraft):VideoDraft=draft.copy(
            segments=Collections.unmodifiableList(draft.segments.map{it.copy(captions=Collections.unmodifiableList(it.captions.toList()))}),
            sources=Collections.unmodifiableList(draft.sources.filter{source->draft.segments.any{it.sourceId==source.id}}))
        fun create(name:String,draft:VideoDraft,id:String=UUID.randomUUID().toString())=VideoProjectVersion(id,name,draft.sourceDurationMs,VideoDraftCodec.encode(snapshot(draft)))
        fun decode(id:String,name:String,duration:Long,bytes:ByteArray)=VideoProjectVersion(id,name,duration,bytes)
    }
}

internal class VideoProjectVersions(val sourceDurationMs:Long,values:List<VideoProjectVersion> = emptyList()) {
    val entries:List<VideoProjectVersion> = Collections.unmodifiableList(values.toList())
    init {
        require(sourceDurationMs in 1..Int.MAX_VALUE.toLong())
        require(entries.size<=20){"Up to 20 versions fit in this original's shelf"}
        require(entries.all{it.sourceDurationMs==sourceDurationMs}){"Original video duration changed"}
        require(entries.sumOf{it.byteCount.toLong()}<=512_000){"Version storage is full. Delete an unused version first."}
        require(entries.map{it.id}.distinct().size==entries.size){"Saved version identities conflict"}
        require(entries.map{VideoVersionName.key(it.name)}.distinct().size==entries.size){"Choose an unused version name"}
    }
    fun add(value:VideoProjectVersion)=VideoProjectVersions(sourceDurationMs,entries+value)
    fun remove(value:VideoProjectVersion):VideoProjectVersions {
        require(entries.singleOrNull{it.id==value.id}==value){"Saved versions changed. Open the shelf again."}
        return VideoProjectVersions(sourceDurationMs,entries.filter{it.id!=value.id})
    }
    override fun equals(other:Any?):Boolean=other is VideoProjectVersions&&sourceDurationMs==other.sourceDurationMs&&entries==other.entries
    override fun hashCode():Int=31*sourceDurationMs.hashCode()+entries.hashCode()
}

/** Bounded metadata-only payload; encrypted by the production record adapter. */
internal object VideoProjectVersionCodec {
    const val MAX_BYTES=540_000
    private const val MAGIC=0x4e565653
    fun encode(scope:VideoVersionScope,catalog:VideoProjectVersions):ByteArray {
        val body=ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeByte(1);out.write(scope.fingerprint());out.writeLong(catalog.sourceDurationMs);out.writeByte(catalog.entries.size)
            catalog.entries.forEach { entry ->
                val id=UUID.fromString(entry.id);out.writeLong(id.mostSignificantBits);out.writeLong(id.leastSignificantBits)
                val name=entry.name.toByteArray(Charsets.UTF_8);out.writeShort(name.size);out.write(name)
                val bytes=entry.encoded();out.writeInt(bytes.size);out.write(bytes)
            }
        } }.toByteArray()
        return ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC);out.writeInt(body.size);out.write(MessageDigest.getInstance("SHA-256").digest(body));out.write(body)
        } }.toByteArray().also { require(it.size<=MAX_BYTES){"Saved version shelf is too large"} }
    }
    fun decode(scope:VideoVersionScope,duration:Long,bytes:ByteArray):VideoProjectVersions {
        require(bytes.size in 82..MAX_BYTES){"Saved versions are damaged or too large"}
        val input=DataInputStream(ByteArrayInputStream(bytes));require(input.readInt()==MAGIC){"Unsupported saved versions"}
        val size=input.readInt();require(size in 42..MAX_BYTES&&input.available()==size+32){"Invalid saved version length"}
        val digest=ByteArray(32).also{input.readFully(it)};val body=ByteArray(size).also{input.readFully(it)}
        require(MessageDigest.isEqual(digest,MessageDigest.getInstance("SHA-256").digest(body))){"Saved versions are damaged"}
        val data=DataInputStream(ByteArrayInputStream(body));require(data.readUnsignedByte()==1){"Unsupported saved version format"}
        val fingerprint=ByteArray(32).also{data.readFully(it)};require(MessageDigest.isEqual(scope.fingerprint(),fingerprint)){"Saved versions belong to another original or owner"}
        require(data.readLong()==duration){"Original video duration changed"}
        val count=data.readUnsignedByte();require(count<=20)
        var total=0L
        val entries=List(count){
            val id=UUID(data.readLong(),data.readLong()).toString()
            val nameSize=data.readUnsignedShort();require(nameSize in 1..160)
            val encodedName=ByteArray(nameSize).also{data.readFully(it)};val name=encodedName.toString(Charsets.UTF_8)
            require(name.toByteArray(Charsets.UTF_8).contentEquals(encodedName)){"Invalid version name encoding"}
            val length=data.readInt();require(length in 1..256_000&&length<=data.available());total+=length;require(total<=512_000)
            val draft=ByteArray(length).also{data.readFully(it)}
            VideoProjectVersion.decode(id,name,duration,draft)
        }
        require(data.available()==0){"Unexpected saved version data"}
        return VideoProjectVersions(duration,entries)
    }
}

/** Reopened editors can assign different IDs to the same URI. Preserve current IDs for Undo. */
internal object VideoVersionSources {
    fun reconcile(primary:String,current:Map<String,VideoSource>,saved:VideoDraft,nextId:()->String={UUID.randomUUID().toString()}):VideoDraft {
        require(current.all{it.key==it.value.id}&&current.values.map{it.uri}.distinct().size==current.size)
        require(current.values.none{it.uri==primary}&&saved.sources.none{it.uri==primary}){"Conflicting original references"}
        require((current.values.map{it.uri}+saved.sources.map{it.uri}).distinct().size<=31){"Too many originals to retain Undo. Save this version, reopen the original, then open the chosen version."}
        val reserved=(current.keys+saved.sources.map{it.id}+"primary").toMutableSet()
        fun allocate(source:VideoSource):String {
            repeat(64){
                val candidate=nextId()
                if(candidate !in reserved&&runCatching{source.copy(id=candidate)}.isSuccess){reserved.add(candidate);return candidate}
            }
            error("A new original reference could not be allocated")
        }
        val mapping=linkedMapOf<String,String>()
        val sources=saved.sources.map { source ->
            val existing=current.values.singleOrNull{it.uri==source.uri}
            val id=if(existing!=null){
                require(existing.durationMs==source.durationMs){"An imported original changed. Reopen it before restoring."}
                existing.id
            }else if(source.id !in current)source.id else allocate(source)
            mapping[source.id]=id;source.copy(id=id)
        }
        return saved.copy(segments=saved.segments.map{clip->if(clip.sourceId=="primary")clip else clip.copy(sourceId=mapping.getValue(clip.sourceId))},sources=sources)
    }
}
