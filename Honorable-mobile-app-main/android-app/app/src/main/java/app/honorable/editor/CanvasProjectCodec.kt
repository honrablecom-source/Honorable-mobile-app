package app.honorable.editor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections

/** Guest-created source/edit state and private export-protection provenance. Never portable media metadata. */
class CanvasProject(val id:String,val name:String,val sourceDigest:String,val edit:PhotoEdit,protectedOriginals:List<String> = emptyList()) {
    val protectedOriginals:List<String> = Collections.unmodifiableList(ImportedPhotoOriginals.validate(protectedOriginals).toList())
    init {
        require(CanvasProjectCodec.validId(id))
        require(name==name.trim()&&name.isNotEmpty()&&name.length<=40&&name.none{it.isISOControl()}){"Use a project name of 1–40 characters"}
        require(name.toByteArray(Charsets.UTF_8).size<=120&&name.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==name)
        require(sourceDigest.matches(Regex("[0-9a-f]{64}")))
    }
    fun copy(id:String=this.id,name:String=this.name,sourceDigest:String=this.sourceDigest,edit:PhotoEdit=this.edit,protectedOriginals:List<String> = this.protectedOriginals)=CanvasProject(id,name,sourceDigest,edit,protectedOriginals)
    override fun equals(other:Any?)=other is CanvasProject&&id==other.id&&name==other.name&&sourceDigest==other.sourceDigest&&edit==other.edit&&protectedOriginals==other.protectedOriginals
    override fun hashCode()=listOf(id,name,sourceDigest,edit,protectedOriginals).hashCode()
}
object CanvasProjectCodec {
    const val MAX_BYTES=100_000
    private const val LEGACY_MAX_BYTES=66_000
    private const val MAGIC=0x4E43414E
    fun validId(value:String)=value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
    private fun scope(id:String)=PhotoSessionCodec.scope("guest-created-canvas","local-only",id)
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun identity(out:DataOutputStream,value:CanvasProject){out.writeUTF(value.id);out.writeUTF(value.name);out.writeUTF(value.sourceDigest)}
    fun encode(value:CanvasProject):ByteArray {
        val session=PhotoSessionCodec.encode(PhotoSessionSnapshot(scope(value.id),value.edit))
        if(value.protectedOriginals.isEmpty())return ByteArrayOutputStream().use{bytes->
            DataOutputStream(bytes).use{out->out.writeInt(MAGIC);out.writeInt(1);identity(out,value);out.writeInt(session.size);out.write(hash(session));out.write(session)}
            bytes.toByteArray().also{require(it.size<=LEGACY_MAX_BYTES)}
        }
        val body=ByteArrayOutputStream().use{bytes->DataOutputStream(bytes).use{out->
            identity(out,value);out.writeInt(value.protectedOriginals.size)
            value.protectedOriginals.forEach{uri->val encoded=uri.toByteArray(Charsets.UTF_8);out.writeInt(encoded.size);out.write(encoded)}
            out.writeInt(session.size);out.write(session)
        };bytes.toByteArray()}
        return ByteArrayOutputStream().use{bytes->DataOutputStream(bytes).use{out->out.writeInt(MAGIC);out.writeInt(2);out.writeInt(body.size);out.write(hash(body));out.write(body)};bytes.toByteArray().also{require(it.size<=MAX_BYTES)}}
    }
    fun decode(bytes:ByteArray,expectedId:String):CanvasProject {
        require(validId(expectedId));require(bytes.size in 150..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use{input->
            require(input.readInt()==MAGIC){"Unsupported canvas project"}
            return when(input.readInt()){
                1->{
                    require(bytes.size<=LEGACY_MAX_BYTES)
                    val id=input.readUTF();require(id==expectedId){"This project belongs to another source"}
                    val name=input.readUTF();val digest=input.readUTF();val count=input.readInt();require(count in 1..PhotoSessionCodec.MAX_BYTES&&count==input.available()-32)
                    val checksum=ByteArray(32);input.readFully(checksum);val session=ByteArray(count);input.readFully(session)
                    require(MessageDigest.isEqual(checksum,hash(session))){"Canvas project is damaged"}
                    CanvasProject(id,name,digest,PhotoSessionCodec.decode(session,scope(id)).edit)
                }
                2->{
                    val size=input.readInt();require(size>0&&size==input.available()-32)
                    val checksum=ByteArray(32);input.readFully(checksum);val body=ByteArray(size);input.readFully(body)
                    require(MessageDigest.isEqual(checksum,hash(body))){"Canvas project is damaged"}
                    DataInputStream(ByteArrayInputStream(body)).use{data->
                        val id=data.readUTF();require(id==expectedId){"This project belongs to another source"}
                        val name=data.readUTF();val digest=data.readUTF();val count=data.readInt();require(count in 1..ImportedPhotoOriginals.MAX_SOURCES)
                        val originals=List(count){
                            val length=data.readInt();require(length in 1..2048&&length<=data.available())
                            val encoded=ByteArray(length);data.readFully(encoded);val uri=encoded.toString(Charsets.UTF_8)
                            require(uri.toByteArray(Charsets.UTF_8).contentEquals(encoded));uri
                        }
                        require(originals.distinct().size==originals.size)
                        val length=data.readInt();require(length in 1..PhotoSessionCodec.MAX_BYTES&&length==data.available())
                        val session=ByteArray(length);data.readFully(session)
                        CanvasProject(id,name,digest,PhotoSessionCodec.decode(session,scope(id)).edit,originals)
                    }
                }
                else->throw IllegalArgumentException("Unsupported canvas project")
            }
        }
    }
}
