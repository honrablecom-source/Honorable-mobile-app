package com.honorablemobile

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class VideoDraftTest {
    private fun draft() = VideoDraft(
        20_000,
        listOf(VideoSegment(10_000, 19_000, 2f, 0f), VideoSegment(1000, 8000, .5f, .5f)),
        1, 4300, 2000, 7000,
    )

    @Test fun reorderedEditsAudioAndPendingTrimSurviveSerialization() {
        val restored = requireNotNull(VideoDraftCodec.decode(VideoDraftCodec.encode(draft()), 20_000))
        assertEquals(draft(), restored)
        val project = VideoEditProject(20_000)
        project.restore(restored.segments)
        project.trim(restored.selectedClip, restored.trimStartMs, restored.trimEndMs)
        assertEquals(listOf(VideoSegment(10_000, 19_000, 2f, 0f), VideoSegment(2000, 7000, .5f, .5f)), project.segments)
        assertEquals(14_500L, project.totalDurationMs)
    }

    @Test fun corruptFutureTruncatedAndChangedSourcesCannotBecomeProjects() {
        val bytes = VideoDraftCodec.encode(draft())
        assertNull(VideoDraftCodec.decode(bytes, 20_001))
        assertNull(VideoDraftCodec.decode(bytes.copyOf(bytes.size - 1), 20_000))
        assertNull(VideoDraftCodec.decode(bytes + byteArrayOf(1), 20_000))
        val future = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(4, 2) }
        assertNull(VideoDraftCodec.decode(future, 20_000))
        val unbounded = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(44, Int.MAX_VALUE) }
        assertNull(VideoDraftCodec.decode(unbounded, 20_000))
        assertNull(VideoDraftCodec.decode(ByteArray(32_001), 20_000))
    }

    @Test fun invalidSelectionsAndClipsAreRejectedBeforeRestore() {
        val bytes = VideoDraftCodec.encode(draft())
        for ((offset, value) in listOf(16 to 5, 44 to 0)) {
            val invalid = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(offset, value) }
            assertNull(VideoDraftCodec.decode(invalid, 20_000))
        }
        val outsideSource = bytes.copyOf().also { ByteBuffer.wrap(it).putLong(56, 21_000) }
        assertNull(VideoDraftCodec.decode(outsideSource, 20_000))
        val invalidSpeed = bytes.copyOf().also { ByteBuffer.wrap(it).putFloat(64, Float.NaN) }
        assertNull(VideoDraftCodec.decode(invalidSpeed, 20_000))
        val invalidTrim = bytes.copyOf().also { ByteBuffer.wrap(it).putLong(28, 0) }
        assertNull(VideoDraftCodec.decode(invalidTrim, 20_000))
    }

    @Test fun accountApiAndSourceAreIsolatedWithoutIdentifiersInNames() {
        val key = VideoDraftCodec.storageName("account-a", "https://api.example", "content://media/1")
        assertEquals(key, VideoDraftCodec.storageName("account-a", "https://api.example", "content://media/1"))
        assertNotEquals(key, VideoDraftCodec.storageName("account-b", "https://api.example", "content://media/1"))
        assertNotEquals(key, VideoDraftCodec.storageName("account-a", "https://other.example", "content://media/1"))
        assertNotEquals(key, VideoDraftCodec.storageName("account-a", "https://api.example", "content://media/2"))
        assertTrue(key.matches(Regex("video-draft-[0-9a-f]{64}")))
        assertNotEquals(VideoDraftCodec.storageName("a|b", "c", "d"), VideoDraftCodec.storageName("a", "b|c", "d"))
    }

    @Test fun restoringADraftKeepsPriorClipEditsUndoableAndRejectsOtherSources() {
        val project = VideoEditProject(20_000)
        project.trim(0, 3000, 12_000)
        val prior = project.segments
        project.applyDraft(draft().segments)
        assertEquals(draft().segments, project.segments)
        assertTrue(project.undo())
        assertEquals(prior, project.segments)
        assertTrue(project.redo())
        assertEquals(draft().segments, project.segments)
        try {
            project.applyDraft(listOf(VideoSegment(0, 21_000)))
            fail("Outside-source draft accepted")
        } catch (_: IllegalArgumentException) { assertEquals(draft().segments, project.segments) }
    }
}
