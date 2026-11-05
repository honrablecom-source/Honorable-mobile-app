package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class MediaPreviewPlaybackTest {
    @Test fun delayedPreparationAfterPauseCannotStartInBackground() {
        val state=MediaPreviewPlayback(42000,true)
        assertFalse(state.resume())
        state.pause(0,false)
        val request=state.prepared(90000)
        assertEquals(42000,request.position)
        assertFalse(request.start)
        assertTrue(state.resume())
    }
    @Test fun rotationRestoresCurrentPositionAndPlayingIntentAfterBackgroundPause() {
        val state=MediaPreviewPlayback(42000,true)
        state.resume(); assertTrue(state.prepared(90000).start)
        state.pause(53000,true)
        val saved=state.snapshot(0,false)
        assertEquals(53000L,saved.position);assertTrue(saved.playing)
        val restored=MediaPreviewPlayback(saved.position,saved.playing)
        restored.resume()
        assertEquals(MediaPreviewPlayback.Prepared(53000,true),restored.prepared(90000))
    }
    @Test fun userPausedVideoStaysPausedAcrossRotationAndResume() {
        val state=MediaPreviewPlayback(0,true)
        state.resume();state.prepared(90000)
        val saved=state.snapshot(12345,false)
        state.pause(12345,false)
        assertFalse(state.resume())
        val restored=MediaPreviewPlayback(saved.position,saved.playing)
        restored.resume()
        assertEquals(MediaPreviewPlayback.Prepared(12345,false),restored.prepared(90000))
    }
    @Test fun restoredPositionsClampToVideoAndApiBounds() {
        val negative=MediaPreviewPlayback(-1,true);negative.resume()
        assertEquals(0,negative.prepared(1000).position)
        val long=MediaPreviewPlayback(Long.MAX_VALUE,true);long.resume()
        assertEquals(1000,long.prepared(1000).position)
        val unknown=MediaPreviewPlayback(Long.MAX_VALUE,false)
        assertEquals(Int.MAX_VALUE,unknown.prepared(-1).position)
    }
    @Test fun completionDoesNotRestartOnForegroundReturn() {
        val state=MediaPreviewPlayback(0,true);state.resume();state.prepared(1000)
        state.completed();state.pause(1000,false)
        assertFalse(state.resume())
        assertFalse(state.snapshot(null,null).playing)
    }
    @Test fun failureCannotResumeAndLatePreparationDoesNotReenableAutoplay() {
        val state=MediaPreviewPlayback(0,true);state.resume();state.prepared(1000)
        state.failed();state.pause(400,true)
        assertFalse(state.resume())
        assertFalse(state.prepared(1000).start)
    }
    @Test fun foregroundSaveUsesCurrentPlayerObservationWithoutRewinding() {
        val state=MediaPreviewPlayback(1000,true);state.resume();state.prepared(90000)
        assertEquals(MediaPreviewPlayback.Saved(56789,true),state.snapshot(56789,true))
        state.pause(56790,true)
        assertEquals(MediaPreviewPlayback.Saved(56790,true),state.snapshot(0,false))
    }
}
