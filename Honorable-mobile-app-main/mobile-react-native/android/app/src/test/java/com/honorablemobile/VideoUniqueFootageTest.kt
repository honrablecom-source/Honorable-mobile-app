package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VideoUniqueFootageTest {
    @Test fun firstOccurrenceWinsEvenWhenTheLaterCopyIsSlowerOrStyledDifferently() {
        val first = VideoSegment(1000, 4000, speed = 2f, title = "First")
        val later = VideoSegment(2000, 5000, speed = .5f, title = "Later")
        val result = VideoUniqueFootage.prepare(listOf(first, later, later))
        assertSame(first, result.clips[0]); assertEquals(listOf(1000L to 4000L, 4000L to 5000L), result.clips.map { it.startMs to it.endMs })
        assertEquals(.5f, result.clips[1].speed); assertEquals("Later", result.clips[1].title)
        assertEquals(listOf(1, 2), result.changedClips); assertEquals(listOf(2), result.removedClips)
        assertEquals(3500000L, result.afterUs); assertEquals(13500000L, result.beforeUs)
    }
    @Test fun aLaterClipCanKeepMultipleHolesInOriginalSourceOrder() {
        val clips = listOf(VideoSegment(200, 400), VideoSegment(600, 800), VideoSegment(0, 1000))
        val result = VideoUniqueFootage.prepare(clips)
        assertEquals(listOf(200L to 400L, 600L to 800L, 0L to 200L, 400L to 600L, 800L to 1000L), result.clips.map { it.startMs to it.endMs })
        assertEquals(listOf(0, 1, 2, 2, 2), result.pieces.map { it.originalIndex })
        assertEquals(1000000L, result.afterUs)
    }
    @Test fun aliasesRemainDistinctAndTouchingWindowsAreNotDuplicates() {
        for (clips in listOf(listOf(VideoSegment(0, 1000), VideoSegment(1000, 2000)),
            listOf(VideoSegment(0, 1000), VideoSegment(0, 1000, sourceId = "other")))) {
            assertThrows(IllegalArgumentException::class.java) { VideoUniqueFootage.prepare(clips) }
        }
    }
    @Test fun partialPiecesRetainAllAppearanceAndOnlyIntersectingSourceBoundCaptions() {
        val captions = listOf(VideoCaption(0, 200, "Before"), VideoCaption(200, 700, "Crossing"), VideoCaption(800, 1000, "After"))
        val original = VideoSegment(0, 1000, speed = .5f, volume = .3f, title = "Styled", fadeEdges = true, audioFade = true,
            captions = captions, captionSourceUri = "content://synthetic/captions")
        val result = VideoUniqueFootage.prepare(listOf(VideoSegment(0, 400), original)).clips[1]
        assertEquals(original.copy(startMs = 400, captions = captions.drop(1)), result)
        assertSame(captions[1], result.captions[0]); assertEquals("content://synthetic/captions", result.captionSourceUri)
    }
    @Test fun tinyNewPiecesRefuseTheWholeProposalRatherThanSilentlyDroppingUniqueFootage() {
        val original = listOf(VideoSegment(100, 1000), VideoSegment(0, 2000, speed = 2f))
        val error = assertThrows(IllegalArgumentException::class.java) { VideoUniqueFootage.prepare(original) }
        assertTrue(error.message.orEmpty().contains("0.1")); assertEquals(0L, original[1].startMs)
    }
    @Test fun wholeTinyOriginalsStayAndTheirExactLaterDuplicatesCanBeRemoved() {
        val tiny = VideoSegment(0, 1, speed = 2f)
        val result = VideoUniqueFootage.prepare(listOf(tiny, tiny))
        assertEquals(listOf(tiny), result.clips); assertSame(tiny, result.clips[0]); assertEquals(500L, result.afterUs)
    }
    @Test fun fragmentCapacityRefusesInsteadOfTruncatingTheProposal() {
        val earlier = List(500) { i -> VideoSegment(i * 400L + 100, i * 400L + 200) }
        val error = assertThrows(IllegalArgumentException::class.java) { VideoUniqueFootage.prepare(earlier + VideoSegment(0, 200000)) }
        assertTrue(error.message.orEmpty().contains("1000"))
    }
    @Test fun cancellationPublishesNoPartialProposal() {
        var calls = 0
        val error = assertThrows(IllegalStateException::class.java) {
            VideoUniqueFootage.prepare(listOf(VideoSegment(0, 1000), VideoSegment(500, 2000)), current = { if (++calls == 4) error("Synthetic cancel") })
        }
        assertEquals("Synthetic cancel", error.message)
    }
    @Test fun randomPlansMatchIndependentPerCellFirstOccurrenceCoverage() {
        val random = Random(148)
        repeat(300) {
            val clips = List(20) {
                val start = random.nextInt(20); val end = random.nextInt(start + 1, 25)
                VideoSegment(start * 200L, end * 200L, speed = listOf(.5f, 1f, 1.5f, 2f)[random.nextInt(4)], sourceId = if (random.nextBoolean()) "primary" else "other")
            }
            val seen = mutableMapOf<String, BooleanArray>()
            val expected = mutableListOf<Triple<Int, Long, Long>>()
            clips.forEachIndexed { index, clip ->
                val cells = seen.getOrPut(clip.sourceId) { BooleanArray(25) }
                var start: Int? = null
                for (cell in (clip.startMs / 200).toInt()..(clip.endMs / 200).toInt()) {
                    val unique = cell * 200L < clip.endMs && !cells[cell]
                    if (unique && start == null) start = cell
                    if (!unique && start != null) { expected += Triple(index, start!! * 200L, cell * 200L); start = null }
                }
                for (cell in (clip.startMs / 200).toInt() until (clip.endMs / 200).toInt()) cells[cell] = true
            }
            val result = VideoUniqueFootage.prepare(clips)
            assertEquals(expected, result.pieces.map { Triple(it.originalIndex, it.clip.startMs, it.clip.endMs) })
            assertTrue(result.afterUs < result.beforeUs)
        }
    }
    @Test fun realProjectApprovalIsOneUndoAndRetainsSoundtrackAndDraftRecovery() {
        val clips = listOf(VideoSegment(0, 2000), VideoSegment(1000, 3000))
        val music = VideoSoundtrack("content://synthetic/music", 10000, .4f, false, 1000, 9000)
        val project = VideoEditProject(10000); project.restore(clips, soundtrack = music)
        val request = VideoBatchReview(clips, music); val stage = Any(); request.open(stage)
        assertTrue(request.propose(stage, VideoUniqueFootage.prepare(clips, music).clips)); assertTrue(request.apply(stage, project) {})
        assertEquals(music, project.soundtrack); assertFalse(request.apply(stage, project) {})
        val draft = VideoDraft(10000, project.segments, 0, 0, 0, 2000, soundtrack = music)
        assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft), 10000))
        assertTrue(project.undo()); assertEquals(clips, project.segments); assertEquals(music, project.soundtrack); assertFalse(project.canUndo)
        assertTrue(project.redo()); assertEquals(2000L, project.segments.last().startMs)
    }
    @Test fun closedForeignPausedAndStaleApprovalsCannotApplyOrBeRevived() {
        for (mode in 0..3) {
            val clips = listOf(VideoSegment(0, 2000), VideoSegment(1000, 3000))
            val project = VideoEditProject(10000); project.restore(clips)
            val request = VideoBatchReview(clips, null); val stage = Any(); request.open(stage); request.propose(stage, VideoUniqueFootage.prepare(clips).clips)
            when (mode) {
                0 -> { request.dismiss(stage); assertFalse(request.apply(stage, project) {}) }
                1 -> assertFalse(request.apply(Any(), project) {})
                2 -> assertThrows(IllegalStateException::class.java) { request.apply(stage, project) { error("Paused") } }
                3 -> { project.setTitle(0, "Changed"); assertThrows(IllegalArgumentException::class.java) { request.apply(stage, project) {} } }
            }
            assertEquals(1000L, project.segments[1].startMs)
        }
    }
}
