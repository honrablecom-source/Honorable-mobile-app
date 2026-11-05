package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

/** Poisoned source/widgets expose an incorrectly admitted request before any Android UI can run. */
class VideoQuietAdmissionTest {
    private fun activity():HonorableVideoEditorActivity {
        val field=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}
        val instance=(field.get(null)as Unsafe).allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
        set(instance,"project",VideoEditProject(4000))
        return instance
    }
    private fun set(instance:Any,name:String,value:Any){instance.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(instance,value)}
    private fun invoke(instance:Any){for(name in listOf("quietPauseOptions","sourceAudioLevels","savedTitleStyles"))instance.javaClass.getDeclaredMethod(name).apply{isAccessible=true}.invoke(instance)}
    @Test fun pausedEditorRefusesBeforeSourceOrWidgetsAreRead(){val instance=activity();invoke(instance);assertNull(instance.javaClass.getDeclaredField("quietAudioCancellation").apply{isAccessible=true}.get(instance))}
    @Test fun busyEditorRefusesBeforeAndroidOrSourceAccess(){val instance=activity();set(instance,"workspaceActive",true);set(instance,"importing",true);invoke(instance);assertTrue(instance.javaClass.getDeclaredField("importing").apply{isAccessible=true}.getBoolean(instance))}
}
