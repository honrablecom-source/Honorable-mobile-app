package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VideoFitDurationTest {
    @Test fun equalShotsBecomeEqualCenteredExcerptsWithoutLosingAnyShot() {
        val clips = listOf(VideoSegment(1000, 11000, title = "First"), VideoSegment(2000, 12000, sourceId = "other", title = "Second"))
        val result = VideoFitDuration.prepare(clips, "6")
        assertEquals(listOf(4500L to 7500L, 5500L to 8500L), result.clips.map { it.startMs to it.endMs })
        assertEquals(listOf("primary", "other"), result.clips.map { it.sourceId })
        assertEquals(6_000_000L, result.afterUs); assertEquals(20_000_000L, result.beforeUs)
    }
    @Test fun unevenShotCapacitiesAreWeightedRatherThanAllCappedAtOneLength() {
        val result = VideoFitDuration.prepare(listOf(VideoSegment(0, 2000), VideoSegment(0, 8000)), "5")
        assertEquals(listOf(1031L, 3969L), result.clips.map { it.endMs - it.startMs })
        assertEquals(5_000_000L, result.afterUs)
    }
    @Test fun wholeTinyOriginalsStayButNewPiecesCannotBecomeTooShort() {
        val tiny = VideoSegment(0, 1, speed = .5f)
        val result = VideoFitDuration.prepare(listOf(tiny, VideoSegment(0, 1000)), "0.102")
        assertSame(tiny, result.clips[0]); assertEquals(100L, result.clips[1].endMs - result.clips[1].startMs)
        assertEquals(102000L, result.minimumUs); assertEquals(102000L, result.afterUs)
        assertThrows(IllegalArgumentException::class.java) { VideoFitDuration.prepare(listOf(tiny, VideoSegment(0, 1000)), "0.101") }
    }
    @Test fun sourceQuantizationCanUndershootButNeverExceedsTargetOrTwoMilliseconds() {
        val result = VideoFitDuration.prepare(listOf(VideoSegment(0, 1000, speed = .5f), VideoSegment(0, 1000, speed = .5f)), "1.001")
        assertEquals(1_001_000L, result.requestedUs); assertEquals(1_000_000L, result.afterUs)
        assertEquals(1000L, result.requestedUs - result.afterUs)
    }
    @Test fun appearanceSpeedSourceMotionAndIntersectingCaptionProvenanceStay() {
        val captions = listOf(VideoCaption(0, 200, "Outside"), VideoCaption(300, 700, "Crossing"), VideoCaption(800, 1000, "Outside end"))
        val original = VideoSegment(0, 1000, speed = 2f, volume = .2f, title = "Styled", fadeEdges = true, audioFade = true,
            motion = VideoMotion(enabled = true, endZoom = 1.3f, anchorEndMs = 1000), captions = captions, captionSourceUri = "content://synthetic/cues")
        val result = VideoFitDuration.prepare(listOf(original), "0.1").clips.single()
        assertEquals(original.copy(startMs = 400, endMs = 600, captions = listOf(captions[1])), result)
        assertSame(captions[1], result.captions.single())
    }
    @Test fun billionMillisecondWindowsDoNotOverflowProportionalArithmetic() {
        val result = VideoFitDuration.prepare(listOf(VideoSegment(0, 1_000_000_000L), VideoSegment(0, 1_000_000_000L)), "1000000")
        assertEquals(1_000_000_000_000L, result.afterUs)
        assertEquals(listOf(250000000L to 750000000L, 250000000L to 750000000L), result.clips.map { it.startMs to it.endMs })
    }
    @Test fun oneThousandShotsRemainOneThousandWithBoundedGlobalRounding() {
        val result = VideoFitDuration.prepare(List(1000) { VideoSegment(0, 1000, speed = listOf(.5f, 1f, 1.5f, 2f)[it % 4]) }, "123.457")
        assertEquals(1000, result.clips.size); assertTrue(result.requestedUs - result.afterUs in 0L until 2000L)
        assertTrue(result.clips.all { it.durationUs >= 100000 })
    }
    @Test fun randomPlansRetainEveryShotAtItsSpeedInsideOriginalCenteredBounds() {
        val random = Random(152)
        repeat(500) {
            val clips = List(random.nextInt(1, 30)) { index ->
                val start = random.nextInt(1000).toLong(); val span = random.nextInt(400, 3000).toLong()
                VideoSegment(start, start + span, speed = listOf(.5f, 1f, 1.5f, 2f)[random.nextInt(4)], title = "Shot $index")
            }
            val before = VideoPlaybackTime.durationUs(clips)
            val targetMs = random.nextLong(clips.size * 100L, (before - 1) / 1000 + 1)
            val text = "${targetMs / 1000}.${(targetMs % 1000).toString().padStart(3, '0')}"
            val result = VideoFitDuration.prepare(clips, text)
            assertEquals(clips.size, result.clips.size)
            var independentUs = 0L
            result.clips.forEachIndexed { index, selected ->
                val original = clips[index]; assertEquals(original.title, selected.title); assertEquals(original.speed, selected.speed)
                assertTrue(selected.startMs >= original.startMs && selected.endMs <= original.endMs)
                assertTrue(kotlin.math.abs((selected.startMs - original.startMs) - (original.endMs - selected.endMs)) <= 1)
                assertTrue(selected.durationUs >= 100000 || selected == original)
                val ticks = selected.endMs - selected.startMs
                independentUs += when (selected.speed) { .5f -> ticks * 2000; 1f -> ticks * 1000; 1.5f -> ticks * 2000 / 3; else -> ticks * 500 }
            }
            assertEquals(independentUs, result.afterUs); assertTrue(targetMs * 1000 - independentUs in 0L until 2000L)
        }
    }
    @Test fun invalidNoOpExpansionTooShortAndUnsupportedTimelineBoundsRefuse() {
        val clips = listOf(VideoSegment(0, 1000), VideoSegment(0, 1000))
        for (text in listOf("", "0", "2", "3", "0.199", "1.0001", "-1", "NaN")) {
            assertThrows(text, IllegalArgumentException::class.java) { VideoFitDuration.prepare(clips, text) }
        }
        assertThrows(IllegalArgumentException::class.java) { VideoFitDuration.prepare(listOf(VideoSegment(0, Int.MAX_VALUE.toLong() + 1)), "1") }
    }
    @Test fun actualProjectOneUseApprovalKeepsMusicAndOneUndoRestoresAllShots() {
        val clips = listOf(VideoSegment(0, 2000), VideoSegment(0, 4000, sourceId = "other"))
        val music = VideoSoundtrack("content://synthetic/music", 10000, .3f, false, 1000, 9000)
        val project = VideoEditProject(10000); project.restore(clips, mapOf("other" to 10000L), music)
        val result = VideoFitDuration.prepare(clips, "3", music); val request = VideoBatchReview(clips, music); val stage = Any()
        request.open(stage); request.propose(stage, result.clips); assertTrue(request.apply(stage, project) {}); assertFalse(request.apply(stage, project) {})
        assertEquals(music, project.soundtrack)
        val first = result.clips.first(); val draft = VideoDraft(10000, project.segments, 0, first.startMs, first.startMs, first.endMs,
            listOf(VideoSource("other", "content://synthetic/other", 10000)), music)
        assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft), 10000))
        assertTrue(project.undo()); assertEquals(clips, project.segments); assertFalse(project.canUndo); assertTrue(project.redo()); assertEquals(result.clips, project.segments)
    }
    @Test fun closedForeignPausedAndStaleApprovalsCannotCommitFitResults() {
        val clips = listOf(VideoSegment(0, 2000), VideoSegment(0, 4000))
        for (mode in 0..3) {
            val project = VideoEditProject(10000); project.restore(clips)
            val request = VideoBatchReview(clips, null); val stage = Any(); request.open(stage); request.propose(stage, VideoFitDuration.prepare(clips, "3").clips)
            when (mode) {
                0 -> { request.dismiss(stage); assertFalse(request.apply(stage, project) {}) }
                1 -> assertFalse(request.apply(Any(), project) {})
                2 -> assertThrows(IllegalStateException::class.java) { request.apply(stage, project) { error("Paused") } }
                3 -> { project.setTitle(0, "Changed"); assertThrows(IllegalArgumentException::class.java) { request.apply(stage, project) {} }; project.undo(); assertFalse(request.apply(stage, project) {}) }
            }
            assertEquals(0L, project.segments.first().startMs)
        }
    }
}
