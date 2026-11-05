package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class DrawingProjectFilesTest {
    private fun rejects(action:()->Unit){try{action();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:java.nio.charset.CharacterCodingException){}catch(_:IllegalStateException){}}
    @Test fun boundedStrictUtf8RoundtripAndInvalidBytes(){
        val text="{\"message\":\"作品🙂\"}"
        assertEquals(text,DrawingProjectFiles.read(ByteArrayInputStream(DrawingProjectFiles.bytes(text))))
        rejects{DrawingProjectFiles.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(),0x28)))}
        rejects{DrawingProjectFiles.bytes("\uD800")};rejects{DrawingProjectFiles.bytes("")}
        rejects{DrawingProjectFiles.read(ByteArrayInputStream(byteArrayOf()))}
    }
    @Test fun exactByteLimitAndCancellationAndZeroReadProgress(){
        val limit=ByteArray(DrawingProjectFiles.MAX_BYTES){65}
        assertEquals(limit.size,DrawingProjectFiles.read(ByteArrayInputStream(limit)).length)
        rejects{DrawingProjectFiles.read(ByteArrayInputStream(limit+byteArrayOf(65)))}
        rejects{DrawingProjectFiles.bytes("界".repeat(DrawingProjectFiles.MAX_BYTES/3+1))}
        rejects{DrawingProjectFiles.read(ByteArrayInputStream(limit)){true}}
        val input=object:InputStream(){var zero=true;val source=ByteArrayInputStream("{}".toByteArray());override fun read()=source.read();override fun read(b:ByteArray,off:Int,len:Int):Int {if(zero){zero=false;return 0};return source.read(b,off,len)}}
        assertEquals("{}",DrawingProjectFiles.read(input))
    }
    @Test fun chunkedUploadDoesNotSplitSurrogatePairsAndRequiresExactLength(){
        val text="x".repeat(DrawingProjectFiles.CHUNK_CHARS-1)+"🙂作品"+"y".repeat(80000)
        val upload=DrawingProjectUpload(1,"My project",text.length);var offset=0
        while(offset<text.length){val chunk=DrawingProjectFiles.chunk(text,offset);assertTrue(chunk.length<=DrawingProjectFiles.CHUNK_CHARS);assertFalse(Character.isHighSurrogate(chunk.last()));upload.append(chunk);offset+=chunk.length}
        assertEquals(text,upload.finish())
        rejects{DrawingProjectFiles.chunk(text,DrawingProjectFiles.CHUNK_CHARS)}
        rejects{DrawingProjectFiles.chunk(text,-1)}
        rejects{DrawingProjectUpload(2,"Name",2).finish()}
        rejects{DrawingProjectUpload(2,"Name",2).append("abc")}
        rejects{DrawingProjectUpload(2,"Name",2).append("")}
    }
    @Test fun requestAndFilenameBoundsStaySeparateFromOtherPickers(){
        assertFalse(DrawingProjectFiles.acceptsRequest(0));assertTrue(DrawingProjectFiles.acceptsRequest(1));assertFalse(DrawingProjectFiles.acceptsRequest(Int.MAX_VALUE))
        assertNotEquals(8301,DrawingProjectFiles.OPEN_REQUEST);assertNotEquals(DrawingProjectFiles.OPEN_REQUEST,DrawingProjectFiles.SAVE_REQUEST)
        assertEquals("folder_name.noble.json",DrawingProjectFiles.filename("folder/name"));assertEquals("Noble project.noble.json",DrawingProjectFiles.filename("..."))
        rejects{DrawingProjectFiles.filename("\nname")};rejects{DrawingProjectUpload(0,"Name",1)};rejects{DrawingProjectUpload(1,"Name",DrawingProjectFiles.MAX_BYTES+1)}
    }
}
