package com.honorablemobile

import android.app.AlertDialog
import android.net.Uri
import app.honorable.editor.EditorAccessMode
import org.junit.Assert.*
import org.junit.Test

/** Real visible-approval/project mutation gates, with metadata-only dialog membership. */
class VideoCaptionClipboardReviewTest {
 private val unsafe=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)as sun.misc.Unsafe
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun activity()=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{val p=VideoEditProject(5000);p.restore(listOf(VideoSegment(0,5000,captions=listOf(VideoCaption(0,500,"A"),VideoCaption(1000,1500,"B")),captionSourceUri="content://synthetic/subtitles")),soundtrack=VideoSoundtrack("content://synthetic/music",10000,loop=false));set(it,"project",p);set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"workspaceActive",true);set(it,"importedSources",linkedMapOf<String,VideoSource>())}
 private fun showing(d:AlertDialog,value:Boolean){android.app.Dialog::class.java.getDeclaredField("mDecor").apply{isAccessible=true}.set(d,if(value)unsafe.allocateInstance(android.view.View::class.java)else null)}
 private fun dialog()=(unsafe.allocateInstance(AlertDialog::class.java)as AlertDialog).also{showing(it,true)}
 private fun capture(a:HonorableVideoEditorActivity)=call(a,"captureCaptionReview")as VideoCaptionReviewState
 private fun copy(a:HonorableVideoEditorActivity,d:AlertDialog,s:VideoCaptionReviewState,action:()->Unit)=call(a,"performCaptionClipboardCopy",d,s,action)as Boolean
 @Test fun pausedReadabilityEntryReturnsBeforeAnyWidgetOrClipboard(){val a=activity();set(a,"workspaceActive",false);call(a,"captionReadability");assertNull(get(a,"captionReviewDialogs"));assertFalse((get(a,"project")as VideoEditProject).canUndo)}
 @Test fun pausedCaptionTextEntryReturnsBeforeAnyWidgetOrClipboard(){val a=activity();set(a,"workspaceActive",false);call(a,"copyCaptionText");assertNull(get(a,"captionReviewDialogs"))}
 @Test fun visibleCurrentReviewCopiesExactlyOnceWithoutProjectChange(){val a=activity();val p=get(a,"project")as VideoEditProject;val old=p.segments;val music=p.soundtrack;val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));var copies=0;assertTrue(copy(a,d,s){copies++});assertEquals(1,copies);assertSame(old,p.segments);assertSame(music,p.soundtrack);assertFalse(p.canUndo)}
 @Test fun removedOrReplacedDialogCannotPublishPrivateText(){for(replaced in listOf(false,true)){val a=activity();val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",if(replaced)linkedSetOf(dialog())else linkedSetOf<AlertDialog>());var copies=0;assertFalse(copy(a,d,s){copies++});assertEquals(0,copies)}}
 @Test fun dismissedDecorRejectsBeforeMembershipListener(){val a=activity();val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));showing(d,false);var copies=0;assertFalse(copy(a,d,s){copies++});assertEquals(0,copies)}
 @Test fun staleSessionSourceRangeGenerationRecoveryOrBusyRejects(){for(flag in listOf("pause","mode","source","range","generation","recovery","busy","duration","catalog")){val a=activity();val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));when(flag){"pause"->set(a,"workspaceActive",false);"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"source"->set(a,"source",Uri.parse("content://synthetic/other"));"range"->set(a,"selectedClip",1);"generation"->set(a,"captionReviewGeneration",s.generation+1);"recovery"->set(a,"recoveryBytes",byteArrayOf(1));"busy"->set(a,"saving",true);"duration"->set(a,"primaryDurationMs",6000L);else->set(a,"importedSources",linkedMapOf("extra" to VideoSource("extra","content://synthetic/extra",6000)))};var copies=0;assertFalse(flag,copy(a,d,s){copies++});assertEquals(flag,0,copies)}}
 @Test fun returningToSameProjectValuesCannotReviveRemovedReview(){val a=activity();val p=get(a,"project")as VideoEditProject;val s=capture(a);val d=dialog();val live=linkedSetOf(d);set(a,"captionReviewDialogs",live);p.setCaptions(0,emptyList());assertTrue(p.undo());live.clear();assertEquals(true,call(a,"canApplyCaptionReview",s));var copies=0;assertFalse(copy(a,d,s){copies++});assertEquals(0,copies);assertTrue(p.canRedo)}
 @Test fun clipboardFailurePropagatesWithoutChangingProject(){val a=activity();val p=get(a,"project")as VideoEditProject;val old=p.segments;val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));try{copy(a,d,s){error("Synthetic clipboard failure")};fail("Swallowed failure")}catch(e:java.lang.reflect.InvocationTargetException){assertEquals("Synthetic clipboard failure",e.cause?.message)};assertSame(old,p.segments);assertFalse(p.canUndo)}
 @Test fun capturedCueTextAndReadabilityRemainUnchanged(){val a=activity();val p=get(a,"project")as VideoEditProject;val clips=p.segments;val text=VideoCaptionText.create(clips).text;val report=VideoCaptionReadability.inspect(clips).text();val s=capture(a);val d=dialog();set(a,"captionReviewDialogs",linkedSetOf(d));val copied=mutableListOf<String>();assertTrue(copy(a,d,s){copied.add(text)});assertTrue(copy(a,d,s){copied.add(report)});assertEquals(listOf(text,report),copied);assertSame(clips,p.segments);assertFalse(p.canUndo)}
}
