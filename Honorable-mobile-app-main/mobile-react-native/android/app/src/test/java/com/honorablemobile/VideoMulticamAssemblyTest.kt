package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VideoMulticamAssemblyTest {
    private val clips = listOf(VideoSegment(0, 10000, speed = 2f, title = "Camera A"),
        VideoSegment(0, 10000, speed = .5f, sourceId = "other", title = "Camera B"))
    private val durations = mapOf("primary" to 10000L, "other" to 10000L)
    private val anchors = "primary,2.500,0\nother,1.250,0"
    private val switches = "0,primary\n1,other\n2,primary"
    @Test fun calibratedCommonClockDeterminesExactSourceWindowsAtNormalSpeed() {
        val result = VideoMulticamAssembly.prepare(clips, durations, anchors, switches, "3")
        assertEquals(listOf(2500L to 3500L, 2250L to 3250L, 4500L to 5500L), result.clips.map { it.startMs to it.endMs })
        assertEquals(listOf("primary", "other", "primary"), result.clips.map { it.sourceId })
        assertTrue(result.clips.all { it.speed == 1f }); assertEquals(3000000L, VideoPlaybackTime.durationUs(result.clips))
        assertEquals(listOf(0, 1, 0), result.cuts.map { it.templateIndex })
        assertEquals(listOf(0L to 1000L, 1000L to 2000L, 2000L to 3000L), result.cuts.map { it.programFromMs to it.programToMs })
    }
    @Test fun negativeOffsetsAndAnchorsAtDifferentProgramTimesWorkWithoutRounding() {
        val result = VideoMulticamAssembly.prepare(clips, durations, "primary,2,2\nother,0,0.5", switches, "3")
        assertEquals(listOf(0L to 1000L, 500L to 1500L, 2000L to 3000L), result.clips.map { it.startMs to it.endMs })
        assertEquals(-500L, result.anchors[1].offsetMs)
    }
    @Test fun firstCameraTemplateKeepsAppearanceSourceMotionAndOnlyItsIntersectingCaptions() {
        val captions = listOf(VideoCaption(0, 1000, "Earlier"), VideoCaption(2500, 3500, "Camera cue"))
        val first = clips[0].copy(volume = .2f, fadeEdges = true, audioFade = true, captions = captions,
            captionSourceUri = "content://synthetic/subtitles", motion = VideoMotion(enabled = true, endZoom = 1.3f, anchorEndMs = 10000))
        val later = first.copy(title = "Different later look", captions = emptyList())
        val result = VideoMulticamAssembly.prepare(listOf(first, clips[1], later), durations, anchors, switches, "3")
        assertEquals(first.copy(startMs = 2500, endMs = 3500, speed = 1f, captions = captions.drop(1)), result.clips[0])
        assertEquals(first.motion, result.clips[2].motion); assertEquals("Camera A", result.clips[2].title)
        assertTrue(result.clips[2].captions.isEmpty()); assertEquals(first.captionSourceUri, result.clips[2].captionSourceUri)
    }
    @Test fun impossibleWindowsDoNotShiftClampOrDropTheRequestedCameras() {
        for (anchor in listOf("primary,0,1\nother,0,0", "primary,9,0\nother,0,0")) {
            assertThrows(IllegalArgumentException::class.java) { VideoMulticamAssembly.prepare(clips, durations, anchor, switches, "3") }
        }
        assertEquals(0L, clips[0].startMs); assertEquals(10000L, clips[1].endMs)
    }
    @Test fun malformedDuplicateUnknownAndOutOfDurationAnchorsRefuse() {
        for (text in listOf("", "primary,0,0", "primary,0,0\nprimary,1,0", "primary,0,0\nmissing,0,0",
            "primary,11,0\nother,0,0", "primary,-1,0\nother,0,0", "primary,0.0001,0\nother,0,0", "primary,0\nother,0,0")) {
            assertThrows(text, IllegalArgumentException::class.java) { VideoMulticamAssembly.prepare(clips, durations, text, switches, "3") }
        }
        assertThrows(IllegalArgumentException::class.java) { VideoMulticamAssembly.prepare(clips, durations + ("unused" to 10000L), anchors + "\nunused,0,0", switches, "3") }
    }
    @Test fun switchOrderZeroStartCameraDiversityMinimumDurationAndEndAreStrict() {
        for (text in listOf("", "0,primary", "0,primary\n1,primary", "1,primary\n2,other", "0,primary\n0,other",
            "0,primary\n2,other\n1,primary", "0,primary\n3,other", "0,primary\n0.099,other", "0,primary\n1,unknown", "0,primary,extra\n1,other")) {
            assertThrows(text, IllegalArgumentException::class.java) { VideoMulticamAssembly.prepare(clips, durations, anchors, text, "3") }
        }
        for (end in listOf("0", "2", "3.0001")) assertThrows(IllegalArgumentException::class.java) { VideoMulticamAssembly.prepare(clips, durations, anchors, switches, end) }
    }
    @Test fun exactTwoHundredSwitchesAndHundredMillisecondCutsAreSupported() {
        val sourceClips = clips.map { it.copy(endMs = 30000) }; val lengths = durations.mapValues { 30000L }
        val text = (0 until 200).joinToString("\n") { index -> "${index / 10}.${index % 10},${if (index % 2 == 0) "primary" else "other"}" }
        val result = VideoMulticamAssembly.prepare(sourceClips, lengths, "primary,0,0\nother,0,0", text, "20")
        assertEquals(200, result.clips.size); assertEquals(20_000_000L, VideoPlaybackTime.durationUs(result.clips))
        assertTrue(result.clips.all { it.endMs - it.startMs == 100L })
        assertThrows(IllegalArgumentException::class.java) { VideoMulticamAssembly.prepare(sourceClips, lengths, "primary,0,0\nother,0,0", text + "\n20,primary", "20.1") }
    }
    @Test fun catalogStalenessNoOpAndOversizedTextRefuseBeforePublication() {
        assertThrows(IllegalArgumentException::class.java) { VideoMulticamAssembly.prepare(clips, durations + ("other" to 9999L), anchors, switches, "3") }
        val same = listOf(VideoSegment(0, 1000), VideoSegment(1000, 2000, sourceId = "other"))
        assertThrows(IllegalArgumentException::class.java) { VideoMulticamAssembly.prepare(same, durations, "primary,0,0\nother,0,0", "0,primary\n1,other", "2") }
        assertThrows(IllegalArgumentException::class.java) { VideoMulticamAssembly.prepare(clips, durations, " ".repeat(16001), switches, "3") }
    }
    @Test fun randomFixedOffsetPlansMatchIndependentAnchorDifferenceArithmetic() {
        val random = Random(150)
        repeat(300) {
            val aSource = random.nextInt(2000, 4000).toLong(); val aProgram = random.nextInt(0, 1000).toLong()
            val bSource = random.nextInt(2000, 4000).toLong(); val bProgram = random.nextInt(0, 1000).toLong()
            fun seconds(ms: Long) = "${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}"
            val anchorText = "primary,${seconds(aSource)},${seconds(aProgram)}\nother,${seconds(bSource)},${seconds(bProgram)}"
            val result = VideoMulticamAssembly.prepare(clips, durations, anchorText, switches, "3")
            result.cuts.forEach { cut ->
                val source = if (cut.clip.sourceId == "primary") aSource else bSource
                val program = if (cut.clip.sourceId == "primary") aProgram else bProgram
                assertEquals(source + (cut.programFromMs - program), cut.clip.startMs)
                assertEquals(source + (cut.programToMs - program), cut.clip.endMs)
                assertEquals(1f, cut.clip.speed)
            }
        }
    }
    @Test fun actualProjectHasOneUndoPreservedSoundtrackAndRecoverableDraft() {
        val music = VideoSoundtrack("content://synthetic/music", 10000, .3f, false)
        val project = VideoEditProject(10000); project.restore(clips, mapOf("other" to 10000L), music)
        val proposal = VideoMulticamAssembly.prepare(clips, durations, anchors, switches, "3", music)
        val request = VideoBatchReview(clips, music); val stage = Any(); request.open(stage); request.propose(stage, proposal.clips)
        assertTrue(request.apply(stage, project) {}); assertFalse(request.apply(stage, project) {}); assertEquals(music, project.soundtrack)
        val draft = VideoDraft(10000, project.segments, 0, 2500, 2500, 3500, listOf(VideoSource("other", "content://synthetic/other", 10000)), music)
        assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft), 10000))
        assertTrue(project.undo()); assertEquals(clips, project.segments); assertFalse(project.canUndo)
        assertTrue(project.redo()); assertEquals(proposal.clips, project.segments)
    }
    @Test fun foreignClosedPausedAndStaleApprovalsCannotReplaceTheTimeline() {
        for (mode in 0..3) {
            val project = VideoEditProject(10000); project.restore(clips, mapOf("other" to 10000L))
            val request = VideoBatchReview(clips, null); val stage = Any(); request.open(stage)
            request.propose(stage, VideoMulticamAssembly.prepare(clips, durations, anchors, switches, "3").clips)
            when (mode) {
                0 -> { request.dismiss(stage); assertFalse(request.apply(stage, project) {}) }
                1 -> assertFalse(request.apply(Any(), project) {})
                2 -> assertThrows(IllegalStateException::class.java) { request.apply(stage, project) { error("Paused") } }
                3 -> { project.setTitle(0, "Changed"); assertThrows(IllegalArgumentException::class.java) { request.apply(stage, project) {} }; project.undo(); assertFalse(request.apply(stage, project) {}) }
            }
            assertEquals(2, project.segments.size)
        }
    }
}
