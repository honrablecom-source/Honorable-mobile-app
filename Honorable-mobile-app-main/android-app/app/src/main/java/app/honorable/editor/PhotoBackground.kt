package app.honorable.editor

import kotlin.math.floor
import kotlin.math.roundToInt

enum class PhotoBackgroundMode { BLUR, SOLID }

data class PhotoBackground(val mode:PhotoBackgroundMode=PhotoBackgroundMode.BLUR,val strength:Int=45,val color:Int=-1) {
    init { require(strength in 1..100);require(color ushr 24==255) }
}

/** Pure pixel operations. The model supplies a source-aligned person matte, never generated pixels. */
object PhotoBackgroundMath {
    fun composite(foreground:Int,background:Int,matte:Int):Int {
        require(matte in 0..255)
        val a=(foreground ushr 24)*matte/255f
        val b=(background ushr 24)*(1-a/255f)
        val total=a+b
        if(total==0f)return 0
        var result=total.roundToInt().coerceIn(0,255) shl 24
        var shift=16
        while(shift>=0) {
            val channel=((((foreground ushr shift) and 255)*a+((background ushr shift) and 255)*b)/total).roundToInt().coerceIn(0,255)
            result=result or (channel shl shift)
            shift-=8
        }
        return result
    }

    /** Bilinear sampling at pixel centers; clamped boundaries prevent crop/rotation seams. */
    fun sample(pixels:IntArray,width:Int,height:Int,x:Float,y:Float):Int {
        val px=(x-.5f).coerceIn(0f,(width-1).toFloat());val py=(y-.5f).coerceIn(0f,(height-1).toFloat())
        val x0=floor(px).toInt();val y0=floor(py).toInt();val x1=minOf(x0+1,width-1);val y1=minOf(y0+1,height-1)
        val fx=px-x0;val fy=py-y0
        val p00=pixels[y0*width+x0];val p10=pixels[y0*width+x1];val p01=pixels[y1*width+x0];val p11=pixels[y1*width+x1]
        val a00=(p00 ushr 24)*(1-fx)*(1-fy);val a10=(p10 ushr 24)*fx*(1-fy)
        val a01=(p01 ushr 24)*(1-fx)*fy;val a11=(p11 ushr 24)*fx*fy
        val total=a00+a10+a01+a11
        if(total==0f)return 0
        var result=total.roundToInt().coerceIn(0,255) shl 24
        var shift=16
        while(shift>=0) {
            val channel=(((p00 ushr shift) and 255)*a00+((p10 ushr shift) and 255)*a10+((p01 ushr shift) and 255)*a01+((p11 ushr shift) and 255)*a11)/total
            result=result or (channel.roundToInt().coerceIn(0,255) shl shift)
            shift-=8
        }
        return result
    }

    /** Three box passes approximate a soft blur. Foreground colors are excluded from its input.
     * Scratch is bounded by the 384-pixel proxy, independent of full export dimensions. */
    fun blur(pixels:IntArray,matte:IntArray,width:Int,height:Int,radius:Int):IntArray {
        require(width in 1..384 && height in 1..384 && pixels.size==width*height && matte.size==pixels.size && radius in 1..24)
        var values=FloatArray(pixels.size*5);var scratch=FloatArray(values.size)
        for(i in pixels.indices) {
            require(matte[i] in 0..255)
            val backgroundWeight=1-matte[i]/255f;val weight=backgroundWeight*(pixels[i] ushr 24)/255f
            values[i*5]=((pixels[i] ushr 16) and 255)*weight;values[i*5+1]=((pixels[i] ushr 8) and 255)*weight
            values[i*5+2]=(pixels[i] and 255)*weight;values[i*5+3]=weight;values[i*5+4]=backgroundWeight
        }
        repeat(3) {
            axis(values,scratch,width,height,radius,true);val swap=values;values=scratch;scratch=swap
            axis(values,scratch,width,height,radius,false);val swap2=values;values=scratch;scratch=swap2
        }
        return IntArray(pixels.size) { i ->
            val weight=values[i*5+3];val backgroundWeight=values[i*5+4]
            if(weight<=.00001f || backgroundWeight<=.00001f)pixels[i] else {
                var pixel=(weight/backgroundWeight*255).roundToInt().coerceIn(0,255) shl 24
                for(channel in 0..2)pixel=pixel or ((values[i*5+channel]/weight).roundToInt().coerceIn(0,255) shl (16-channel*8))
                pixel
            }
        }
    }
    private fun axis(input:FloatArray,output:FloatArray,width:Int,height:Int,radius:Int,horizontal:Boolean) {
        val length=if(horizontal)width else height;val lines=if(horizontal)height else width
        val step=if(horizontal)5 else width*5;val diameter=radius*2+1
        for(line in 0 until lines) {
            val start=if(horizontal)line*width*5 else line*5
            for(channel in 0..4) {
                var sum=0.0
                for(k in -radius..radius)sum+=input[start+k.coerceIn(0,length-1)*step+channel]
                for(position in 0 until length) {
                    output[start+position*step+channel]=(sum/diameter).toFloat()
                    sum-=input[start+(position-radius).coerceIn(0,length-1)*step+channel]
                    sum+=input[start+(position+radius+1).coerceIn(0,length-1)*step+channel]
                }
            }
        }
    }
}
