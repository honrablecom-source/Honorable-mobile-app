package com.honorablemobile

import kotlin.math.abs
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException

/** One local preparation budget, shared across header/EXIF/decode streams. */
internal class PhotoDifferenceReadBudget(val perStream:Long=64L*1024*1024,val totalLimit:Long=256L*1024*1024,private val current:()->Unit={}){
    init{require(perStream in 1..64L*1024*1024&&totalLimit in 1..256L*1024*1024)}
    var bytesRead=0L;private set
    private var failed=false
    private fun healthy(){current();check(!failed){"Photo input could not be read completely"}}
    fun wrap(source:InputStream):Stream {healthy();return Stream(source)}
    inner class Stream internal constructor(private val source:InputStream):FilterInputStream(source){
        private var count=0L;private var emptyReads=0
        private fun remaining():Long {healthy();val n=minOf(perStream-count,totalLimit-bytesRead);if(n<=0){failed=true;error("Photo read limit reached")};return n}
        private fun accept(n:Int){if(n>0){count+=n;bytesRead+=n;emptyReads=0}else if(n==0){emptyReads++;if(emptyReads>=3){failed=true;error("Photo input made no progress")}};healthy()}
        override fun read():Int{remaining();val n=try{source.read()}catch(error:Exception){failed=true;throw error};if(n>=0)accept(1)else healthy();return n}
        override fun read(bytes:ByteArray,offset:Int,length:Int):Int{
            if(length==0){healthy();return 0};val n=minOf(length.toLong(),remaining(),64*1024L).toInt()
            val read=try{source.read(bytes,offset,n)}catch(error:Exception){failed=true;throw error};accept(read);return read
        }
        override fun skip(amount:Long):Long{if(amount<=0)return 0;var left=amount;val buffer=ByteArray(8192);while(left>0){val n=read(buffer,0,minOf(buffer.size.toLong(),left).toInt());if(n<0)break;if(n==0){failed=true;error("Photo input made no progress")};left-=n};return amount-left}
        override fun markSupported()=false
        override fun reset(){failed=true;throw IOException("Photo stream cannot rewind")}
        override fun available():Int{healthy();return try{minOf(source.available(),64*1024,maxOf(0L,minOf(perStream-count,totalLimit-bytesRead)).toInt())}catch(error:Exception){failed=true;throw error}}
        fun finish(){healthy();check(count<perStream&&bytesRead<totalLimit){failed=true;"Photo read limit reached"}}
    }
}

/** Integer decoded-pixel comparison, not image alignment or perceptual similarity. */
internal object PhotoPixelDifference {
    const val MAX_EDGE=1024
    const val MAX_PIXELS=1_048_576
    data class Size(val width:Int,val height:Int){init{require(width in 1..MAX_EDGE&&height in 1..MAX_EDGE&&width.toLong()*height<=MAX_PIXELS)}}
    data class Original(val width:Int,val height:Int,val orientation:Int){
        init{require(width in 1..32768&&height in 1..32768&&width.toLong()*height<=100_000_000&&orientation in 1..8){"Photo dimensions or orientation exceed comparison limits"}}
        val orientedWidth get()=if(orientation>=5)height else width
        val orientedHeight get()=if(orientation>=5)width else height
    }
    data class Plan(val size:Size,val sample:Int,val sourceWidth:Int,val sourceHeight:Int){val sampled get()=size.width!=sourceWidth||size.height!=sourceHeight}
    fun plan(left:Original,right:Original):Plan {
        require(left.orientedWidth==right.orientedWidth&&left.orientedHeight==right.orientedHeight){"Choose photos with the same orientation-corrected source dimensions. No automatic alignment or crop is performed"}
        var sample=1
        while((left.orientedWidth.toLong()+sample-1)/sample>MAX_EDGE||(left.orientedHeight.toLong()+sample-1)/sample>MAX_EDGE)sample*=2
        return Plan(Size((left.orientedWidth+sample-1)/sample,(left.orientedHeight+sample-1)/sample),sample,left.orientedWidth,left.orientedHeight)
    }
    /** Normalize decoder rounding and readable EXIF into a shared nearest-neighbor grid. */
    fun normalize(raw:IntArray,width:Int,height:Int,orientation:Int,target:Size,current:()->Unit={}):IntArray {
        require(width in 1..MAX_EDGE&&height in 1..MAX_EDGE&&raw.size==width*height&&orientation in 1..8)
        val ow=if(orientation>=5)height else width;val oh=if(orientation>=5)width else height
        return IntArray(target.width*target.height).also{out->
            for(y in 0 until target.height){if(y%16==0)current();val oy=(y.toLong()*oh/target.height).toInt()
                for(x in 0 until target.width){val ox=(x.toLong()*ow/target.width).toInt()
                    val index=when(orientation){1->oy*width+ox;2->oy*width+width-1-ox;3->(height-1-oy)*width+width-1-ox;4->(height-1-oy)*width+ox;5->ox*width+oy;6->(height-1-ox)*width+oy;7->(height-1-ox)*width+width-1-oy;else->ox*width+width-1-oy}
                    out[y*target.width+x]=raw[index]
                }
            };current()
        }
    }
    class Result(val pixels:IntArray,val changedPixels:Int,val maximumDelta:Int,val meanDelta:Double)
    fun compare(size:Size,left:IntArray,right:IntArray,threshold:Int,current:()->Unit={}):Result {
        val count=size.width*size.height;require(left.size==count&&right.size==count&&threshold in 0..255){"Choose a threshold from 0 to 255"}
        var changed=0;var maximum=0;var sum=0L
        val out=IntArray(count)
        val shifts=intArrayOf(16,8,0)
        fun premultiplied(c:Int,a:Int)=(c*a+127)/255
        for(i in 0 until count){if(i%4096==0)current()
            val a=left[i] ushr 24;val b=right[i] ushr 24
            var delta=abs(a-b)
            for(shift in shifts)delta=maxOf(delta,abs(premultiplied(left[i] ushr shift and 255,a)-premultiplied(right[i] ushr shift and 255,b)))
            maximum=maxOf(maximum,delta);sum+=delta
            if(delta>threshold){changed++;out[i]=-1}else out[i]=0xff000000.toInt()
        }
        current();return Result(out,changed,maximum,sum.toDouble()/count)
    }
}

/** Owns only newly produced preview artifacts; stale tokens cannot consume a successor. */
internal class PhotoDifferenceLease<T>(private val token:(T)->String,private val dispose:(T)->Unit){
    private var value:T?=null
    @Synchronized fun offer(next:T){val old=value;value=next;if(old!==next)old?.let(dispose)}
    @Synchronized fun peek(id:String):T?=value?.takeIf{token(it)==id}
    @Synchronized fun take(id:String):T? {val owned=peek(id)?:return null;value=null;return owned}
    @Synchronized fun release(id:String){take(id)?.let(dispose)}
    @Synchronized fun clear(){val old=value;value=null;old?.let(dispose)}
}
