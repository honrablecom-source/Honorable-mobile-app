package app.honorable.editor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

data class PhotoSessionSnapshot(val scope:String,val edit:PhotoEdit,
    val undo:List<PhotoEdit> = emptyList(),val redo:List<PhotoEdit> = emptyList(),
    val compareOriginal:Boolean=false,val activeTool:Int = -1,val pendingExport:PhotoEdit?=null)

/** Small, validated lifecycle state. A mask must be recomputed, never serialized into a Bundle. */
object PhotoSessionCodec {
    private const val MAGIC=0x48504831
    const val MAX_BYTES=4_096
    fun scope(account:String,api:String,source:String):String {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            for(value in listOf(account,api,source)) {
                require(value.isNotBlank() && value.length<=16_000)
                val raw=value.toByteArray(Charsets.UTF_8);out.writeInt(raw.size);out.write(raw)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString(""){"%02x".format(it)}
    }
    fun encode(s:PhotoSessionSnapshot):ByteArray {
        require(s.scope.matches(Regex("[0-9a-f]{64}")) && s.activeTool in -1..3)
        require(s.undo.size<=50 && s.redo.size<=50)
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC);out.writeInt(1);out.writeUTF(s.scope);writeEdit(out,s.edit)
            for(history in listOf(s.undo,s.redo)){out.writeInt(history.size);history.forEach{writeEdit(out,it)}}
            out.writeBoolean(s.compareOriginal);out.writeInt(s.activeTool)
            out.writeBoolean(s.pendingExport!=null);s.pendingExport?.let{writeEdit(out,it)}
        }
        return bytes.toByteArray().also{require(it.size<=MAX_BYTES)}
    }
    fun decode(bytes:ByteArray,expectedScope:String):PhotoSessionSnapshot {
        require(bytes.size in 1..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt()==MAGIC && input.readInt()==1)
            val scope=input.readUTF();require(scope==expectedScope && scope.matches(Regex("[0-9a-f]{64}")))
            val edit=readEdit(input)
            fun history():List<PhotoEdit>{val count=input.readInt();require(count in 0..50);return List(count){readEdit(input)}}
            val undo=history();val redo=history();val compare=input.readBoolean();val tool=input.readInt();require(tool in -1..3)
            val pending=if(input.readBoolean())readEdit(input)else null
            require(input.available()==0)
            return PhotoSessionSnapshot(scope,edit,undo,redo,compare,tool,pending)
        }
    }
    private fun writeEdit(out:DataOutputStream,e:PhotoEdit){
        out.writeInt(e.rotation);out.writeFloat(e.crop);out.writeFloat(e.brightness);out.writeFloat(e.contrast)
        out.writeFloat(e.saturation);out.writeFloat(e.exposure);out.writeBoolean(e.personCutout)
    }
    private fun readEdit(input:DataInputStream)=PhotoEdit(input.readInt(),input.readFloat(),input.readFloat(),
        input.readFloat(),input.readFloat(),input.readFloat(),input.readBoolean())
}
