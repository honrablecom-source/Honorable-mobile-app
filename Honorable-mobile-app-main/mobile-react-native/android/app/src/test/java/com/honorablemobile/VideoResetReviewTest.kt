package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.app.AlertDialog
import android.net.Uri
import app.honorable.editor.EditorAccessMode
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoResetReviewTest {
 private val unsafe=JvmTestAllocator
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun activity()=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{val p=VideoEditProject(5000);p.restore(listOf(VideoSegment(1000,4000,speed=2f,volume=.25f,title="Keep in Undo",captions=listOf(VideoCaption(1000,1500,"Caption")),captionSourceUri="content://synthetic/subtitles")),soundtrack=VideoSoundtrack("content://synthetic/music",10000,loop=false));set(it,"project",p);set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"workspaceActive",true);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"importedSources",linkedMapOf<String,VideoSource>())}
 private fun showing(d:AlertDialog,value:Boolean)=android.app.Dialog::class.java.getDeclaredField("mDecor").apply{isAccessible=true}.set(d,if(value)unsafe.allocateInstance(android.view.View::class.java)else null)
 private fun dialog(a:HonorableVideoEditorActivity)=(unsafe.allocateInstance(AlertDialog::class.java)as AlertDialog).also{showing(it,true);set(a,"captionReviewDialogs",linkedSetOf(it));set(a,"inspectorReviewDialog",it)}
 private fun capture(a:HonorableVideoEditorActivity)=call(a,"captureCaptionReview")as VideoCaptionReviewState
 private fun commit(a:HonorableVideoEditorActivity,d:AlertDialog,s:VideoCaptionReviewState,p:VideoEditProject)=call(a,"commitProjectReset",d,s,p)as Boolean
 @Test fun pausedBusyAndRecoveryEntryReturnsBeforeAndroidUi(){for(flag in listOf("workspaceActive","saving","importing","draftBusy","recoveryBlocked","recoveryChecking")){val a=activity();set(a,flag,flag!="workspaceActive");call(a,"confirmProjectReset")}}
 @Test fun approvedResetIsOneUndoRedoAndProtectsSubtitleMusicHistory(){val a=activity();val p=get(a,"project")as VideoEditProject;val old=p.segments;val music=p.soundtrack;val s=capture(a);val d=dialog(a);assertTrue(commit(a,d,s,p));assertEquals(listOf(VideoSegment(0,5000)),p.segments);assertNull(p.soundtrack);assertTrue(p.captionSourceUris().contains("content://synthetic/subtitles"));assertTrue(p.soundtrackUris().contains("content://synthetic/music"));assertTrue(p.undo());assertEquals(old,p.segments);assertSame(music,p.soundtrack);assertFalse(p.canUndo);assertTrue(p.redo());assertEquals(listOf(VideoSegment(0,5000)),p.segments)}
 @Test fun dismissedUntrackedAndReplacedReviewsCannotReset(){for(kind in listOf("dismissed","untracked","replaced")){val a=activity();val p=get(a,"project")as VideoEditProject;val old=p.segments;val s=capture(a);val d=dialog(a);when(kind){"dismissed"->showing(d,false);"untracked"->set(a,"captionReviewDialogs",linkedSetOf<AlertDialog>());else->dialog(a)};assertEquals(true,call(a,"canApplyCaptionReview",s));assertFalse(kind,commit(a,d,s,p));assertSame(old,p.segments);assertFalse(p.canUndo)}}
 @Test fun fullSnapshotRejectsGenerationSourceDurationSessionRangeAndModelChanges(){for(flag in listOf("generation","source","duration","mode","selected","range","music","model","busy","recovery","catalog")){val a=activity();val p=get(a,"project")as VideoEditProject;var s=capture(a);val d=dialog(a);when(flag){"generation"->set(a,"captionReviewGeneration",s.generation+1);"source"->set(a,"source",Uri.parse("content://synthetic/other"));"duration"->set(a,"primaryDurationMs",6000L);"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"selected"->set(a,"selectedClip",1);"range"->s=VideoCaptionReviewState(VideoCloseState.capture(p,5000,0,1500,3000,false,false,"content://synthetic/video"),s.mode,s.namespace,s.generation,s.sources,s.durations);"music"->p.setSoundtrack(null);"model"->p.setVolume(0,0f);"busy"->set(a,"saving",true);"recovery"->set(a,"recoveryBytes",byteArrayOf(1));else->set(a,"importedSources",linkedMapOf("extra" to VideoSource("extra","content://synthetic/extra",7000)))};val before=p.segments;assertFalse(flag,commit(a,d,s,p));assertSame(before,p.segments)}}
 @Test fun currentReviewNeverResetsAnotherProject(){val a=activity();val s=capture(a);val d=dialog(a);val foreign=VideoEditProject(5000);foreign.setVolume(0,0f);val before=foreign.segments;assertFalse(commit(a,d,s,foreign));assertSame(before,foreign.segments)}
 @Test fun alreadyOriginalResetDoesNotCreateFalseUndo(){val a=activity();val p=VideoEditProject(5000);set(a,"project",p);val s=capture(a);val d=dialog(a);assertTrue(commit(a,d,s,p));assertFalse(p.canUndo);assertFalse(p.canRedo)}
 @Test fun undoCannotReviveDismissedResetApproval(){val a=activity();val p=get(a,"project")as VideoEditProject;val old=p.segments;val s=capture(a);val d=dialog(a);assertTrue(commit(a,d,s,p));showing(d,false);assertTrue(p.undo());assertEquals(true,call(a,"canApplyCaptionReview",s));assertFalse(commit(a,d,s,p));assertEquals(old,p.segments);assertFalse(p.canUndo);assertTrue(p.canRedo)}
 @Test fun primaryAndImportedSourceCatalogRemainUntouched(){val a=activity();val p=get(a,"project")as VideoEditProject;val catalog=linkedMapOf("extra" to VideoSource("extra","content://synthetic/extra",7000));set(a,"importedSources",catalog);val primary=get(a,"source");val s=capture(a);val d=dialog(a);assertTrue(commit(a,d,s,p));assertSame(primary,get(a,"source"));assertSame(catalog,get(a,"importedSources"));assertEquals(7000L,catalog["extra"]!!.durationMs);assertEquals("content://synthetic/extra",catalog["extra"]!!.uri)}
 @Test fun undoCannotReviveStillVisibleResetApproval(){val a=activity();val p=get(a,"project")as VideoEditProject;val old=p.segments;val s=capture(a);val d=dialog(a);assertTrue(commit(a,d,s,p));assertTrue(p.undo());assertEquals(true,call(a,"canApplyCaptionReview",s));assertFalse(commit(a,d,s,p));assertEquals(old,p.segments);assertFalse(p.canUndo);assertTrue(p.canRedo)}
 @Test fun oldResetCannotApplyOrConsumeNewInspectorOwner(){val a=activity();val p=get(a,"project")as VideoEditProject;val s=capture(a);val old=dialog(a);val next=dialog(a);set(a,"captionReviewDialogs",linkedSetOf(old,next));assertFalse(commit(a,old,s,p));assertSame(next,get(a,"inspectorReviewDialog"));assertTrue(commit(a,next,s,p));assertNull(get(a,"inspectorReviewDialog"))}
 @Test fun noOpResetConsumesStillVisibleApprovalWithoutFalseHistory(){val a=activity();val p=VideoEditProject(5000);set(a,"project",p);val s=capture(a);val d=dialog(a);assertTrue(commit(a,d,s,p));assertFalse(commit(a,d,s,p));assertFalse(p.canUndo);assertFalse(p.canRedo);assertNull(get(a,"inspectorReviewDialog"))}
}
