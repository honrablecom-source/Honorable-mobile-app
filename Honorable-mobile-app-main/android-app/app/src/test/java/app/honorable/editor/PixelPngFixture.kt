package app.honorable.editor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Independent PNG pixel oracle using java.base only: AGP's test compiler excludes java.desktop. */
internal object PixelPngFixture {
    data class Image(val width:Int,val height:Int,private val pixels:IntArray){
        fun getRGB(x:Int,y:Int,w:Int,h:Int,output:IntArray?,offset:Int,stride:Int):IntArray{
            require(x>=0&&y>=0&&w>0&&h>0&&x+w<=width&&y+h<=height&&offset>=0&&stride>=w)
            val target=output?:IntArray(offset+(h-1)*stride+w);require(target.size>=offset+(h-1)*stride+w)
            for(row in 0 until h)pixels.copyInto(target,offset+row*stride,(y+row)*width+x,(y+row)*width+x+w)
            return target
        }
    }
    fun write(width:Int,height:Int,pixels:IntArray,file:File):Boolean{
        require(width in 1..4096&&height in 1..4096&&pixels.size==width*height)
        val raw=ByteArrayOutputStream()
        for(y in 0 until height){raw.write(0);for(x in 0 until width){val pixel=pixels[y*width+x];raw.write(pixel ushr 16 and 255);raw.write(pixel ushr 8 and 255);raw.write(pixel and 255);raw.write(pixel ushr 24)}}
        DataOutputStream(file.outputStream()).use{out->
            out.write(byteArrayOf(-119,80,78,71,13,10,26,10))
            fun chunk(name:String,data:ByteArray){val type=name.toByteArray(Charsets.US_ASCII);out.writeInt(data.size);out.write(type);out.write(data);out.writeInt(CRC32().apply{update(type);update(data)}.value.toInt())}
            val header=ByteArrayOutputStream();DataOutputStream(header).use{it.writeInt(width);it.writeInt(height);it.write(byteArrayOf(8,6,0,0,0))};chunk("IHDR",header.toByteArray())
            val compressed=ByteArrayOutputStream();DeflaterOutputStream(compressed).use{it.write(raw.toByteArray())};chunk("IDAT",compressed.toByteArray());chunk("IEND",byteArrayOf())
        };return true
    }
    fun read(file:File)=file.inputStream().use{read(it)}
    fun read(input:InputStream):Image{
        val bytes=input.readBytes();require(bytes.size in 45..16_777_216)
        val source=DataInputStream(ByteArrayInputStream(bytes));val signature=ByteArray(8);source.readFully(signature);require(signature.contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)))
        var width=0;var height=0;var type=-1;var palette=ByteArray(0);var alpha=ByteArray(0);val compressed=ByteArrayOutputStream()
        while(true){
            val length=source.readInt();require(length>=0&&length<=source.available()-8);val name=ByteArray(4);source.readFully(name);val data=ByteArray(length);source.readFully(data)
            val crc=source.readInt();require(CRC32().apply{update(name);update(data)}.value.toInt()==crc)
            when(String(name,Charsets.US_ASCII)){
                "IHDR"->{require(length==13&&width==0);val header=DataInputStream(ByteArrayInputStream(data));width=header.readInt();height=header.readInt();require(width in 1..4096&&height in 1..4096);require(header.readUnsignedByte()==8);type=header.readUnsignedByte();require(type in listOf(0,2,3,4,6));repeat(3){require(header.readUnsignedByte()==0)}}
                "PLTE"->{require(data.size%3==0);palette=data}
                "tRNS"->alpha=data
                "IDAT"->compressed.write(data)
                "IEND"->{require(length==0&&source.available()==0);break}
            }
        }
        val channels=when(type){0,3->1;2->3;4->2;6->4;else->error("Missing PNG header")};val stride=width*channels;val expected=(stride+1)*height
        val raw=InflaterInputStream(ByteArrayInputStream(compressed.toByteArray())).use{stream->val out=ByteArray(expected);var at=0;while(at<out.size){val n=stream.read(out,at,out.size-at);require(n>0);at+=n};require(stream.read()==-1);out}
        val pixels=IntArray(width*height);var previous=ByteArray(stride);var at=0
        fun paeth(a:Int,b:Int,c:Int):Int{val p=a+b-c;val pa=kotlin.math.abs(p-a);val pb=kotlin.math.abs(p-b);val pc=kotlin.math.abs(p-c);return if(pa<=pb&&pa<=pc)a else if(pb<=pc)b else c}
        for(y in 0 until height){
            val filter=raw[at++].toInt() and 255;require(filter in 0..4);val row=ByteArray(stride)
            for(i in row.indices){val left=if(i>=channels)row[i-channels].toInt() and 255 else 0;val up=previous[i].toInt() and 255;val corner=if(i>=channels)previous[i-channels].toInt() and 255 else 0
                val predictor=when(filter){0->0;1->left;2->up;3->(left+up)/2;else->paeth(left,up,corner)};row[i]=((raw[at++].toInt() and 255)+predictor).toByte()
            }
            for(x in 0 until width){val i=x*channels;fun value(offset:Int)=row[i+offset].toInt() and 255
                val r:Int;val g:Int;val b:Int;val a:Int
                when(type){
                    0->{r=value(0);g=r;b=r;a=255}
                    2->{r=value(0);g=value(1);b=value(2);a=255}
                    3->{val index=value(0);require(index*3+2<palette.size);r=palette[index*3].toInt() and 255;g=palette[index*3+1].toInt() and 255;b=palette[index*3+2].toInt() and 255;a=if(index<alpha.size)alpha[index].toInt() and 255 else 255}
                    4->{r=value(0);g=r;b=r;a=value(1)}
                    else->{r=value(0);g=value(1);b=value(2);a=value(3)}
                };pixels[y*width+x]=(a shl 24) or (r shl 16) or (g shl 8) or b
            };previous=row
        };return Image(width,height,pixels)
    }
}
