package com.honorablemobile

import java.io.IOException
import java.io.OutputStream

/** One flattened, read-only artwork snapshot. No original URI or destination path enters this protocol. */
internal object DrawingPrintPolicy {
    const val CHUNK_CHARS=32768
    const val MAX_CHARS=DrawingPolicy.PNG_PREFIX.length+((DrawingPolicy.MAX_BYTES+2)/3)*4
    const val MAX_PDF_BYTES=32*1024*1024
    fun acceptsRequest(id:Int)=id in 1..1_000_000_000
    data class Placement(val left:Float,val top:Float,val right:Float,val bottom:Float)
    fun fit(width:Int,height:Int,left:Int,top:Int,right:Int,bottom:Int):Placement {
        require(width in 1..2048&&height in 1..2048&&width.toLong()*height<=4_194_304)
        require(left in 0 until 14400&&top in 0 until 14400&&right>left&&right<=14400&&bottom>top&&bottom<=14400) { "Choose a printable page with room for the artwork" }
        val scale=minOf((right-left).toDouble()/width,(bottom-top).toDouble()/height)
        val w=width*scale;val h=height*scale
        val x=left+(right-left-w)/2;val y=top+(bottom-top-h)/2
        return Placement(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()).also {
            require(it.right>it.left&&it.bottom>it.top) { "Artwork is too thin for this page. Choose a larger printable area." }
        }
    }
}

internal class DrawingPrintUpload(val id:Int,private val expected:Int) {
    private val buffer=StringBuilder()
    init { require(DrawingPrintPolicy.acceptsRequest(id));require(expected in (DrawingPolicy.PNG_PREFIX.length+4)..DrawingPrintPolicy.MAX_CHARS) }
    @Synchronized fun append(value:String){
        require(value.isNotEmpty()&&value.length<=DrawingPrintPolicy.CHUNK_CHARS&&value.length<=expected-buffer.length) { "Invalid artwork transfer chunk" }
        buffer.append(value)
    }
    @Synchronized fun finish():String {
        require(buffer.length==expected) { "Artwork transfer is incomplete" }
        return buffer.toString().also { DrawingPolicy.base64Payload(it) }
    }
}

internal class DrawingPrintCancelled:IOException("Printing was cancelled")

/** Bounded cooperative writes to the print framework's descriptor; never opens an app/media URI. */
internal class DrawingPrintOutput(private val output:OutputStream,private val current:()->Boolean,private val budget:Int=DrawingPrintPolicy.MAX_PDF_BYTES):OutputStream() {
    private var written=0
    init { require(budget in 1..DrawingPrintPolicy.MAX_PDF_BYTES) }
    private fun active(){if(!current()||Thread.currentThread().isInterrupted)throw DrawingPrintCancelled()}
    override fun write(value:Int){active();require(written<budget){"Printed artwork exceeds its file budget"};output.write(value);written++}
    override fun write(bytes:ByteArray,offset:Int,length:Int){
        require(offset>=0&&length>=0&&offset<=bytes.size-length)
        active();require(length<=budget-written){"Printed artwork exceeds its file budget"}
        var position=offset;val end=offset+length
        while(position<end){active();val count=minOf(end-position,65536);output.write(bytes,position,count);position+=count;written+=count}
    }
    override fun flush(){active();output.flush()}
}
