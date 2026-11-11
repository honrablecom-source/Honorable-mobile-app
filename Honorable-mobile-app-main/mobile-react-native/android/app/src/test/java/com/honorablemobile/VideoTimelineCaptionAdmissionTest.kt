package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.app.Activity
import android.content.Intent
import android.net.Uri
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.PhotoPresetNamespace
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Real Activity/controller metadata and Intent phases, without constructors/UI/provider/Keystore execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoTimelineCaptionAdmissionTest {
    private val type=Class.forName("sun.misc.Unsafe");private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    private val document="content://synthetic/playback-subtitles"
    private fun allocate(clazz:Class<*>)=JvmTestAllocator.allocateInstance(clazz)
    private fun set(value:Any,name:String,data:Any?)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(value,data)
    private fun get(value:Any,name:String)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(value)
    private fun call(value:Any,name:String,vararg args:Any?)=value.javaClass.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(value,*args)
    private fun activity()=(allocate(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{
        set(it,"project",VideoEditProject(5000));set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"workspaceActive",true)
    }
    private fun owner(a:HonorableVideoEditorActivity)=(allocate(VideoTimelineCaptionControls::class.java)as VideoTimelineCaptionControls).also{set(a,"timelineCaptionImport",it);set(a,"importing",true)}
    private fun snapshot(a:HonorableVideoEditorActivity)=call(a,"closeState")as VideoCloseState
    private fun plan(project:VideoEditProject)=VideoTimelineCaptionImport.prepare(project.segments,"1\n00:00:01,000 --> 00:00:02,000\nNew caption\n".toByteArray(),document,project.soundtrack)
    private fun commit(a:HonorableVideoEditorActivity,owner:VideoTimelineCaptionControls,captured:VideoCloseState,plan:VideoTimelineCaptionImport)=call(a,"commitTimelineCaptionImport",owner,captured,EditorAccessMode.GUEST_BASIC,PhotoPresetNamespace.GUEST,plan)as Boolean
    @Test fun pausedBusyRecoveryAndMissingProjectEntryReturnsBeforeAndroidContext(){
        for(flag in listOf("paused","saving","analyzing","draftBusy","importing","pending","recovery","recoveryBlocked","recoveryChecking","project")){
            val a=activity();when(flag){"paused"->set(a,"workspaceActive",false);"pending"->set(a,"pendingRange",0L to 1000L);"recovery"->set(a,"recoveryBytes",byteArrayOf(1));"project"->set(a,"project",null);else->set(a,flag,true)}
            assertEquals(false,call(a,"canRequestTimelineCaptionImport"));call(a,"requestTimelineCaptionImport");assertNull(get(a,"timelineCaptionImport"))
        }
    }
    @Test fun actualApprovedCommitIsOneUndoAndKeepsMusicAndSubtitleProtection(){
        val a=activity();val project=get(a,"project")as VideoEditProject;val old=VideoSegment(0,5000,captions=listOf(VideoCaption(0,500,"Old")),captionSourceUri="content://synthetic/old-subtitles");val music=VideoSoundtrack("content://synthetic/music",10_000,loop=false)
        project.restore(listOf(old),soundtrack=music);val control=owner(a);val captured=snapshot(a);val proposal=plan(project);val source=get(a,"source")
        assertTrue(commit(a,control,captured,proposal));assertEquals(proposal.segments,project.segments);assertSame(music,project.soundtrack);assertSame(source,get(a,"source"));assertTrue(project.captionSourceUris().contains(document));assertTrue(project.undo());assertEquals(listOf(old),project.segments);assertFalse(project.canUndo);assertTrue(project.redo());assertEquals(proposal.segments,project.segments)
    }
    @Test fun staleProjectSourceModeRangeAndOperationStateCannotCommit(){
        for(flag in listOf("paused","source","mode","selected","duration","project","model","music","recovery","saving","draftBusy")){
            val a=activity();val project=get(a,"project")as VideoEditProject;val control=owner(a);val captured=snapshot(a);val proposal=plan(project)
            when(flag){"paused"->set(a,"workspaceActive",false);"source"->set(a,"source",Uri.parse("content://synthetic/other"));"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"selected"->set(a,"selectedClip",1);"duration"->set(a,"primaryDurationMs",6000L);"project"->set(a,"project",VideoEditProject(5000));"model"->project.split(0,2000);"music"->project.setSoundtrack(VideoSoundtrack("content://synthetic/music",1000));"recovery"->set(a,"recoveryBytes",byteArrayOf(1));else->set(a,flag,true)}
            val segments=project.segments;assertFalse("stale $flag",commit(a,control,captured,proposal));assertSame(segments,project.segments)
        }
    }
    @Test fun releasingOldControllerCannotUnlockANewerImport(){
        val a=activity();val old=owner(a);val current=owner(a);call(a,"releaseTimelineCaptionImport",old);assertSame(current,get(a,"timelineCaptionImport"));assertEquals(true,get(a,"importing"));call(a,"releaseTimelineCaptionImport",current);assertNull(get(a,"timelineCaptionImport"));assertEquals(false,get(a,"importing"))
    }
    private fun control(phase:Int,foreground:()->Boolean={false},current:()->Boolean={true},release:()->Unit={}):VideoTimelineCaptionControls=
        (allocate(VideoTimelineCaptionControls::class.java)as VideoTimelineCaptionControls).also{set(it,"phase",AtomicInteger(phase));set(it,"worker",Executors.newSingleThreadExecutor());set(it,"isForeground",foreground);set(it,"isCurrent",current);set(it,"canReview",{true});set(it,"release",release);set(it,"message",{_:String->Unit})}
    private fun phase(value:VideoTimelineCaptionControls)=(get(value,"phase")as AtomicInteger).get()
    private fun result(uri:String=document)=Intent().setData(Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    @Test fun pickerResultWaitsForForegroundWithoutTouchingProviderOrUi(){
        var releases=0;val value=control(1,release={releases++});value.accept(Activity.RESULT_OK,result());assertEquals(2,phase(value));assertEquals(document,(get(value,"pending")as Pair<*,*>).first);value.onPause();value.resume();assertEquals(2,phase(value));assertEquals(0,releases);value.close();assertEquals(1,releases);assertNull(get(value,"pending"))
    }
    @Test fun cancelInvalidUriAndDuplicateResultAreSingleUse(){
        var releases=0;val cancelled=control(1,release={releases++});cancelled.accept(Activity.RESULT_CANCELED,null);cancelled.accept(Activity.RESULT_OK,result());assertEquals(5,phase(cancelled));assertEquals(1,releases)
        val invalid=control(1,release={releases++});invalid.accept(Activity.RESULT_OK,result("file:///private"));assertEquals(5,phase(invalid));assertEquals(2,releases)
        val valid=control(1);valid.accept(Activity.RESULT_OK,result());valid.accept(Activity.RESULT_OK,result("content://synthetic/other"));assertEquals(document,(get(valid,"pending")as Pair<*,*>).first);valid.close()
    }
    @Test fun pauseStopsDisclosureReadAndReviewButKeepsOnlyPickerMetadata(){
        for(start in listOf(0,3,4)){var releases=0;val value=control(start,release={releases++});val token=Any();set(value,"token",token);value.onPause();assertEquals(5,phase(value));assertEquals(1,releases);assertEquals(false,call(value,"owns",token));value.close();assertEquals(1,releases)}
        val pending=control(1);pending.onPause();assertEquals(1,phase(pending));pending.close()
    }
    @Test fun staleClosedTokensAndInvalidOpenCannotReachAndroidUi(){
        val value=control(0,current={false});value.open();assertEquals(5,phase(value));value.open()
        val token=Any();val current=control(4);set(current,"token",token);assertEquals(true,call(current,"owns",token));set(current,"token",Any());assertEquals(false,call(current,"owns",token));current.close();assertEquals(false,call(current,"owns",get(current,"token")?:token))
    }
    @Test fun changedContextWhilePickerWasAwayDropsResultBeforeReading(){
        var current=true;var foreground=false;var releases=0;val value=control(1,foreground={foreground},current={current},release={releases++});value.accept(Activity.RESULT_OK,result());current=false;foreground=true;value.resume();assertEquals(5,phase(value));assertNull(get(value,"pending"));assertEquals(1,releases)
    }
    @Test fun pendingTrimMustMatchRoundedDefaultWholeClipRange(){
        val a=activity();val clip=VideoSegment(1001,2501);assertEquals(true,call(a,"timelineCaptionRangeMatches",clip,1,3));assertEquals(false,call(a,"timelineCaptionRangeMatches",clip,2,3));assertEquals(false,call(a,"timelineCaptionRangeMatches",clip,1,2));assertEquals(false,call(a,"timelineCaptionRangeMatches",clip,3,1))
    }
}
