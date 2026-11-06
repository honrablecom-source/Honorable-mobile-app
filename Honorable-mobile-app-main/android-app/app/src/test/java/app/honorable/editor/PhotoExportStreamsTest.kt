package app.honorable.editor

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import java.io.*
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class PhotoExportStreamsTest {
    private val bytes=ByteArray(600_123){(it%251).toByte()}
    @Test fun completeCopyAndDigestMatchReferenceWithoutTakingStreamOwnership(){
        var inputClosed=0;var outputClosed=0
        val input=object:ByteArrayInputStream(bytes){override fun close(){inputClosed++;super.close()}}
        val output=object:ByteArrayOutputStream(){override fun close(){outputClosed++;super.close()}}
        PhotoExportStreams.copy(input,output){};assertArrayEquals(bytes,output.toByteArray())
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(bytes),PhotoExportStreams.digest(ByteArrayInputStream(bytes)){})
        assertEquals(0,inputClosed);assertEquals(0,outputClosed);input.close();output.close();assertEquals(1,inputClosed);assertEquals(1,outputClosed)
    }
    @Test fun alreadyCancelledCopyAndDigestNeverReadOrWrite(){
        val input=object:InputStream(){override fun read():Int=error("Unexpected read")};val output=object:OutputStream(){override fun write(value:Int)=error("Unexpected write")}
        assertTrue(runCatching{PhotoExportStreams.copy(input,output){throw PhotoExportOperation.Cancelled()}}.exceptionOrNull()is PhotoExportOperation.Cancelled)
        assertTrue(runCatching{PhotoExportStreams.digest(input){throw PhotoExportOperation.Cancelled()}}.exceptionOrNull()is PhotoExportOperation.Cancelled)
    }
    @Test fun ownershipLostDuringReadPreventsThatChunkFromBeingWrittenOrDigested(){
        for(digest in listOf(false,true)){
            var current=true;val input=object:ByteArrayInputStream(bytes){override fun read(buffer:ByteArray,offset:Int,length:Int):Int=super.read(buffer,offset,length).also{current=false}}
            val output=ByteArrayOutputStream();val guard={if(!current)throw PhotoExportOperation.Cancelled()}
            val result=runCatching{if(digest)PhotoExportStreams.digest(input,guard)else PhotoExportStreams.copy(input,output,guard)}
            assertTrue(result.exceptionOrNull()is PhotoExportOperation.Cancelled);assertEquals(0,output.size())
        }
    }
    @Test fun cancellationAfterWriteLeavesOnlyOneBoundedPartialChunk(){
        var current=true;val output=object:ByteArrayOutputStream(){override fun write(buffer:ByteArray,offset:Int,length:Int){super.write(buffer,offset,length);current=false}}
        assertTrue(runCatching{PhotoExportStreams.copy(ByteArrayInputStream(bytes),output){if(!current)throw PhotoExportOperation.Cancelled()}}.exceptionOrNull()is PhotoExportOperation.Cancelled)
        assertTrue(output.size()in 1..256*1024);assertArrayEquals(bytes.copyOf(output.size()),output.toByteArray())
    }
    @Test fun zeroReadsMakeProgressAndCancellationAtEofNeverPublishesSuccess(){
        val source=byteArrayOf(1,2,3);var index=0
        val input=object:InputStream(){override fun read()=if(index<source.size)source[index++].toInt()else -1;override fun read(buffer:ByteArray,offset:Int,length:Int)=0}
        val output=ByteArrayOutputStream();PhotoExportStreams.copy(input,output){};assertArrayEquals(source,output.toByteArray())
        var current=true;val eof=object:InputStream(){override fun read():Int{current=false;return -1}}
        assertTrue(runCatching{PhotoExportStreams.digest(eof){if(!current)throw PhotoExportOperation.Cancelled()}}.exceptionOrNull()is PhotoExportOperation.Cancelled)
    }
    @Test fun sourceAndDestinationFailuresPropagateWithoutAutomaticCloseOrRollback(){
        val expected=IOException("Synthetic write failure");var closed=false
        val output=object:OutputStream(){override fun write(value:Int){throw expected};override fun close(){closed=true}}
        assertSame(expected,runCatching{PhotoExportStreams.copy(ByteArrayInputStream(bytes),output){}}.exceptionOrNull());assertFalse(closed)
        val input=object:InputStream(){override fun read():Int=throw IOException("Synthetic read failure")}
        assertTrue(runCatching{PhotoExportStreams.digest(input){}}.exceptionOrNull()is IOException)
    }
    @Test fun safeCopyCancellationEntryPrecedesAnyContextBitmapOrProviderAccess(){
        val type=Class.forName("sun.misc.Unsafe");val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
        fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
        val context=allocate(ContextWrapper::class.java)as ContextWrapper;val bitmap=allocate(Bitmap::class.java)as Bitmap
        val error=runCatching{SafePhotoCopy.save(context,Uri.parse("content://synthetic/source"),Uri.parse("content://synthetic/copy"),bitmap,requireCurrent={throw PhotoExportOperation.Cancelled()})}.exceptionOrNull()
        assertTrue(error is PhotoExportOperation.Cancelled)
    }
}
