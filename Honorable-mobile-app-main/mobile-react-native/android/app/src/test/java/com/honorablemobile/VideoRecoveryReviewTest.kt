package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe
import app.honorable.editor.EditorAccessMode
import java.lang.reflect.InvocationTargetException

/** Production private recovery paths with poisoned Android views/workers and real canonical models. */
class VideoRecoveryReviewTest {
    private val raw=byteArrayOf(7,0,19,-1,5)
    private fun activity(project:VideoEditProject=VideoEditProject(8000)):HonorableVideoEditorActivity {
        val field=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}
        val value=(field.get(null)as Unsafe).allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
        set(value,"project",project);set(value,"primaryDurationMs",8000L);set(value,"importedSources",linkedMapOf<String,VideoSource>());set(value,"accessMode",EditorAccessMode.ACCOUNT)
        return value
    }
    private fun set(value:Any,name:String,data:Any){value.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(value,data)}
    private fun get(value:Any,name:String):Any?=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(value)
    private fun call(value:Any,name:String,vararg arguments:Any?):Any? {
        val method=value.javaClass.declaredMethods.single { it.name==name }
        method.isAccessible=true;return method.invoke(value,*arguments)
    }
    @Test fun opaqueRecoveryBytesWinOverPoisonedCurrentDraftAndAreDefensivelyCopied() {
        val value=activity();set(value,"recoveryBytes",raw.copyOf())
        val output=call(value,"recoveryStateBytes")as ByteArray
        assertArrayEquals(raw,output);output[0]=99
        assertArrayEquals(raw,call(value,"recoveryStateBytes")as ByteArray)
        assertFalse(get(value,"recoveryBytes")===output)
    }
    @Test fun failedRecoveryBlocksCoreMutationCommandsBeforeWidgetsOrWorkers() {
        val value=activity();set(value,"recoveryBytes",raw.copyOf())
        call(value,"blockRecovery","Synthetic missing original")
        assertEquals(true,get(value,"recoveryBlocked"));assertEquals(true,get(value,"draftBusy"));assertEquals(false,call(value,"editorIdle"))
        for(command in listOf("saveCopy","saveAudioCopy","exportSubtitles","importVideo","editTitle"))call(value,command)
        call(value,"draftOperation",true)
        assertArrayEquals(raw,call(value,"recoveryStateBytes")as ByteArray)
        val project=get(value,"project")as VideoEditProject;assertEquals(listOf(VideoSegment(0,8000)),project.segments);assertFalse(project.canUndo)
    }
    @Test fun invalidAndOversizedRetryKeepBytesInsteadOfFallingBackOrLaunchingWorker() {
        for(bytes in listOf(raw,ByteArray(256_077){65})) {
            val value=activity();set(value,"workspaceActive",true);set(value,"recoveryBytes",bytes.copyOf())
            call(value,"retryRecovery")
            assertEquals(true,get(value,"recoveryBlocked"));assertEquals(true,get(value,"draftBusy"));assertEquals(false,get(value,"recoveryChecking"))
            assertArrayEquals(bytes,call(value,"recoveryStateBytes")as ByteArray)
        }
    }
    @Test fun successfulCanonicalRestoreReleasesRecoveryDespiteUnavailablePreview() {
        val project=VideoEditProject(8000);val value=activity(project)
        set(value,"recoveryBytes",raw.copyOf());call(value,"blockRecovery","Synthetic unavailable source")
        val saved=VideoDraft(8000,listOf(VideoSegment(1000,6000,title="Recovered words")),0,2000,1000,6000)
        assertEquals(false,call(value,"applyPendingRecovery",project,saved))
        assertNull(get(value,"recoveryBytes"));assertEquals(false,get(value,"draftBusy"));assertEquals(false,get(value,"recoveryBlocked"))
        assertEquals(saved.segments,project.segments);assertFalse(project.canUndo)
        val encoded=call(value,"recoveryStateBytes")as ByteArray
        assertEquals(saved.segments,VideoDraftCodec.decode(encoded,8000)!!.segments)
    }
    @Test fun modelConflictPreservesOpaqueRecoveryAndCurrentProject() {
        val project=VideoEditProject(8000);val value=activity(project)
        val old=VideoSource("extra","content://synthetic/old",4000);set(value,"importedSources",linkedMapOf("extra" to old))
        set(value,"recoveryBytes",raw.copyOf());call(value,"blockRecovery","Synthetic conflict")
        val other=old.copy(uri="content://synthetic/other")
        val saved=VideoDraft(8000,listOf(VideoSegment(0,4000,sourceId="extra")),0,0,0,4000,listOf(other))
        try{call(value,"applyPendingRecovery",project,saved);fail("Conflict applied")}catch(error:InvocationTargetException){assertTrue(error.cause is IllegalArgumentException)}
        assertArrayEquals(raw,call(value,"recoveryStateBytes")as ByteArray);assertEquals(true,get(value,"draftBusy"))
        assertEquals(listOf(VideoSegment(0,8000)),project.segments);assertFalse(project.canUndo)
    }
    @Test fun discardRequiresExactRecoveryTargetAndNoCheckingBeforeFreshOriginal() {
        val project=VideoEditProject(8000);project.split(0,4000);val value=activity(project)
        set(value,"recoveryBytes",raw.copyOf());call(value,"blockRecovery","Synthetic review")
        assertEquals(false,call(value,"discardRecovery",VideoEditProject(8000),raw))
        assertEquals(false,call(value,"discardRecovery",project,raw+1.toByte()))
        set(value,"recoveryChecking",true);assertEquals(false,call(value,"discardRecovery",project,raw));set(value,"recoveryChecking",false)
        assertArrayEquals(raw,call(value,"recoveryStateBytes")as ByteArray)
        assertEquals(true,call(value,"discardRecovery",project,raw))
        assertNull(get(value,"recoveryBytes"));assertEquals(false,get(value,"draftBusy"))
        val fresh=get(value,"project")as VideoEditProject;assertNotSame(project,fresh);assertEquals(listOf(VideoSegment(0,8000)),fresh.segments);assertFalse(fresh.canUndo)
        assertEquals(2,project.segments.size)
    }
    @Test fun pendingRecoveryRefusesStaleImportAndSaveResultsWithoutUnlockingEdits() {
        val value=activity();set(value,"recoveryBytes",raw.copyOf());call(value,"blockRecovery","Synthetic review")
        for(code in 22..28) {
            set(value,"importing",true);set(value,"pendingRange",0L to 1000L)
            assertEquals(true,call(value,"holdRecoveryResult",code))
            assertNull(get(value,"pendingRange"));assertEquals(false,get(value,"importing"));assertEquals(true,get(value,"draftBusy"))
            assertArrayEquals(raw,call(value,"recoveryStateBytes")as ByteArray)
        }
        assertEquals(false,call(value,"holdRecoveryResult",99))
    }
    @Test fun healthyCanonicalEditsStillSerializeWhenPreviewAndRangeWidgetsAreUnavailable() {
        val project=VideoEditProject(8000);project.restore(listOf(VideoSegment(2000,7000,title="Keep canonical edit",volume=.5f)))
        val value=activity(project)
        val encoded=call(value,"recoveryStateBytes")as ByteArray
        val decoded=VideoDraftCodec.decode(encoded,8000)!!
        assertEquals(project.segments,decoded.segments);assertEquals(2000L,decoded.trimStartMs);assertEquals(7000L,decoded.trimEndMs);assertEquals(2000L,decoded.previewPositionMs)
        assertFalse(project.canUndo)
    }
}
