package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VideoPlaybackMuteTest {
    @Test fun crossSourceAndSpeedIntervalMutesOnlyTheMappedAudioWhileKeepingAllFootage() {
        val clips = listOf(VideoSegment(1000, 5000, speed = 2f, volume = .7f), VideoSegment(500, 2500, speed = .5f, sourceId = "other", volume = .3f))
        val result = VideoPlaybackMute.prepare(clips, "1,3")
        assertEquals(listOf(1000L to 3000L, 3000L to 5000L, 500L to 1000L, 1000L to 2500L), result.clips.map { it.startMs to it.endMs })
        assertEquals(listOf(.7f, 0f, 0f, .3f), result.clips.map { it.volume })
        assertEquals(listOf(0, 0, 1, 1), result.pieces.map { it.originalIndex }); assertEquals(listOf(false, true, true, false), result.pieces.map { it.newlyMuted })
        assertEquals(6000000L, result.beforeUs); assertEquals(result.beforeUs, result.afterUs)
    }
    @Test fun overlappingTouchingAndRepeatedRequestsAreUnionedWithoutRedundantSplits() {
        val result = VideoPlaybackMute.prepare(listOf(VideoSegment(0, 5000)), "1,2\n1.5,3\n3,4\n1,2")
        assertEquals(listOf(VideoPlaybackMute.Range(1000000, 4000000)), result.intervals)
        assertEquals(listOf(0L to 1000L, 1000L to 4000L, 4000L to 5000L), result.clips.map { it.startMs to it.endMs })
    }
    @Test fun oneMillisecondProgramRequestRoundsOutwardAtOneAndAHalfSpeed() {
        val result = VideoPlaybackMute.prepare(listOf(VideoSegment(100, 110, speed = 1.5f)), "0.001,0.002")
        assertEquals(listOf(100L to 101L, 101L to 103L, 103L to 110L), result.clips.map { it.startMs to it.endMs })
        assertEquals(listOf(1f, 0f, 1f), result.clips.map { it.volume })
        assertTrue(result.beforeUs - result.afterUs in 0L..2L)
    }
    @Test fun lastFractionalPlaybackMillisecondClipsToActualEndAndMutesTheWholeTinyOriginal() {
        val original = VideoSegment(0, 1, speed = 1.5f)
        val result = VideoPlaybackMute.prepare(listOf(original), "0,0.001")
        assertEquals(listOf(original.copy(volume = 0f)), result.clips)
        assertEquals(666L, result.intervals.single().to); assertEquals(666L, result.afterUs)
    }
    @Test fun alreadyMutedOriginalsStayIntactAndFullyMutedNoOpsRefuse() {
        val silent = VideoSegment(0, 1000, volume = 0f)
        val result = VideoPlaybackMute.prepare(listOf(silent, VideoSegment(0, 1000, volume = .2f)), "0.5,1.5")
        assertSame(silent, result.clips[0]); assertEquals(3, result.clips.size)
        assertEquals(listOf(0f, 0f, .2f), result.clips.map { it.volume })
        assertThrows(IllegalArgumentException::class.java) { VideoPlaybackMute.prepare(listOf(silent), "0.1,0.2") }
    }
    @Test fun wholeMuteChangesOnlyVolumeAndPartialSplitsKeepIntersectingCaptionProvenanceAndAppearance() {
        val cues = listOf(VideoCaption(0, 100, "Before"), VideoCaption(100, 900, "Crossing"), VideoCaption(900, 1100, "After"))
        val clip = VideoSegment(0, 1000, volume = .4f, title = "Styled", fadeEdges = true, audioFade = true,
            motion = VideoMotion(enabled = true, endZoom = 1.5f, anchorEndMs = 1000), captions = cues, captionSourceUri = "content://synthetic/subtitles")
        assertEquals(listOf(clip.copy(volume = 0f)), VideoPlaybackMute.prepare(listOf(clip), "0,1").clips)
        val result = VideoPlaybackMute.prepare(listOf(clip), "0.2,0.8")
        assertEquals(clip.copy(startMs = 200, endMs = 800, volume = 0f, captions = listOf(cues[1])), result.clips[1])
        assertEquals(clip.motion, result.clips[0].motion); assertEquals(clip.captionSourceUri, result.clips[2].captionSourceUri)
    }
    @Test fun pieceCapacityAndCaptionDuplicationLimitsRefuseWholeProposal() {
        assertThrows(IllegalArgumentException::class.java) { VideoPlaybackMute.prepare(List(1000) { VideoSegment(0, 1000) }, "0.001,0.002") }
        val cues = List(200) { index -> VideoCaption(index * 10L, index * 10L + 10, "Cue") }
        assertThrows(IllegalArgumentException::class.java) { VideoPlaybackMute.prepare(List(5) { VideoSegment(0, 2000, captions = cues) }, "0.005,0.006") }
    }
    @Test fun invalidRangesSyntaxOversizedListsAndUnsupportedBoundsRefuse() {
        val clips = listOf(VideoSegment(0, 1000))
        for (text in listOf("", "0,0", "0.5,0.1", "-1,0.5", "0,1.001", "1,1.001", "0.0001,0.1", "0,0.1,extra", " ".repeat(16001), List(101) { "0,0.1" }.joinToString("\n"))) {
            assertThrows(IllegalArgumentException::class.java) { VideoPlaybackMute.prepare(clips, text) }
        }
        assertThrows(IllegalArgumentException::class.java) { VideoPlaybackMute.prepare(listOf(VideoSegment(0, Int.MAX_VALUE.toLong() + 1)), "0,0.1") }
    }
    @Test fun randomEverySourceMillisecondMatchesIndependentContinuousTimeIntersection() {
        val random = Random(156)
        repeat(500) {
            val originals = List(random.nextInt(1, 5)) { index ->
                val start = random.nextInt(100).toLong()
                VideoSegment(start, start + random.nextInt(1, 21), speed = listOf(.5f, 1f, 1.5f, 2f)[random.nextInt(4)], volume = listOf(0f, .25f, .8f)[random.nextInt(3)], sourceId = if (index % 2 == 0) "primary" else "other")
            }
            val before = VideoPlaybackTime.durationUs(originals); val ceilMs = (before + 999) / 1000
            val requests = List(random.nextInt(1, 8)) { val a = random.nextLong(ceilMs); val b = random.nextLong(a + 1, ceilMs + 1); a to b }
            fun seconds(ms: Long) = "${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}"
            val text = requests.joinToString("\n") { "${seconds(it.first)},${seconds(it.second)}" }
            var offset = 0L; var changes = 0
            val expected = originals.map { clip ->
                val end = offset + clip.durationUs
                val values = (clip.startMs until clip.endMs).map { sourceMs ->
                    val startUs = offset + (sourceMs - clip.startMs) * 1000.0 / clip.speed
                    val endUs = offset + (sourceMs + 1 - clip.startMs) * 1000.0 / clip.speed
                    val overlaps = requests.any { (a, b) ->
                        val from = maxOf(offset, a * 1000); val to = minOf(end, b * 1000, before)
                        from < to && from < endUs && to > startUs
                    }
                    if (overlaps && clip.volume != 0f) changes++
                    if (overlaps) 0f else clip.volume
                }
                offset = end; values
            }
            if (changes == 0) assertThrows(IllegalArgumentException::class.java) { VideoPlaybackMute.prepare(originals, text) }
            else {
                val result = VideoPlaybackMute.prepare(originals, text)
                originals.forEachIndexed { index, original ->
                    val pieces = result.pieces.filter { it.originalIndex == index }.map { it.clip }
                    assertEquals(original.startMs, pieces.first().startMs); assertEquals(original.endMs, pieces.last().endMs)
                    assertTrue(pieces.zipWithNext().all { it.first.endMs == it.second.startMs })
                    for ((tick, sourceMs) in (original.startMs until original.endMs).withIndex()) {
                        val piece = pieces.single { sourceMs >= it.startMs && sourceMs < it.endMs }
                        assertEquals(expected[index][tick], piece.volume); assertEquals(original.speed, piece.speed); assertEquals(original.sourceId, piece.sourceId)
                    }
                }
                assertTrue(before - result.afterUs in 0L..(result.clips.size - originals.size).toLong())
            }
        }
    }
    @Test fun actualProjectKeepsSoundtrackAndAllFootageWithOneUndoAndDraftRecovery() {
        val clips = listOf(VideoSegment(0, 2000), VideoSegment(0, 4000, sourceId = "other"))
        val music = VideoSoundtrack("content://synthetic/music", 10000, .3f, false, 1000, 9000)
        val project = VideoEditProject(10000); project.restore(clips, mapOf("other" to 10000L), music)
        val result = VideoPlaybackMute.prepare(clips, "1,3", music); val request = VideoBatchReview(clips, music); val stage = Any()
        request.open(stage); request.propose(stage, result.clips); assertTrue(request.apply(stage, project) {}); assertFalse(request.apply(stage, project) {}); assertEquals(music, project.soundtrack)
        val first = result.clips.first(); val draft = VideoDraft(10000, project.segments, 0, first.startMs, first.startMs, first.endMs,
            listOf(VideoSource("other", "content://synthetic/other", 10000)), music)
        assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft), 10000))
        assertTrue(project.undo()); assertEquals(clips, project.segments); assertFalse(project.canUndo); assertTrue(project.redo()); assertEquals(result.clips, project.segments)
    }
    @Test fun closedForeignPausedAndStaleApprovalsCannotApplyOrReviveMutedProposal() {
        val clips = listOf(VideoSegment(0, 2000))
        for (mode in 0..3) {
            val project = VideoEditProject(10000); project.restore(clips); val request = VideoBatchReview(clips, null); val stage = Any()
            request.open(stage); request.propose(stage, VideoPlaybackMute.prepare(clips, "0.5,1").clips)
            when (mode) {
                0 -> { request.dismiss(stage); assertFalse(request.apply(stage, project) {}) }
                1 -> assertFalse(request.apply(Any(), project) {})
                2 -> assertThrows(IllegalStateException::class.java) { request.apply(stage, project) { error("Paused") } }
                3 -> { project.setTitle(0, "Changed"); assertThrows(IllegalArgumentException::class.java) { request.apply(stage, project) {} }; project.undo(); assertFalse(request.apply(stage, project) {}) }
            }
            assertEquals(1f, project.segments.first().volume)
        }
    }
}
