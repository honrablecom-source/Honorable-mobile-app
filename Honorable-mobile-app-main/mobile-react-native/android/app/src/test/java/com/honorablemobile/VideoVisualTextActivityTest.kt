package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=android.app.Application::class)
class VideoVisualTextActivityTest {
    private fun set(value:Any,key:String,item:Any?){value.javaClass.getDeclaredField(key).apply{isAccessible=true}.set(value,item)}
    private fun get(value:Any,key:String)=value.javaClass.getDeclaredField(key).apply{isAccessible=true}.get(value)
    private fun call(value:Any,name:String,vararg args:Any?)=value.javaClass.declaredMethods.single{it.name==name}.apply{isAccessible=true}.invoke(value,*args)
    private fun clip()=VideoSegment(1000,2000,speed=1.5f,volume=.5f,title="Original",fadeEdges=true,captions=listOf(VideoCaption(1000,1200,"Old")),captionSourceUri="content://synthetic/old-subtitles")
    private fun activity()=Robolectric.buildActivity(HonorableVideoEditorActivity::class.java).get().also{
        val project=VideoEditProject(5000);project.restore(listOf(clip(),VideoSegment(2500,3500)),soundtrack=VideoSoundtrack("content://synthetic/music",5000))
        set(it,"project",project);set(it,"workspaceActive",true);set(it,"importing",true)
    }
    private fun owner(activity:HonorableVideoEditorActivity)=(JvmTestAllocator.allocateInstance(VideoVisualTextControls::class.java)as VideoVisualTextControls).also{set(activity,"visualTextControls",it)}
    private fun commit(a:HonorableVideoEditorActivity,c:VideoVisualTextControls,p:VideoEditProject,clip:VideoSegment,current:Boolean=true)=call(a,"commitVisualText",c,p,0,clip,listOf(VideoCaption(1100,1800,"Corrected")),{current})as Boolean
    @Test fun actualParentApplyClearsCurrentSubtitleBindingPreservesEverythingElseAndOneUndo() {
        val a=activity();val p=get(a,"project")as VideoEditProject;val c=owner(a);val before=p.segments;val music=p.soundtrack
        assertTrue(commit(a,c,p,before[0]));assertEquals(before[0].copy(captions=listOf(VideoCaption(1100,1800,"Corrected")),captionSourceUri=null),p.segments[0])
        assertSame(before[1],p.segments[1]);assertSame(music,p.soundtrack);assertTrue(p.undo());assertEquals(before,p.segments);assertSame(music,p.soundtrack);assertFalse(p.canUndo)
    }
    @Test fun staleLeaseSessionOrBusyParentCannotApplyAnything() {
        for(flag in listOf("owner","paused","saving","analyzing","draftBusy","recoveryBlocked","recoveryChecking","pendingRange","recoveryBytes","project","selection","snapshot")){
            val a=activity();val p=get(a,"project")as VideoEditProject;val c=owner(a);val before=p.segments
            when(flag){"owner"->owner(a);"paused"->set(a,"workspaceActive",false);"pendingRange"->set(a,"pendingRange",1L to 2L);"recoveryBytes"->set(a,"recoveryBytes",byteArrayOf(1));"project"->set(a,"project",VideoEditProject(5000));"selection"->set(a,"selectedClip",1);"snapshot"->{};else->set(a,flag,true)}
            assertFalse(flag,commit(a,c,p,before[0],flag!="snapshot"));assertSame(before,p.segments);assertFalse(p.canUndo)
        }
    }
    @Test fun staleReleaseAndLatePickerCannotClearNewOwnersBusyState() {
        val a=activity();val old=owner(a);val current=owner(a);call(a,"releaseVisualText",old)
        assertSame(current,get(a,"visualTextControls"));assertEquals(true,get(a,"importing"))
        for(code in 22..29)assertEquals(false,call(a,"allowsMediaImportResult",code))
        call(a,"releaseVisualText",current);assertNull(get(a,"visualTextControls"));assertEquals(false,get(a,"importing"))
    }
    @Test fun blockedEntryReturnsBeforeSourceViewsModelOrUi() {
        for(flag in listOf("paused","saving","analyzing","draftBusy","recoveryBlocked","recoveryChecking")){
            val a=activity();set(a,"importing",false);if(flag=="paused")set(a,"workspaceActive",false)else set(a,flag,true)
            call(a,"requestVisualTextCaptions");assertNull(get(a,"visualTextControls"))
        }
    }
}
