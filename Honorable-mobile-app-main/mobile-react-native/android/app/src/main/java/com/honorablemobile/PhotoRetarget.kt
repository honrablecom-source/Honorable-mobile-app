package com.honorablemobile

import kotlin.math.abs

/** Repeated minimum-energy connected vertical seams, no subject recognition or original writes. */
internal object PhotoRetarget {
    data class Result(val pixels:IntArray,val width:Int,val height:Int,val removedColumns:Int)
    fun percent(size:PhotoPixelDifference.Size,pixels:IntArray,percent:Int,current:()->Unit={}):Result {
        require(percent in 1..12){"Use a whole narrowing percent from1to12"}
        require(size.width>=2){"Photo grid must be at least two pixels wide"}
        return narrow(size,pixels,((size.width*percent+99)/100).coerceAtMost(size.width-1),current)
    }
    fun narrow(size:PhotoPixelDifference.Size,pixels:IntArray,remove:Int,current:()->Unit={}):Result {
        require(pixels.size==size.width*size.height){"Photo grid dimensions changed"}
        require(remove in 1..minOf(128,size.width-1)){"Remove1–128columns while keeping at least one"}
        current()
        val stride=size.width;val height=size.height;val work=pixels.copyOf();var width=stride
        val parents=ByteArray(pixels.size);val seam=IntArray(height)
        var previous=IntArray(stride);var row=IntArray(stride)
        fun delta(a:Int,b:Int):Int {
            val aa=a ushr 24;val ab=b ushr 24
            fun channel(p:Int,shift:Int,alpha:Int)=((p ushr shift and 255)*alpha+127)/255
            return abs(aa-ab)+abs(channel(a,16,aa)-channel(b,16,ab))+abs(channel(a,8,aa)-channel(b,8,ab))+abs(channel(a,0,aa)-channel(b,0,ab))
        }
        repeat(remove){
            for(y in 0 until height){
                current()
                for(x in 0 until width){
                    val energy=delta(work[y*stride+(x-1).coerceAtLeast(0)],work[y*stride+(x+1).coerceAtMost(width-1)])+delta(work[(y-1).coerceAtLeast(0)*stride+x],work[(y+1).coerceAtMost(height-1)*stride+x])
                    if(y==0)row[x]=energy else {
                        var parent=(x-1).coerceAtLeast(0)
                        for(candidate in parent+1..(x+1).coerceAtMost(width-1))if(previous[candidate]<previous[parent])parent=candidate
                        row[x]=previous[parent]+energy;parents[y*stride+x]=(parent-x).toByte()
                    }
                }
                val swap=previous;previous=row;row=swap
            }
            var x=0;for(candidate in 1 until width)if(previous[candidate]<previous[x])x=candidate
            for(y in height-1 downTo 0){seam[y]=x;if(y>0)x+=parents[y*stride+x]}
            for(y in 0 until height){current();val at=y*stride+seam[y];System.arraycopy(work,at+1,work,at,width-seam[y]-1)}
            width--
        }
        val out=IntArray(width*height)
        for(y in 0 until height){current();System.arraycopy(work,y*stride,out,y*width,width)}
        current();return Result(out,width,height,remove)
    }
}
