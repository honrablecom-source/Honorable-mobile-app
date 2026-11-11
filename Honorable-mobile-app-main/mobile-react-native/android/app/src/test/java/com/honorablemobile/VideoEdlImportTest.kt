package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

class VideoEdlImportTest {
    private val lengths = mapOf("primary" to 10000L, "other" to 10000L, "third" to 10000L)
    private val links = "N001,primary\nN002,other"
    private val header = "TITLE: Synthetic cuts\nFCM: NON-DROP FRAME\n* NOBLE FRAME RATE: 30\n"
    private val first = "001 N001 V C 00:00:00:01 00:00:00:04 01:00:00:00 01:00:00:03"
    private val second = "002 N002 V C 00:00:00:10 00:00:00:13 01:00:00:03 01:00:00:06"
    private fun text() = header + first + "\n" + second + "\n"
    @Test fun knownFractionalFrameWindowsUseMillisecondEnvelopeAndNormalizeRecordOrigin() {
        val result = VideoEdlImport.prepare(text(), links, 30, lengths)
        assertEquals(listOf(33L to 134L, 333L to 434L), result.clips.map { it.startMs to it.endMs })
        assertEquals(listOf("primary", "other"), result.clips.map { it.sourceId })
        assertEquals(108000L, result.recordOriginFrame); assertEquals(6L, result.recordFrames)
        assertEquals(200000L, result.frameDurationUs); assertEquals(202000L, result.nativeDurationUs); assertEquals(2, result.widenedEvents)
    }
    @Test fun singleFrameCutsAreKeptRatherThanDroppedByAMinimumShotPreset() {
        val edl = "FCM: NON-DROP FRAME\n001 N001 V C 00:00:00:01 00:00:00:02 00:00:00:00 00:00:00:01"
        val result = VideoEdlImport.prepare(edl, "N001,primary", 25, lengths)
        assertEquals(VideoSegment(40, 80), result.clips.single()); assertEquals(40000L, result.nativeDurationUs); assertEquals(0, result.widenedEvents)
    }
    @Test fun explicitLinksAreAuthorityAndCommentsCannotSupplyUrisOrRelinkSources() {
        val input = header + "* NOBLE SOURCE N001 = third\n* FROM CLIP NAME: content://synthetic/private\n" + first
        val result = VideoEdlImport.prepare(input, "N001,other", 30, lengths)
        assertEquals("other", result.clips.single().sourceId)
        assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(input, "N002,other", 30, lengths) }
    }
    @Test fun clipsAreNeutralAtOneTimesWithoutInventingOmittedEffectsOrCaptions() {
        val result = VideoEdlImport.prepare(text(), links, 30, lengths)
        result.clips.forEach { assertEquals(VideoSegment(it.startMs, it.endMs, sourceId = it.sourceId), it) }
        assertTrue(result.clips.all { it.speed == 1f && it.volume == 1f && it.captions.isEmpty() && it.captionSourceUri == null && it.motion == VideoMotion() })
    }
    @Test fun realNobleExportImportReexportKeepsEveryListedFrameAtAllSupportedRates() {
        val random = Random(154)
        for (fps in listOf(24, 25, 30)) repeat(100) {
            val originals = List(random.nextInt(1, 15)) { index ->
                val start = random.nextInt(5000).toLong()
                VideoEdl.Cut(listOf("primary", "other", "third")[index % 3], start, start + random.nextInt(200, 3000))
            }
            val exported = VideoEdl.prepare(originals, fps)
            val mapping = exported.reels.entries.joinToString("\n") { (source, reel) -> "$reel,$source" }
            val imported = VideoEdlImport.prepare(exported.text, mapping, fps, lengths)
            val reexported = VideoEdl.prepare(imported.clips.map { VideoEdl.Cut(it.sourceId, it.startMs, it.endMs, it.speed) }, fps)
            assertEquals(exported.text, reexported.text)
            assertEquals(originals.size, imported.clips.size)
            imported.clips.forEachIndexed { index, clip ->
                assertEquals(originals[index].sourceId, clip.sourceId)
                assertTrue(clip.startMs >= originals[index].startMs && clip.endMs <= originals[index].endMs)
                val event = imported.events[index]
                assertTrue(clip.startMs * fps <= event.sourceInFrame * 1000)
                assertTrue((clip.startMs + 1) * fps > event.sourceInFrame * 1000)
                assertTrue(clip.endMs * fps >= event.sourceOutFrame * 1000)
                assertTrue((clip.endMs - 1) * fps < event.sourceOutFrame * 1000)
            }
            assertTrue(imported.nativeDurationUs >= imported.frameDurationUs)
            assertTrue(imported.nativeDurationUs - imported.frameDurationUs < imported.clips.size * 2000L)
        }
    }
    @Test fun unsupportedAudioTransitionsSpeedRowsAndUnknownNonCommentDataRefuseEverything() {
        for (bad in listOf(first.replace(" V C ", " A C "), first.replace(" V C ", " V D 025 "), first + "\nM2 N001 050.0 00:00:00:01", first + "\nUNSUPPORTED: do something")) {
            assertThrows(bad, IllegalArgumentException::class.java) { VideoEdlImport.prepare(header + bad, links, 30, lengths) }
        }
    }
    @Test fun recordGapsOverlapsSpanMismatchAndEventOrderRefuseWithoutFixingTheList() {
        for (bad in listOf(second.replace("01:00:00:03 01:00:00:06", "01:00:00:04 01:00:00:07"),
            second.replace("01:00:00:03 01:00:00:06", "01:00:00:02 01:00:00:05"),
            second.replace("01:00:00:06", "01:00:00:07"), second.replace("002", "001"))) {
            assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(header + first + "\n" + bad, links, 30, lengths) }
        }
        assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(header + second, links, 30, lengths) }
    }
    @Test fun frameRateTimecodeHeaderMappingAndOriginalBoundsAreStrict() {
        for (bad in listOf(text().replace("NON-DROP FRAME", "DROP FRAME"), text().replace("RATE: 30", "RATE: 25"),
            text().replace("00:00:00:01", "00:00:00:30"), text().replace("00:00:00:01", "00:60:00:01"), text().replace("00:00:00:01", "24:00:00:01"),
            first, header + header + first)) assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(bad, links, 30, lengths) }
        for (mapping in listOf("", "N001,missing", "N001,primary\nN001,other", "../N001,primary", "N001,primary,extra"))
            assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(text(), mapping, 30, lengths) }
        assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(text(), links, 30, lengths + ("other" to 433L)) }
        assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(text(), links, 29, lengths) }
    }
    @Test fun maximumEventCountWorksAndAnyAdditionalEventRefuses() {
        fun tc(frame: Int) = String.format(Locale.ROOT, "%02d:%02d:%02d:%02d", frame / (25 * 3600), frame / (25 * 60) % 60, frame / 25 % 60, frame % 25)
        val events = (1..999).joinToString("\n") { number -> String.format(Locale.ROOT, "%03d N001 V C 00:00:00:00 00:00:00:01 %s %s", number, tc(number - 1), tc(number)) }
        val edl = "FCM: NON-DROP FRAME\n$events"
        val result = VideoEdlImport.prepare(edl, "N001,primary", 25, lengths)
        assertEquals(999, result.clips.size); assertEquals(999L * 40000, result.nativeDurationUs)
        assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(edl + "\n001 N001 V C 00:00:00:00 00:00:00:01 00:00:39:24 00:00:40:00", "N001,primary", 25, lengths) }
    }
    @Test fun byteLimitsAndControlCharactersRefuseBeforeParsing() {
        for (bad in listOf("*" + "😀".repeat(32000), text() + '\u0000', ""))
            assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(bad, links, 30, lengths) }
        assertThrows(IllegalArgumentException::class.java) { VideoEdlImport.prepare(text(), " ".repeat(8193), 30, lengths) }
    }
    @Test fun actualProjectImportIsOneUndoWithExistingSoundtrackAndRecoverableDraft() {
        val original = listOf(VideoSegment(0, 2000, title = "Old edit", volume = .2f))
        val music = VideoSoundtrack("content://synthetic/music", 10000, .3f, false)
        val project = VideoEditProject(10000); project.restore(original, mapOf("other" to 10000L), music)
        val result = VideoEdlImport.prepare(text(), links, 30, lengths, music)
        val request = VideoBatchReview(original, music); val stage = Any(); request.open(stage); request.propose(stage, result.clips)
        assertTrue(request.apply(stage, project) {}); assertFalse(request.apply(stage, project) {}); assertEquals(music, project.soundtrack)
        val first = result.clips.first(); val draft = VideoDraft(10000, project.segments, 0, first.startMs, first.startMs, first.endMs,
            listOf(VideoSource("other", "content://synthetic/other", 10000)), music)
        assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft), 10000))
        assertTrue(project.undo()); assertEquals(original, project.segments); assertFalse(project.canUndo); assertTrue(project.redo()); assertEquals(result.clips, project.segments)
    }
    @Test fun closedForeignPausedAndStaleImportApprovalsCannotApplyOrReviveAfterUndo() {
        val original = listOf(VideoSegment(0, 2000))
        for (mode in 0..3) {
            val project = VideoEditProject(10000); project.restore(original, mapOf("other" to 10000L))
            val request = VideoBatchReview(original, null); val stage = Any(); request.open(stage); request.propose(stage, VideoEdlImport.prepare(text(), links, 30, lengths).clips)
            when (mode) {
                0 -> { request.dismiss(stage); assertFalse(request.apply(stage, project) {}) }
                1 -> assertFalse(request.apply(Any(), project) {})
                2 -> assertThrows(IllegalStateException::class.java) { request.apply(stage, project) { error("Paused") } }
                3 -> { project.setTitle(0, "Changed"); assertThrows(IllegalArgumentException::class.java) { request.apply(stage, project) {} }; project.undo(); assertFalse(request.apply(stage, project) {}) }
            }
            assertEquals(2000L, project.segments.first().endMs)
        }
    }
}
