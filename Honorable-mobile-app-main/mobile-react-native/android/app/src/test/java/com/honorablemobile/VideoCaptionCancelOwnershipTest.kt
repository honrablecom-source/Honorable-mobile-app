package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.app.AlertDialog
import android.net.Uri
import app.honorable.editor.EditorAccessMode
import org.junit.Assert.*
import org.junit.Test

/** Real visible-approval/project mutation gates, with metadata-only dialog membership. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoCaptionCancelOwnershipTest {
 private val unsafe=JvmTestAllocator
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun activity()=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{val p=VideoEditProject(5000);p.restore(listOf(VideoSegment(0,5000,captions=listOf(VideoCaption(0,500,"A"),VideoCaption(1000,1500,"B")),captionSourceUri="content://synthetic/subtitles")),soundtrack=VideoSoundtrack("content://synthetic/music",10000,loop=false));set(it,"project",p);set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"workspaceActive",true);set(it,"importedSources",linkedMapOf<String,VideoSource>())}
 private fun showing(d:AlertDialog,value:Boolean){android.app.Dialog::class.java.getDeclaredField("mDecor").apply{isAccessible=true}.set(d,if(value)unsafe.allocateInstance(android.view.View::class.java)else null)}
 private fun dialog()=(unsafe.allocateInstance(AlertDialog::class.java)as AlertDialog).also{showing(it,true)}
 private fun capture(a:HonorableVideoEditorActivity)=call(a,"captureCaptionReview")as VideoCaptionReviewState
 private fun commit(a:HonorableVideoEditorActivity,d:AlertDialog,s:VideoCaptionReviewState,clip:VideoSegment,cues:List<VideoCaption>)=call(a,"commitVisibleCaptionReview",d,s,get(a,"project"),0,clip,cues)as Boolean
 @Test fun dismissedDialogCannotAddEditDeleteOrClearDespiteCurrentSnapshot(){for(kind in listOf("add","edit","delete","clear")){val a=activity();val p=get(a,"project")as VideoEditProject;val clip=p.segments[0];val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf<AlertDialog>());val cues=when(kind){"add"->clip.captions+VideoCaption(2000,2500,"New");"edit"->clip.captions.map{it.copy(text="Changed")};"delete"->clip.captions.dropLast(1);else->emptyList()};assertEquals(true,call(a,"canApplyCaptionReview",s));assertFalse(kind,commit(a,d,s,clip,cues));assertSame(clip,p.segments[0]);assertFalse(p.canUndo)}}
 @Test fun registeredCurrentDialogCommitsOneUndoKeepsMusicAndDocument(){val a=activity();val p=get(a,"project")as VideoEditProject;val clip=p.segments[0];val music=p.soundtrack;val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));assertTrue(commit(a,d,s,clip,emptyList()));assertSame(music,p.soundtrack);assertTrue(p.captionSourceUris().contains("content://synthetic/subtitles"));assertTrue(p.undo());assertSame(clip,p.segments[0]);assertFalse(p.canUndo);assertTrue(p.redo());assertTrue(p.segments[0].captions.isEmpty())}
 @Test fun replacingVisibleDialogDoesNotAuthorizeTheOldCallback(){val a=activity();val p=get(a,"project")as VideoEditProject;val clip=p.segments[0];val s=capture(a);val old=dialog();val current=dialog();set(a,"captionReviewDialogs",linkedSetOf(current));assertFalse(commit(a,old,s,clip,emptyList()));assertEquals(setOf(current),get(a,"captionReviewDialogs"));assertTrue(commit(a,current,s,clip,emptyList()))}
 @Test fun cancelThenUndoCannotReviveOldApprovalWhenAllValuesReturn(){val a=activity();val p=get(a,"project")as VideoEditProject;val clip=p.segments[0];val s=capture(a);val d=dialog();val live=linkedSetOf(d);set(a,"captionReviewDialogs",live);assertTrue(commit(a,d,s,clip,emptyList()));live.remove(d);assertTrue(p.undo());assertEquals(true,call(a,"canApplyCaptionReview",s));assertFalse(commit(a,d,s,clip,emptyList()));assertFalse(p.canUndo);assertTrue(p.canRedo)}
 @Test fun registeredDialogStillRequiresForegroundSessionGenerationAndIdleState(){for(flag in listOf("paused","generation","mode","source","busy")){val a=activity();val p=get(a,"project")as VideoEditProject;val clip=p.segments[0];val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));when(flag){"paused"->set(a,"workspaceActive",false);"generation"->set(a,"captionReviewGeneration",s.generation+1);"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"source"->set(a,"source",Uri.parse("content://synthetic/other"));else->set(a,"saving",true)};assertFalse(flag,commit(a,d,s,clip,emptyList()));assertFalse(p.canUndo)}}
 @Test fun highlightAndManualVisibilityShareTheSameExactDialogGate(){val a=activity();val s=capture(a);val d=dialog();val live=linkedSetOf(d);set(a,"captionReviewDialogs",live);assertEquals(true,call(a,"activeCaptionReviewDialog",d,s));assertEquals(true,call(a,"activeCaptionHighlightDialog",d,s));live.clear();assertEquals(false,call(a,"activeCaptionReviewDialog",d,s));assertEquals(false,call(a,"activeCaptionHighlightDialog",d,s))}
 @Test fun dismissedDecorRejectsBeforeAsyncDismissListenerRemovesMembership(){val a=activity();val p=get(a,"project")as VideoEditProject;val clip=p.segments[0];val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));showing(d,false);assertFalse(commit(a,d,s,clip,emptyList()));assertSame(clip,p.segments[0]);assertFalse(p.canUndo)}
}
