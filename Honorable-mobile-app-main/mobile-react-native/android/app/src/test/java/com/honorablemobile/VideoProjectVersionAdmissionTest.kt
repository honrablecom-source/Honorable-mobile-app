package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.junit.Assert.*
import org.junit.Test

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoProjectVersionAdmissionTest {
    private val unsafe = JvmTestAllocator
    private fun set(value:Any,name:String,data:Any){value.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(value,data)}
    @Test fun pausedBusyAndPendingRecoveryRefuseBeforeAndroidStorageOrWidgets() {
        for(kind in 0..2){
            val activity=unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
            set(activity,"project",VideoEditProject(8000))
            if(kind>0)set(activity,"workspaceActive",true)
            if(kind==1)set(activity,"importing",true)
            if(kind==2)set(activity,"recoveryBytes",byteArrayOf(1,2,3))
            activity.javaClass.getDeclaredMethod("savedVideoVersions").apply{isAccessible=true}.invoke(activity)
            assertNull(activity.javaClass.getDeclaredField("versionShelf").apply{isAccessible=true}.get(activity))
        }
    }
    private fun shelf(owns:Boolean,events:MutableList<Boolean>):VideoProjectVersionShelf {
        val value=unsafe.allocateInstance(VideoProjectVersionShelf::class.java)as VideoProjectVersionShelf
        set(value,"closed",AtomicBoolean(false));set(value,"worker",Executors.newSingleThreadExecutor())
        set(value,"ownsBusy",owns);set(value,"busy",{flag:Boolean->events.add(flag);Unit})
        return value
    }
    @Test fun idleCloseDoesNotReleaseAnotherOperationBusyAndClosedJobsDoNotStart() {
        val events=mutableListOf<Boolean>();val value=shelf(false,events);value.close();value.close();assertTrue(events.isEmpty())
        var calls=0
        val method=value.javaClass.declaredMethods.single{it.name=="job"}.apply{isAccessible=true}
        method.invoke(value,{calls++;Unit},{_:Any?->calls++;Unit})
        assertEquals(0,calls);assertTrue(events.isEmpty())
    }
    @Test fun ownedBusyIsReleasedOnceAcrossRepeatedClose() {
        val events=mutableListOf<Boolean>();val value=shelf(true,events);value.close();value.close()
        assertEquals(listOf(false),events)
    }
    @Test fun replacedAndClosedDialogTokensCannotAuthorizeOldActions() {
        val value=shelf(false,mutableListOf());val old=Any();val latest=Any()
        val owns=value.javaClass.getDeclaredMethod("owns",Any::class.java).apply{isAccessible=true}
        set(value,"dialogToken",old);assertEquals(true,owns.invoke(value,old))
        set(value,"dialogToken",latest);assertEquals(false,owns.invoke(value,old));assertEquals(true,owns.invoke(value,latest))
        value.close();assertEquals(false,owns.invoke(value,latest))
    }
}
