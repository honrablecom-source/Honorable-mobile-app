package app.honorable.editor

import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Collections

enum class PhotoObjectType { RECTANGLE, ELLIPSE, TEXT }

/** Output-relative geometry. Objects are drawn after photo composition, identically in preview/export. */
data class PhotoObject(
    val id:String,
    val type:PhotoObjectType,
    val x:Float=.5f,val y:Float=.5f,
    val width:Float=.35f,val height:Float=.2f,
    val rotation:Float=0f,val opacity:Float=1f,
    val color:Int=-0x1000000,val text:String="",val textSize:Float=.06f,
    val visible:Boolean=true,val locked:Boolean=false,val curve:Float=0f,val textStyle:PhotoTextStyle=PhotoTextStyle(),
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid object identifier" }
        require(listOf(x,y,width,height,rotation,opacity,textSize,curve).all{it.isFinite()})
        require(x in 0f..1f && y in 0f..1f && width in .02f..1f && height in .02f..1f)
        require(rotation in -180f..180f && opacity in 0f..1f && textSize in .02f.. .2f)
        require(text.length<=200 && text.count{it=='\n'}<=2 && text.none{it.isISOControl()&&it!='\n'})
        require(text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==text)
        require(curve==0f || kotlin.math.abs(curve) in 1f..180f)
        require(type==PhotoObjectType.TEXT || (curve==0f && textStyle==PhotoTextStyle()))
        require(type!=PhotoObjectType.TEXT || text.isNotBlank()) { "Write text for this object" }
    }
}

class PhotoLayers(objects:List<PhotoObject> = emptyList()) {
    val objects:List<PhotoObject> = Collections.unmodifiableList(objects.toList())
    init { require(this.objects.size<=32) { "Use up to 32 composition objects" };require(this.objects.map{it.id}.distinct().size==this.objects.size) }
    fun add(value:PhotoObject)=PhotoLayers(objects+value)
    fun update(value:PhotoObject):PhotoLayers {
        val current=requireNotNull(objects.find{it.id==value.id}) { "Object is unavailable" }
        require(!current.locked || value==current.copy(locked=false) || value==current.copy(visible=!current.visible)) { "Unlock this object before changing it" }
        return PhotoLayers(objects.map{if(it.id==value.id)value else it})
    }
    fun withVisibility(visible:Boolean)=PhotoLayers(objects.map{it.copy(visible=visible)})
    fun withLocking(locked:Boolean)=PhotoLayers(objects.map{it.copy(locked=locked)})
    fun delete(id:String):PhotoLayers { val current=requireNotNull(objects.find{it.id==id});require(!current.locked){"Unlock this object before deleting it"};return PhotoLayers(objects.filterNot{it.id==id}) }
    fun move(id:String,toFront:Boolean):PhotoLayers { val current=requireNotNull(objects.find{it.id==id});require(!current.locked){"Unlock this object before moving it"};val other=objects.filterNot{it.id==id};return PhotoLayers(if(toFront)other+current else listOf(current)+other) }
    override fun equals(other:Any?)=other is PhotoLayers && objects==other.objects
    override fun hashCode()=objects.hashCode()
    internal fun write(out:DataOutputStream,version:Int=9) {
        out.writeInt(objects.size)
        for(value in objects){out.writeUTF(value.id);out.writeInt(value.type.ordinal);for(number in listOf(value.x,value.y,value.width,value.height,value.rotation,value.opacity))out.writeFloat(number);out.writeInt(value.color);out.writeUTF(value.text);out.writeFloat(value.textSize);out.writeBoolean(value.visible);out.writeBoolean(value.locked);if(version>=9){out.writeFloat(value.curve);value.textStyle.write(out)}}
    }
    companion object {
        internal fun read(input:DataInputStream,version:Int=9):PhotoLayers {
            val count=input.readInt();require(count in 0..32)
            return PhotoLayers(List(count){
                val id=input.readUTF();val type=input.readInt();require(type in PhotoObjectType.entries.indices)
                val value=PhotoObject(id,PhotoObjectType.entries[type],input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readInt(),input.readUTF(),input.readFloat(),input.readBoolean(),input.readBoolean())
                if(version>=9)value.copy(curve=input.readFloat(),textStyle=PhotoTextStyle.read(input))else value
            })
        }
    }
}

/** Shared finite plan used by the Android renderer and source-derived workspace diagrams. */
data class PhotoObjectPlan(val centerX:Float,val centerY:Float,val width:Float,val height:Float,val textSize:Float) {
    companion object {
        fun create(value:PhotoObject,width:Int,height:Int):PhotoObjectPlan {
            require(width>0&&height>0)
            return PhotoObjectPlan(value.x*width,value.y*height,(value.width*width).coerceAtLeast(1f),(value.height*height).coerceAtLeast(1f),(value.textSize*minOf(width,height)).coerceAtLeast(1f))
        }
    }
}
