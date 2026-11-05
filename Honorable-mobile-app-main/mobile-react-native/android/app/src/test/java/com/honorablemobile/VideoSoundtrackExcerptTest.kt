package com.honorablemobile

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class VideoSoundtrackExcerptTest {
    private val clip=VideoSegment(0,10000)
    private val music=VideoSoundtrack("content://fixture/music",30000)
    @Test fun validatesExcerptInsideOriginalAndKeepsOriginalMetadata(){
        val excerpt=music.copy(startMs=10000,endMs=20000)
        assertEquals(30000L,excerpt.durationMs);assertEquals(10000L,excerpt.playbackDurationMs);assertTrue(excerpt.hasExcerpt)
        assertFalse(music.hasExcerpt)
        for(pair in listOf(-1L to 1000L,0L to 0L,1000L to 999L,0L to 30001L,Long.MAX_VALUE to Long.MAX_VALUE))assertTrue(runCatching{music.copy(startMs=pair.first,endMs=pair.second)}.isFailure)
        assertEquals(music,excerpt.copy(startMs=0,endMs=excerpt.durationMs))
    }
    @Test fun loopingUsesExcerptLengthAndPreservesRepeatLimit(){
        val excerpt=music.copy(startMs=1000,endMs=4000)
        assertEquals(4L,VideoTimeline.soundtrackRepeatCount(listOf(clip),excerpt))
        assertEquals(1L,VideoTimeline.soundtrackRepeatCount(listOf(clip),music))
        val tiny=music.copy(startMs=1000,endMs=1001)
        assertTrue(runCatching{VideoTimeline.soundtrackRepeatCount(listOf(clip),tiny)}.isFailure)
        assertEquals(1L,VideoTimeline.soundtrackRepeatCount(listOf(clip),tiny.copy(loop=false)))
    }
    @Test fun oneShotClipsAtAbsoluteSourceEndWithoutExtendingVideo(){
        val excerpt=music.copy(startMs=10000,endMs=20000,loop=false)
        assertEquals(12_000_000L,VideoTimeline.soundtrackEndUs(listOf(VideoSegment(0,2000)),excerpt))
        assertEquals(20_000_000L,VideoTimeline.soundtrackEndUs(listOf(VideoSegment(0,20000)),excerpt))
        assertEquals(20_000_000L,VideoTimeline.soundtrackEndUs(listOf(VideoSegment(0,2000)),excerpt.copy(loop=true)))
        val huge=VideoSegment(0,Long.MAX_VALUE)
        assertEquals(20_000_000L,VideoTimeline.soundtrackEndUs(listOf(huge),excerpt))
    }
    @Test fun versionNineRoundTripsLoopAndOneShotThroughDraftAndGuestEnvelope(){
        val rich=clip.copy(title="Title",captions=listOf(VideoCaption(0,1000,"Caption")))
        for(loop in listOf(true,false)){
            val draft=VideoDraft(10000,listOf(rich),0,0,0,10000,soundtrack=music.copy(startMs=1000,endMs=9000,loop=loop))
            val bytes=VideoDraftCodec.encode(draft)
            assertEquals(9,ByteBuffer.wrap(bytes).getInt(4));assertEquals(draft,VideoDraftCodec.decode(bytes,10000))
            val primary="content://fixture/video"
            assertEquals(draft,GuestVideoProjectCodec.decode(GuestVideoProjectCodec.encode(primary,draft),primary,10000))
        }
        val old=VideoDraft(10000,listOf(clip),0,0,0,10000,soundtrack=music)
        assertEquals(3,ByteBuffer.wrap(VideoDraftCodec.encode(old)).getInt(4))
        assertEquals(old,VideoDraftCodec.decode(VideoDraftCodec.encode(old),10000))
        val narration=old.copy(soundtrack=music.copy(loop=false))
        assertEquals(8,ByteBuffer.wrap(VideoDraftCodec.encode(narration)).getInt(4))
        assertEquals(narration,VideoDraftCodec.decode(VideoDraftCodec.encode(narration),10000))
    }
    @Test fun damagedTruncatedFutureAndInvalidExcerptBoundsFailClosed(){
        val draft=VideoDraft(10000,listOf(clip),0,0,0,10000,soundtrack=music.copy(startMs=1000,endMs=9000))
        val bytes=VideoDraftCodec.encode(draft)
        for(change in listOf<(ByteArray)->Unit>({ByteBuffer.wrap(it).putLong(it.size-16,-1)},{ByteBuffer.wrap(it).putLong(it.size-8,30001)},{ByteBuffer.wrap(it).putLong(it.size-8,999)},{ByteBuffer.wrap(it).putInt(4,127)})){
            val damaged=bytes.copyOf();change(damaged);assertNull(VideoDraftCodec.decode(damaged,10000))
        }
        assertNull(VideoDraftCodec.decode(bytes.copyOf(bytes.size-1),10000));assertNull(VideoDraftCodec.decode(bytes+byteArrayOf(0),10000))
    }
    @Test fun excerptAndFullResetAreSingleUndoEditsRetainingClipAudioAndHistory(){
        val project=VideoEditProject(10000);val initial=clip.copy(volume=.5f,title="keep")
        project.restore(listOf(initial),soundtrack=music)
        val excerpt=music.copy(startMs=1000,endMs=4000,loop=false)
        project.setSoundtrack(excerpt);assertEquals(listOf(initial),project.segments)
        assertTrue(project.undo());assertEquals(music,project.soundtrack)
        assertTrue(project.redo());assertEquals(excerpt,project.soundtrack)
        project.setSoundtrack(excerpt.copy(startMs=0,endMs=excerpt.durationMs))
        assertFalse(project.soundtrack!!.hasExcerpt);assertFalse(project.soundtrack!!.loop)
        assertTrue(project.undo());assertEquals(excerpt,project.soundtrack)
    }
    @Test fun excerptVersionBudgetRejectsBeforeHistoryOrSoundtrackMutation(){
        val project=VideoEditProject(1000)
        project.restore(List(1000){VideoSegment(0,1000,title="x".repeat(120))},soundtrack=music)
        assertTrue(runCatching{project.setSoundtrack(music.copy(startMs=1000,endMs=9000))}.isFailure)
        assertEquals(music,project.soundtrack);assertFalse(project.canUndo)
    }
}
