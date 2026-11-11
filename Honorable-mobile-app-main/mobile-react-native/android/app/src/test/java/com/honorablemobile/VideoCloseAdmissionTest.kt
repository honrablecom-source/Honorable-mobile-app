package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.app.Activity
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoCloseAdmissionTest {
    private fun activity():HonorableVideoEditorActivity {

        return JvmTestAllocator.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
    }
    private fun request(activity:HonorableVideoEditorActivity) {
        activity.javaClass.getDeclaredMethod("requestClose").apply{isAccessible=true}.invoke(activity)
        assertNull(activity.javaClass.getDeclaredField("closeDialog").apply{isAccessible=true}.get(activity))
    }
    @Test fun pausedEditorDoesNotReadPoisonedWidgetsOrShowDialog(){request(activity())}
    @Test fun destroyedEditorDoesNotReadPoisonedWidgetsOrShowDialog(){
        val activity=activity()
        activity.javaClass.getDeclaredField("workspaceActive").apply{isAccessible=true}.setBoolean(activity,true)
        Activity::class.java.getDeclaredField("mDestroyed").apply{isAccessible=true}.setBoolean(activity,true)
        request(activity)
    }
}
