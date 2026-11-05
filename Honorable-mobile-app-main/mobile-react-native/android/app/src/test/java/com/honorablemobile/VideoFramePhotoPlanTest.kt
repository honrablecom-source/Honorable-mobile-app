package com.honorablemobile

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VideoFramePhotoPlanTest {
    @Test fun usesExactSelectedSourceClockRegardlessOfSpeedAndRejectsOutsideTrim(){
        val clip=VideoSegment(1000,3000,speed=2f)
        assertEquals(1_500_000L,VideoFramePhotoPlan(clip,1500).sourceUs)
        assertEquals(1_000_000L,VideoFramePhotoPlan(clip,1000).sourceUs)
        assertTrue(runCatching{VideoFramePhotoPlan(clip,999)}.isFailure)
        assertTrue(runCatching{VideoFramePhotoPlan(clip,3000)}.isFailure)
        assertTrue(runCatching{VideoFramePhotoPlan(VideoSegment(0,Long.MAX_VALUE),Long.MAX_VALUE/1000+1)}.isFailure)
        assertEquals(Long.MAX_VALUE/1000*1000,VideoFramePhotoPlan(VideoSegment(0,Long.MAX_VALUE),Long.MAX_VALUE/1000).sourceUs)
    }
    @Test fun limitsEitherOrientationAndExtremeAspectRatiosWithoutUpscaling(){
        assertEquals(1920 to 1080,VideoFramePhotoPlan.size(3840,2160))
        assertEquals(1080 to 1920,VideoFramePhotoPlan.size(2160,3840))
        assertEquals(640 to 360,VideoFramePhotoPlan.size(640,360))
        assertEquals(1920 to 1,VideoFramePhotoPlan.size(Int.MAX_VALUE,1))
        assertTrue(runCatching{VideoFramePhotoPlan.size(0,100)}.isFailure)
        assertTrue(runCatching{VideoFramePhotoPlan.size(100,-1)}.isFailure)
    }
    @Test fun boundsOlderAndroidFullFrameDecodeByPixelsWithSafeMultiplication(){
        assertTrue(VideoFramePhotoPlan.legacyDecodeAllowed(3840,2160))
        assertTrue(VideoFramePhotoPlan.legacyDecodeAllowed(2160,3840))
        assertFalse(VideoFramePhotoPlan.legacyDecodeAllowed(7680,4320))
        assertFalse(VideoFramePhotoPlan.legacyDecodeAllowed(Int.MAX_VALUE,Int.MAX_VALUE))
        assertFalse(VideoFramePhotoPlan.legacyDecodeAllowed(0,100))
    }
    @Test fun successfulOwnedWriteKeepsUnrelatedFilesAndNeverChangesSyntheticSource(){
        val root=Files.createTempDirectory("frame-success").toFile()
        try{
            val source=java.io.File(root,"original.mp4").apply{writeText("synthetic original")}
            val target=VideoFramePhotoFiles.write(root,{}, {it.writeBytes(byteArrayOf(1,2,3))})
            assertEquals("created-images",target.parentFile.name)
            assertTrue(target.name.startsWith("noble-video-frame-"));assertTrue(target.name.endsWith(".png"))
            assertArrayEquals(byteArrayOf(1,2,3),target.readBytes());assertEquals("synthetic original",source.readText())
        }finally{root.deleteRecursively()}
    }
    @Test fun staleOrCancelledRequestDeletesOnlyItsAttemptAndNeverCreatesBeforeConsentGuard(){
        val root=Files.createTempDirectory("frame-cancel").toFile()
        try{
            assertTrue(runCatching{VideoFramePhotoFiles.write(root,{error("cancelled")},{it.writeText("never")})}.isFailure)
            assertFalse(java.io.File(root,"created-images").exists())
            val kept=VideoFramePhotoFiles.write(root,{}, {it.writeText("previous image")})
            var current=true
            assertTrue(runCatching{VideoFramePhotoFiles.write(root,{check(current)},{it.writeText("unfinished");current=false})}.isFailure)
            assertEquals(listOf(kept.name),kept.parentFile.list()!!.toList())
            assertEquals("previous image",kept.readText())
        }finally{root.deleteRecursively()}
    }
    @Test fun encoderFailureEmptyAndOversizedCopiesAreRemoved(){
        val root=Files.createTempDirectory("frame-failure").toFile()
        try{
            for(encode in listOf<(java.io.File)->Unit>({it.writeText("partial");error("encoder failed")},{},{file->java.io.RandomAccessFile(file,"rw").use{it.setLength(VideoFramePhotoFiles.MAX_BYTES+1)}})){
                assertTrue(runCatching{VideoFramePhotoFiles.write(root,{},encode)}.isFailure)
                assertTrue(java.io.File(root,"created-images").listFiles()!!.isEmpty())
            }
        }finally{root.deleteRecursively()}
    }
    @Test fun rejectsCacheDirectorySymlinkWithoutTouchingDestination(){
        val root=Files.createTempDirectory("frame-link").toFile();val outside=Files.createTempDirectory("frame-outside").toFile()
        try{
            val sentinel=java.io.File(outside,"keep.png").apply{writeText("keep")}
            Files.createSymbolicLink(java.io.File(root,"created-images").toPath(),outside.toPath())
            assertTrue(runCatching{VideoFramePhotoFiles.write(root,{}, {it.writeText("new")})}.isFailure)
            assertEquals("keep",sentinel.readText());assertEquals(1,outside.listFiles()!!.size)
        }finally{java.io.File(root,"created-images").delete();root.deleteRecursively();outside.deleteRecursively()}
    }
}
