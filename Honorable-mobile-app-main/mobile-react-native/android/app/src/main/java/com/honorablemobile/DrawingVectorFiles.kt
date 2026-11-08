package com.honorablemobile

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Plain-text transfer only. SVG syntax is checked in the detached web reader. */
internal object DrawingVectorFiles {
    const val MAX_BYTES=64*1024
    const val CHUNK=16*1024
    const val FIRST_CODE=55000
    const val LAST_CODE=59999
    fun acceptsRequest(id:Int)=id in 1..1_000_000_000
    fun acceptsCode(code:Int)=code in FIRST_CODE..LAST_CODE
    fun nextCode(previous:Int):Int {
        require(previous==0||acceptsCode(previous))
        check(previous<LAST_CODE){"Reopen the drawing before choosing another SVG."}
        return if(previous==0)FIRST_CODE else previous+1
    }
    fun read(input:InputStream,cancelled:()->Boolean={false}):String {
        val bytes=ByteArrayOutputStream();val buffer=ByteArray(8192);var emptyReads=0
        fun active(){if(cancelled()||Thread.currentThread().isInterrupted)throw InterruptedIOException("SVG reading stopped")}
        while(true){
            active();val count=input.read(buffer,0,minOf(buffer.size,MAX_BYTES+1-bytes.size()));active()
            if(count<0)break
            if(count==0){check(++emptyReads<=8){"SVG provider did not return data"};continue}
            emptyReads=0;bytes.write(buffer,0,count);require(bytes.size()<=MAX_BYTES){"SVG file exceeds 64 KB"}
        }
        active();require(bytes.size()>0){"SVG file is empty"}
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString().also{require(it.isNotEmpty())}
    }
    fun chunk(text:String,offset:Int):String {
        require(offset>=0&&offset<text.length&&text.length<=MAX_BYTES)
        require(offset==0||!Character.isLowSurrogate(text[offset])||!Character.isHighSurrogate(text[offset-1]))
        var end=minOf(text.length,offset+CHUNK)
        if(end<text.length&&Character.isHighSurrogate(text[end-1])&&Character.isLowSurrogate(text[end]))end--
        return text.substring(offset,end)
    }
}
