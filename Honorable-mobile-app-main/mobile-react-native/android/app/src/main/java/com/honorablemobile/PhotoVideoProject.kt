package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections

internal enum class PhotoVideoFormat(val width:Int,val height:Int,val title:String) {
    SQUARE(720,720,"Square · 720 × 720"), PORTRAIT(720,1280,"Portrait · 720 × 1280"), LANDSCAPE(1280,720,"Landscape · 1280 × 720")
}
internal data class PhotoVideoAsset(val durationMs:Long,val digest:String) {
    init{require(durationMs in 1..60_000 && digest.matches(Regex("[0-9a-f]{64}")))}
}
internal class PhotoVideoProject(val id:String,val name:String,val authority:String,val format:PhotoVideoFormat,assets:List<PhotoVideoAsset>) {
    val assets:List<PhotoVideoAsset> = Collections.unmodifiableList(assets.toList())
    init{
        require(PhotoVideoProjectCodec.validId(id))
        require(name==name.trim()&&name.isNotEmpty()&&name.length<=40&&name.none{it.isISOControl()}&&name.toByteArray(Charsets.UTF_8).size<=120&&name.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==name){"Use a project name of 1–40 characters"}
        require(authority.matches(Regex("[A-Za-z][A-Za-z0-9_.]{1,180}\\.creation")))
        require(this.assets.size in 1..20)
    }
    fun source(index:Int):String {require(index in assets.indices);return PhotoVideoProjectCodec.sourceUri(authority,id,index)}
    fun initialDraft():VideoDraft {
        val clips=assets.mapIndexed{index,asset->VideoSegment(0,asset.durationMs,volume=0f,sourceId=if(index==0)"primary"else"photo_$index")}
        val sources=assets.drop(1).mapIndexed{offset,asset->val index=offset+1;VideoSource("photo_$index",source(index),asset.durationMs)}
        return VideoDraft(assets.first().durationMs,clips,0,0,0,assets.first().durationMs,sources)
    }
    override fun equals(other:Any?)=other is PhotoVideoProject&&id==other.id&&name==other.name&&authority==other.authority&&format==other.format&&assets==other.assets
    override fun hashCode()=listOf(id,name,authority,format,assets).hashCode()
}
internal object PhotoVideoProjectCodec {
    const val MAX_BYTES=4096
    fun validId(value:String)=value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
    fun filename(index:Int):String {require(index in 0..19);return "clip-${index.toString().padStart(2,'0')}.mp4"}
    fun sourceUri(authority:String,id:String,index:Int):String {require(validId(id)&&authority.matches(Regex("[A-Za-z][A-Za-z0-9_.]{1,180}\\.creation")));return "content://$authority/photo_video_projects/$id/${filename(index)}"}
    fun encode(project:PhotoVideoProject):ByteArray {
        val payload=ByteArrayOutputStream().use{bytes->DataOutputStream(bytes).use{out->out.writeUTF(project.id);out.writeUTF(project.name);out.writeUTF(project.authority);out.writeInt(project.format.ordinal);out.writeInt(project.assets.size);project.assets.forEach{out.writeLong(it.durationMs);out.writeUTF(it.digest)}};bytes.toByteArray()}
        return ByteArrayOutputStream().use{bytes->DataOutputStream(bytes).use{out->out.writeInt(0x4E505650);out.writeInt(1);out.writeInt(payload.size);out.write(MessageDigest.getInstance("SHA-256").digest(payload));out.write(payload)};bytes.toByteArray().also{require(it.size<=MAX_BYTES)}}
    }
    fun decode(bytes:ByteArray,id:String):PhotoVideoProject {
        require(validId(id)&&bytes.size in 100..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use{input->
            require(input.readInt()==0x4E505650&&input.readInt()==1)
            val count=input.readInt();require(count==input.available()-32&&count>0);val digest=ByteArray(32);input.readFully(digest);val payload=ByteArray(count);input.readFully(payload)
            require(MessageDigest.isEqual(digest,MessageDigest.getInstance("SHA-256").digest(payload))){"Photo video project is damaged"}
            DataInputStream(ByteArrayInputStream(payload)).use{data->
                val storedId=data.readUTF();require(storedId==id);val name=data.readUTF();val authority=data.readUTF();val format=data.readInt();require(format in PhotoVideoFormat.entries.indices)
                val size=data.readInt();require(size in 1..20);val assets=List(size){PhotoVideoAsset(data.readLong(),data.readUTF())};require(data.available()==0)
                return PhotoVideoProject(id,name,authority,PhotoVideoFormat.entries[format],assets)
            }
        }
    }
}
