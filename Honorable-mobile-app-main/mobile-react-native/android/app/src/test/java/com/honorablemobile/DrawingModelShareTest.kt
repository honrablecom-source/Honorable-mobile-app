package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Base64

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[34],manifest=org.robolectric.annotation.Config.NONE,application=android.app.Application::class)
class DrawingModelShareTest {
    private fun fixture():ByteArray=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(0x46546c67).putInt(2).putInt(24).putInt(4).putInt(0x4e4f534a).put("{}  ".toByteArray()).array()
    private fun rejects(action:()->Unit){assertTrue(runCatching(action).isFailure)}
    @Test fun realBinaryHeaderAndChunkTransportRoundtrip(){
        val bytes=fixture();val upload=DrawingModelUpload(1,bytes.size)
        upload.append(Base64.getEncoder().encodeToString(bytes.copyOfRange(0,12)));upload.append(Base64.getEncoder().encodeToString(bytes.copyOfRange(12,24)))
        assertArrayEquals(bytes,upload.finish());rejects{upload.finish()};rejects{upload.append("AAAA")}
    }
    @Test fun invalidBudgetsPartialAndMalformedChunksFailClosed(){
        for(id in listOf(0,-1,1_000_000_001))rejects{DrawingModelUpload(id,24)}
        for(size in listOf(0,19,DrawingModelUpload.MAX_BYTES+1))rejects{DrawingModelUpload(1,size)}
        val upload=DrawingModelUpload(1,24)
        for(chunk in listOf("","!!!!","AA= =","AB==","A".repeat(32772)))rejects{upload.append(chunk)}
        rejects{upload.finish()};rejects{upload.append(Base64.getEncoder().encodeToString(ByteArray(25)))}
        upload.append(Base64.getEncoder().encodeToString(fixture()));assertArrayEquals(fixture(),upload.finish())
    }
    @Test fun unsupportedHeadersAndChunkLengthsFailClosed(){
        for(offset in listOf(0,4,8,12,16)){
            val bytes=fixture();bytes[offset]=0
            val upload=DrawingModelUpload(1,bytes.size);upload.append(Base64.getEncoder().encodeToString(bytes));rejects{upload.finish()}
        }
    }
    @Test fun exactFileBytesVerifiedAndCancelledPartialFileRemoved(){
        val directory=Files.createTempDirectory("noble-model-test").toFile()
        try{
            val file=DrawingModelShare.prepare(directory,fixture()){};assertArrayEquals(fixture(),file.readBytes());file.delete()
            var calls=0;rejects{DrawingModelShare.prepare(directory,ByteArray(200000)){check(++calls<4)}}
            assertEquals(0,directory.listFiles()!!.size)
        }finally{directory.deleteRecursively()}
    }
    @Test fun handoffAcceptsOnlyExactPrivateCacheParentAndBoundedValidBytes(){
        val root=Files.createTempDirectory("noble-depth-handoff-test").toFile()
        try{
            val directory=java.io.File(root,"noble-depth-handoff").apply{mkdirs()}
            val file=java.io.File(directory,"depth.glb").apply{writeBytes(fixture())}
            assertEquals(file.canonicalFile,DrawingDepthHandoff.file(directory,file.path))
            assertArrayEquals(fixture(),DrawingDepthHandoff.read(file));assertTrue(file.exists())
            val outside=java.io.File(root,"outside.glb").apply{writeBytes(fixture())}
            rejects{DrawingDepthHandoff.file(directory,outside.path)}
            file.writeText("invalid");rejects{DrawingDepthHandoff.read(file)}
            java.io.RandomAccessFile(file,"rw").use{it.setLength(DrawingDepthHandoff.MAX_BYTES.toLong()+1)}
            rejects{DrawingDepthHandoff.file(directory,file.path)};rejects{DrawingDepthHandoff.read(file)}
        }finally{root.deleteRecursively()}
    }

}
