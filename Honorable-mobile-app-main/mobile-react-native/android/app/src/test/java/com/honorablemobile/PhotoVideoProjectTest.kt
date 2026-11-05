package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class PhotoVideoProjectTest {
    private val id="12345678-1234-1234-1234-123456789abc"
    private fun project(count:Int=3)=PhotoVideoProject(id,"Photo story","com.honorablemobile.creation",PhotoVideoFormat.PORTRAIT,List(count){PhotoVideoAsset(3000L+it,"a".repeat(64))})
    @Test fun roundTripAndCanonicalTimeline(){
        val original=project();assertEquals(original,PhotoVideoProjectCodec.decode(PhotoVideoProjectCodec.encode(original),id))
        val draft=original.initialDraft();assertEquals(3,draft.segments.size);assertEquals(2,draft.sources.size)
        assertEquals(listOf("primary","photo_1","photo_2"),draft.segments.map{it.sourceId})
        assertEquals(listOf(3000L,3001L,3002L),draft.segments.map{it.endMs});assertTrue(draft.segments.all{it.volume==0f})
        assertEquals(draft,VideoDraftCodec.decode(VideoDraftCodec.encode(draft),3000))
    }
    @Test fun rejectsDamagedAndWrongOwner(){
        val bytes=PhotoVideoProjectCodec.encode(project());bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java){PhotoVideoProjectCodec.decode(bytes,id)}
        assertThrows(IllegalArgumentException::class.java){PhotoVideoProjectCodec.decode(PhotoVideoProjectCodec.encode(project()),"aaaaaaaa-1234-1234-1234-123456789abc")}
    }
    @Test fun boundsAndPrivateSourceNames(){
        assertThrows(IllegalArgumentException::class.java){project(0)};assertThrows(IllegalArgumentException::class.java){project(21)}
        assertThrows(IllegalArgumentException::class.java){PhotoVideoProjectCodec.filename(20)}
        assertFalse(PhotoVideoProjectCodec.validId("../../outside"))
        assertEquals("content://com.honorablemobile.creation/photo_video_projects/$id/clip-02.mp4",project().source(2))
        assertThrows(IllegalArgumentException::class.java){PhotoVideoAsset(0,"a".repeat(64))}
        assertThrows(IllegalArgumentException::class.java){PhotoVideoAsset(1,"invalid")}
    }
    @Test fun snapshotsInputAndHandlesMaximum(){
        val assets=mutableListOf(PhotoVideoAsset(5000,"a".repeat(64)))
        val saved=PhotoVideoProject(id,"Independent","com.honorablemobile.creation",PhotoVideoFormat.SQUARE,assets);assets.clear();assertEquals(1,saved.assets.size)
        val max=project(20);assertTrue(PhotoVideoProjectCodec.encode(max).size<=PhotoVideoProjectCodec.MAX_BYTES)
        assertEquals(20,max.initialDraft().segments.size)
    }
}
