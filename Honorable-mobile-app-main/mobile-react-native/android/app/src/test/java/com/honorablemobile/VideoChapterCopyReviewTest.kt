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
class VideoChapterCopyReviewTest {
 private val unsafe=JvmTestAllocator
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun activity()=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{val p=VideoEditProject(5000);p.restore(listOf(VideoSegment(0,5000,captions=listOf(VideoCaption(0,500,"A"),VideoCaption(1000,1500,"B")),captionSourceUri="content://synthetic/subtitles")),soundtrack=VideoSoundtrack("content://synthetic/music",10000,loop=false));set(it,"project",p);set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"workspaceActive",true);set(it,"importedSources",linkedMapOf<String,VideoSource>())}
 private fun showing(d:AlertDialog,value:Boolean){android.app.Dialog::class.java.getDeclaredField("mDecor").apply{isAccessible=true}.set(d,if(value)unsafe.allocateInstance(android.view.View::class.java)else null)}
 private fun dialog()=(unsafe.allocateInstance(AlertDialog::class.java)as AlertDialog).also{showing(it,true)}
 private fun capture(a:HonorableVideoEditorActivity)=call(a,"captureCaptionReview")as VideoCaptionReviewState
 private fun copy(a:HonorableVideoEditorActivity,d:AlertDialog,r:VideoCaptionReviewState,action:()->Unit)=call(a,"performCaptionClipboardCopy",d,r,action)as Boolean
 @Test fun currentChapterCopyPreservesEditedTimesDuplicateClipsTitlesAndProject(){val a=activity();val p=get(a,"project")as VideoEditProject;val first=VideoSegment(0,5000,speed=2f,title="Opening\n scene");p.restore(listOf(first,VideoSegment(0,5000),first),soundtrack=p.soundtrack);val old=p.segments;val music=p.soundtrack;val r=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));val text=VideoChapters.text(old);var copied:String?=null;assertTrue(copy(a,d,r){copied=text});assertEquals("Clip starts in edited playback time (HH:MM:SS.mmm)\n\n00:00:00.000 Opening scene\n00:00:02.500 Clip 2\n00:00:07.500 Opening scene",copied);assertSame(old,p.segments);assertSame(music,p.soundtrack);assertFalse(p.canUndo)}
 @Test fun removedForeignOrDismissedReviewCannotCopy(){for(flag in listOf("removed","foreign","dismissed")){val a=activity();val r=capture(a);val d=dialog();set(a,"captionReviewDialogs",when(flag){"removed"->linkedSetOf<AlertDialog>();"foreign"->linkedSetOf(dialog());else->linkedSetOf(d)});if(flag=="dismissed")showing(d,false);var copies=0;assertFalse(flag,copy(a,d,r){copies++});assertEquals(0,copies)}}
 @Test fun chapterCopyRequiresCurrentForegroundGenerationSourceRangeSessionAndRecovery(){for(flag in listOf("pause","generation","source","duration","range","mode","recovery","busy","music")){val a=activity();val p=get(a,"project")as VideoEditProject;val r=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));when(flag){"pause"->set(a,"workspaceActive",false);"generation"->set(a,"captionReviewGeneration",r.generation+1);"source"->set(a,"source",Uri.parse("content://synthetic/other"));"duration"->set(a,"primaryDurationMs",6000L);"range"->set(a,"selectedClip",1);"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"recovery"->set(a,"recoveryBytes",byteArrayOf(1));"busy"->set(a,"saving",true);else->p.setSoundtrack(p.soundtrack!!.copy(volume=.5f))};var copies=0;assertFalse(flag,copy(a,d,r){copies++});assertEquals(0,copies)}}
 @Test fun undoRestoringTitlesCannotReviveDismissedCopy(){val a=activity();val p=get(a,"project")as VideoEditProject;val r=capture(a);val d=dialog();val live=linkedSetOf(d);set(a,"captionReviewDialogs",live);val text=VideoChapters.text(p.segments);p.setTitle(0,"Changed");live.clear();assertTrue(p.undo());assertEquals(text,VideoChapters.text(p.segments));assertEquals(true,call(a,"canApplyCaptionReview",r));var copies=0;assertFalse(copy(a,d,r){copies++});assertEquals(0,copies);assertFalse(p.canUndo);assertTrue(p.canRedo)}
 @Test fun clipboardBoundaryFailureDoesNotChangeProject(){val a=activity();val p=get(a,"project")as VideoEditProject;val old=p.segments;val r=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));try{copy(a,d,r){error("Synthetic clipboard failure")};fail("Swallowed failure")}catch(e:java.lang.reflect.InvocationTargetException){assertEquals("Synthetic clipboard failure",e.cause?.message)};assertSame(old,p.segments);assertFalse(p.canUndo)}
 @Test fun pausedChapterEntryRetainsItsEarlyReturn(){val a=activity();set(a,"workspaceActive",false);call(a,"chapterList");assertNull(get(a,"captionReviewDialogs"));assertFalse((get(a,"project")as VideoEditProject).canUndo)}
}
