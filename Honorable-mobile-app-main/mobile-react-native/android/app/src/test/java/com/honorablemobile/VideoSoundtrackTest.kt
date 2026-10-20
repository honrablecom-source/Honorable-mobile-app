package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class VideoSoundtrackTest {
    private val first = VideoSoundtrack("content://audio/first",3000)
    private val second = VideoSoundtrack("content://audio/second",40_000,.5f)
    private fun rejected(block: () -> Unit) { try { block();fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    @Test fun clipAndSoundtrackHistoryRemainOneUndoableProject() {
        val p=VideoEditProject(10_000)
        p.setSoundtrack(first);p.setTitle(0,"Title");p.setSoundtrack(second)
        assertEquals(setOf(first.uri,second.uri),p.soundtrackUris())
        assertEquals(10_000L,p.totalDurationMs)
        p.setSoundtrack(null);assertNull(p.soundtrack)
        assertTrue(p.undo());assertEquals(second,p.soundtrack)
        assertTrue(p.undo());assertEquals(first,p.soundtrack);assertEquals("Title",p.segments.single().title)
        assertTrue(p.redo());assertEquals(second,p.soundtrack)
        p.reset();assertNull(p.soundtrack);assertEquals("",p.segments.single().title)
        assertTrue(p.undo());assertEquals(second,p.soundtrack)
    }
    @Test fun soundtrackAloneUsesCreativeDraftAndIsRestoredUndoably() {
        val draft=VideoDraft(10_000,listOf(VideoSegment(0,10_000)),0,0,0,10_000,soundtrack=first)
        val bytes=VideoDraftCodec.encode(draft)
        assertEquals(3,ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(draft,VideoDraftCodec.decode(bytes,10_000))
        val p=VideoEditProject(10_000);p.setSoundtrack(second)
        p.applyDraft(draft.segments,soundtrack=draft.soundtrack)
        assertEquals(first,p.soundtrack);assertTrue(p.undo());assertEquals(second,p.soundtrack)
        p.restore(draft.segments,soundtrack=first);assertFalse(p.canUndo);assertEquals(first,p.soundtrack)
    }
    @Test fun invalidAudioMetadataAndReservedClipKeysAreRejected() {
        rejected { first.copy(volume=Float.NaN) };rejected { first.copy(volume=1.1f) }
        rejected { first.copy(durationMs=0) };rejected { first.copy(uri="https://remote/audio") }
        rejected { first.copy(uri="content://audio/"+"x".repeat(2048)) }
        rejected { VideoSegment(0,1000,sourceId="__audio_0") }
        rejected { VideoSource("__soundtrack__","content://audio/file",1000) }
    }
    @Test fun legacyDraftRestoreClearsMusicAndUndoBringsItBack() {
        val p=VideoEditProject(10_000);p.setSoundtrack(first)
        p.applyDraft(listOf(VideoSegment(0,5000)))
        assertNull(p.soundtrack);assertTrue(p.undo());assertEquals(first,p.soundtrack)
        assertTrue(p.soundtrackUris().contains(first.uri))
    }
    @Test fun truncatedSoundtrackDataIsRejected() {
        val draft=VideoDraft(1000,listOf(VideoSegment(0,1000,title="Title",fadeEdges=true)),0,0,0,1000,soundtrack=second)
        val bytes=VideoDraftCodec.encode(draft)
        for(length in bytes.indices) assertNull(VideoDraftCodec.decode(bytes.copyOf(length),1000))
        assertNull(VideoDraftCodec.decode(bytes+byteArrayOf(1),1000))
    }
    @Test fun veryShortAudioCannotExpandIntoAnUnboundedPreviewPlaylist() {
        val clips=listOf(VideoSegment(0,10_000))
        assertEquals(4L,VideoTimeline.soundtrackRepeatCount(clips,first))
        assertEquals(1L,VideoTimeline.soundtrackRepeatCount(clips,second))
        rejected { VideoTimeline.soundtrackRepeatCount(clips,first.copy(durationMs=1)) }
    }

}
