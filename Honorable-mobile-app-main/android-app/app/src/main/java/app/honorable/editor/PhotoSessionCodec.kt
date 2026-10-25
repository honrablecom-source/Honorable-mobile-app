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
    const val MAX_BYTES=65_536
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
            val version=if(all.any{!it.creative.isIdentity})5 else if(all.any{!it.finish.isIdentity})4 else if(all.any{it.background!=null})3 else if(all.any{!it.curve.isIdentity || it.radialLight!=null})2 else 1
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
            require(input.readInt()==MAGIC);val version=input.readInt();require(version in 1..5)
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
        if(version>=2) {
            out.writeFloat(e.curve.shadows);out.writeFloat(e.curve.midtones);out.writeFloat(e.curve.highlights)
            out.writeBoolean(e.radialLight!=null)
            e.radialLight?.let { out.writeFloat(it.centerX);out.writeFloat(it.centerY);out.writeFloat(it.radius);out.writeFloat(it.feather);out.writeFloat(it.exposure) }
        }
        if(version>=3){out.writeBoolean(e.background!=null);e.background?.let{out.writeInt(it.mode.ordinal);out.writeInt(it.strength);out.writeInt(it.color)}}
        if(version>=4){val f=e.finish;out.writeFloat(f.ratio);out.writeBoolean(f.mirrorX);out.writeBoolean(f.mirrorY)
            for(v in listOf(f.warmth,f.tint,f.shadows,f.highlights,f.vignette,f.sharpen))out.writeFloat(v)
            out.writeBoolean(f.caption!=null);f.caption?.let{val text=it.text.toByteArray(Charsets.UTF_8);out.writeInt(text.size);out.write(text);out.writeFloat(it.x);out.writeFloat(it.y);out.writeFloat(it.size);out.writeBoolean(it.light);out.writeBoolean(it.plate)}
        }
        if(version>=5)e.creative.write(out)
    }
    private fun readEdit(input:DataInputStream,version:Int):PhotoEdit {
        val base=PhotoEdit(input.readInt(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readBoolean())
        if(version==1)return base
        val curve=PhotoToneCurve(input.readFloat(),input.readFloat(),input.readFloat())
        val radial=if(input.readBoolean())PhotoRadialLight(input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat())else null
        val background=if(version>=3 && input.readBoolean()){
            val mode=input.readInt();require(mode in PhotoBackgroundMode.values().indices)
            PhotoBackground(PhotoBackgroundMode.values()[mode],input.readInt(),input.readInt())
        }else null
        val finish=if(version>=4){
            val ratio=input.readFloat();val mx=input.readBoolean();val my=input.readBoolean()
            val warmth=input.readFloat();val tint=input.readFloat();val shadows=input.readFloat();val highlights=input.readFloat();val vignette=input.readFloat();val sharp=input.readFloat()
            val caption=if(input.readBoolean()){val n=input.readInt();require(n in 1..360);val bytes=ByteArray(n);input.readFully(bytes);PhotoCaption(Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString(),input.readFloat(),input.readFloat(),input.readFloat(),input.readBoolean(),input.readBoolean())}else null
            PhotoFinish(ratio,mx,my,warmth,tint,shadows,highlights,vignette,sharp,caption)
        }else PhotoFinish()
        val creative=if(version>=5)PhotoCreative.read(input)else PhotoCreative()
        return base.copy(curve=curve,radialLight=radial,background=background,finish=finish,creative=creative)
    }
}
