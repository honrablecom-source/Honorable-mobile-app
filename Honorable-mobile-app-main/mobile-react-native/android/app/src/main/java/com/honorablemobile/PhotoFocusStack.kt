package com.honorablemobile

import kotlin.math.abs

/** Contrast-selected focus brackets. Requires prealigned equal-size decoded samples. */
internal object PhotoFocusStack {
    class Result(val pixels:IntArray,val changedPixels:Int,val contributions:IntArray)
    fun create(size:PhotoPixelDifference.Size,frames:List<IntArray>,current:()->Unit={}):Result {
        require(frames.size in 2..5){"Select two to five aligned focus-bracket photos"}
        val w=size.width;val h=size.height;val count=w*h
        require(frames.all{it.size==count}){"Focus grids must have matching dimensions"}
        val best=IntArray(count){-1};val winner=IntArray(count);val output=IntArray(count)
        val gray=IntArray(count);val stride=w+1;val integral=IntArray(stride*(h+1))
        for(f in frames.indices){
            current();integral.fill(0)
            for(i in 0 until count){
                if(i%1024==0)current()
                val p=frames[f][i];val a=p ushr 24
                val luma=((p ushr 16 and 255)*77+(p ushr 8 and 255)*150+(p and 255)*29+128) ushr 8
                gray[i]=(luma*a+127)/255
            }
            for(y in 0 until h){
                current();var row=0
                for(x in 0 until w){
                    val i=y*w+x
                    val lap=abs(4*gray[i]-gray[y*w+(x-1).coerceAtLeast(0)]-gray[y*w+(x+1).coerceAtMost(w-1)]-gray[(y-1).coerceAtLeast(0)*w+x]-gray[(y+1).coerceAtMost(h-1)*w+x])
                    row+=lap;integral[(y+1)*stride+x+1]=row+integral[y*stride+x+1]
                }
            }
            for(y in 0 until h){
                current();val top=(y-2).coerceAtLeast(0);val bottom=(y+3).coerceAtMost(h)
                for(x in 0 until w){
                    val left=(x-2).coerceAtLeast(0);val right=(x+3).coerceAtMost(w);val i=y*w+x
                    val score=integral[bottom*stride+right]-integral[top*stride+right]-integral[bottom*stride+left]+integral[top*stride+left]
                    if(score>best[i]){best[i]=score;winner[i]=f;output[i]=frames[f][i]}
                }
            }
        }
        val contributions=IntArray(frames.size);var changed=0
        for(i in 0 until count){if(i%1024==0)current();contributions[winner[i]]++;if(output[i]!=frames[0][i])changed++}
        current();return Result(output,changed,contributions)
    }
}
