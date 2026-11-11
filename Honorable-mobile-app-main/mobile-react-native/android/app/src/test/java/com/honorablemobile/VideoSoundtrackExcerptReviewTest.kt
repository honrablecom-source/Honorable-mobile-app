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
class VideoSoundtrackExcerptReviewTest {
 private val unsafe=JvmTestAllocator
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun activity()=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{val p=VideoEditProject(5000);p.restore(listOf(VideoSegment(0,5000,captions=listOf(VideoCaption(0,500,"A"),VideoCaption(1000,1500,"B")),captionSourceUri="content://synthetic/subtitles")),soundtrack=VideoSoundtrack("content://synthetic/music",10000,loop=false));set(it,"project",p);set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"workspaceActive",true);set(it,"importedSources",linkedMapOf<String,VideoSource>())}
 private fun showing(d:AlertDialog,value:Boolean){android.app.Dialog::class.java.getDeclaredField("mDecor").apply{isAccessible=true}.set(d,if(value)unsafe.allocateInstance(android.view.View::class.java)else null)}
 private fun dialog()=(unsafe.allocateInstance(AlertDialog::class.java)as AlertDialog).also{showing(it,true)}
 private fun capture(a:HonorableVideoEditorActivity)=call(a,"captureCaptionReview")as VideoCaptionReviewState
 private fun pair(a:HonorableVideoEditorActivity):Pair<AlertDialog,AlertDialog>{val editor=dialog();val confirmation=dialog();set(a,"soundtrackExcerptEditor",editor);set(a,"soundtrackExcerptConfirmation",confirmation);set(a,"captionReviewDialogs",linkedSetOf(editor,confirmation));return editor to confirmation}
 private fun apply(a:HonorableVideoEditorActivity,pair:Pair<AlertDialog,AlertDialog>,review:VideoCaptionReviewState,track:VideoSoundtrack,next:VideoSoundtrack)=call(a,"commitSoundtrackExcerpt",pair.first,pair.second,review,get(a,"project"),track,next)as Boolean
 private fun next(track:VideoSoundtrack)=track.copy(startMs=1000,endMs=4000,loop=true)
 @Test fun exactVisiblePairAppliesOneUndoAndPreservesOriginalsMixAndCaptions(){val a=activity();val p=get(a,"project")as VideoEditProject;val track=requireNotNull(p.soundtrack);val clips=p.segments;val pair=pair(a);val review=capture(a);val n=next(track);assertTrue(apply(a,pair,review,track,n));assertEquals(n,p.soundtrack);assertSame(clips,p.segments);assertEquals(track.uri,p.soundtrack?.uri);assertEquals(track.volume,p.soundtrack!!.volume);assertTrue(p.captionSourceUris().contains("content://synthetic/subtitles"));assertTrue(p.undo());assertEquals(track,p.soundtrack);assertFalse(p.canUndo);assertTrue(p.redo());assertEquals(n,p.soundtrack)}
 @Test fun eitherUntrackedDialogRejectsWithoutUndo(){for(which in listOf(0,1)){val a=activity();val p=get(a,"project")as VideoEditProject;val t=requireNotNull(p.soundtrack);val pair=pair(a);val review=capture(a);set(a,"captionReviewDialogs",linkedSetOf(if(which==0)pair.second else pair.first));assertFalse(apply(a,pair,review,t,next(t)));assertEquals(t,p.soundtrack);assertFalse(p.canUndo)}}
 @Test fun eitherReplacedOwnerRejectsEvenIfOldPairStillVisibleAndTracked(){for(field in listOf("soundtrackExcerptEditor","soundtrackExcerptConfirmation")){val a=activity();val p=get(a,"project")as VideoEditProject;val t=requireNotNull(p.soundtrack);val pair=pair(a);val review=capture(a);set(a,field,dialog());assertFalse(apply(a,pair,review,t,next(t)));assertFalse(p.canUndo)}}
 @Test fun eitherDismissedDecorRejectsBeforeMembershipRemoval(){for(which in listOf(0,1)){val a=activity();val p=get(a,"project")as VideoEditProject;val t=requireNotNull(p.soundtrack);val pair=pair(a);val review=capture(a);showing(if(which==0)pair.first else pair.second,false);assertFalse(apply(a,pair,review,t,next(t)));assertFalse(p.canUndo)}}
 @Test fun staleForegroundGenerationSessionSourceRangeMusicOrBusyRejects(){for(flag in listOf("paused","generation","mode","source","range","music","busy","recovery")){val a=activity();val p=get(a,"project")as VideoEditProject;val t=requireNotNull(p.soundtrack);val pair=pair(a);val review=capture(a);when(flag){"paused"->set(a,"workspaceActive",false);"generation"->set(a,"captionReviewGeneration",review.generation+1);"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"source"->set(a,"source",Uri.parse("content://synthetic/other"));"range"->set(a,"selectedClip",1);"music"->p.setSoundtrack(t.copy(volume=.5f));"busy"->set(a,"saving",true);else->set(a,"recoveryBytes",byteArrayOf(1))};val before=p.soundtrack;assertFalse(flag,apply(a,pair,review,t,next(t)));assertEquals(before,p.soundtrack)}}
 @Test fun cancelledPairCannotReviveAfterUndoRestoresValues(){val a=activity();val p=get(a,"project")as VideoEditProject;val t=requireNotNull(p.soundtrack);val pair=pair(a);val review=capture(a);assertTrue(apply(a,pair,review,t,next(t)));set(a,"soundtrackExcerptConfirmation",null);assertTrue(p.undo());assertEquals(true,call(a,"canApplyCaptionReview",review));assertFalse(apply(a,pair,review,t,next(t)));assertFalse(p.canUndo);assertTrue(p.canRedo)}
 @Test fun sourceDurationAndVolumeCannotBeSmuggledThroughRangeApproval(){for(flag in listOf("uri","duration","volume")){val a=activity();val p=get(a,"project")as VideoEditProject;val t=requireNotNull(p.soundtrack);val pair=pair(a);val review=capture(a);val n=when(flag){"uri"->next(t).copy(uri="content://synthetic/foreign");"duration"->next(t).copy(durationMs=20000);else->next(t).copy(volume=.5f)};try{apply(a,pair,review,t,n);fail("Accepted $flag")}catch(e:java.lang.reflect.InvocationTargetException){assertTrue(e.cause is IllegalArgumentException)};assertEquals(t,p.soundtrack);assertFalse(p.canUndo)}}
 @Test fun identicalOrOverBudgetLoopCannotMutateHistory(){for(over in listOf(false,true)){val a=activity();val p=get(a,"project")as VideoEditProject;val t=requireNotNull(p.soundtrack);val pair=pair(a);val review=capture(a);val n=if(over)t.copy(startMs=0,endMs=1,loop=true)else t;try{apply(a,pair,review,t,n);fail("Accepted invalid plan")}catch(e:java.lang.reflect.InvocationTargetException){assertTrue(e.cause is IllegalArgumentException)};assertEquals(t,p.soundtrack);assertFalse(p.canUndo)}}
}
