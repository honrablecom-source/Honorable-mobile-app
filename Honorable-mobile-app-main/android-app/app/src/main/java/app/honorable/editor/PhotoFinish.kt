package app.honorable.editor

import kotlin.math.pow
import kotlin.math.roundToInt

data class PhotoCaption(val text:String,val x:Float=.5f,val y:Float=.85f,val size:Float=.06f,val light:Boolean=true,val plate:Boolean=true) {
    init {
        require(text.isNotBlank() && text.length<=120 && text.toByteArray(Charsets.UTF_8).size<=360)
        require(text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==text && text.count{it=='\n'}<=2 && text.none{it.isISOControl()&&it!='\n'})
        require(x.isFinite()&&x in .05f.. .95f&&y.isFinite()&&y in .05f.. .95f&&size.isFinite()&&size in .025f.. .15f)
    }
}
data class PhotoFinish(val ratio:Float=0f,val mirrorX:Boolean=false,val mirrorY:Boolean=false,
    val warmth:Float=0f,val tint:Float=0f,val shadows:Float=0f,val highlights:Float=0f,
    val vignette:Float=0f,val sharpen:Float=0f,val caption:PhotoCaption?=null) {
    init {
        require(ratio.isFinite()&&(ratio==0f||ratio in .25f..4f))
        require(listOf(warmth,tint,shadows,highlights).all{it.isFinite()&&it in -1f..1f})
        require(vignette.isFinite()&&vignette in 0f..1f&&sharpen.isFinite()&&sharpen in 0f..1f)
    }
    val isIdentity:Boolean get()=this==PhotoFinish()
}

class PhotoFinishKernel(private val finish:PhotoFinish,private val width:Int,private val height:Int) {
    val active:Boolean get()=finish.warmth!=0f||finish.tint!=0f||finish.shadows!=0f||finish.highlights!=0f||finish.vignette!=0f
    fun apply(pixel:Int,x:Int,y:Int):Int {
        val r=(pixel ushr 16) and 255;val g=(pixel ushr 8) and 255;val b=pixel and 255
        val luma=(.2126f*r+.7152f*g+.0722f*b)/255f
        val gain=2f.pow(finish.shadows*(1-luma)*(1-luma)+finish.highlights*luma*luma)
        val dx=2*(x+.5f)/width-1;val dy=2*(y+.5f)/height-1
        val edge=1-finish.vignette*.8f*((dx*dx+dy*dy)/2).coerceIn(0f,1f)
        fun channel(value:Float)=(value*gain*edge).roundToInt().coerceIn(0,255)
        return (pixel and -0x1000000) or (channel(r*(1+.18f*finish.warmth-.08f*finish.tint)) shl 16) or
            (channel(g*(1+.12f*finish.tint)) shl 8) or channel(b*(1-.18f*finish.warmth-.08f*finish.tint))
    }
    companion object {
        fun sharp(pixel:Int,left:Int,right:Int,up:Int,down:Int,amount:Float):Int {
            if(amount==0f||pixel ushr 24==0)return pixel
            var result=pixel and -0x1000000
            var shift=16
            while(shift>=0){
                val center=(pixel ushr shift) and 255
                val al=(left ushr 24)/255f;val ar=(right ushr 24)/255f;val au=(up ushr 24)/255f;val ad=(down ushr 24)/255f
                val weight=al+ar+au+ad
                val sum=((left ushr shift) and 255)*al+((right ushr shift) and 255)*ar+((up ushr shift) and 255)*au+((down ushr shift) and 255)*ad
                val average=if(weight==0f)center.toFloat()else sum/weight
                result=result or ((center+amount*(center-average)).roundToInt().coerceIn(0,255) shl shift);shift-=8
            }
            return result
        }
    }
}
