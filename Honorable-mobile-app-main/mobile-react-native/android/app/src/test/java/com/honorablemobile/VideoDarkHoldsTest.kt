package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VideoDarkHoldsTest {
    private val clip = VideoSegment(1000, 6000)
    private val states = listOf(VideoDarkHolds.State.BRIGHT, VideoDarkHolds.State.DARK, VideoDarkHolds.State.DARK, VideoDarkHolds.State.DARK, VideoDarkHolds.State.BRIGHT, VideoDarkHolds.State.DARK, VideoDarkHolds.State.DARK, VideoDarkHolds.State.DARK, VideoDarkHolds.State.BRIGHT, VideoDarkHolds.State.BRIGHT)
    private fun analysis(value: VideoSegment = clip) = VideoDarkHolds.analyze(value, states)
    private fun fails(part: String, action: () -> Unit) { val error = assertThrows(IllegalArgumentException::class.java) { action() }; assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(part)) }
    @Test fun thousandSamplePlansHaveBoundedDistinctHalfOpenCentersAtEverySourceOffset() {
        val random = Random(166)
        repeat(1000) {
            val span = random.nextInt(2500, 120001); val start = random.nextLong(0, Int.MAX_VALUE.toLong() - span); val source = VideoSegment(start, start + span)
            val actual = VideoDarkHolds.times(source); val count = minOf(64, (span + 499) / 500)
            assertEquals(count, actual.size); assertEquals(count, actual.distinct().size); assertTrue(actual.all { it in source.startMs until source.endMs })
            actual.forEachIndexed { index, time -> val expected = start + (index + .5) * span / count; assertTrue(time <= expected && expected - time < 1.0) }
        }
        assertEquals(64, VideoDarkHolds.times(VideoSegment(0, 120000)).size)
        for (source in listOf(VideoSegment(0, 2499), VideoSegment(0, 120001), VideoSegment(Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong() + 2500))) fails("2.5") { VideoDarkHolds.times(source) }
    }
    @Test fun strictRgbThresholdAndAnyTransparencyAreClassifiedWithoutAveragingOrMutation() {
        for (channel in 0..255) for (shift in listOf(0, 8, 16)) {
            val pixels = IntArray(35) { 0xff000000.toInt() }; pixels[17] = pixels[17] or (channel shl shift); val original = pixels.copyOf()
            assertEquals(if (channel <= 12) VideoDarkHolds.State.DARK else VideoDarkHolds.State.BRIGHT, VideoDarkHolds.classify(7, 5, pixels)); assertArrayEquals(original, pixels)
        }
        for (alpha in 0..254) assertEquals(VideoDarkHolds.State.UNKNOWN, VideoDarkHolds.classify(1, 1, intArrayOf((alpha shl 24) or 0xffffff)))
        assertEquals(VideoDarkHolds.State.DARK, VideoDarkHolds.classify(1, 1, intArrayOf(0xff0c0c0c.toInt())))
        val mixed = intArrayOf(0xffffffff.toInt(), 0x00000000); assertEquals(VideoDarkHolds.State.UNKNOWN, VideoDarkHolds.classify(2, 1, mixed))
    }
    @Test fun randomArgbPlanesMatchIndependentPerPixelOracleIncludingMaximumAndCancellation() {
        val random = Random(1661)
        repeat(100) {
            val width = random.nextInt(1, 65); val height = random.nextInt(1, 65)
            val pixels = IntArray(width * height) { if (it == 0) random.nextInt() else 0xff000000.toInt() or random.nextInt(13) or (random.nextInt(13) shl 8) or (random.nextInt(13) shl 16) }
            val opaque = pixels.all { (it ushr 24) == 255 }; val dark = pixels.all { (it and 255) <= 12 && (it ushr 8 and 255) <= 12 && (it ushr 16 and 255) <= 12 }
            assertEquals(if (!opaque) VideoDarkHolds.State.UNKNOWN else if (dark) VideoDarkHolds.State.DARK else VideoDarkHolds.State.BRIGHT, VideoDarkHolds.classify(width, height, pixels))
        }
        val max = IntArray(256 * 256) { 0xff000000.toInt() }; assertEquals(VideoDarkHolds.State.DARK, VideoDarkHolds.classify(256, 256, max))
        var calls = 0; assertThrows(IllegalStateException::class.java) { VideoDarkHolds.classify(256, 256, max) { if (++calls == 100) error("Synthetic cancellation") } }; assertEquals(100, calls)
        assertThrows(IllegalArgumentException::class.java) { VideoDarkHolds.classify(257, 1, IntArray(257)) }; assertThrows(IllegalArgumentException::class.java) { VideoDarkHolds.classify(2, 2, IntArray(3)) }
    }
    @Test fun imageSizesKeepAspectAndNeverUpscale() {
        assertEquals(256 to 144, VideoDarkHolds.size(3840, 2160)); assertEquals(144 to 256, VideoDarkHolds.size(2160, 3840)); assertEquals(12 to 7, VideoDarkHolds.size(12, 7)); assertEquals(256 to 1, VideoDarkHolds.size(100000, 1))
        assertThrows(IllegalArgumentException::class.java) { VideoDarkHolds.size(0, 10) }
    }
    @Test fun onlyInteriorThreeSampleRunsWithBrightNeighborsUseInwardRequestedCenters() {
        val a = analysis()
        assertEquals(listOf(VideoDarkHolds.Candidate(1, 3, 1750, 2750), VideoDarkHolds.Candidate(5, 7, 3750, 4750)), a.candidates)
        val leading = states.toMutableList().apply { this[0] = VideoDarkHolds.State.DARK }; assertEquals(listOf(a.candidates[1]), VideoDarkHolds.analyze(clip, leading).candidates)
        val trailing = states.toMutableList().apply { this[8] = VideoDarkHolds.State.DARK; this[9] = VideoDarkHolds.State.DARK }; assertEquals(listOf(a.candidates[0]), VideoDarkHolds.analyze(clip, trailing).candidates)
        fails("No interior") { VideoDarkHolds.analyze(clip, List(10) { VideoDarkHolds.State.DARK }) }
        fails("No interior") { VideoDarkHolds.analyze(clip, List(10) { VideoDarkHolds.State.BRIGHT }) }
        fails("Incomplete") { VideoDarkHolds.analyze(clip, states.dropLast(1)) }
    }
    @Test fun unknownNeighborsAndShortDarkRunsNeverBecomeRemovedIntervals() {
        val unknown = states.toMutableList().apply { this[4] = VideoDarkHolds.State.UNKNOWN }
        fails("No interior") { VideoDarkHolds.analyze(clip, unknown) }
        val short = states.toMutableList().apply { this[2] = VideoDarkHolds.State.BRIGHT }; assertEquals(listOf(analysis().candidates[1]), VideoDarkHolds.analyze(clip, short).candidates)
        val far = states.toMutableList().apply { this[9] = VideoDarkHolds.State.UNKNOWN }; assertEquals(analysis().candidates, VideoDarkHolds.analyze(clip, far).candidates)
    }
    @Test fun selectingOnlyOneHoldPreservesExactComplementAllOtherClipsAndFullSettings() {
        val styled = clip.copy(speed = 1.5f, volume = .3f, title = "Keep title", fadeEdges = true, audioFade = true, captionSourceUri = "content://synthetic/captions")
        val before = listOf(VideoSegment(0, 400), styled, VideoSegment(6500, 7000))
        val result = VideoDarkHolds.prepare(before, 1, analysis(styled), listOf(0))
        assertEquals(listOf(1000L to 1750L, 2750L to 6000L), result.retained.map { it.startMs to it.endMs })
        assertEquals(before.first(), result.clips.first()); assertEquals(before.last(), result.clips.last())
        result.retained.forEach { piece -> assertEquals(styled.copy(startMs = piece.startMs, endMs = piece.endMs), piece) }
        assertEquals(4000L, result.retained.sumOf { it.endMs - it.startMs }); assertTrue(result.afterUs < result.beforeUs)
        assertTrue(result.reviewText.contains("bright flashes")); assertTrue(result.reviewText.contains("original audio")); assertTrue(result.reviewText.contains("music synchronization")); assertFalse(result.reviewText.contains("content://"))
    }
    @Test fun fullCaptionReviewReportsWhollyRemovedOutsideAndCrossingCuesWithRetainedAssignments() {
        val captions = listOf(VideoCaption(0, 500, "outside left"), VideoCaption(1000, 1600, "first kept"), VideoCaption(1800, 2200, "removed words"), VideoCaption(2700, 4000, "crossing"), VideoCaption(5000, 5900, "last kept"), VideoCaption(6000, 6200, "outside right"))
        val source = clip.copy(captions = captions); val result = VideoDarkHolds.prepare(listOf(source), 0, analysis(source), listOf(1, 0))
        assertEquals(listOf(1000L to 1750L, 2750L to 3750L, 4750L to 6000L), result.retained.map { it.startMs to it.endMs })
        assertEquals(listOf("first kept"), result.retained[0].captions.map { it.text }); assertEquals(listOf("crossing"), result.retained[1].captions.map { it.text }); assertEquals(listOf("last kept"), result.retained[2].captions.map { it.text })
        assertEquals(captions[3], result.retained[1].captions.single())
        for (text in captions.map { it.text }) assertTrue(result.reviewText.contains(text))
        assertTrue(result.reviewText.contains("output none\nremoved words")); assertTrue(result.reviewText.contains("output none\noutside right")); assertEquals(captions, source.captions)
    }
    @Test fun twoHundredSeededSelectionsMatchIndependentSourceMembershipAtAllSpeeds() {
        val random = Random(1662)
        repeat(200) {
            val speed = listOf(.5f, 1f, 1.5f, 2f)[random.nextInt(4)]; val source = clip.copy(speed = speed); val a = analysis(source)
            val selected = a.candidates.indices.filter { random.nextBoolean() }.ifEmpty { listOf(0) }; val result = VideoDarkHolds.prepare(listOf(source), 0, a, selected)
            for (time in source.startMs until source.endMs) {
                val shouldKeep = selected.none { time >= a.candidates[it].fromMs && time < a.candidates[it].toMs }
                assertEquals(shouldKeep, result.retained.any { time >= it.startMs && time < it.endMs })
            }
            assertTrue(result.retained.all { it.speed == speed && it.durationUs >= 160000 }); assertEquals(result.retained.sumOf { it.durationUs }, result.afterUs)
        }
    }
    @Test fun staleForgedDuplicateAndEmptySelectionsFailWithoutChangingOriginals() {
        val a = analysis()
        fails("at least one") { VideoDarkHolds.prepare(listOf(clip), 0, a, emptyList()) }
        for (bad in listOf(listOf(0, 0), listOf(-1), listOf(2))) fails("distinct") { VideoDarkHolds.prepare(listOf(clip), 0, a, bad) }
        fails("changed") { VideoDarkHolds.prepare(listOf(clip.copy(volume = .5f)), 0, a, listOf(0)) }
        val forged = a.copy(candidates = a.candidates.mapIndexed { index, c -> if (index == 0) c.copy(fromMs = c.fromMs - 1) else c })
        fails("analysis changed") { VideoDarkHolds.prepare(listOf(clip), 0, forged, listOf(0)) }
        fails("clip limit") { VideoDarkHolds.prepare(List(1000) { clip }, 0, a, listOf(0)) }
        assertEquals(5000L, clip.endMs - clip.startMs)
    }
    @Test fun duplicatedCaptionAndUtf8TitleBudgetsRefuseBeforeApproval() {
        val captions = listOf(VideoCaption(1000, 3000, "long")) + List(199) { index -> VideoCaption(3000 + index * 10L, 3005 + index * 10L, "x") }
        val source = clip.copy(captions = captions); fails("caption") { VideoDarkHolds.prepare(List(5) { source }, 0, analysis(source), listOf(0)) }
        val titled = clip.copy(title = "界".repeat(120)); fails("title") { VideoDarkHolds.prepare(List(355) { titled }, 0, analysis(titled), listOf(0)) }
        assertEquals(200, source.captions.size); assertEquals(360, titled.title.toByteArray(Charsets.UTF_8).size)
    }
    @Test fun capturedAnalysisAndPreparedListsAreImmutableAndNullFramePackDisposalIsIdempotent() {
        val input = states.toMutableList(); val captions = mutableListOf(VideoCaption(1000, 5000, "retained")); val source = clip.copy(captions = captions)
        val a = VideoDarkHolds.analyze(source, input); val result = VideoDarkHolds.prepare(listOf(source), 0, a, listOf(0))
        input.clear(); captions.clear(); assertEquals(10, a.states.size); assertEquals(1, a.clip.captions.size); assertTrue(result.retained.all { it.captions.size == 1 })
        assertThrows(UnsupportedOperationException::class.java) { (a.states as MutableList).clear() }; assertThrows(UnsupportedOperationException::class.java) { (result.clips as MutableList).clear() }; assertThrows(UnsupportedOperationException::class.java) { (result.retained[0].captions as MutableList).clear() }
        val pack = VideoDarkHoldPack(a, List(10) { null }); assertThrows(UnsupportedOperationException::class.java) { (pack.frames as MutableList).clear() }; pack.dispose(); pack.dispose()
    }
    @Test fun actualBatchOneUseUndoRedoAndCodecKeepSoundtrackWhilePictureRipples() {
        val music = VideoSoundtrack("content://synthetic/music", 10000, volume = .4f, loop = false, startMs = 200, endMs = 8000)
        val source = clip.copy(captions = listOf(VideoCaption(1000, 5900, "long caption")))
        val result = VideoDarkHolds.prepare(listOf(source), 0, analysis(source), listOf(0, 1), music)
        val project = VideoEditProject(7000); project.restore(listOf(source), soundtrack = music); val before = project.segments
        val request = VideoBatchReview(before, music); val stage = Any(); assertTrue(request.open(stage)); assertTrue(request.propose(stage, result.clips)); assertTrue(request.apply(stage, project) {}); assertFalse(request.apply(stage, project) {})
        assertEquals(result.clips, project.segments); assertEquals(music, project.soundtrack); assertTrue(project.undo()); assertEquals(before, project.segments); assertEquals(music, project.soundtrack); assertTrue(project.redo()); assertEquals(result.clips, project.segments)
        val draft = VideoDraft(7000, result.clips, 0, 1000, 1000, result.clips.first().endMs, soundtrack = music)
        assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft), 7000))
    }
}
