package com.honorablemobile

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class VoiceoverAudioPolicyTest {
    private val clip=VideoSegment(0,2000)
    private val music=VideoSoundtrack("content://fixture/music",500)
    @Test fun narrationPlaysOnceAndClipsToTheEditedProjectWhileOldMusicLoops() {
        val once=music.copy(loop=false)
        assertEquals(4L,VideoTimeline.soundtrackRepeatCount(listOf(clip),music))
        assertEquals(1L,VideoTimeline.soundtrackRepeatCount(listOf(clip),once))
        assertEquals(500_000L,VideoTimeline.soundtrackEndUs(listOf(clip),once))
        assertEquals(2_000_000L,VideoTimeline.soundtrackEndUs(listOf(clip),once.copy(durationMs=9000,endMs=9000)))
    }
    @Test fun voiceoverRecoveryUsesVersionEightAndExistingMusicKeepsItsPriorVersion() {
        val old=VideoDraft(2000,listOf(clip),0,0,0,2000,soundtrack=music)
        val oldBytes=VideoDraftCodec.encode(old);assertEquals(3,ByteBuffer.wrap(oldBytes).getInt(4));assertEquals(old,VideoDraftCodec.decode(oldBytes,2000))
        val next=old.copy(soundtrack=music.copy(loop=false));val bytes=VideoDraftCodec.encode(next)
        assertEquals(8,ByteBuffer.wrap(bytes).getInt(4));assertEquals(next,VideoDraftCodec.decode(bytes,2000))
        assertNull(VideoDraftCodec.decode(bytes.copyOf(bytes.size-1),2000))
    }
    @Test fun voiceoverAttachmentUsesExistingUndoAndPreservesClipAudio() {
        val project=VideoEditProject(2000);project.restore(listOf(clip),soundtrack=music)
        val once=music.copy(loop=false);project.setSoundtrack(once)
        assertEquals(listOf(clip),project.segments);assertEquals(1f,project.segments.single().volume,0f)
        assertTrue(project.undo());assertEquals(music,project.soundtrack);assertTrue(project.redo());assertEquals(once,project.soundtrack)
    }
    @Test fun versionEightRecoveryBudgetIsCheckedBeforeChangingTheSoundtrack() {
        val project=VideoEditProject(1000)
        project.restore(List(1000){VideoSegment(0,1000,title="x".repeat(120))})
        assertTrue(runCatching{project.setSoundtrack(music.copy(loop=false))}.isFailure)
        assertNull(project.soundtrack);assertFalse(project.canUndo)
    }
}
