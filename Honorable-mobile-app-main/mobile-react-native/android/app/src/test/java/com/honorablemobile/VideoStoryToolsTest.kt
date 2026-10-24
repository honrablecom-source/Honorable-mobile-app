package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoStoryToolsTest {
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected invalid input rejection") } catch (_: IllegalArgumentException) {} }

    @Test fun montageBalancesSourcesPreservesStylesAndUsesOneUndoStep() {
        val project = VideoEditProject(60_000)
        project.appendSource("second", 20_000)
        project.setTitle(0, "Our trip")
        val before = project.segments
        val result = VideoStoryTools.montage(before, 15_000)
        assertEquals(listOf("primary", "second"), result.map { it.sourceId })
        assertEquals(listOf(7_500L, 7_500L), result.map { it.durationMs })
        assertEquals("Our trip", result[0].title)
        assertEquals(26_250L, result[0].startMs)
        project.applyDraft(result, soundtrack = project.soundtrack)
        assertTrue(project.undo()); assertEquals(before, project.segments)
        assertTrue(project.redo()); assertEquals(result, project.segments)
    }

    @Test fun montageRedistributesShortClipsWithoutRepeatingOrStretching() {
        val clips = listOf(VideoSegment(0, 1_000), VideoSegment(0, 30_000))
        val result = VideoStoryTools.montage(clips, 15_000)
        assertEquals(listOf(1_000L, 14_000L), result.map { it.durationMs })
        assertEquals(clips, VideoStoryTools.montage(clips, 120_000))
    }

    @Test fun montageRespectsSpeedAndDropsInvisibleCaptions() {
        val clip = VideoSegment(0, 60_000, speed=2f, captions=listOf(VideoCaption(0, 1_000, "Gone"), VideoCaption(25_000, 30_000, "Kept")))
        val result = VideoStoryTools.montage(listOf(clip), 10_000).single()
        assertEquals(20_000L, result.startMs); assertEquals(40_000L, result.endMs)
        assertEquals(10_000L, result.durationMs); assertEquals(listOf("Kept"), result.captions.map { it.text })
    }

    @Test fun montageSamplesLargeTimelinesInOrderAndKeepsEndpoints() {
        val clips = (0 until 1000).map { VideoSegment(it * 10_000L, (it + 1) * 10_000L, title="$it") }
        val result = VideoStoryTools.montage(clips, 5_000)
        assertEquals(10, result.size); assertEquals("0", result.first().title); assertEquals("999", result.last().title)
        assertEquals(5_000L, result.sumOf { it.durationMs })
        assertTrue(result.zipWithNext().all { (a,b) -> a.startMs < b.startMs })
    }

    @Test fun montageValidatesBoundsAndHandlesFractionalSpeedRounding() {
        rejects { VideoStoryTools.montage(emptyList(), 15_000) }
        rejects { VideoStoryTools.montage(listOf(VideoSegment(0, 10_000)), 4_999) }
        rejects { VideoStoryTools.montage(listOf(VideoSegment(0, 10_000)), 120_001) }
        val result = VideoStoryTools.montage(List(7) { VideoSegment(0, 60_000, speed=1.5f) }, 15_000)
        assertTrue(result.sumOf { it.durationMs } in 14_993L..15_000L)
    }

    @Test fun scriptWeightsLinesAndCoversTrimmedSourceExactly() {
        val clip = VideoSegment(10_000, 20_000)
        val cues = VideoStoryTools.scriptCaptions(clip, "One\r\n\r\nA much longer sentence\rLast")
        assertEquals(3, cues.size); assertEquals(10_000L, cues.first().startMs); assertEquals(20_000L, cues.last().endMs)
        assertTrue(cues.zipWithNext().all { (a,b) -> a.endMs == b.startMs })
        assertTrue(cues[1].endMs-cues[1].startMs > cues[0].endMs-cues[0].startMs)
    }

    @Test fun scriptMinimumUsesPlaybackSpeedAndRejectsUnsafeOrOversizedText() {
        val clip = VideoSegment(0, 2_000, speed=2f)
        assertEquals(2, VideoStoryTools.scriptCaptions(clip, "One\nTwo").size)
        rejects { VideoStoryTools.scriptCaptions(clip, "One\nTwo\nThree") }
        rejects { VideoStoryTools.scriptCaptions(clip, " ") }
        rejects { VideoStoryTools.scriptCaptions(clip, "x".repeat(241)) }
        rejects { VideoStoryTools.scriptCaptions(clip, "x\u0000y") }
        rejects { VideoStoryTools.scriptCaptions(clip, "\uD800") }
        rejects { VideoStoryTools.scriptCaptions(clip, "x".repeat(16_001)) }
    }

    @Test fun scriptKeepsUnicodeAndUsesExistingTimelineCaptionMapping() {
        val clip = VideoSegment(2_000, 8_000, speed=2f)
        val captions = VideoStoryTools.scriptCaptions(clip, "你好\nA 🌅 morning")
        val result = VideoCaptions.resolve(listOf(clip.copy(captions=captions)))
        assertEquals("你好", result[0].text); assertEquals("A 🌅 morning", result[1].text)
        assertEquals(0L, result.first().startUs); assertEquals(3_000_000L, result.last().endUs)
    }

    @Test fun scriptReplacementAndMontagePreserveSoundtrackAndRecoverableDrafts() {
        val project = VideoEditProject(40_000)
        val music = VideoSoundtrack("content://music/1", 10_000, .5f)
        project.setSoundtrack(music)
        project.setCaptions(0, VideoStoryTools.scriptCaptions(project.segments[0], "Welcome\nThanks for watching"), null)
        val before = project.segments
        project.applyDraft(VideoStoryTools.montage(before, 15_000), soundtrack=project.soundtrack)
        assertEquals(music, project.soundtrack)
        val draft = VideoDraft(40_000, project.segments, 0, 0, project.segments[0].startMs, project.segments[0].endMs, soundtrack=music)
        assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft), 40_000))
        assertTrue(project.undo()); assertEquals(before, project.segments); assertEquals(music, project.soundtrack)
    }

    @Test fun montageAcrossMixedSpeedsIsDeterministicBoundedAndNeverMutatesInputs() {
        val random = java.util.Random(42)
        repeat(80) {
            val clips = List(1 + random.nextInt(90)) { i ->
                val start = random.nextInt(50_000).toLong()
                VideoSegment(start, start + 1 + random.nextInt(120_000), speed=listOf(.5f,1f,1.5f,2f)[random.nextInt(4)], title="$i")
            }
            val before = clips.toList()
            val target = listOf(15_000L,30_000L,60_000L)[random.nextInt(3)]
            val result = VideoStoryTools.montage(clips, target)
            assertEquals(result, VideoStoryTools.montage(clips, target)); assertEquals(before, clips)
            assertTrue(result.sumOf { it.durationUs } <= target * 1_000)
            result.forEach { excerpt ->
                val original = clips[excerpt.title.toInt()]
                assertTrue(excerpt.startMs >= original.startMs && excerpt.endMs <= original.endMs)
                assertEquals(original.speed, excerpt.speed)
            }
        }
    }
}
