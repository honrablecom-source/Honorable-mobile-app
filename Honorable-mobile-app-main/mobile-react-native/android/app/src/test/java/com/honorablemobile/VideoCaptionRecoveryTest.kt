package com.honorablemobile

import app.honorable.editor.PhotoPresetNamespace
import org.junit.Assert.*
import org.junit.Test

class VideoCaptionRecoveryTest {
    private val other = VideoSource("other", "content://synthetic/other", 10000)
    private fun cue(text: String) = listOf(VideoCaption(100, 800, text))
    private fun draft(clips: List<VideoSegment>, sources: List<VideoSource> = emptyList(), music: VideoSoundtrack? = null) =
        VideoDraft(10000, clips, 0, clips[0].startMs, clips[0].startMs, clips[0].endMs, sources, music)
    @Test fun reordersAndSpeedDifferencesRecoverOnlyCaptionsWhileAllCurrentMetadataStays() {
        val music = VideoSoundtrack("content://synthetic/music", 10000, .2f, false)
        val currentClips = listOf(VideoSegment(0, 1000, sourceId = "other", speed = 2f, volume = .3f, title = "Current other", captions = cue("Old other")),
            VideoSegment(0, 1000, speed = .5f, title = "Current primary", fadeEdges = true, audioFade = true, captions = cue("Old primary")))
        val current = draft(currentClips, listOf(other), music).copy(selectedClip = 1, previewPositionMs = 500, trimStartMs = 200, trimEndMs = 900)
        val savedClips = listOf(VideoSegment(0, 1000, title = "Saved style ignored", captions = cue("Saved primary")),
            VideoSegment(0, 1000, sourceId = "other", captions = cue("Saved other"), captionSourceUri = "content://synthetic/new-subtitles"))
        val saved = draft(savedClips, listOf(other), VideoSoundtrack("content://synthetic/ignored-music", 20000))
        val result = VideoCaptionRecovery.prepare(current, saved)
        val expected = listOf(currentClips[0].copy(captions = savedClips[1].captions, captionSourceUri = savedClips[1].captionSourceUri), currentClips[1].copy(captions = savedClips[0].captions))
        assertEquals(current.copy(segments = expected), result.draft)
        assertEquals(listOf(0, 1), result.changes.map { it.currentIndex }); assertEquals(VideoPlaybackTime.durationUs(currentClips), VideoPlaybackTime.durationUs(result.draft.segments))
    }
    @Test fun unmatchedWindowsStayAndSameCaptionMatchesAreReportedWithoutChangingStyles() {
        val current = draft(listOf(VideoSegment(0, 1000, captions = cue("Old")), VideoSegment(1000, 2000, title = "Unmatched"), VideoSegment(2000, 3000, captions = cue("Same"))))
        val saved = draft(listOf(VideoSegment(0, 1000, captions = cue("New")), VideoSegment(2000, 3000, title = "Ignored", captions = cue("Same"))))
        val result = VideoCaptionRecovery.prepare(current, saved)
        assertEquals(listOf(1), result.unmatched); assertEquals(1, result.sameCaptions)
        assertEquals(current.segments[1], result.draft.segments[1]); assertEquals(current.segments[2], result.draft.segments[2])
        assertTrue(result.reviewText.contains("Unmatched current positions kept: 2"))
    }
    @Test fun matchingAddedSourceIdsDoNotBypassUriOrDurationIdentity() {
        val current = draft(listOf(VideoSegment(0, 1000, sourceId = "other")), listOf(other))
        for (source in listOf(other.copy(uri = "content://synthetic/different-original"), other.copy(durationMs = 9999))) {
            val saved = draft(listOf(VideoSegment(0, 1000, sourceId = "other", captions = cue("Wrong source"))), listOf(source))
            assertThrows(IllegalArgumentException::class.java) { VideoCaptionRecovery.prepare(current, saved) }
        }
        val primary = draft(listOf(VideoSegment(0, 1000)))
        assertThrows(IllegalArgumentException::class.java) { VideoCaptionRecovery.prepare(primary, primary.copy(sourceDurationMs = 10001, segments = listOf(VideoSegment(0, 1000, captions = cue("Changed"))))) }
    }
    @Test fun identicalSavedDuplicatesAreEquivalentButConflictingTextOrProvenanceRefuses() {
        val current = draft(listOf(VideoSegment(0, 1000)))
        val a = VideoSegment(0, 1000, captions = cue("Recovered"), captionSourceUri = "content://synthetic/a")
        val result = VideoCaptionRecovery.prepare(current, draft(listOf(a, a.copy(title = "Different look", speed = 2f))))
        assertEquals(listOf(0, 1), result.groups.single().savedIndices); assertEquals(cue("Recovered"), result.draft.segments.single().captions)
        for (b in listOf(a.copy(captions = cue("Conflict")), a.copy(captionSourceUri = "content://synthetic/b"))) {
            assertThrows(IllegalArgumentException::class.java) { VideoCaptionRecovery.prepare(current, draft(listOf(a, b))) }
        }
    }
    @Test fun everyCurrentDuplicateReceivesTheReviewedPayloadWithoutDroppingAnyCurrentShot() {
        val current = draft(listOf(VideoSegment(0, 1000, title = "First", captions = cue("A")), VideoSegment(0, 1000, title = "Second", captions = cue("B"))))
        val result = VideoCaptionRecovery.prepare(current, draft(listOf(VideoSegment(0, 1000, captions = cue("Saved")))))
        assertEquals(2, result.changes.size); assertEquals(1, result.groups.size)
        assertEquals(listOf("First", "Second"), result.draft.segments.map { it.title }); assertTrue(result.draft.segments.all { it.captions == cue("Saved") })
    }
    @Test fun emptySavedCuesClearOnlyTheReviewedCaptionsAndReference() {
        val current = draft(listOf(VideoSegment(0, 1000, title = "Keep", captions = cue("Clear this"), captionSourceUri = "content://synthetic/private-old")))
        val result = VideoCaptionRecovery.prepare(current, draft(listOf(VideoSegment(0, 1000))))
        assertEquals(current.segments[0].copy(captions = emptyList(), captionSourceUri = null), result.draft.segments.single())
        assertTrue(result.reviewText.contains("Clear this")); assertTrue(result.reviewText.contains("Saved captions to recover (0 cues):\n(none)"))
        assertFalse(result.reviewText.contains("content://synthetic/private-old"))
    }
    @Test fun provenanceOnlyChangesAreExplicitWithoutPrintingUriMetadata() {
        val a = VideoSegment(0, 1000, captions = cue("Same words"), captionSourceUri = "content://synthetic/private-before")
        val b = a.copy(captionSourceUri = "content://synthetic/private-after")
        val result = VideoCaptionRecovery.prepare(draft(listOf(a)), draft(listOf(b)))
        assertEquals(b.captionSourceUri, result.draft.segments.single().captionSourceUri)
        assertTrue(result.reviewText.contains("Subtitle document reference: changes")); assertFalse(result.reviewText.contains("content://"))
    }
    @Test fun noOpAndAllUnmatchedVersionsRefuseWithoutCopyingOtherFields() {
        val current = draft(listOf(VideoSegment(0, 1000, captions = cue("Same"))))
        assertThrows(IllegalArgumentException::class.java) { VideoCaptionRecovery.prepare(current, current.copy(segments = listOf(current.segments[0].copy(title = "Ignored style")))) }
        assertThrows(IllegalArgumentException::class.java) { VideoCaptionRecovery.prepare(current, draft(listOf(VideoSegment(0, 900, captions = cue("Different window"))))) }
        assertEquals("Same", current.segments[0].captions[0].text)
    }
    @Test fun recoveryLimitsRefuseDuplicatedCuesInsteadOfTruncatingTheMerge() {
        val current = draft(List(6) { VideoSegment(0, 2000) })
        val saved = draft(listOf(VideoSegment(0, 2000, captions = List(200) { index -> VideoCaption(index * 10L, index * 10L + 10, "Cue") })))
        assertThrows(IllegalArgumentException::class.java) { VideoCaptionRecovery.prepare(current, saved) }
        assertTrue(current.segments.all { it.captions.isEmpty() })
    }
    @Test fun frozenProposalSurvivesInputMutationAndKeepsUnusedCurrentSources() {
        val beforeCues = mutableListOf(VideoCaption(100, 800, "Before")); val savedCues = mutableListOf(VideoCaption(100, 800, "After"))
        val currentClips = mutableListOf(VideoSegment(0, 1000, captions = beforeCues)); val savedClips = mutableListOf(VideoSegment(0, 1000, captions = savedCues))
        val current = draft(currentClips, listOf(other)); val result = VideoCaptionRecovery.prepare(current, draft(savedClips))
        beforeCues.clear(); savedCues.clear(); currentClips.clear(); savedClips.clear()
        assertEquals("Before", result.changes.single().before.captions.single().text); assertEquals("After", result.draft.segments.single().captions.single().text)
        assertEquals(listOf(other), result.draft.sources)
        assertThrows(UnsupportedOperationException::class.java) { (result.draft.segments as MutableList<VideoSegment>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.groups.single().savedIndices as MutableList<Int>).clear() }
    }
    @Test fun completeThousandPositionReviewGroupsEquivalentSavedOriginsOnlyOnce() {
        val current = draft(List(1000) { VideoSegment(0, 1000) })
        val saved = draft(List(1000) { VideoSegment(0, 1000, captions = cue("New")) })
        val result = VideoCaptionRecovery.prepare(current, saved)
        assertEquals(1000, result.changes.size); assertEquals(1000, result.groups.single().savedIndices.size)
        assertTrue(result.reviewText.contains("Current position 1000")); assertTrue(result.reviewText.toByteArray().size <= 1_000_000)
        assertEquals(1, Regex("Saved source-window groups").findAll(result.reviewText).count())
    }
    @Test fun actualProjectUndoRedoAndCodecRetainCurrentMusicAndWorkingSelectionMetadata() {
        val music = VideoSoundtrack("content://synthetic/music", 10000, .3f, false, 1000, 9000)
        val current = draft(listOf(VideoSegment(0, 1000, captions = cue("Old"))), music = music).copy(previewPositionMs = 500, trimStartMs = 200, trimEndMs = 900)
        val result = VideoCaptionRecovery.prepare(current, draft(listOf(VideoSegment(0, 1000, captions = cue("Recovered")))))
        val project = VideoEditProject(10000); project.restore(current.segments, soundtrack = music)
        project.applyDraft(result.draft.segments, soundtrack = result.draft.soundtrack)
        assertEquals(music, project.soundtrack); assertEquals(500L, result.draft.previewPositionMs); assertEquals(200L, result.draft.trimStartMs); assertEquals(900L, result.draft.trimEndMs)
        assertEquals(result.draft, VideoDraftCodec.decode(VideoDraftCodec.encode(result.draft), 10000))
        assertTrue(project.undo()); assertEquals(current.segments, project.segments); assertFalse(project.canUndo); assertTrue(project.redo()); assertEquals(result.draft.segments, project.segments)
    }
    @Test fun actualVersionStoreRejectsDeletedOrForeignPrimaryEntriesBeforeRecomputation() {
        class Record : VideoVersionRecord { var bytes: ByteArray? = null; override fun read() = bytes?.copyOf(); override fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() } }
        val scope = VideoVersionScope(PhotoPresetNamespace.GUEST, "content://synthetic/primary"); val record = Record()
        val store = VideoProjectVersionStore(scope, 10000, record)
        val saved = VideoProjectVersion.create("Saved captions", draft(listOf(VideoSegment(0, 1000, captions = cue("Recovered")))))
        store.save(saved) {}; val current = draft(listOf(VideoSegment(0, 1000)))
        assertEquals("Recovered", VideoCaptionRecovery.prepare(current, store.requireEntry(saved).draft()).draft.segments[0].captions[0].text)
        store.delete(saved) {}; assertThrows(IllegalArgumentException::class.java) { store.requireEntry(saved) }
        val otherScope = VideoVersionScope(PhotoPresetNamespace.GUEST, "content://synthetic/foreign-primary")
        record.bytes = VideoProjectVersionCodec.encode(otherScope, VideoProjectVersions(10000, listOf(saved)))
        assertThrows(IllegalArgumentException::class.java) { store.requireEntry(saved) }
        assertTrue(current.segments[0].captions.isEmpty())
    }
}
