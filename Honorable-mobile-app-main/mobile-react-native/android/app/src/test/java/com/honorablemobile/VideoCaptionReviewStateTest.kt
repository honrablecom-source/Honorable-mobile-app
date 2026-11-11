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
class VideoCaptionReviewStateTest {
 private val unsafe=JvmTestAllocator
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun activity()=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{set(it,"project",VideoEditProject(5000));set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"importedSources",linkedMapOf<String,VideoSource>());set(it,"workspaceActive",true)}
 @Test fun pausedManualCaptionEntryReturnsBeforeAndroidUi(){val a=activity();set(a,"workspaceActive",false);val p=get(a,"project")as VideoEditProject;call(a,"editCaption",p,0,p.segments[0],null,{true})}
 @Test fun staleManualCaptionEntryReturnsBeforeAndroidUi(){val a=activity();val stale=VideoEditProject(5000);call(a,"editCaption",stale,0,stale.segments[0],null,{true})}
 @Test fun pausedCaptionMenuReturnsBeforeAndroidUi(){val a=activity();set(a,"workspaceActive",false);call(a,"captionOptions")}

 private fun capture(a:HonorableVideoEditorActivity)=call(a,"captureCaptionReview")as VideoCaptionReviewState
 private fun valid(a:HonorableVideoEditorActivity,s:VideoCaptionReviewState)=call(a,"canApplyCaptionReview",s)as Boolean
 private fun commit(a:HonorableVideoEditorActivity,s:VideoCaptionReviewState,p:VideoEditProject,clip:VideoSegment,cues:List<VideoCaption>)=call(a,"commitCaptionReview",s,p,0,clip,cues)as Boolean
 @Test fun freshActualSnapshotApprovesOnlyCurrentIdleForeground(){val a=activity();val s=capture(a);assertTrue(valid(a,s));set(a,"importing",true);assertFalse(valid(a,s));set(a,"importing",false);assertTrue(valid(a,s))}
 @Test fun pauseResumeInvalidatesOldSnapshotEvenWhenAllValuesReturn(){val a=activity();val old=capture(a);call(a,"invalidateCaptionReviews");set(a,"workspaceActive",false);assertFalse(valid(a,old));set(a,"workspaceActive",true);assertFalse(valid(a,old));assertTrue(valid(a,capture(a)))}
 @Test fun sameUriImportedDurationChangeInvalidatesActualReview(){val a=activity();val catalog=linkedMapOf("extra" to VideoSource("extra","content://synthetic/extra",6000));set(a,"importedSources",catalog);val s=capture(a);catalog["extra"]=VideoSource("extra","content://synthetic/extra",7000);assertFalse(valid(a,s))}
 @Test fun importedUriReplacementInvalidatesActualReview(){val a=activity();val catalog=linkedMapOf("extra" to VideoSource("extra","content://synthetic/extra",6000));set(a,"importedSources",catalog);val s=capture(a);catalog["extra"]=VideoSource("extra","content://synthetic/replaced",6000);assertFalse(valid(a,s))}
 @Test fun sourceModeSelectionProjectMusicRecoveryAndBusyChangesReject(){for(flag in listOf("source","mode","selected","project","model","music","recovery","saving","analyzing","draftBusy","duration")){val a=activity();val s=capture(a);val p=get(a,"project")as VideoEditProject;when(flag){"source"->set(a,"source",Uri.parse("content://synthetic/other"));"mode"->set(a,"accessMode",EditorAccessMode.ACCOUNT);"selected"->set(a,"selectedClip",1);"project"->set(a,"project",VideoEditProject(5000));"model"->p.split(0,2500);"music"->p.setSoundtrack(VideoSoundtrack("content://synthetic/music",1000));"recovery"->set(a,"recoveryBytes",byteArrayOf(1));"duration"->set(a,"primaryDurationMs",6000L);else->set(a,flag,true)};assertFalse(flag,valid(a,s))}}
 @Test fun exactClipIdentityRejectsEqualReconstructedStaleTargets(){val a=activity();val p=get(a,"project")as VideoEditProject;val clip=p.segments[0];val s=capture(a);assertFalse(commit(a,s,p,clip.copy(),listOf(VideoCaption(0,500,"New"))));assertFalse(p.canUndo)}
 @Test fun addEditDeleteAndClearKeepOneUndoAndDocumentProtection(){for(kind in listOf("add","edit","delete","clear")){val a=activity();val p=get(a,"project")as VideoEditProject;val old=VideoSegment(0,5000,captions=listOf(VideoCaption(0,500,"A"),VideoCaption(1000,1500,"B")),captionSourceUri="content://synthetic/captions");val music=VideoSoundtrack("content://synthetic/music",10000,loop=false);p.restore(listOf(old),soundtrack=music);val s=capture(a);val proposal=when(kind){"add"->old.captions+VideoCaption(2000,2500,"New");"edit"->old.captions.map{it.copy(text=it.text+" edited")};"delete"->old.captions.dropLast(1);else->emptyList()};assertTrue(kind,commit(a,s,p,p.segments[0],proposal));assertSame(music,p.soundtrack);assertEquals(proposal,p.segments[0].captions);assertTrue(p.captionSourceUris().contains("content://synthetic/captions"));assertTrue(p.undo());assertEquals(listOf(old),p.segments);assertFalse(p.canUndo);assertTrue(p.redo());assertEquals(proposal,p.segments[0].captions)}}
 @Test fun invalidCueProposalLeavesProjectAndHistoryUnchanged(){val a=activity();val p=get(a,"project")as VideoEditProject;val old=p.segments;val s=capture(a);try{commit(a,s,p,p.segments[0],listOf(VideoCaption(0,1500,"A"),VideoCaption(1000,2000,"Overlap")));fail("Accepted overlapping cues")}catch(e:java.lang.reflect.InvocationTargetException){assertTrue(e.cause is IllegalArgumentException)};assertSame(old,p.segments);assertFalse(p.canUndo)}
 @Test fun snapshotDefensivelyCopiesCatalogs(){val a=activity();val original=capture(a);val uris=original.sources.toMutableMap();val durations=original.durations.toMutableMap();val s=VideoCaptionReviewState(original.state,original.mode,original.namespace,original.generation,uris,durations);uris.clear();durations.clear();assertEquals(original.sources,s.sources);assertEquals(original.durations,s.durations);assertTrue(valid(a,s))}
 @Test fun repeatedInvalidationIsSafeWithoutInitializedAndroidDialogs(){val a=activity();val old=capture(a);set(a,"captionReviewDialogs",linkedSetOf<android.app.AlertDialog>());call(a,"invalidateCaptionReviews");call(a,"invalidateCaptionReviews");assertFalse(valid(a,old));assertEquals(2L,get(a,"captionReviewGeneration"));assertTrue((get(a,"captionReviewDialogs")as Set<*>).isEmpty())}
}
