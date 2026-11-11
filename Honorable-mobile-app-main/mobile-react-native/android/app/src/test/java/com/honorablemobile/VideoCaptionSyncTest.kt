package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.net.Uri
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.PhotoPresetNamespace
import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.*
import org.junit.Test

/** Actual model/Activity/controller metadata, without Android UI/provider construction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoCaptionSyncTest {
    private val cues=listOf(VideoCaption(1000,1200,"First"),VideoCaption(2000,2200,"Middle\nline"),VideoCaption(3000,3200,"Last"))
    private fun clip()=VideoSegment(1500,4000,speed=1.5f,title="Keep",captions=cues,captionSourceUri="content://synthetic/subtitles")
    private fun plan(clips:List<VideoSegment> = listOf(clip()),index:Int=0,first:Long=500,last:Long=3500,duration:Long=5000)=VideoCaptionSync.prepare(clips,index,duration,first,last)
    private fun rejected(action:()->Unit){try{action();fail("Must reject invalid proposal")}catch(_:IllegalArgumentException){}}
    @Test fun pureDelayUsesBothAnchorsWithoutChangingText(){val p=plan(first=1500);assertEquals(cues.map{it.copy(startMs=it.startMs+500,endMs=it.endMs+500)},p.after);assertEquals(3,p.changedCues)}
    @Test fun driftMapsEveryBoundaryAndExactlyMatchesAnchors(){val p=plan();assertEquals(listOf(500L,2000L,3500L),p.after.map{it.startMs});assertEquals(listOf(800L,2300L,3800L),p.after.map{it.endMs});assertEquals(clip().copy(captions=p.after),p.segments[0])}
    @Test fun sourceTimingIgnoresTrimAndSpeedAndKeepsOffTrimCues(){val p=plan();assertTrue(p.after.first().endMs<p.segments[0].startMs);assertEquals(cues.map{it.text},p.after.map{it.text});assertEquals(1.5f,p.segments[0].speed)}
    @Test fun onlySelectedClipChangesIncludingRepeatedSourceClips(){val clips=listOf(clip(),clip().copy(title="Other"));val p=plan(clips,1);assertEquals(clips[0],p.segments[0]);assertEquals(clips[1].captionSourceUri,p.segments[1].captionSourceUri);assertEquals(1,p.index)}
    @Test fun independentDecimalReferenceMatchesRationalRounding(){
        val original=(0 until 20).map{VideoCaption(100L+it*91,150L+it*91,"$it")};val c=VideoSegment(0,5000,captions=original)
        for(span in listOf(777L,1001L,1801L,2301L)){val p=plan(listOf(c),first=7,last=7+span);val denominator=original.last().startMs-original.first().startMs
            fun reference(at:Long)=7+BigDecimal.valueOf(at-original.first().startMs).multiply(BigDecimal.valueOf(span)).divide(BigDecimal.valueOf(denominator),0,RoundingMode.HALF_UP).longValueExact()
            for(i in original.indices){assertEquals(reference(original[i].startMs),p.after[i].startMs);assertEquals(reference(original[i].endMs),p.after[i].endMs)}
        }
    }
    @Test fun maximumTimestampMappingDoesNotOverflow(){val end=Int.MAX_VALUE.toLong();val list=listOf(VideoSegment(0,end,captions=listOf(VideoCaption(0,100,"A"),VideoCaption(end-100,end,"B"))));val p=plan(list,first=0,last=end-100,duration=end);assertFalse(p.changed);assertEquals(end,p.after.last().endMs)}
    @Test fun collapsedCuesRejectRatherThanDropOrInflate(){rejected{plan(first=0,last=1)}}
    @Test fun mappedTailOutsideSourceRejectsRatherThanClamps(){rejected{plan(first=1000,last=4950)}}
    @Test fun reversedEqualNegativeAndOutOfSourceAnchorsReject(){for(pair in listOf(500L to 500L,1000L to 500L,-1L to 3000L,1000L to 6000L))rejected{plan(first=pair.first,last=pair.second)}}
    @Test fun missingInsufficientUnorderedAndUnknownSourceBoundsReject(){rejected{plan(emptyList())};rejected{plan(listOf(clip().copy(captions=cues.take(1))))};rejected{plan(listOf(clip().copy(captions=cues.reversed())))};rejected{plan(duration=3000)}}
    @Test fun identityTimingHasNoChanges(){val p=plan(first=1000,last=3000);assertFalse(p.changed);assertEquals(0,p.changedCues)}
    @Test fun inputAndProposalAreDefensiveCopies(){val mutable=cues.toMutableList();val clips=mutableListOf(clip().copy(captions=mutable));val p=plan(clips);mutable.clear();clips.clear();assertEquals(cues,p.before);assertEquals(3,p.after.size);assertEquals(1,p.base.size)}
    @Test fun oneUndoKeepsMusicAndOriginalSubtitleProtection(){val p=VideoEditProject(5000);val music=VideoSoundtrack("content://synthetic/music",10000,loop=false);p.restore(listOf(clip()),soundtrack=music);val change=plan(p.segments);change.apply(p){};assertSame(music,p.soundtrack);assertEquals(setOf("content://synthetic/subtitles"),p.captionSourceUris());assertTrue(p.undo());assertEquals(listOf(clip()),p.segments);assertFalse(p.canUndo);assertTrue(p.redo());assertEquals(change.segments,p.segments)}
    @Test fun cancelledGuardAndStaleBaseNeverChangeProject(){val p=VideoEditProject(5000);p.restore(listOf(clip()));val change=plan(p.segments);var stopped=false;try{change.apply(p){error("Stopped")}}catch(_:IllegalStateException){stopped=true};assertTrue(stopped);assertFalse(p.canUndo);p.split(0,2500);val before=p.segments;rejected{change.apply(p){}};assertSame(before,p.segments)}
    private val unsafe=JvmTestAllocator
    private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
    private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
    private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
    private fun activity()=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{val p=VideoEditProject(5000);p.restore(listOf(clip()));set(it,"project",p);set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"workspaceActive",true)}
    private fun owner(a:HonorableVideoEditorActivity)=(unsafe.allocateInstance(VideoCaptionSyncControls::class.java)as VideoCaptionSyncControls).also{set(a,"captionSyncControls",it);set(a,"importing",true)}
    private fun commit(a:HonorableVideoEditorActivity,c:VideoCaptionSyncControls,snapshot:VideoCloseState,p:VideoCaptionSync)=call(a,"commitCaptionSync",c,snapshot,EditorAccessMode.GUEST_BASIC,PhotoPresetNamespace.GUEST,p)as Boolean
    @Test fun actualPausedBusyRecoveryEntryReturnsBeforeAndroidUi(){for(flag in listOf("workspaceActive","saving","draftBusy","importing","recoveryBlocked","recoveryChecking")){val a=activity();set(a,flag,flag!="workspaceActive");call(a,"requestCaptionSync");assertNull(get(a,"captionSyncControls"))}}
    @Test fun actualApprovedCommitIsUndoableAndExactOwnerReleaseOnly(){val a=activity();val p=get(a,"project")as VideoEditProject;val c=owner(a);val snapshot=call(a,"closeState")as VideoCloseState;assertTrue(commit(a,c,snapshot,plan(p.segments)));assertTrue(p.undo());assertEquals(listOf(clip()),p.segments);val newer=owner(a);call(a,"releaseCaptionSync",c);assertSame(newer,get(a,"captionSyncControls"));assertEquals(true,get(a,"importing"));call(a,"releaseCaptionSync",newer);assertNull(get(a,"captionSyncControls"));assertEquals(false,get(a,"importing"))}
    @Test fun actualStaleSourceSessionSelectionAndBusyStateCannotCommit(){for(flag in listOf("paused","source","mode","selected","project","model","music","saving","recovery")){val a=activity();val p=get(a,"project")as VideoEditProject;val c=owner(a);val snapshot=call(a,"closeState")as VideoCloseState;val change=plan(p.segments);when(flag){"paused"->set(a,"workspaceActive",false);"source"->set(a,"source",Uri.parse("content://synthetic/other"));"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"selected"->set(a,"selectedClip",1);"project"->set(a,"project",VideoEditProject(5000));"model"->p.split(0,2500);"music"->p.setSoundtrack(VideoSoundtrack("content://synthetic/music",1000));"saving"->set(a,"saving",true);else->set(a,"recoveryBytes",byteArrayOf(1))};val before=p.segments;assertFalse(flag,commit(a,c,snapshot,change));assertSame(before,p.segments)}}
    @Test fun latePickerResultsCannotReleaseSyncReviewBusyState(){val a=activity();val c=owner(a);for(code in 22..29)assertEquals(false,call(a,"allowsMediaImportResult",code));assertEquals(true,call(a,"allowsMediaImportResult",99));assertSame(c,get(a,"captionSyncControls"));assertEquals(true,get(a,"importing"))}
    @Test fun closedAndReplacedControllerTokensCannotOwnReview(){var releases=0;val c=unsafe.allocateInstance(VideoCaptionSyncControls::class.java)as VideoCaptionSyncControls;set(c,"release",{releases++});val old=Any();set(c,"token",old);assertEquals(true,call(c,"owns",old));set(c,"token",Any());assertEquals(false,call(c,"owns",old));c.close();c.close();assertEquals(1,releases);assertEquals(false,call(c,"owns",old))}
    @Test fun invalidControllerOpenReturnsBeforeAnyAndroidUi(){var releases=0;val c=unsafe.allocateInstance(VideoCaptionSyncControls::class.java)as VideoCaptionSyncControls;set(c,"isCurrent",{false});set(c,"release",{releases++});c.open();assertEquals(1,releases);assertEquals(true,get(c,"closed"))}
}
