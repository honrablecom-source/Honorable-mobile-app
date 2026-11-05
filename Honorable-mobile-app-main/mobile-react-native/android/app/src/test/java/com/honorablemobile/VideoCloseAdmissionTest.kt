package com.honorablemobile

import android.app.Activity
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

class VideoCloseAdmissionTest {
    private fun activity():HonorableVideoEditorActivity {
        val field=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}
        return (field.get(null)as Unsafe).allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
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
