package com.honorablemobile

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Bounded document transport only. The drawing document codec validates project semantics. */
internal object DrawingProjectFiles {
    const val OPEN_REQUEST = 8302
    const val SAVE_REQUEST = 8303
    const val MAX_BYTES = 2 * 1024 * 1024 + 4096
    const val CHUNK_CHARS = 32768
    fun acceptsRequest(id:Int)=id in 1..1_000_000_000
    fun filename(name:String):String {
        require(name.isNotBlank() && name.length<=40 && name.none{it.isISOControl()}) { "Use a project name of 1–40 characters." }
        val safe=name.trim().replace(Regex("[\\\\/:*?\"<>|]"),"_").trim(' ','.').ifBlank{"Noble project"}
        return "$safe.noble.json"
    }
    fun bytes(text:String):ByteArray {
        require(text.isNotEmpty() && text.length<=MAX_BYTES) { "Project file is empty or too large." }
        val encoded=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
        require(encoded.remaining()<=MAX_BYTES) { "Project file is too large." }
        return ByteArray(encoded.remaining()).also{encoded.get(it)}
    }
    /** Caller validates text before opening/truncating a destination and retains stream ownership. */
    fun write(output:OutputStream,data:ByteArray,cancelled:()->Boolean={false}) {
        require(data.isNotEmpty()&&data.size<=MAX_BYTES){"Project file is empty or too large."}
        fun active(){check(!cancelled()&&!Thread.currentThread().isInterrupted){"Project file writing stopped."}}
        var offset=0
        while(offset<data.size){active();val count=minOf(8192,data.size-offset);output.write(data,offset,count);offset+=count}
        active();output.flush();active()
    }
    fun read(input:InputStream,cancelled:()->Boolean={false}):String {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){
            check(!cancelled() && !Thread.currentThread().isInterrupted) { "Project file reading stopped." }
            val count=input.read(buffer)
            if(count<0)break
            if(count==0){val one=input.read();if(one<0)break;require(out.size()<MAX_BYTES){"Project file is too large."};out.write(one)}
            else {require(count<=MAX_BYTES-out.size()){"Project file is too large."};out.write(buffer,0,count)}
        }
        require(out.size()>0){"Project file is empty."}
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString()
    }
    fun chunk(text:String,offset:Int):String {
        require(offset in 0 until text.length){"Project chunk is unavailable."}
        require(offset==0 || !(Character.isLowSurrogate(text[offset]) && Character.isHighSurrogate(text[offset-1]))){"Invalid project chunk boundary."}
        var end=minOf(text.length,offset+CHUNK_CHARS)
        if(end<text.length && Character.isHighSurrogate(text[end-1]) && Character.isLowSurrogate(text[end]))end--
        return text.substring(offset,end)
    }
}

internal class DrawingProjectUpload(val id:Int,val name:String,private val expected:Int) {
    private val text=StringBuilder()
    init { require(DrawingProjectFiles.acceptsRequest(id));DrawingProjectFiles.filename(name);require(expected in 1..DrawingProjectFiles.MAX_BYTES) }
    @Synchronized fun append(value:String){
        require(value.isNotEmpty() && value.length<=DrawingProjectFiles.CHUNK_CHARS && value.length<=expected-text.length){"Invalid project chunk size."}
        text.append(value)
    }
    @Synchronized fun finish():String {
        require(text.length==expected){"Project transfer is incomplete."}
        val value=text.toString();DrawingProjectFiles.bytes(value);return value
    }
}
