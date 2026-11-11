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
class VideoClipBlockMoveTest {
 private fun clips(n:Int=5)=(0 until n).map{VideoSegment(it*1000L,(it+1)*1000L,title="Clip $it",captions=listOf(VideoCaption(it*1000L,it*1000L+500,"Caption $it")),captionSourceUri="content://synthetic/subtitles")}
 private fun plan(selected:List<Int>,before:Int,clips:List<VideoSegment> = clips())=VideoClipBlockMove.prepare(clips,selected,before)
 private fun rejected(action:()->Unit){try{action();fail("Expected invalid proposal rejection")}catch(_:IllegalArgumentException){}}
 @Test fun contiguousGroupMovesToEndAsOneBlock(){val p=plan(listOf(1,2),5);assertEquals(listOf(0,3,4,1,2),p.order);assertEquals(3,p.insertionIndex)}
 @Test fun noncontiguousCheckboxOrderDoesNotReorderSelectedOrOtherClips(){val p=plan(listOf(3,1),5);assertEquals(listOf(1,3),p.selected);assertEquals(listOf(0,2,4,1,3),p.order)}
 @Test fun groupCanMoveBeforeFirstUnselectedClip(){assertEquals(listOf(2,4,0,1,3),plan(listOf(4,2),0).order)}
 @Test fun destinationIsAnOriginalBoundaryNotAnAfterRemovalIndex(){val p=plan(listOf(0,2),4);assertEquals(listOf(1,3,0,2,4),p.order);assertEquals(2,p.insertionIndex)}
 @Test fun everyClipFieldAndCaptionSourceReferenceIsPreserved(){val original=clips();val p=plan(listOf(1,3),5,original);assertEquals(original.map{it.title}.toSet(),p.segments.map{it.title}.toSet());for(i in p.order.indices)assertEquals(original[p.order[i]],p.segments[i])}
 @Test fun durationAndSpeedsStayExact(){val original=clips().mapIndexed{i,c->c.copy(speed=listOf(.5f,1f,1.5f,2f,1f)[i])};val p=plan(listOf(0,2),5,original);assertEquals(VideoPlaybackTime.durationUs(original),p.durationUs);assertEquals(original.map{it.speed}.sorted(),p.segments.map{it.speed}.sorted())}
 @Test fun adjacentDestinationCanBeANoOp(){val p=plan(listOf(1,2),3);assertFalse(p.changed);assertEquals(clips(),p.segments)}
 @Test fun ReorderingValueIdenticalClipsDoesNotCreateFalseVisibleChange(){val same=List(3){VideoSegment(0,1000)};assertFalse(plan(listOf(0),3,same).changed)}
 @Test fun wholeSelectionAtEndKeepsOriginalOrder(){val p=plan(listOf(4,2,1,0,3),5);assertFalse(p.changed);assertEquals((0..4).toList(),p.order)}
 @Test fun invalidSelectionsRejectBeforeProposal(){for(selected in listOf(emptyList(),listOf(0,0),listOf(-1),listOf(5)))rejected{plan(selected,5)};rejected{plan((0..200).toList(),202,clips(202))}}
 @Test fun invalidOrSelectedDestinationsReject(){for(before in listOf(-1,6,1))rejected{plan(listOf(1,3),before)}}
 @Test fun maximumClipAndSelectionBoundsRemainSupported(){val p=plan((0 until 200).toList(),1000,clips(1000));assertEquals(1000,p.segments.size);assertEquals(800,p.insertionIndex);assertEquals((0 until 200).toList(),p.order.takeLast(200));rejected{plan(listOf(0),1001,clips(1001))}}
 @Test fun exhaustiveSmallGroupsAreStablePermutationsAtTheRequestedBoundary(){
  for(n in 2..7){val original=clips(n);for(mask in 1 until(1 shl n)){val selected=(0 until n).filter{mask and(1 shl it)!=0};for(before in (0..n).filter{it==n||it !in selected}){val p=plan(selected.reversed(),before,original);assertEquals((0 until n).toSet(),p.order.toSet());assertEquals(n,p.order.size);assertEquals(selected,p.order.filter{it in selected});assertEquals((0 until n).filter{it !in selected},p.order.filter{it !in selected});assertEquals(selected,p.order.subList(p.insertionIndex,p.insertionIndex+selected.size));if(before<n)assertEquals(before,p.order[p.insertionIndex+selected.size]);else assertEquals(n,p.insertionIndex+selected.size)}}}
 }
 @Test fun callerCollectionsCannotChangePreparedMove(){val original=clips().toMutableList();val selected=mutableListOf(1,3);val p=plan(selected,5,original);original.clear();selected.clear();assertEquals(5,p.base.size);assertEquals(listOf(1,3),p.selected);assertEquals(5,p.segments.size)}
 @Test fun oneUndoRedoKeepsMusicAndOriginalDocumentProtection(){val p=VideoEditProject(5000);val original=clips();val music=VideoSoundtrack("content://synthetic/music",10000,loop=false);p.restore(original,soundtrack=music);val move=plan(listOf(0,2),5,p.segments);move.apply(p){};assertSame(music,p.soundtrack);assertTrue(p.captionSourceUris().contains("content://synthetic/subtitles"));assertTrue(p.undo());assertEquals(original,p.segments);assertFalse(p.canUndo);assertTrue(p.redo());assertEquals(move.segments,p.segments)}
 @Test fun cancelledOrChangedBaseNeverMutatesTimeline(){val p=VideoEditProject(5000);p.restore(clips());val move=plan(listOf(0),5,p.segments);try{move.apply(p){error("Stopped")};fail("Ignored guard")}catch(_:IllegalStateException){};assertFalse(p.canUndo);p.setVolume(0,0f);val before=p.segments;rejected{move.apply(p){}};assertSame(before,p.segments)}
 private val unsafe=JvmTestAllocator
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun activity()=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{val p=VideoEditProject(5000);p.restore(clips());set(it,"project",p);set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"workspaceActive",true);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"importedSources",linkedMapOf<String,VideoSource>())}
 private fun snapshot(a:HonorableVideoEditorActivity)=call(a,"captureCaptionReview")as VideoCaptionReviewState
 private fun commit(a:HonorableVideoEditorActivity,s:VideoCaptionReviewState,p:VideoClipBlockMove)=call(a,"commitClipBlockMove",s,p)as Boolean
 @Test fun pausedBusyRecoveryEntryReturnsBeforeAndroidUi(){for(flag in listOf("workspaceActive","saving","importing","draftBusy","recoveryBlocked")){val a=activity();set(a,flag,flag!="workspaceActive");call(a,"requestClipBlockMove")}}
 @Test fun actualApprovedCommitIsUndoableAndCannotReplay(){val a=activity();val p=get(a,"project")as VideoEditProject;val s=snapshot(a);val move=plan(listOf(0,2),5,p.segments);assertTrue(commit(a,s,move));assertFalse(commit(a,s,move));assertTrue(p.undo());assertEquals(clips(),p.segments);assertFalse(p.canUndo)}
 @Test fun actualGenerationSessionSourceAndBusyChangesRefuseCommit(){for(flag in listOf("generation","mode","source","project","busy")){val a=activity();val p=get(a,"project")as VideoEditProject;val s=snapshot(a);val move=plan(listOf(0),5,p.segments);when(flag){"generation"->call(a,"invalidateCaptionReviews");"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"source"->set(a,"source",Uri.parse("content://synthetic/other"));"project"->set(a,"project",VideoEditProject(5000));else->set(a,"saving",true)};val before=p.segments;assertFalse(flag,commit(a,s,move));assertSame(before,p.segments)}}
 @Test fun actualNoOpKeepsExistingHistoryUntouched(){val a=activity();val p=get(a,"project")as VideoEditProject;val s=snapshot(a);assertFalse(commit(a,s,plan(listOf(1,2),3,p.segments)));assertFalse(p.canUndo);assertFalse(p.canRedo)}
}
