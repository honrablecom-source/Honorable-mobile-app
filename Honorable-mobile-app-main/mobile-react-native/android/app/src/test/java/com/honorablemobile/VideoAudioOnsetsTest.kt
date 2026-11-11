package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VideoAudioOnsetsTest {
    private fun windows(clip: VideoSegment, pulses: Map<Int, Double> = mapOf(20 to .8, 50 to .5, 80 to .2, 110 to .7), base: Double = .005): List<VideoQuietWindow> =
        (0 until ((clip.endMs - clip.startMs + 19) / 20).toInt()).map { index -> val start = clip.startMs + index * 20; VideoQuietWindow(start, minOf(clip.endMs, start + 20), pulses[index] ?: base) }
    private val clip = VideoSegment(0, 4000)
    private fun analysis(value: VideoSegment = clip) = VideoAudioOnsets.detect(value, windows(value))
    private fun fails(part: String, action: () -> Unit) {
        val error = assertThrows(IllegalArgumentException::class.java) { action() }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(part))
    }
    @Test fun isolatedAnalyticPulsesYieldExactSourceWindowStartsAndRecordedBaselines() {
        val result = analysis()
        assertEquals(listOf(400L, 1000L, 1600L, 2200L), result.events.map { it.sourceMs })
        assertEquals(listOf(.8, .5, .2, .7), result.events.map { it.peakRms }); assertTrue(result.events.all { it.baselineRms == .005 })
        assertEquals(4000L, result.knownMs); assertEquals(4000L, result.totalMs); assertEquals(0, result.suppressed)
        val offset = VideoSegment(1234, 5234); assertEquals(listOf(1634L, 2234L, 2834L, 3434L), analysis(offset).events.map { it.sourceMs })
    }
    @Test fun strongestRiseWinsNearbyAlternativesAndTiesChooseEarlierWithExactGapInclusive() {
        val nearby = VideoAudioOnsets.detect(clip, windows(clip, mapOf(20 to .2, 24 to .5, 28 to .5)))
        assertEquals(listOf(480L), nearby.events.map { it.sourceMs }); assertEquals(2, nearby.suppressed)
        val spaced = VideoAudioOnsets.detect(clip, windows(clip, mapOf(20 to .7, 24 to .5, 28 to .6)))
        assertEquals(listOf(400L, 560L), spaced.events.map { it.sourceMs }); assertEquals(1, spaced.suppressed)
        val plateau = VideoAudioOnsets.detect(clip, windows(clip, mapOf(20 to .3, 21 to .3, 22 to .3)))
        assertEquals(listOf(400L), plateau.events.map { it.sourceMs })
    }
    @Test fun absoluteRiseRelativeThresholdAndSilenceFloorRefuseFlatOrWeakSignals() {
        for (base in listOf(0.0, .005, .3, 1.0)) fails("No clear") { VideoAudioOnsets.detect(clip, windows(clip, emptyMap(), base)) }
        fails("No clear") { VideoAudioOnsets.detect(clip, windows(clip, mapOf(20 to .019999))) }
        fails("No clear") { VideoAudioOnsets.detect(clip, windows(clip, mapOf(19 to .015, 20 to .02))) }
        fails("No clear") { VideoAudioOnsets.detect(clip, windows(clip, mapOf(19 to .001, 20 to .039), .02)) }
        assertEquals(400L, VideoAudioOnsets.detect(clip, windows(clip, mapOf(19 to .001, 20 to .04), .02)).events.single().sourceMs)
        assertEquals(.001, VideoAudioOnsets.detect(clip, windows(clip, mapOf(20 to .02), 0.0)).events.single().baselineRms, 0.0)
    }
    @Test fun speedAwareMarginsAndSeparationKeepAtLeast160PlaybackMilliseconds() {
        val pulses = mapOf(10 to .4, 16 to .5, 20 to .8, 24 to .3, 28 to .7, 184 to .6, 190 to .9)
        for (speed in listOf(.5f, 1f, 1.5f, 2f)) {
            val source = clip.copy(speed = speed); val result = VideoAudioOnsets.detect(source, windows(source, pulses)); val times = listOf(source.startMs) + result.events.map { it.sourceMs } + source.endMs
            assertTrue(times.zipWithNext().all { (a, b) -> (b - a) * 1000 / speed >= 160000 })
            assertTrue(result.events.all { it.sourceMs >= source.startMs + 200 })
        }
    }
    @Test fun unknownHistoryAndNeighborsNeverSuggestAcrossGapsAndCoverageIsDurationWeighted() {
        val input = windows(clip).mapIndexed { index, window -> if (index in setOf(12, 51)) window.copy(rms = null) else window }
        assertEquals(listOf(1600L, 2200L), VideoAudioOnsets.detect(clip, input).events.map { it.sourceMs })
        val short = VideoSegment(0, 1001); val partial = windows(short, mapOf(20 to .4)).mapIndexed { index, window -> if (index in setOf(1, 2, 50)) window.copy(rms = null) else window }
        assertEquals(960L, VideoAudioOnsets.detect(short, partial).knownMs)
        fails("95%") { VideoAudioOnsets.detect(short, partial.mapIndexed { index, window -> if (index == 3) window.copy(rms = null) else window }) }
    }
    @Test fun gridsInvalidNumbersBoundsAndExcessiveSuggestionsRefuseInsteadOfGuessing() {
        fails("Incomplete") { VideoAudioOnsets.detect(clip, windows(clip).dropLast(1)) }
        fails("changed") { VideoAudioOnsets.detect(clip, windows(clip).reversed()) }
        fails("changed") { VideoAudioOnsets.detect(clip, windows(clip).mapIndexed { index, value -> if (index == 4) value.copy(startMs = 81) else value }) }
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.001, 1.001)) assertThrows(IllegalArgumentException::class.java) { VideoAudioOnsets.detect(clip, windows(clip).mapIndexed { index, value -> if (index == 3) value.copy(rms = bad) else value }) }
        fails("two source") { val long = VideoSegment(0, 120001); VideoAudioOnsets.detect(long, emptyList()) }
        val long = VideoSegment(0, 120000); val pulses = (20 until 5990 step 10).associateWith { .5 }
        fails("200") { VideoAudioOnsets.detect(long, windows(long, pulses)) }
    }
    @Test fun cancellationAtValidationDetectionAndFinalReturnNeverReturnsPartialSuggestions() {
        var total = 0; VideoAudioOnsets.detect(clip, windows(clip)) { total++ }
        for (at in listOf(1, 150, 250, total)) { var calls = 0; assertThrows(IllegalStateException::class.java) { VideoAudioOnsets.detect(clip, windows(clip)) { if (++calls == at) error("Synthetic stale owner") } }; assertEquals(at, calls) }
    }
    @Test fun chosenSubsetSplitsOnlyChosenClipAndKeepsAllSourceCoverageAppearanceAndIntersectingCues() {
        val cues = listOf(VideoCaption(50, 300, "outside left"), VideoCaption(500, 2500, "long cue"), VideoCaption(3500, 3900, "last"))
        val styled = VideoSegment(400, 3600, speed = 1.5f, volume = .3f, sourceId = "camera", title = "Keep", fadeEdges = true, audioFade = true, captions = cues, captionSourceUri = "content://synthetic/captions")
        val before = listOf(clip.copy(endMs = 200), styled, clip.copy(startMs = 3800)); val a = analysis(styled)
        val chosen = listOf(a.events[2].sourceMs, a.events[0].sourceMs)
        val result = VideoAudioOnsets.prepare(before, 1, a, chosen)
        assertEquals(before.first(), result.clips.first()); assertEquals(before.last(), result.clips.last())
        val edges = listOf(styled.startMs) + chosen.sorted() + styled.endMs
        assertEquals(edges.zipWithNext(), result.pieces.map { it.startMs to it.endMs })
        result.pieces.forEach { piece -> assertEquals(styled.copy(startMs = piece.startMs, endMs = piece.endMs, captions = cues.filter { it.startMs < piece.endMs && it.endMs > piece.startMs }), piece) }
        assertEquals(styled.endMs - styled.startMs, result.pieces.sumOf { it.endMs - it.startMs })
        assertTrue(result.beforeUs - result.afterUs in 0..2); assertTrue(result.reviewText.contains("outside left")); assertTrue(result.reviewText.contains("output none")); assertFalse(result.reviewText.contains("content://"))
        assertTrue(result.reviewText.contains("long cue")); assertTrue(result.reviewText.contains("One Undo")); assertTrue(result.reviewText.contains("fades repeat"))
        assertEquals(cues, styled.captions)
    }
    @Test fun seededRandomSubsetsPreserveEveryMillisecondAndNativeRoundingForAllSpeeds() {
        val random = Random(164)
        repeat(200) {
            val speed = listOf(.5f, 1f, 1.5f, 2f)[random.nextInt(4)]; val source = clip.copy(speed = speed)
            val a = analysis(source); val picked = a.events.filter { random.nextBoolean() }.ifEmpty { listOf(a.events.first()) }.map { it.sourceMs }
            val result = VideoAudioOnsets.prepare(listOf(source), 0, a, picked)
            assertEquals(0L, result.pieces.first().startMs); assertEquals(4000L, result.pieces.last().endMs)
            assertTrue(result.pieces.zipWithNext().all { (x, y) -> x.endMs == y.startMs }); assertEquals(4000L, result.pieces.sumOf { it.endMs - it.startMs })
            assertTrue(result.beforeUs - result.afterUs in 0..picked.size.toLong()); assertTrue(result.pieces.all { it.speed == speed && it.durationUs >= 160000 })
        }
    }
    @Test fun staleOrInventedSelectionsAndProjectCapacityRefuseWithoutMutation() {
        val a = analysis()
        fails("changed") { VideoAudioOnsets.prepare(listOf(clip.copy(volume = .5f)), 0, a, listOf(400)) }
        for (times in listOf(emptyList(), listOf(400L, 400L))) fails("distinct") { VideoAudioOnsets.prepare(listOf(clip), 0, a, times) }
        fails("belong") { VideoAudioOnsets.prepare(listOf(clip), 0, a, listOf(401)) }
        fails("clip limit") { VideoAudioOnsets.prepare(List(1000) { clip }, 0, a, listOf(400)) }
        val cues = List(200) { index -> VideoCaption(maxOf(0, index * 10L - 1), index * 10L + 4, "x") }
        val captioned = clip.copy(captions = cues); val crowded = List(5) { captioned }
        fails("caption") { VideoAudioOnsets.prepare(crowded, 0, analysis(captioned), listOf(400)) }
        assertEquals(1000, crowded.sumOf { it.captions.size })
    }
    @Test fun proposalsAreImmutableAndActualBatchApprovalUndoRedoAndDraftCodecRetainSoundtrack() {
        val mutable = mutableListOf(VideoCaption(0, 3000, "Synthetic caption")); val source = clip.copy(captions = mutable)
        val music = VideoSoundtrack("content://synthetic/music", 10000, volume = .3f, loop = false, startMs = 100, endMs = 8000)
        val a = analysis(source); val result = VideoAudioOnsets.prepare(listOf(source), 0, a, listOf(400, 1600), music)
        mutable.clear(); assertEquals(1, a.clip.captions.size); assertTrue(result.pieces.all { it.captions.size == 1 })
        assertThrows(UnsupportedOperationException::class.java) { (result.clips as MutableList).clear() }; assertThrows(UnsupportedOperationException::class.java) { (result.pieces[0].captions as MutableList).clear() }; assertThrows(UnsupportedOperationException::class.java) { (a.events as MutableList).clear() }
        val project = VideoEditProject(4000); project.restore(listOf(a.clip), soundtrack = music); val original = project.segments
        val request = VideoBatchReview(original, music); val stage = Any(); assertTrue(request.open(stage)); assertTrue(request.propose(stage, result.clips)); assertTrue(request.apply(stage, project) {})
        assertFalse(request.apply(stage, project) {}); assertEquals(result.clips, project.segments); assertTrue(project.undo()); assertEquals(original, project.segments); assertTrue(project.redo()); assertEquals(result.clips, project.segments)
        assertEquals(music, project.soundtrack)
        val draft = VideoDraft(4000, result.clips, 0, 0, 0, result.clips.first().endMs, soundtrack = music); assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft), 4000))
    }
    @Test fun multibyteTitleBudgetIsCheckedBeforeReviewAndRetainsOriginal() {
        val styled = clip.copy(title = "界".repeat(120)); val original = List(353) { styled }; val a = analysis(styled)
        fails("title") { VideoAudioOnsets.prepare(original, 0, a, a.events.map { it.sourceMs }) }
        assertEquals(127080, original.sumOf { it.title.toByteArray(Charsets.UTF_8).size })
    }
    @Test fun exactly200SuggestionsProduceACompleteReviewed201PieceProject() {
        val source = VideoSegment(0, 42000); val pulses = (20 until 2020 step 10).associateWith { .5 }
        val a = VideoAudioOnsets.detect(source, windows(source, pulses)); assertEquals(200, a.events.size)
        val result = VideoAudioOnsets.prepare(listOf(source), 0, a, a.events.map { it.sourceMs })
        assertEquals(201, result.clips.size); assertEquals(42000L, result.clips.sumOf { it.endMs - it.startMs }); assertEquals(result.beforeUs, result.afterUs)
        assertTrue(result.reviewText.contains("201:")); assertTrue(result.reviewText.toByteArray(Charsets.UTF_8).size < 1_000_000)
        val draft = VideoDraft(42000, result.clips, 0, 0, 0, result.clips.first().endMs)
        assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft), 42000))
    }
    @Test fun actualStereoPcmOppositePhasesStillDetectARealSyntheticEnergyPulse() {
        val energy = VideoQuietEnergy(0, 1000, 8000)
        val buffer = java.nio.ByteBuffer.allocate(8000 * 4).order(java.nio.ByteOrder.nativeOrder())
        repeat(8000) { index -> val value = if (index in 3200 until 3360) 16384.toShort() else 164.toShort(); buffer.putShort(value); buffer.putShort((-value.toInt()).toShort()) }
        buffer.flip(); energy.pcm(buffer, 0, buffer.remaining(), 0, 2, false)
        val result = VideoAudioOnsets.detect(VideoSegment(0, 1000), energy.finish()); assertEquals(listOf(400L), result.events.map { it.sourceMs }); assertEquals(.5, result.events.single().peakRms, 1e-12)
    }
}
