package com.honorablemobile

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

/** Real JDK stream/file boundaries for bounded transport; no Android provider durability claim. */
class DrawingProjectWriteTest {
    private class Output:ByteArrayOutputStream(){
        val writes=mutableListOf<Int>();var flushes=0;var closed=false;var failAfter:Int?=null;var failFlush=false
        override fun write(data:ByteArray,offset:Int,length:Int){writes.add(length);if(failAfter?.let{size()>=it}==true)throw IOException("Synthetic destination write failure");super.write(data,offset,length)}
        override fun flush(){flushes++;if(failFlush)throw IOException("Synthetic destination flush failure")}
        override fun close(){closed=true}
    }
    private fun cancelled(action:()->Unit){try{action();fail("Expected cancellation")}catch(_:IllegalStateException){}}
    private fun invalid(action:()->Unit){try{action();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    private fun failed(action:()->Unit){try{action();fail("Expected I/O failure")}catch(_:IOException){}}
    @Test fun exactUnicodeBytesUseBoundedWritesAndCallerOwnsClose(){
        val value=DrawingProjectFiles.bytes("作品🙂 café\n".repeat(4000));val out=Output()
        DrawingProjectFiles.write(out,value){false};assertArrayEquals(value,out.toByteArray());assertTrue(out.writes.all{it in 1..8192});assertTrue(out.writes.size>1);assertEquals(1,out.flushes);assertFalse(out.closed)
    }
    @Test fun emptyAndOversizedInputsNeverWriteOrFlush(){for(data in listOf(byteArrayOf(),ByteArray(DrawingProjectFiles.MAX_BYTES+1))){val out=Output();invalid{DrawingProjectFiles.write(out,data){false}};assertEquals(0,out.size());assertEquals(0,out.flushes)}}
    @Test fun cancellationBeforeWritingDoesNotTouchDestination(){val out=Output();cancelled{DrawingProjectFiles.write(out,"{}".toByteArray()){true}};assertEquals(0,out.size());assertEquals(0,out.flushes)}
    @Test fun cancellationAfterFirstWriteStopsAtNextBoundaryWithoutFlush(){
        val value=ByteArray(30000){(it%127).toByte()};val before=value.copyOf();val out=Output()
        cancelled{DrawingProjectFiles.write(out,value){out.size()>0}};assertEquals(8192,out.size());assertArrayEquals(value.copyOfRange(0,8192),out.toByteArray());assertEquals(0,out.flushes);assertArrayEquals(before,value)
    }
    @Test fun cancellationBeforeFlushDoesNotReportCompletion(){val out=Output();val value="{}".toByteArray();cancelled{DrawingProjectFiles.write(out,value){out.size()==value.size}};assertArrayEquals(value,out.toByteArray());assertEquals(0,out.flushes)}
    @Test fun destinationWriteFailureStopsFurtherWritesAndDoesNotFlush(){val out=Output().apply{failAfter=8192};failed{DrawingProjectFiles.write(out,ByteArray(30000){65}){false}};assertEquals(8192,out.size());assertEquals(2,out.writes.size);assertEquals(0,out.flushes);assertFalse(out.closed)}
    @Test fun flushFailurePropagatesWithoutRetryOrClosingCallerStream(){val out=Output().apply{failFlush=true};failed{DrawingProjectFiles.write(out,"{}".toByteArray()){false}};assertEquals(1,out.flushes);assertEquals(2,out.size());assertFalse(out.closed)}
    @Test fun exactFileBudgetSucceedsAndNeverUsesAnOversizedWrite(){val value=ByteArray(DrawingProjectFiles.MAX_BYTES){65};val out=Output();DrawingProjectFiles.write(out,value){false};assertArrayEquals(value,out.toByteArray());assertTrue(out.writes.all{it<=8192});assertEquals(1,out.flushes)}
    @Test fun interruptedThreadCannotStartWriting(){val out=Output();try{Thread.currentThread().interrupt();cancelled{DrawingProjectFiles.write(out,"{}".toByteArray()){false}};assertEquals(0,out.size());assertEquals(0,out.flushes)}finally{Thread.interrupted()}}
    @Test fun actualTemporaryFileRoundtripPreservesAllUtf8(){val file=File.createTempFile("noble-project-transport-",".json");try{val value="{\"title\":\"作品🙂 café\"}";file.outputStream().use{DrawingProjectFiles.write(it,DrawingProjectFiles.bytes(value)){false}};assertEquals(value,file.inputStream().use{DrawingProjectFiles.read(it)})}finally{file.delete()}}
}
