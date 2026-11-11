package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import app.honorable.editor.EditorAccessMode
import org.junit.Assert.*
import org.junit.Test

/** Actual restore entry/approval guards; views and storage remain deliberately unavailable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoRestoreReviewTest {
    private fun activity(mode:EditorAccessMode):HonorableVideoEditorActivity {

        val value=JvmTestAllocator.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
        val project=VideoEditProject(8000);project.restore(listOf(VideoSegment(0,8000,title="Keep current")))
        set(value,"project",project);set(value,"accessMode",mode);set(value,"durationMs",8000);set(value,"primaryDurationMs",8000L)
        return value
    }
    private fun set(value:Any,name:String,data:Any){value.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(value,data)}
    private fun get(value:Any,name:String):Any?=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(value)
    private fun call(value:Any,name:String,vararg args:Any?):Any?=value.javaClass.declaredMethods.single{it.name==name}.apply{isAccessible=true}.invoke(value,*args)
    @Test fun pausedGuestAndAccountRestoreCannotReadWidgetsOrStartStorage() {
        for((mode,method)in listOf(EditorAccessMode.GUEST_BASIC to "guestProjectOperation",EditorAccessMode.ACCOUNT to "draftOperation")) {
            val value=activity(mode);val project=get(value,"project")as VideoEditProject;val before=project.segments.toList()
            call(value,method,true)
            assertNull(get(value,"restoreReviewDialog"));assertEquals(false,get(value,"draftBusy"));assertEquals(before,project.segments);assertFalse(project.canUndo)
        }
    }
    @Test fun busyGuestAndAccountRestoreCannotShowReviewOrStartAnotherOperation() {
        for((mode,method)in listOf(EditorAccessMode.GUEST_BASIC to "guestProjectOperation",EditorAccessMode.ACCOUNT to "draftOperation")) {
            val value=activity(mode);set(value,"workspaceActive",true);set(value,"importing",true)
            call(value,method,true)
            assertNull(get(value,"restoreReviewDialog"));assertEquals(false,get(value,"draftBusy"));assertEquals(true,get(value,"importing"))
        }
    }
    @Test fun approvalRequiresExactCurrentProjectModeForegroundAndIdleState() {
        val value=activity(EditorAccessMode.ACCOUNT);set(value,"workspaceActive",true)
        val captured=call(value,"closeState")as VideoCloseState
        assertEquals(true,call(value,"canApproveSavedRestore",captured,EditorAccessMode.ACCOUNT))
        set(value,"workspaceActive",false);assertEquals(false,call(value,"canApproveSavedRestore",captured,EditorAccessMode.ACCOUNT));set(value,"workspaceActive",true)
        set(value,"draftBusy",true);assertEquals(false,call(value,"canApproveSavedRestore",captured,EditorAccessMode.ACCOUNT));set(value,"draftBusy",false)
        set(value,"accessMode",EditorAccessMode.GUEST_BASIC);assertEquals(false,call(value,"canApproveSavedRestore",captured,EditorAccessMode.ACCOUNT));set(value,"accessMode",EditorAccessMode.ACCOUNT)
        val project=get(value,"project")as VideoEditProject;project.trim(0,1000,7000)
        assertEquals(false,call(value,"canApproveSavedRestore",captured,EditorAccessMode.ACCOUNT));project.undo()
        assertEquals(true,call(value,"canApproveSavedRestore",captured,EditorAccessMode.ACCOUNT))
        set(value,"project",VideoEditProject(8000));assertEquals(false,call(value,"canApproveSavedRestore",captured,EditorAccessMode.ACCOUNT))
    }
}
