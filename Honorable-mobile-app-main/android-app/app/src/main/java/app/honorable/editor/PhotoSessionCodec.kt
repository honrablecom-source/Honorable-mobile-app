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
    const val MAX_BYTES=8_192
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
            val all=listOf(s.edit)+s.undo+s.redo+listOfNotNull(s.pendingExport)
            val version=if(all.any{!it.curve.isIdentity || it.radialLight!=null})2 else 1
            out.writeInt(MAGIC);out.writeInt(version);out.writeUTF(s.scope);writeEdit(out,s.edit,version)
            for(history in listOf(s.undo,s.redo)){out.writeInt(history.size);history.forEach{writeEdit(out,it,version)}}
            out.writeBoolean(s.compareOriginal);out.writeInt(s.activeTool)
            out.writeBoolean(s.pendingExport!=null);s.pendingExport?.let{writeEdit(out,it,version)}
        }
        return bytes.toByteArray().also{require(it.size<=MAX_BYTES)}
    }
    fun decode(bytes:ByteArray,expectedScope:String):PhotoSessionSnapshot {
        require(bytes.size in 1..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt()==MAGIC);val version=input.readInt();require(version in 1..2)
            val scope=input.readUTF();require(scope==expectedScope && scope.matches(Regex("[0-9a-f]{64}")))
            val edit=readEdit(input,version)
            fun history():List<PhotoEdit>{val count=input.readInt();require(count in 0..50);return List(count){readEdit(input,version)}}
            val undo=history();val redo=history();val compare=input.readBoolean();val tool=input.readInt();require(tool in -1..3)
            val pending=if(input.readBoolean())readEdit(input,version)else null
            require(input.available()==0)
            return PhotoSessionSnapshot(scope,edit,undo,redo,compare,tool,pending)
        }
    }
    private fun writeEdit(out:DataOutputStream,e:PhotoEdit,version:Int){
        out.writeInt(e.rotation);out.writeFloat(e.crop);out.writeFloat(e.brightness);out.writeFloat(e.contrast)
        out.writeFloat(e.saturation);out.writeFloat(e.exposure);out.writeBoolean(e.personCutout)
        if(version==2) {
            out.writeFloat(e.curve.shadows);out.writeFloat(e.curve.midtones);out.writeFloat(e.curve.highlights)
            out.writeBoolean(e.radialLight!=null)
            e.radialLight?.let { out.writeFloat(it.centerX);out.writeFloat(it.centerY);out.writeFloat(it.radius);out.writeFloat(it.feather);out.writeFloat(it.exposure) }
        }
    }
    private fun readEdit(input:DataInputStream,version:Int):PhotoEdit {
        val base=PhotoEdit(input.readInt(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readBoolean())
        if(version==1)return base
        val curve=PhotoToneCurve(input.readFloat(),input.readFloat(),input.readFloat())
        val radial=if(input.readBoolean())PhotoRadialLight(input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat())else null
        return base.copy(curve=curve,radialLight=radial)
    }
}
