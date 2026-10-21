package app.honorable.editor

import kotlin.math.pow
import kotlin.math.roundToInt

data class PhotoToneCurve(val shadows:Float=.25f,val midtones:Float=.5f,val highlights:Float=.75f) {
    init { require(shadows.isFinite() && midtones.isFinite() && highlights.isFinite() && shadows>=0 && shadows<=midtones && midtones<=highlights && highlights<=1) }
    val isIdentity:Boolean get() = this == PhotoToneCurve()
    fun table():IntArray {
        val points=floatArrayOf(0f,shadows,midtones,highlights,1f)
        return IntArray(256) { value ->
            val x=value/255f*4;val section=x.toInt().coerceAtMost(3);val fraction=x-section
            ((points[section]+(points[section+1]-points[section])*fraction)*255).roundToInt().coerceIn(0,255)
        }
    }
}

data class PhotoRadialLight(val centerX:Float=.5f,val centerY:Float=.5f,val radius:Float=.35f,val feather:Float=.7f,val exposure:Float=0f) {
    init {
        require(centerX.isFinite() && centerX in 0f..1f && centerY.isFinite() && centerY in 0f..1f)
        require(radius.isFinite() && radius in .05f..1f && feather.isFinite() && feather in 0f..1f)
        require(exposure.isFinite() && exposure in -2f..2f)
    }
}

data class PhotoPoint(val x:Float,val y:Float)

/** Crop geometry matches the raster canvas; positions are continuous pixel coordinates. */
class PhotoToneGeometry(val width:Int,val height:Int,val edit:PhotoEdit) {
    init { require(width>0 && height>0) }
    private val wanted=edit.finish.ratio.let{if(it==0f)0f else if(edit.rotation%180==0)it else 1/it}
    private val baseWidth=(width*edit.crop).toInt().coerceAtLeast(1)
    private val baseHeight=(height*edit.crop).toInt().coerceAtLeast(1)
    val cropWidth=if(wanted>0 && baseWidth.toFloat()/baseHeight>wanted)(baseHeight*wanted).toInt().coerceAtLeast(1)else baseWidth
    val cropHeight=if(wanted>0 && baseWidth.toFloat()/baseHeight<wanted)(baseWidth/wanted).toInt().coerceAtLeast(1)else baseHeight
    val left=(width-cropWidth)/2
    val top=(height-cropHeight)/2
    val outputWidth=if(edit.rotation%180==0)cropWidth else cropHeight
    val outputHeight=if(edit.rotation%180==0)cropHeight else cropWidth
    fun sourceX(x:Float,y:Float):Float {
        val local=when(edit.rotation){0->x;90->y;180->cropWidth-x;else->cropWidth-y}
        return left+if(edit.finish.mirrorX)cropWidth-local else local
    }
    fun sourceY(x:Float,y:Float):Float {
        val local=when(edit.rotation){0->y;90->cropHeight-x;180->cropHeight-y;else->x}
        return top+if(edit.finish.mirrorY)cropHeight-local else local
    }
    fun sourcePoint(x:Float,y:Float)=PhotoPoint(sourceX(x,y),sourceY(x,y))
    fun outputPoint(sourceX:Float,sourceY:Float):PhotoPoint {
        val localX=sourceX-left;val localY=sourceY-top
        val x=if(edit.finish.mirrorX)cropWidth-localX else localX
        val y=if(edit.finish.mirrorY)cropHeight-localY else localY
        return when(edit.rotation){0->PhotoPoint(x,y);90->PhotoPoint(cropHeight-y,x);180->PhotoPoint(cropWidth-x,cropHeight-y);else->PhotoPoint(y,cropWidth-x)}
    }
    fun normalizedSourcePoint(x:Float,y:Float):PhotoPoint = sourcePoint(x,y).let { PhotoPoint((it.x/width).coerceIn(0f,1f),(it.y/height).coerceIn(0f,1f)) }
}

/** Row renderer uses constant-size scratch memory and never changes alpha. */
class PhotoToneKernel(private val edit:PhotoEdit,width:Int,height:Int) {
    private val curve=edit.curve.table()
    private val geometry=PhotoToneGeometry(width,height,edit)
    private val light=edit.radialLight?.takeIf { it.exposure!=0f }
    private val centerX=(light?.centerX?:0f)*width
    private val centerY=(light?.centerY?:0f)*height
    private val radius=(light?.radius?:1f)*minOf(width,height)
    private val outer=radius*radius
    private val inner=outer*(1-(light?.feather?:0f))*(1-(light?.feather?:0f))
    private val gains=FloatArray(256) { 2.0.pow((light?.exposure?:0f)*it/255.0).toFloat() }
    val active:Boolean get() = !edit.curve.isIdentity || light!=null
    fun apply(pixel:Int,x:Int,y:Int):Int {
        var gain=1f
        if(light!=null) {
            val dx=geometry.sourceX(x+.5f,y+.5f)-centerX
            val dy=geometry.sourceY(x+.5f,y+.5f)-centerY
            val distance=dx*dx+dy*dy
            val weight=if(distance>=outer)0f else if(distance<=inner || outer==inner)1f else {
                val t=((distance-inner)/(outer-inner)).coerceIn(0f,1f);1-t*t*(3-2*t)
            }
            gain=gains[(weight*255).roundToInt().coerceIn(0,255)]
        }
        val red=(curve[(pixel ushr 16) and 255]*gain).roundToInt().coerceIn(0,255)
        val green=(curve[(pixel ushr 8) and 255]*gain).roundToInt().coerceIn(0,255)
        val blue=(curve[pixel and 255]*gain).roundToInt().coerceIn(0,255)
        return (pixel and -0x1000000) or (red shl 16) or (green shl 8) or blue
    }
}
