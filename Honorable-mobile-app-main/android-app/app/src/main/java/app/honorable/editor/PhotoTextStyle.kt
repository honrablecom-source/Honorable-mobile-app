package app.honorable.editor

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

/** Built-in platform fonts only. All lengths are relative to the finished canvas's shorter edge. */
data class PhotoTextStyle(
    val family:Int=0,val bold:Boolean=false,val italic:Boolean=false,val alignment:Int=1,
    val tracking:Float=0f,val outline:Float=0f,val outlineColor:Int=-1,
    val shadow:Float=0f,val shadowX:Float=.01f,val shadowY:Float=.01f,val shadowColor:Int=0x99000000.toInt(),
) {
    init {
        require(family in 0..2 && alignment in 0..2)
        require(listOf(tracking,outline,shadow,shadowX,shadowY).all{it.isFinite()})
        require(tracking in -.05f.. .5f && outline in 0f.. .03f && shadow in 0f.. .04f && shadowX in -.05f.. .05f && shadowY in -.05f.. .05f)
    }
    internal fun write(out:DataOutputStream){out.writeInt(family);out.writeBoolean(bold);out.writeBoolean(italic);out.writeInt(alignment);out.writeFloat(tracking);out.writeFloat(outline);out.writeInt(outlineColor);out.writeFloat(shadow);out.writeFloat(shadowX);out.writeFloat(shadowY);out.writeInt(shadowColor)}
    companion object{internal fun read(input:DataInputStream)=PhotoTextStyle(input.readInt(),input.readBoolean(),input.readBoolean(),input.readInt(),input.readFloat(),input.readFloat(),input.readInt(),input.readFloat(),input.readFloat(),input.readFloat(),input.readInt())}
}

/** Centered circular text baseline; Android uses this exact geometry with its own font metrics. */
data class PhotoTextArcPlan(val radius:Float,val centerY:Float,val startDegrees:Float,val sweepDegrees:Float,val length:Float) {
    companion object {
        fun fit(frameWidth:Float,degrees:Float,textAdvance:Float):PhotoTextArcPlan {
            require(textAdvance.isFinite()&&textAdvance>=0)
            val cap=create(frameWidth,degrees)
            val ratio=if(textAdvance<cap.length)textAdvance/cap.length else 1f
            return create((frameWidth*ratio).coerceIn(1f,frameWidth),degrees)
        }
        fun create(width:Float,degrees:Float):PhotoTextArcPlan {
            require(width.isFinite()&&width>=1f && degrees.isFinite() && abs(degrees) in 1f..180f)
            val angle=Math.toRadians(abs(degrees).toDouble());val radius=width/(2*sin(angle/2));val sagitta=radius*(1-cos(angle/2))
            val upward=degrees>0
            val center=(radius*cos(angle/2)+sagitta/2)*(if(upward)1 else -1)
            return PhotoTextArcPlan(radius.toFloat(),center.toFloat(),if(upward)270f-abs(degrees)/2 else 90f+abs(degrees)/2,degrees,(radius*angle).toFloat())
        }
    }
}
