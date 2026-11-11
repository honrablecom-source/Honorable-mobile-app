package com.honorablemobile

import kotlin.math.abs

/** Encoded 8-bit exposure-confidence blend, not HDR reconstruction or image registration. */
internal object PhotoExposureBlend {
    fun create(size:PhotoPixelDifference.Size,a:IntArray,b:IntArray,current:()->Unit={}):PhotoPixelDifference.Result {
        val count=size.width*size.height
        require(a.size==count&&b.size==count){"Exposure bracket grids must match"}
        current();val out=IntArray(count);var changed=0
        fun weight(p:Int):Long {
            val luma=((p ushr 16 and 255)*77+(p ushr 8 and 255)*150+(p and 255)*29+128) ushr 8
            val confidence=256-abs(2*luma-255)
            return confidence.toLong()*confidence*(p ushr 24)
        }
        for(i in 0 until count){
            if(i%1024==0)current()
            val x=a[i];val y=b[i];val ax=x ushr 24;val ay=y ushr 24
            val wx=weight(x);val wy=weight(y);val sum=wx+wy
            val pixel=if(sum==0L)0 else {
                val alphaWeight=wx*ax+wy*ay
                val alpha=((alphaWeight+sum/2)/sum).toInt()
                fun channel(shift:Int)=((wx*ax*(x ushr shift and 255)+wy*ay*(y ushr shift and 255)+alphaWeight/2)/alphaWeight).toInt()
                (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
            out[i]=pixel;if(pixel!=x)changed++
        }
        current();return PhotoPixelDifference.Result(out,changed,0,0.0)
    }
}
