package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class DrawingPrintPolicyTest {
    private val image=DrawingPolicy.PNG_PREFIX+"AAAA".repeat(20000)
    private fun rejects(action:()->Unit){assertTrue(runCatching(action).isFailure)}
    @Test fun chunkedSnapshotReconstructsExactlyWithoutSceneOrLocationFields(){
        val upload=DrawingPrintUpload(12,image.length)
        image.chunked(DrawingPrintPolicy.CHUNK_CHARS).forEach(upload::append)
        assertEquals(image,upload.finish());assertEquals(12,upload.id)
    }
    @Test fun uploadRejectsBoundsIncompleteChunksAndUnsupportedPayloads(){
        for(id in listOf(0,-1,1_000_000_001))rejects{DrawingPrintUpload(id,100)}
        for(size in listOf(0,DrawingPolicy.PNG_PREFIX.length,DrawingPrintPolicy.MAX_CHARS+1))rejects{DrawingPrintUpload(1,size)}
        val upload=DrawingPrintUpload(1,image.length);rejects{upload.finish()};rejects{upload.append("")};rejects{upload.append("x".repeat(DrawingPrintPolicy.CHUNK_CHARS+1))}
        val wrong="https://example.com/"+"A".repeat(20);val bad=DrawingPrintUpload(2,wrong.length);bad.append(wrong);rejects{bad.finish()}
        val exact=DrawingPrintUpload(3,DrawingPolicy.PNG_PREFIX.length+4);exact.append(DrawingPolicy.PNG_PREFIX+"AAAA");rejects{exact.append("AAAA")};assertEquals(DrawingPolicy.PNG_PREFIX+"AAAA",exact.finish())
    }
    @Test fun fitCentersWideAndTallArtworkWithoutCropping(){
        assertEquals(DrawingPrintPolicy.Placement(100f,200f,500f,400f),DrawingPrintPolicy.fit(1000,500,100,100,500,500))
        assertEquals(DrawingPrintPolicy.Placement(260f,100f,340f,500f),DrawingPrintPolicy.fit(100,500,100,100,500,500))
        assertEquals(DrawingPrintPolicy.Placement(24f,24f,576f,576f),DrawingPrintPolicy.fit(2048,2048,24,24,576,576))
        // PrintedPdfDocument's recording canvas starts at the content origin; page margins are applied by the framework.
        assertEquals(DrawingPrintPolicy.Placement(0f,225f,540f,495f),DrawingPrintPolicy.fit(1200,600,0,0,540,720))
    }
    @Test fun printGeometryRejectsInvalidSourcePageAndOverflowBounds(){
        for(pair in listOf(0 to 10,10 to 0,2049 to 1,-1 to 1))rejects{DrawingPrintPolicy.fit(pair.first,pair.second,0,0,600,800)}
        for(rect in listOf(listOf(-1,0,600,800),listOf(0,0,0,800),listOf(0,0,600,14401),listOf(Int.MAX_VALUE,0,100,100),listOf(10,10,5,5)))rejects{DrawingPrintPolicy.fit(100,100,rect[0],rect[1],rect[2],rect[3])}
    }
    @Test fun normalPaperPlacementsStayInsideMarginsAndKeepAspectRatio(){
        for(width in listOf(1,100,1080,2048))for(height in listOf(1,300,1920,2048)){
            val fit=DrawingPrintPolicy.fit(width,height,24,36,588,756)
            assertTrue(fit.left>=24&&fit.top>=36&&fit.right<=588&&fit.bottom<=756)
            assertEquals(width.toDouble()/height,(fit.right-fit.left).toDouble()/(fit.bottom-fit.top),0.02)
        }
    }
    @Test fun outputIsBoundedAndNeverPartiallyStartsAnOversizedWrite(){
        val bytes=ByteArrayOutputStream();val out=DrawingPrintOutput(bytes,{true},8)
        out.write(byteArrayOf(1,2,3));rejects{out.write(ByteArray(6))};assertArrayEquals(byteArrayOf(1,2,3),bytes.toByteArray())
        out.write(ByteArray(5){4});rejects{out.write(5)};assertEquals(8,bytes.size())
        rejects{out.write(byteArrayOf(1),-1,1)};rejects{out.write(byteArrayOf(1),0,2)}
    }
    @Test fun cancellationStopsAtBoundedChunksAndDoesNotFlushAfterCancellation(){
        val bytes=ByteArrayOutputStream();var checks=0
        val out=DrawingPrintOutput(bytes,{++checks<=2})
        rejects{out.write(ByteArray(100000){7})};assertEquals(65536,bytes.size())
        rejects{out.flush()};assertEquals(65536,bytes.size())
        val cancelled=DrawingPrintOutput(bytes,{false});rejects{cancelled.write(1)}
    }
}
