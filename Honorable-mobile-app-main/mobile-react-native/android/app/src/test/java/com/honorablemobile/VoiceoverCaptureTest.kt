package com.honorablemobile

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class VoiceoverCaptureTest {
    private val roots=mutableListOf<File>()
    private val captures=mutableListOf<VoiceoverCapture>()
    private class Backend:VoiceoverBackend {
        var active=false;var failStart=false;var failStop=false
        override fun start(file:File){active=true;file.writeBytes(byteArrayOf(1,2,3));if(failStart)error("start failed")}
        override fun stop(){if(failStop)error("stop failed");active=false}
        override fun release(){active=false}
    }
    private fun root()=Files.createTempDirectory("voiceover-test-").toFile().also{roots.add(it)}
    private fun capture(files:VoiceoverFiles,backend:Backend=Backend(),owner:()->Unit={}):VoiceoverCapture=VoiceoverCapture(files,backend,owner).also{captures.add(it)}
    @After fun cleanup(){captures.forEach{runCatching{it.discard()}};roots.forEach{it.deleteRecursively()}}
    @Test fun permissionAndForegroundGatesNeverStartOrCreateATake() {
        val root=root();val backend=Backend();val capture=capture(VoiceoverFiles(root,"guest-basic"),backend)
        assertTrue(runCatching{capture.start(false,true)}.isFailure)
        assertTrue(runCatching{capture.start(true,false)}.isFailure)
        assertFalse(backend.active);assertNull(capture.pending);assertEquals(VoiceoverCapture.Phase.IDLE,capture.phase)
        assertTrue(root.walk().none{it.isFile})
    }
    @Test fun explicitKeepPromotesOnlyReviewedTakeAndDiscardDoesNotDeleteSavedOriginal() {
        val files=VoiceoverFiles(root(),"guest-basic");val capture=capture(files)
        capture.start(true,true);val unfinished=requireNotNull(capture.pending);capture.stop(true)
        assertEquals(VoiceoverCapture.Phase.REVIEW,capture.phase);assertTrue(files.saved().isEmpty())
        val saved=capture.accept{1500};assertTrue(saved.isFile);assertFalse(unfinished.exists());assertNull(capture.pending)
        val bytes=saved.readBytes();capture.discard();assertArrayEquals(bytes,saved.readBytes());assertEquals(listOf(saved),files.saved())
    }
    @Test fun startAndStopFailuresReleaseMicrophoneAndRemoveOnlyUnfinishedFile() {
        val files=VoiceoverFiles(root(),"guest-basic");val backend=Backend();val capture=capture(files,backend)
        backend.failStart=true;assertTrue(runCatching{capture.start(true,true)}.isFailure);assertFalse(backend.active);assertNull(capture.pending)
        backend.failStart=false;capture.start(true,true);val file=requireNotNull(capture.pending);backend.failStop=true
        assertTrue(runCatching{capture.stop(true)}.isFailure);assertFalse(backend.active);assertFalse(file.exists());assertNull(capture.pending)
        assertEquals(VoiceoverCapture.Phase.IDLE,capture.phase)
    }
    @Test fun ownerChangeBeforeKeepDoesNotPromoteOrExposeUnacceptedAudio() {
        var allowed=true;val files=VoiceoverFiles(root(),"account-"+"a".repeat(64));val capture=capture(files,owner={check(allowed)})
        capture.start(true,true);capture.stop(true);val file=requireNotNull(capture.pending);allowed=false
        assertTrue(runCatching{capture.accept{1500}}.isFailure);assertTrue(files.saved().isEmpty())
        capture.discard();assertFalse(file.exists())
    }
    @Test fun concurrentCapturesAndAbandonedCleanupRespectLiveReviewedTakes() {
        val files=VoiceoverFiles(root(),"guest-basic");val first=capture(files);val second=capture(files)
        first.start(true,true);assertTrue(runCatching{second.start(true,true)}.isFailure)
        first.stop(true);second.start(true,true)
        files.cleanAbandoned(VoiceoverCapture.livePaths());assertTrue(requireNotNull(first.pending).exists());assertTrue(requireNotNull(second.pending).exists())
        first.discard();second.discard();val abandoned=files.createPending();files.cleanAbandoned(VoiceoverCapture.livePaths());assertFalse(abandoned.exists())
    }
    @Test fun durationShelfAndPathLimitsPreserveExistingSavedFiles() {
        val files=VoiceoverFiles(root(),"guest-basic");val capture=capture(files)
        capture.start(true,true);capture.stop(true);assertTrue(runCatching{capture.accept{VoiceoverFiles.MAX_ACCEPTED_MS+1}}.isFailure)
        capture.discard()
        repeat(VoiceoverFiles.MAX_SAVED){files.savedFile(UUID.randomUUID().toString()).writeBytes(byteArrayOf(7))}
        assertTrue(runCatching{capture.start(true,true)}.isFailure);assertEquals(20,files.saved().size)
        assertTrue(runCatching{files.savedFile("../other")}.isFailure);assertTrue(runCatching{VoiceoverFiles(root(),"../owner")}.isFailure)
        assertTrue(files.saved().all{it.readBytes().contentEquals(byteArrayOf(7))})
    }
    @Test fun namespacesAndCanonicalPathsCannotReadOrDeleteAnotherOwnersTakes() {
        val root=root();val guest=VoiceoverFiles(root,"guest-basic");val account=VoiceoverFiles(root,"account-"+"a".repeat(64));val id=UUID.randomUUID().toString()
        guest.savedFile(id).writeBytes(byteArrayOf(9));assertTrue(account.saved().isEmpty());assertTrue(runCatching{account.deleteSaved(id)}.isFailure)
        assertArrayEquals(byteArrayOf(9),guest.savedFile(id).readBytes())
        val isolated=root();val other=File(isolated,"other").apply{mkdirs()};Files.createSymbolicLink(File(isolated,"guest-basic").toPath(),other.toPath())
        assertTrue(runCatching{VoiceoverFiles(isolated,"guest-basic").saved()}.isFailure)
    }
}
