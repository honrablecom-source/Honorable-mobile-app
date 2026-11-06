package com.honorablemobile

import java.io.File
import java.io.FileOutputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Bounded transport for SVG from our trusted bundled renderer, not an arbitrary SVG importer. */
internal object DrawingSvgShare {
    const val MAX_BYTES=2*1024*1024
    const val CHUNK_CHARS=32768
    fun acceptsRequest(id:Int)=id in 1..1_000_000_000
    fun bytes(value:String):ByteArray {
        require(value.isNotBlank()&&value.length<=MAX_BYTES) { "SVG artwork is empty or too large" }
        require(Regex("^\\s*<svg(?:\\s|>)").containsMatchIn(value)&&value.trimEnd().endsWith("</svg>")) { "Prepare SVG artwork first" }
        require(value.none{(it.code<32&&it !in listOf('\t','\n','\r'))||it=='\uFFFE'||it=='\uFFFF'}) { "SVG text contains unsupported controls" }
        require(!value.contains("<!DOCTYPE",true)&&!value.contains("<!ENTITY",true)) { "SVG declarations are unsupported" }
        require(!Regex("<\\s*(?:script|foreignObject)(?:\\s|>)",RegexOption.IGNORE_CASE).containsMatchIn(value)) { "Unsupported SVG content" }
        val encoded=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
        require(encoded.remaining()<=MAX_BYTES) { "SVG artwork exceeds its file budget" }
        return ByteArray(encoded.remaining()).also{encoded.get(it)}
    }
    fun prepare(directory:File,value:String,requireCurrent:()->Unit):File {
        val data=bytes(value);requireCurrent()
        check(directory.isDirectory||directory.mkdirs()) { "SVG cache is unavailable" }
        val file=File.createTempFile("noble-artwork-",".svg",directory)
        var kept=false
        try {
            FileOutputStream(file).use{output->
                var position=0
                while(position<data.size){requireCurrent();val count=minOf(65536,data.size-position);output.write(data,position,count);position+=count}
                requireCurrent();output.fd.sync()
            }
            requireCurrent();check(file.length()==data.size.toLong()) { "SVG copy is incomplete" }
            val verified=file.inputStream().use{DrawingProjectFiles.read(it){requireCurrent();false}}
            check(verified==value) { "SVG copy differs from prepared artwork" }
            requireCurrent();kept=true;return file
        }finally{if(!kept)file.delete()}
    }
}

internal class DrawingSvgUpload(val id:Int,private val expected:Int) {
    private val value=StringBuilder()
    init{require(DrawingSvgShare.acceptsRequest(id));require(expected in 1..DrawingSvgShare.MAX_BYTES)}
    @Synchronized fun append(chunk:String){require(chunk.isNotEmpty()&&chunk.length<=DrawingSvgShare.CHUNK_CHARS&&chunk.length<=expected-value.length);value.append(chunk)}
    @Synchronized fun finish():String {require(value.length==expected){"SVG transfer is incomplete"};return value.toString()}
}
