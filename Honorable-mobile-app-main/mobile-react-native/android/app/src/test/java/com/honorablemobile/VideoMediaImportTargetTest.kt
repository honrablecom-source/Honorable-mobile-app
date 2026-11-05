package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoMediaImportTargetTest {
    private val uri="content://synthetic/primary"
    private fun ticket(project:VideoEditProject,sources:Map<String,VideoSource> = emptyMap())=
        VideoMediaImportTarget(VideoMediaImportKind.VIDEO,project,uri,8000,"ACCOUNT",0,sources)
    @Test fun pendingRequestIsConsumedOnceAndCancellationPreventsAnyFurtherValidation() {
        val project=VideoEditProject(8000);val target=ticket(project)
        assertTrue(target.isPending);assertFalse(target.isChecking)
        assertTrue(target.beginValidation());assertFalse(target.beginValidation());target.requireChecking()
        assertTrue(target.matches(project,uri,8000,"ACCOUNT",0,emptyMap()))
        target.cancel();target.cancel();assertFalse(target.isChecking);assertFalse(target.isPending);assertFalse(target.beginValidation())
        assertFalse(target.matches(project,uri,8000,"ACCOUNT",0,emptyMap()))
        try{target.requireChecking();fail("Cancelled request admitted")}catch(_:IllegalStateException){}
    }
    @Test fun differentProjectSourceDurationModeSelectionOrCatalogCannotMatch() {
        val project=VideoEditProject(8000);val target=ticket(project)
        assertFalse(target.matches(VideoEditProject(8000),uri,8000,"ACCOUNT",0,emptyMap()))
        assertFalse(target.matches(project,"content://synthetic/other",8000,"ACCOUNT",0,emptyMap()))
        assertFalse(target.matches(project,uri,8001,"ACCOUNT",0,emptyMap()))
        assertFalse(target.matches(project,uri,8000,"GUEST_BASIC",0,emptyMap()))
        assertFalse(target.matches(project,uri,8000,"ACCOUNT",1,emptyMap()))
        assertFalse(target.matches(project,uri,8000,"ACCOUNT",0,mapOf("extra" to VideoSource("extra","content://synthetic/extra",4000))))
    }
    @Test fun canonicalTimelineAndSoundtrackChangesInvalidatePickerSnapshot() {
        val project=VideoEditProject(8000);val target=ticket(project)
        project.split(0,4000);assertFalse(target.matches(project,uri,8000,"ACCOUNT",0,emptyMap()))
        project.undo();assertTrue(target.matches(project,uri,8000,"ACCOUNT",0,emptyMap()))
        project.setSoundtrack(VideoSoundtrack("content://synthetic/music",8000));assertFalse(target.matches(project,uri,8000,"ACCOUNT",0,emptyMap()))
    }
    @Test fun captureDetachesMutableCaptionAndSourceCollections() {
        val cues=mutableListOf(VideoCaption(0,1000,"Keep"));val project=VideoEditProject(8000)
        project.restore(listOf(VideoSegment(0,8000,captions=cues)))
        val sources=linkedMapOf("extra" to VideoSource("extra","content://synthetic/extra",4000));val target=ticket(project,sources)
        sources.clear();assertFalse(target.matches(project,uri,8000,"ACCOUNT",0,sources))
        sources["extra"]=VideoSource("extra","content://synthetic/extra",4000)
        assertTrue(target.matches(project,uri,8000,"ACCOUNT",0,sources))
        cues.clear();assertFalse(target.matches(project,uri,8000,"ACCOUNT",0,sources))
    }
}
