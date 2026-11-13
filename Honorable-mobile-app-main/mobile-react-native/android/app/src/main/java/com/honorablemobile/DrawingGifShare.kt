package com.honorablemobile

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** Bounded structural check for our local full-frame GIF encoder, not an arbitrary media decoder. */
internal object DrawingGifShare {
    const val MAX_BYTES=8*1024*1024
    fun validate(data:ByteArray):Int {
        require(data.size in 20..MAX_BYTES&&data.copyOfRange(0,6).toString(Charsets.US_ASCII)=="GIF89a")
        var position=6
        fun byte():Int {require(position<data.size);return data[position++].toInt() and 255}
        fun word()=byte() or (byte() shl 8)
        fun skip(length:Int){require(length>=0&&length<=data.size-position);position+=length}
        fun table(flags:Int){if(flags and 128!=0)skip(3*(1 shl ((flags and 7)+1)))}
        fun blocks():Int {var count=0;while(true){val size=byte();if(size==0)return count;skip(size);count+=size}}
        val width=word();val height=word();require(width in 16..384&&height in 16..384)
        val flags=byte();skip(2);table(flags)
        var frames=0;var delay=0;var duration=0
        while(position<data.size){when(byte()){
            0x21->{val label=byte();if(label==0xf9){require(byte()==4);byte();delay=word();byte();require(byte()==0)}else blocks()}
            0x2c->{require(word()==0&&word()==0&&word()==width&&word()==height);table(byte());require(byte() in 2..8);require(blocks()>0);require(delay in 1..100);duration+=delay;require(duration<=600);delay=0;frames++;require(frames<=60)}
            0x3b->{require(position==data.size&&frames in 2..60);return frames}
            else->error("Unsupported GIF block")
        }}
        error("GIF transfer is incomplete")
    }
    fun prepare(directory:File,data:ByteArray,current:()->Unit):File {
        validate(data);current();check(directory.isDirectory||directory.mkdirs())
        val file=File.createTempFile("noble-animation-",".gif",directory);var kept=false
        try{FileOutputStream(file).use{out->var offset=0;while(offset<data.size){current();val count=minOf(65536,data.size-offset);out.write(data,offset,count);offset+=count};current();out.fd.sync()};current();check(file.length()==data.size.toLong()&&file.readBytes().contentEquals(data));current();kept=true;return file}
        finally{if(!kept)file.delete()}
    }
}
internal class DrawingGifUpload(val id:Int,private val expected:Int){
    private val bytes=ByteArrayOutputStream();private var sealed=false
    init{require(id in 1..1_000_000_000&&expected in 20..DrawingGifShare.MAX_BYTES)}
    @Synchronized fun append(value:String){require(!sealed&&value.isNotEmpty()&&value.length<=32768&&value.length%4==0&&value.matches(Regex("[A-Za-z0-9+/]+={0,2}")));val data=Base64.decode(value,Base64.NO_WRAP);require(data.isNotEmpty()&&data.size<=expected-bytes.size()&&Base64.encodeToString(data,Base64.NO_WRAP)==value);bytes.write(data)}
    @Synchronized fun finish():ByteArray{require(!sealed&&bytes.size()==expected){"GIF transfer is incomplete"};sealed=true;return bytes.toByteArray().also{DrawingGifShare.validate(it)}}
}
