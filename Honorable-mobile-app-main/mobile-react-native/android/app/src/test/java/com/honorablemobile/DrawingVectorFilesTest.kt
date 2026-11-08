package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InterruptedIOException
class DrawingVectorFilesTest {
    private fun rejects(block:()->Unit){assertTrue(runCatching(block).isFailure)}
    @Test fun utf8TextAndOriginalBytesArePreserved(){val bytes="<svg>café 🎬</svg>".toByteArray();val original=bytes.clone();assertEquals("<svg>café 🎬</svg>",DrawingVectorFiles.read(ByteArrayInputStream(bytes)));assertArrayEquals(original,bytes)}
    @Test fun emptyAndMalformedUtf8AreRejected(){for(bytes in listOf(byteArrayOf(),byteArrayOf(0xff.toByte()),byteArrayOf(0xc0.toByte(),0xaf.toByte()),byteArrayOf(0xed.toByte(),0xa0.toByte(),0x80.toByte())))rejects{DrawingVectorFiles.read(ByteArrayInputStream(bytes))}}
    @Test fun exactByteLimitIsAcceptedAndOversizeConsumesOnlyOneExtraByte(){assertEquals(65536,DrawingVectorFiles.read(ByteArrayInputStream(ByteArray(65536){97})).length);var consumed=0;val stream=object:InputStream(){override fun read()=97.also{consumed++};override fun read(b:ByteArray,off:Int,len:Int):Int{java.util.Arrays.fill(b,off,off+len,97.toByte());consumed+=len;return len}};rejects{DrawingVectorFiles.read(stream)};assertEquals(65537,consumed)}
    @Test fun zeroReturningProviderCannotSpinForever(){var calls=0;val stream=object:InputStream(){override fun read()=0;override fun read(b:ByteArray,o:Int,l:Int)=0.also{calls++}};rejects{DrawingVectorFiles.read(stream)};assertEquals(9,calls)}
    @Test fun cancellationBeforeReadTouchesNoProviderBytes(){var reads=0;val stream=object:InputStream(){override fun read()=0.also{reads++}};val result=runCatching{DrawingVectorFiles.read(stream){true}};assertTrue(result.exceptionOrNull() is InterruptedIOException);assertEquals(0,reads)}
    @Test fun cancellationAfterReadReturnsNoPartialText(){var stopped=false;var reads=0;val stream=object:ByteArrayInputStream("<svg/>".toByteArray()){override fun read(b:ByteArray,o:Int,l:Int):Int{reads++;stopped=true;return super.read(b,o,l)}};assertTrue(runCatching{DrawingVectorFiles.read(stream){stopped}}.exceptionOrNull() is InterruptedIOException);assertEquals(1,reads)}
    @Test fun threadInterruptionStopsBeforeReadAndIsNotSwallowed(){Thread.currentThread().interrupt();try{assertTrue(runCatching{DrawingVectorFiles.read(ByteArrayInputStream(byteArrayOf(97)))}.exceptionOrNull() is InterruptedIOException);assertTrue(Thread.currentThread().isInterrupted)}finally{Thread.interrupted()}}
    @Test fun chunksDoNotSplitSurrogatePairs(){val text="a".repeat(DrawingVectorFiles.CHUNK-1)+"🎬z";val first=DrawingVectorFiles.chunk(text,0);val second=DrawingVectorFiles.chunk(text,first.length);assertEquals(text,first+second);assertFalse(Character.isHighSurrogate(first.last()));rejects{DrawingVectorFiles.chunk(text,DrawingVectorFiles.CHUNK)}}
    @Test fun chunkOffsetsAndLengthAreBounded(){for(offset in listOf(-1,3,99))rejects{DrawingVectorFiles.chunk("abc",offset)};rejects{DrawingVectorFiles.chunk("x".repeat(65537),0)}}
    @Test fun requestIdsAreBounded(){assertTrue(DrawingVectorFiles.acceptsRequest(1));assertTrue(DrawingVectorFiles.acceptsRequest(1_000_000_000));assertFalse(DrawingVectorFiles.acceptsRequest(0));assertFalse(DrawingVectorFiles.acceptsRequest(Int.MAX_VALUE))}
    @Test fun pickerCodesNeverWrapOrReuseWithinActivityHistory(){var previous=0;val seen=mutableSetOf<Int>();repeat(5000){previous=DrawingVectorFiles.nextCode(previous);assertTrue(seen.add(previous));assertTrue(DrawingVectorFiles.acceptsCode(previous))};assertEquals(DrawingVectorFiles.LAST_CODE,previous);rejects{DrawingVectorFiles.nextCode(previous)};rejects{DrawingVectorFiles.nextCode(-1)}}
}
