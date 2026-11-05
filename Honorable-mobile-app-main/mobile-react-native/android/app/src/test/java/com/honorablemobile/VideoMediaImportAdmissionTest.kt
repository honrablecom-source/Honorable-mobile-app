package com.honorablemobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import app.honorable.editor.EditorAccessMode
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

/** Actual callback admission with poisoned views, resolver and worker; no provider is opened. */
class VideoMediaImportAdmissionTest {
    private val primary="content://synthetic/primary"
    private fun activity():HonorableVideoEditorActivity {
        val field=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}
        val value=(field.get(null)as Unsafe).allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
        set(value,"project",VideoEditProject(8000));set(value,"source",Uri.parse(primary));set(value,"primaryDurationMs",8000L)
        set(value,"accessMode",EditorAccessMode.ACCOUNT);set(value,"importedSources",linkedMapOf<String,VideoSource>())
        return value
    }
    private fun set(value:Any,name:String,data:Any){value.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(value,data)}
    private fun get(value:Any,name:String):Any?=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(value)
    private fun call(value:Any,name:String,vararg args:Any?):Any?=value.javaClass.declaredMethods.single{it.name==name}.apply{isAccessible=true}.invoke(value,*args)
    private fun target(value:Any,kind:VideoMediaImportKind=VideoMediaImportKind.VIDEO)=VideoMediaImportTarget(kind,get(value,"project")as VideoEditProject,primary,8000,"ACCOUNT",0,emptyMap())
    private fun result()=Intent().setData(Uri.parse("content://synthetic/chosen"))
    @Test fun reopenedActivityWithNoPendingRequestRefusesVideoAndAudioBeforeResolverOrWorker() {
        val value=activity();val before=(get(value,"project")as VideoEditProject).segments.toList()
        call(value,"finishImport",Activity.RESULT_OK,result());call(value,"finishSoundtrackImport",Activity.RESULT_OK,result())
        assertEquals(false,get(value,"importing"));assertNull(get(value,"mediaImportTarget"));assertEquals(before,(get(value,"project")as VideoEditProject).segments)
    }
    @Test fun resultForAnotherKindCannotConsumeRequestOrClearItsBusyFlag() {
        val value=activity();val target=target(value);set(value,"mediaImportTarget",target);set(value,"importing",true)
        call(value,"finishSoundtrackImport",Activity.RESULT_OK,result())
        assertSame(target,get(value,"mediaImportTarget"));assertTrue(target.isPending);assertEquals(true,get(value,"importing"))
    }
    @Test fun duplicateResultDuringValidationDoesNotLaunchAnotherWorkerOrUnlock() {
        val value=activity();val target=target(value);assertTrue(target.beginValidation());set(value,"mediaImportTarget",target);set(value,"importing",true)
        call(value,"finishImport",Activity.RESULT_OK,result())
        assertSame(target,get(value,"mediaImportTarget"));assertTrue(target.isChecking);assertEquals(true,get(value,"importing"))
    }
    @Test fun staleProjectResultReleasesOnlyItsRequestWithoutInspectionOrModelMutation() {
        val value=activity();val target=target(value);set(value,"mediaImportTarget",target);set(value,"importing",true)
        val replacement=VideoEditProject(8000);set(value,"project",replacement)
        call(value,"finishImport",Activity.RESULT_OK,result())
        assertNull(get(value,"mediaImportTarget"));assertEquals(false,get(value,"importing"));assertFalse(target.isChecking)
        assertEquals(listOf(VideoSegment(0,8000)),replacement.segments);assertFalse(replacement.canUndo)
    }
    @Test fun oldWorkerReleaseCannotClearNewRequestOrItsBusyOwnership() {
        val value=activity();val old=target(value);val newer=target(value,VideoMediaImportKind.SOUNDTRACK)
        set(value,"mediaImportTarget",newer);set(value,"importing",true)
        assertEquals(false,call(value,"finishMediaImportTarget",old))
        assertSame(newer,get(value,"mediaImportTarget"));assertEquals(true,get(value,"importing"));assertTrue(newer.isPending)
        assertEquals(true,call(value,"finishMediaImportTarget",newer));assertNull(get(value,"mediaImportTarget"));assertEquals(false,get(value,"importing"))
    }
    @Test fun resultRouterKeepsOtherCallbacksAwayAndExpectedCancelReleasesTheGate() {
        val value=activity();val target=target(value);set(value,"mediaImportTarget",target);set(value,"importing",true)
        for(code in 22..28)assertEquals(code==23,call(value,"allowsMediaImportResult",code))
        assertEquals(true,call(value,"allowsMediaImportResult",999))
        call(value,"finishImport",Activity.RESULT_CANCELED,null)
        assertNull(get(value,"mediaImportTarget"));assertEquals(false,get(value,"importing"));assertFalse(target.isPending)
        for(code in 22..28)assertEquals(true,call(value,"allowsMediaImportResult",code))
        assertEquals(listOf(VideoSegment(0,8000)),(get(value,"project")as VideoEditProject).segments)
    }
}
