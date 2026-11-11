package com.honorablemobile

import kotlin.math.abs

/** Robust temporal stack for explicitly aligned captures. Every output color comes from a source. */
internal object PhotoCleanPlate {
    class Result(val pixels:IntArray,val changedPixels:Int)
    fun create(size:PhotoPixelDifference.Size,frames:List<IntArray>,current:()->Unit={}):Result {
        require(frames.size==3||frames.size==5){"Select three or five aligned photo originals"}
        val count=size.width*size.height
        require(frames.all{it.size==count}){"Aligned photo grids must have matching dimensions"}
        val n=frames.size;val middle=n/2
        val alpha=IntArray(n);val red=IntArray(n);val green=IntArray(n);val blue=IntArray(n)
        val out=IntArray(count);var changed=0
        fun premultiply(value:Int,a:Int)=(value*a+127)/255
        for(i in 0 until count){
            if(i%1024==0)current()
            for(f in 0 until n){val pixel=frames[f][i];val a=pixel ushr 24;alpha[f]=a;red[f]=premultiply(pixel ushr 16 and 255,a);green[f]=premultiply(pixel ushr 8 and 255,a);blue[f]=premultiply(pixel and 255,a)}
            alpha.sort();red.sort();green.sort();blue.sort()
            var best=0;var distance=Int.MAX_VALUE
            for(f in 0 until n){
                val pixel=frames[f][i];val a=pixel ushr 24
                val d=abs(a-alpha[middle])+abs(premultiply(pixel ushr 16 and 255,a)-red[middle])+abs(premultiply(pixel ushr 8 and 255,a)-green[middle])+abs(premultiply(pixel and 255,a)-blue[middle])
                if(d<distance){distance=d;best=f}
            }
            out[i]=frames[best][i]
            if(out[i]!=frames[0][i])changed++
        }
        current();return Result(out,changed)
    }
}
