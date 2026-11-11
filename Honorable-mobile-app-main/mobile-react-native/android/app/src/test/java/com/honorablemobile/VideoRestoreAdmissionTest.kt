package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.junit.Assert.*
import org.junit.Test

import java.lang.reflect.InvocationTargetException

/** Execute the actual restore method; absent Android widgets deliberately fail after model application. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoRestoreAdmissionTest {
    private fun activity(project:VideoEditProject,sources:Map<String,VideoSource> = emptyMap()):HonorableVideoEditorActivity {

        val instance=JvmTestAllocator.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
        for((name,value) in mapOf("project" to project,"importedSources" to LinkedHashMap(sources)))
            instance.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(instance,value)
        return instance
    }
    private fun apply(activity:HonorableVideoEditorActivity,project:VideoEditProject,draft:VideoDraft,undoable:Boolean):Boolean {
        val method=activity.javaClass.getDeclaredMethod("applySavedDraft",VideoEditProject::class.java,VideoDraft::class.java,Boolean::class.javaPrimitiveType).apply{isAccessible=true}
        return method.invoke(activity,project,draft,undoable)as Boolean
    }
    @Test fun poisonedPreviewReportsAppliedAndKeepsOneUndo() {
        val project=VideoEditProject(8000)
        val original=project.segments.toList()
        val imported=VideoSource("extra","content://synthetic/extra",5000)
        val clips=listOf(VideoSegment(1000,4000,title="Restored words"),VideoSegment(0,5000,sourceId="extra"))
        val draft=VideoDraft(8000,clips,1,1000,0,5000,listOf(imported))
        val activity=activity(project)
        assertFalse(apply(activity,project,draft,true))
        assertEquals(clips,project.segments)
        assertEquals(1,activity.javaClass.getDeclaredField("selectedClip").apply{isAccessible=true}.getInt(activity))
        val sources=activity.javaClass.getDeclaredField("importedSources").apply{isAccessible=true}.get(activity)as Map<*,*>
        assertEquals(imported,sources["extra"])
        assertTrue(project.undo());assertEquals(original,project.segments);assertFalse(project.canUndo)
        assertTrue(project.redo());assertEquals(clips,project.segments)
    }
    @Test fun startupRestoreRemainsAppliedWhenPreviewCannotInitialize() {
        val project=VideoEditProject(8000);project.split(0,4000)
        val clips=listOf(VideoSegment(2000,6000,title="Saved title"))
        val draft=VideoDraft(8000,clips,0,2500,2000,6000)
        assertFalse(apply(activity(project),project,draft,false))
        assertEquals(clips,project.segments);assertFalse(project.canUndo);assertFalse(project.canRedo)
    }
    @Test fun sourceConflictFailsBeforeModelOrHistoryMutation() {
        val project=VideoEditProject(8000);project.split(0,4000)
        val before=project.segments.toList()
        val old=VideoSource("extra","content://synthetic/old",5000)
        val replacement=VideoSource("extra","content://synthetic/replaced",5000)
        val activity=activity(project,mapOf("extra" to old))
        val draft=VideoDraft(8000,listOf(VideoSegment(0,5000,sourceId="extra")),0,0,0,5000,listOf(replacement))
        try { apply(activity,project,draft,true);fail("Source conflict accepted") }
        catch(error:InvocationTargetException) { assertTrue(error.cause is IllegalArgumentException) }
        assertEquals(before,project.segments)
        assertTrue(project.undo());assertEquals(listOf(VideoSegment(0,8000)),project.segments);assertFalse(project.canUndo)
    }
}
