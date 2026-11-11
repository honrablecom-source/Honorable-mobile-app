package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.net.Uri
import app.honorable.editor.EditorAccessMode
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoCaptionHighlightsTest {
 private val cues=listOf(VideoCaption(500,1000,"Keep first"),VideoCaption(1000,1500,"Keep next"),VideoCaption(2500,3000,"Other"),VideoCaption(4000,4800,"Keep last"))
 private fun clip()=VideoSegment(0,5000,speed=2f,title="Appearance stays",volume=.5f,captions=cues,captionSourceUri="content://synthetic/subtitles")
 private fun plan(clips:List<VideoSegment> = listOf(clip()),vararg selected:Pair<Int,Int>)=VideoCaptionHighlights.prepare(clips,selected.map{VideoCaptionHighlights.Selection(it.first,it.second)})
 private fun rejected(action:()->Unit){try{action();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
 @Test fun selectedCaptionWindowsReplaceWholeTimelineInEditedOrder(){val p=plan(listOf(clip(),clip().copy(title="Second")),1 to 3,0 to 0);assertEquals(listOf(0,1),p.excerpts.map{it.clipIndex});assertEquals(listOf(500L,4000L),p.segments.map{it.startMs});assertEquals(2,p.selectedCues);assertEquals(650000L,p.afterUs)}
 @Test fun touchingWindowsMergeOnlyWithinSameTimelineClip(){val p=plan(listOf(clip()),0 to 1,0 to 0);assertEquals(1,p.segments.size);assertEquals(500L,p.segments[0].startMs);assertEquals(1500L,p.segments[0].endMs);assertEquals(cues.take(2),p.segments[0].captions)}
 @Test fun repeatedSourceClipsRemainSeparateEvenForEqualSourceWindows(){val p=plan(listOf(clip(),clip()),0 to 0,1 to 0);assertEquals(2,p.segments.size);assertEquals(listOf(0,1),p.excerpts.map{it.clipIndex});assertEquals(500000L,p.afterUs)}
 @Test fun selectionIntersectsStoredTrimsAndKeepsOriginalCaptionTimes(){val c=clip().copy(startMs=750,endMs=4500);val p=plan(listOf(c),0 to 0,0 to 3);assertEquals(listOf(750L,4000L),p.segments.map{it.startMs});assertEquals(4500L,p.segments.last().endMs);assertEquals(cues[0],p.segments.first().captions.single());assertEquals(cues[3],p.segments.last().captions.single())}
 @Test fun unselectedGapsAndCuesAreNotIncluded(){val p=plan(listOf(clip()),0 to 0,0 to 3);assertEquals(listOf(cues[0],cues[3]),p.segments.flatMap{it.captions});assertEquals(2,p.segments.size)}
 @Test fun everyRetainedClipSettingAndSubtitleProvenanceStays(){val c=clip();val p=plan(listOf(c),0 to 0);assertEquals(c.copy(startMs=500,endMs=1000,captions=listOf(cues[0])),p.segments.single());assertEquals(c.captionSourceUri,p.segments.single().captionSourceUri)}
 @Test fun noEmptyDuplicateOrUnknownSelectionIsAccepted(){rejected{plan(listOf(clip()))};rejected{plan(listOf(clip()),0 to 0,0 to 0)};rejected{plan(listOf(clip()),-1 to 0)};rejected{plan(listOf(clip()),0 to -1)};rejected{plan(listOf(clip()),1 to 0)};rejected{plan(listOf(clip()),0 to 99)}}
 @Test fun offTrimCaptionCannotBecomeAHighlight(){rejected{plan(listOf(clip().copy(startMs=1600)),0 to 0)}}
 @Test fun allVisibleMatchingCuesAreExplicitSearchResultsIncludingRepeatedClips(){val clips=listOf(clip(),clip().copy(startMs=1600));val hits=VideoCaptionSearch.find(clips,"keep");assertEquals(listOf(0 to 0,0 to 1,0 to 3,1 to 3),hits.map{it.clipIndex to it.cueIndex});val selected=hits.takeLast(1).map{VideoCaptionHighlights.Selection(it.clipIndex,it.cueIndex)};assertEquals(1,VideoCaptionHighlights.prepare(clips,selected).segments.size)}
 @Test fun exactMaximumSelectionsWorkAndMoreThan200Reject(){val all=(0 until 200).map{VideoCaption(it*20L,it*20L+10,"Caption")};val c=VideoSegment(0,5000,captions=all);val selections=all.indices.map{VideoCaptionHighlights.Selection(0,it)};assertEquals(200,VideoCaptionHighlights.prepare(listOf(c),selections).segments.size);rejected{VideoCaptionHighlights.prepare(listOf(c,c),selections+VideoCaptionHighlights.Selection(1,0))}}
 @Test fun wholeCaptionCoverageCanBeANoOp(){val c=VideoSegment(0,1000,captions=listOf(VideoCaption(0,1000,"All")));assertFalse(plan(listOf(c),0 to 0).changed)}
 @Test fun inputCollectionsAreDefensivelyCopied(){val mutable=cues.toMutableList();val clips=mutableListOf(clip().copy(captions=mutable));val p=plan(clips,0 to 0);mutable.clear();clips.clear();assertEquals(cues,p.base.single().captions);assertEquals(cues[0],p.segments.single().captions.single());assertEquals(1,p.excerpts.size)}
 @Test fun oneUndoRedoKeepsMusicAndAllOriginalSubtitleHistory(){val p=VideoEditProject(5000);val music=VideoSoundtrack("content://synthetic/music",10000,loop=false);p.restore(listOf(clip()),soundtrack=music);val cut=VideoCaptionHighlights.prepare(p.segments,listOf(VideoCaptionHighlights.Selection(0,0)),music);cut.apply(p){};assertSame(music,p.soundtrack);assertTrue(p.captionSourceUris().contains("content://synthetic/subtitles"));assertTrue(p.undo());assertEquals(listOf(clip()),p.segments);assertFalse(p.canUndo);assertTrue(p.redo());assertEquals(cut.segments,p.segments)}
 @Test fun changedBaseAndCancelledApprovalNeverApply(){val p=VideoEditProject(5000);p.restore(listOf(clip()));val cut=plan(p.segments,0 to 0);try{cut.apply(p){error("Stopped")};fail("Ignored guard")}catch(_:IllegalStateException){};assertFalse(p.canUndo);p.split(0,2000);val before=p.segments;rejected{cut.apply(p){}};assertSame(before,p.segments)}
 private val unsafe=JvmTestAllocator
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun activity()=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{val p=VideoEditProject(5000);p.restore(listOf(clip()));set(it,"project",p);set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"workspaceActive",true);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"importedSources",linkedMapOf<String,VideoSource>())}
 private fun snapshot(a:HonorableVideoEditorActivity)=call(a,"captureCaptionReview")as VideoCaptionReviewState
 private fun commit(a:HonorableVideoEditorActivity,s:VideoCaptionReviewState,p:VideoCaptionHighlights)=call(a,"commitCaptionHighlights",s,p)as Boolean
 @Test fun pausedBusyRecoveryEntryReturnsBeforeAndroidUi(){for(flag in listOf("workspaceActive","saving","importing","draftBusy","recoveryBlocked")){val a=activity();set(a,flag,flag!="workspaceActive");call(a,"requestCaptionHighlights")}}
 @Test fun actualApprovedCommitIsOneUndoAndSecondApplicationCannotReplay(){val a=activity();val p=get(a,"project")as VideoEditProject;val s=snapshot(a);val cut=plan(p.segments,0 to 0);assertTrue(commit(a,s,cut));assertFalse(commit(a,s,cut));assertTrue(p.undo());assertEquals(listOf(clip()),p.segments);assertFalse(p.canUndo)}
 @Test fun actualPauseGenerationAndChangedSessionRejectApproval(){for(flag in listOf("generation","mode","source","project","busy")){val a=activity();val p=get(a,"project")as VideoEditProject;val s=snapshot(a);val cut=plan(p.segments,0 to 0);when(flag){"generation"->call(a,"invalidateCaptionReviews");"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"source"->set(a,"source",Uri.parse("content://synthetic/other"));"project"->set(a,"project",VideoEditProject(5000));else->set(a,"saving",true)};val before=p.segments;assertFalse(flag,commit(a,s,cut));assertSame(before,p.segments)}}
 @Test fun dismissedHighlightDialogCannotOwnCurrentReview(){val a=activity();val s=snapshot(a);val dialog=unsafe.allocateInstance(android.app.AlertDialog::class.java)as android.app.AlertDialog;android.app.Dialog::class.java.getDeclaredField("mDecor").apply{isAccessible=true}.set(dialog,unsafe.allocateInstance(android.view.View::class.java));assertEquals(false,call(a,"activeCaptionHighlightDialog",dialog,s));val set=linkedSetOf(dialog);set(a,"captionReviewDialogs",set);assertEquals(true,call(a,"activeCaptionHighlightDialog",dialog,s));set.clear();assertEquals(false,call(a,"activeCaptionHighlightDialog",dialog,s))}
}
