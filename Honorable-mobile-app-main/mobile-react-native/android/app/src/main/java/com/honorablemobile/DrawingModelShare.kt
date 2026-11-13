package com.honorablemobile

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Base64

/** Receives only bounded, locally generated GLB copies from our trusted bundled workspace. */
internal class DrawingModelUpload(val id:Int, private val expected:Int) {
    companion object {
        const val MAX_BYTES=8*1024*1024;const val CHUNK_CHARS=32768
        fun validate(data:ByteArray) {
            require(data.size in 20..MAX_BYTES)
            val header=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            require(header.int==0x46546c67&&header.int==2&&header.int==data.size){"Unsupported model file"}
            var offset=12;var chunks=0
            while(offset<data.size) {
                require(data.size-offset>=8)
                val length=header.getInt(offset);val kind=header.getInt(offset+4)
                require(length>=0&&length%4==0&&length<=data.size-offset-8)
                require(if(chunks==0)kind==0x4e4f534a&&length>0 else chunks==1&&kind==0x004e4942)
                offset+=8+length;chunks++
            }
            require(offset==data.size&&chunks in 1..2)
        }
    }
    private val bytes=ByteArrayOutputStream()
    private var sealed=false
    init { require(id in 1..1_000_000_000);require(expected in 20..MAX_BYTES) }
    @Synchronized fun append(value:String) {
        require(!sealed&&value.isNotEmpty()&&value.length<=CHUNK_CHARS&&value.length%4==0)
        require(Regex("[A-Za-z0-9+/]+={0,2}").matches(value))
        val data=Base64.decode(value,Base64.NO_WRAP)
        require(data.isNotEmpty()&&data.size<=expected-bytes.size())
        require(Base64.encodeToString(data,Base64.NO_WRAP)==value)
        bytes.write(data)
    }
    @Synchronized fun finish():ByteArray {
        require(!sealed&&bytes.size()==expected){"Model transfer is incomplete"};sealed=true
        val data=bytes.toByteArray();validate(data)
        return data
    }
}
internal object DrawingModelShare {
    fun prepare(directory:File,data:ByteArray,current:()->Unit):File {
        current();check(directory.isDirectory||directory.mkdirs())
        val file=File.createTempFile("noble-model-",".glb",directory);var kept=false
        try {
            FileOutputStream(file).use { out ->
                var offset=0
                while(offset<data.size){current();val count=minOf(65536,data.size-offset);out.write(data,offset,count);offset+=count}
                current();out.fd.sync()
            }
            current();check(file.length()==data.size.toLong());check(file.readBytes().contentEquals(data));current();kept=true;return file
        } finally { if(!kept)file.delete() }
    }
}

/** Private same-UID handoff, kept across screen recreation until the activity actually finishes. */
internal object DrawingDepthHandoff {
    const val MAX_BYTES=3*1024*1024
    fun file(directory:File,path:String):File {
        val parent=directory.canonicalFile
        val file=File(path).canonicalFile
        require(file.parentFile==parent&&file.isFile&&file.length() in 24..MAX_BYTES.toLong()) { "Depth handoff is unavailable" }
        return file
    }
    fun read(file:File):ByteArray {
        require(file.length() in 24..MAX_BYTES.toLong())
        val output=ByteArrayOutputStream()
        file.inputStream().use{input->val buffer=ByteArray(65536);while(true){val count=input.read(buffer);if(count<0)break;require(count<=MAX_BYTES-output.size());output.write(buffer,0,count)}}
        return output.toByteArray().also{DrawingModelUpload.validate(it)}
    }
}
