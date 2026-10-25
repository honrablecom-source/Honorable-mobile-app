package app.honorable.editor

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

enum class PhotoCompositionTool(val title:String) {
    CANVAS("Canvas padding"), FRAME("Photo frame"), SHADOW("Drop shadow"), REFLECTION("Photo reflection"),
    SHAPE("Shape cutout"), TILES("Repeat pattern"), KALEIDOSCOPE("Kaleidoscope"),
    PERSPECTIVE("Perspective poster"), WATERMARK("Repeated watermark"), COLOR_KEY("Color-key transparency")
}

/** Local presentation state; colors are output colors, never application theme colors. */
data class PhotoComposition(
    val padding:Float=0f, val canvasColor:Int=0, val frame:Float=0f, val frameColor:Int=-0x1000000,
    val shadow:Float=0f, val reflection:Float=0f, val shape:Int=0, val tiles:Int=1,
    val sectors:Int=0, val perspective:Float=0f, val watermark:String="", val watermarkOpacity:Float=.2f,
    val watermarkLight:Boolean=true, val keyColor:Int?=null, val keyTolerance:Float=.15f,
) {
    init {
        require(padding.isFinite() && padding in 0f.. .4f)
        require(frame.isFinite() && frame in 0f.. .12f)
        require(shadow.isFinite() && shadow in 0f..1f)
        require(reflection.isFinite() && reflection in 0f.. .5f)
        require(shape in 0..4 && tiles in 1..6 && (sectors==0 || sectors in 2..12))
        require(perspective.isFinite() && perspective in -.4f.. .4f)
        require(watermark.isEmpty() || watermark.isNotBlank())
        require(watermark.length<=60 && watermark.toByteArray(Charsets.UTF_8).size<=180 && watermark.none{it.isISOControl()})
        require(watermark.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==watermark)
        require(watermarkOpacity.isFinite() && watermarkOpacity in .05f.. .6f)
        require(keyColor==null || keyColor in 0..0xFFFFFF)
        require(keyTolerance.isFinite() && keyTolerance in .02f.. .8f)
    }
    val needsPixels:Boolean get()=shape!=0 || tiles!=1 || sectors!=0 || keyColor!=null
    val needsCanvas:Boolean get()=padding!=0f || frame!=0f || shadow!=0f || reflection!=0f || perspective!=0f
    val isIdentity:Boolean get()=!needsPixels && !needsCanvas && watermark.isEmpty()
    fun write(out:DataOutputStream) {
        out.writeFloat(padding);out.writeInt(canvasColor);out.writeFloat(frame);out.writeInt(frameColor)
        out.writeFloat(shadow);out.writeFloat(reflection);out.writeInt(shape);out.writeInt(tiles);out.writeInt(sectors)
        out.writeFloat(perspective);out.writeUTF(watermark);out.writeFloat(watermarkOpacity);out.writeBoolean(watermarkLight)
        out.writeBoolean(keyColor!=null);keyColor?.let{out.writeInt(it)};out.writeFloat(keyTolerance)
    }
    companion object {
        fun read(input:DataInputStream)=PhotoComposition(input.readFloat(),input.readInt(),input.readFloat(),input.readInt(),
            input.readFloat(),input.readFloat(),input.readInt(),input.readInt(),input.readInt(),input.readFloat(),
            input.readUTF(),input.readFloat(),input.readBoolean(),if(input.readBoolean())input.readInt()else null,input.readFloat())
    }
}

/** Output never exceeds the decoded photo's pixel budget, including reflections and shadows. */
data class PhotoCompositionPlan(val width:Int,val height:Int,val scale:Float,val left:Float,val top:Float,val frame:Float,val reflectionHeight:Float,val reflectionTop:Float) {
    companion object {
        fun create(width:Int,height:Int,value:PhotoComposition):PhotoCompositionPlan {
            require(width in 1..8192 && height in 1..8192 && width.toLong()*height<=6_000_000)
            val unit=minOf(width,height).toFloat()
            val frame=unit*value.frame
            val margin=unit*(value.padding+value.shadow*.20f)+frame
            val reflection=height*value.reflection
            val logicalWidth=width+2*margin
            val logicalHeight=height+2*margin+reflection+(if(reflection>0)unit*.015f else 0f)
            val scale=minOf(1.0,sqrt(width.toDouble()*height/(logicalWidth.toDouble()*logicalHeight))).toFloat()
            return PhotoCompositionPlan(maxOf(1,floor(logicalWidth*scale).toInt()),maxOf(1,floor(logicalHeight*scale).toInt()),scale,margin*scale,margin*scale,frame*scale,reflection*scale,(margin+height+frame+(if(reflection>0)unit*.015f else 0f))*scale)
        }
    }
}

/** Deterministic source-coordinate sampler, also exercised by plain JVM tests. */
class PhotoCompositionKernel(private val pixels:IntArray,private val width:Int,private val height:Int,private val value:PhotoComposition) {
    init {require(width in 1..8192 && height in 1..8192 && width.toLong()*height==pixels.size.toLong() && pixels.size<=6_000_000)}
    fun sample(x:Int,y:Int):Int {
        require(x in 0 until width && y in 0 until height)
        var sx=(x*value.tiles)%width;var sy=(y*value.tiles)%height
        if(value.sectors>0) {
            val nx=2.0*(sx+.5)/width-1;val ny=2.0*(sy+.5)/height-1
            val radius=hypot(nx,ny);val sector=2*PI/value.sectors
            var angle=((atan2(ny,nx)%(sector)+sector)%sector)
            if(angle>sector/2)angle=sector-angle
            sx=floor((radius*cos(angle)+1)*width/2).toInt().coerceIn(0,width-1)
            sy=floor((radius*sin(angle)+1)*height/2).toInt().coerceIn(0,height-1)
        }
        val pixel=pixels[sy*width+sx]
        val nx=2.0*(x+.5)/width-1;val ny=2.0*(y+.5)/height-1
        val inside=when(value.shape) {
            1->{val dx=(x+.5-width/2.0)/(minOf(width,height)/2.0);val dy=(y+.5-height/2.0)/(minOf(width,height)/2.0);dx*dx+dy*dy<=1}
            2->{val r=minOf(width,height)*.18;val dx=max(abs(x+.5-width/2.0)-(width/2.0-r),0.0);val dy=max(abs(y+.5-height/2.0)-(height/2.0-r),0.0);dx*dx+dy*dy<=r*r}
            3->abs(nx)+abs(ny)<=1
            4->{val hx=nx*1.25;val hy=-ny*1.25;val a=hx*hx+hy*hy-1;a*a*a-hx*hx*hy*hy*hy<=0}
            else->true
        }
        if(!inside)return 0
        val key=value.keyColor?:return pixel
        val dr=((pixel ushr 16)and 255)-((key ushr 16)and 255)
        val dg=((pixel ushr 8)and 255)-((key ushr 8)and 255)
        val db=(pixel and 255)-(key and 255)
        val distance=sqrt((dr*dr+dg*dg+db*db).toDouble())/sqrt(3.0*255*255)
        val alpha=((pixel ushr 24)*((distance-value.keyTolerance*.6)/(value.keyTolerance*.4)).coerceIn(0.0,1.0)).roundToInt()
        return (pixel and 0xFFFFFF) or (alpha shl 24)
    }
}
