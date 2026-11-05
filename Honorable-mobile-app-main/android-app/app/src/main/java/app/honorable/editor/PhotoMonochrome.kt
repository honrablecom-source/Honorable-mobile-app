package app.honorable.editor

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.roundToInt

/** Normalized nonnegative contributions. Zero total deliberately produces black; originals stay untouched. */
data class PhotoMonochrome(val enabled:Boolean=false,val red:Float=.2126f,val green:Float=.7152f,val blue:Float=.0722f){
    init {require(listOf(red,green,blue).all{it.isFinite()&&it in 0f..1f})}
    internal fun write(out:DataOutputStream){out.writeBoolean(enabled);out.writeFloat(red);out.writeFloat(green);out.writeFloat(blue)}
    companion object{internal fun read(input:DataInputStream)=PhotoMonochrome(input.readBoolean(),input.readFloat(),input.readFloat(),input.readFloat())}
}

/** Allocation-free scalar conversion shared by photo preview and export; original alpha/hidden RGB retained. */
class PhotoMonochromeKernel(private val edit:PhotoMonochrome){
    private val sum=edit.red.toDouble()+edit.green.toDouble()+edit.blue.toDouble()
    private val r=if(sum>0)edit.red/sum else 0.0
    private val g=if(sum>0)edit.green/sum else 0.0
    private val b=if(sum>0)edit.blue/sum else 0.0
    fun apply(pixel:Int):Int{
        if(!edit.enabled||pixel ushr 24==0)return pixel
        val gray=(((pixel ushr 16)and 255)*r+((pixel ushr 8)and 255)*g+(pixel and 255)*b).roundToInt().coerceIn(0,255)
        return (pixel and -0x1000000)or(gray shl 16)or(gray shl 8)or gray
    }
}
