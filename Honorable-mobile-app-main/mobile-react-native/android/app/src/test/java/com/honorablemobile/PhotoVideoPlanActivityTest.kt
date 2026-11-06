package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

/** Actual Activity entry guards only. No Android constructor, widgets, dialogs or lifecycle are run. */
class PhotoVideoPlanActivityTest {
    private fun instance():Pair<PhotoVideoCreatorActivity,PhotoVideoPlanGate> {
        val unsafe=Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible=true }.get(null) as Unsafe
        val activity=unsafe.allocateInstance(PhotoVideoCreatorActivity::class.java) as PhotoVideoCreatorActivity
        val gate=PhotoVideoPlanGate()
        PhotoVideoCreatorActivity::class.java.getDeclaredField("planGate").apply { isAccessible=true }.set(activity,gate)
        PhotoVideoCreatorActivity::class.java.getDeclaredField("alive").apply { isAccessible=true }.setBoolean(activity,true)
        return activity to gate
    }
    private fun call(activity:PhotoVideoCreatorActivity,name:String):Any? =
        PhotoVideoCreatorActivity::class.java.getDeclaredMethod(name).apply { isAccessible=true }.invoke(activity)
    private fun locked(activity:PhotoVideoCreatorActivity)=call(activity,"editingLocked") as Boolean
    @Test fun newPlanEntrypointsRefuseBeforeResumeAfterPauseAndAfterDestroy() {
        val (activity,gate)=instance()
        fun denied() { call(activity,"savePreparationPlan");call(activity,"openPreparationPlans");assertFalse(gate.busy()) }
        denied();gate.resume();gate.pause();denied();gate.destroy();denied()
        assertFalse(locked(activity))
    }
    @Test fun actualCreatorSelectionAndPlanEntrypointsShareMetadataBusyOwnership() {
        val (activity,gate)=instance();gate.resume();val lease=gate.begin()!!
        assertTrue(locked(activity));call(activity,"savePreparationPlan");call(activity,"openPreparationPlans");call(activity,"fitPhotoTiming")
        PhotoVideoCreatorActivity::class.java.getDeclaredMethod("restoreSelectionEdit",Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(activity,false)
        assertTrue(gate.current(lease));assertTrue(gate.finish(lease));assertFalse(locked(activity))
    }
    @Test fun oldCompletionAndPausedWorkCannotUnlockAnotherActualCreatorOperation() {
        val (activity,gate)=instance();gate.resume();val old=gate.begin()!!;assertTrue(gate.finish(old))
        val current=gate.begin()!!;assertFalse(gate.finish(old));assertTrue(locked(activity))
        gate.pause();assertTrue(locked(activity));assertFalse(gate.finish(current));assertFalse(locked(activity))
    }
}
