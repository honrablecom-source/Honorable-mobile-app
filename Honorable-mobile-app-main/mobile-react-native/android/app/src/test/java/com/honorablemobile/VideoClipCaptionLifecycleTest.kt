package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*
import org.junit.Test
import android.net.Uri
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoClipCaptionLifecycleTest {
 private val unsafe=JvmTestAllocator
 private fun activity()=unsafe.allocateInstance(HonorableVideoEditorActivity::class.java) as HonorableVideoEditorActivity
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun call(a:Any,n:String,vararg v:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==v.size}.apply{isAccessible=true}.invoke(a,*v)
 @Test fun pausedOriginalClipImportReturnsBeforeAndroidUi(){val a=activity();val p=VideoEditProject(5000);set(a,"project",p);call(a,"importCaptions",p,0,p.segments[0])}
 @Test fun staleOriginalClipImportReturnsBeforeAndroidUi(){val a=activity();val p=VideoEditProject(5000);set(a,"workspaceActive",true);set(a,"project",p);set(a,"source",Uri.parse("content://synthetic/video"));val stale=VideoEditProject(5000);call(a,"importCaptions",stale,0,stale.segments[0])}

 private val doc="content://synthetic/subtitles"
 private fun srt(count:Int=1,text:String="Caption")=(1..count).joinToString("\n\n"){i->"$i\n${VideoCaptions.formatTime((i-1)*1000L)} --> ${VideoCaptions.formatTime(i*1000L)}\n$text"}.toByteArray()
 private fun prepare(clips:List<VideoSegment>,index:Int=0,bytes:ByteArray=srt(),duration:Long=5000)=VideoTimelineCaptionImport.prepareSourceClip(clips,VideoTimelineCaptionImport.SourceTarget(index,duration),bytes,doc)
 private fun rejected(action:()->Unit){try{action();fail("Must reject invalid proposal")}catch(_:IllegalArgumentException){}}
 @Test fun sourceCuesOutsideTrimStayInOriginalTimeAndOnlySelectedClipChanges(){
  val clips=listOf(VideoSegment(2000,4000,speed=2f,title="Keep"),VideoSegment(0,5000,captions=listOf(VideoCaption(0,500,"Other")),captionSourceUri="content://synthetic/other"))
  val plan=prepare(clips);assertEquals(0,plan.sourceClip);assertEquals(listOf(VideoCaption(0,1000,"Caption")),plan.segments[0].captions);assertEquals(clips[0].copy(captions=plan.segments[0].captions,captionSourceUri=doc),plan.segments[0]);assertEquals(clips[1],plan.segments[1]);assertEquals(1,plan.changedClips)
 }
 @Test fun sourceProposalUsesOneUndoKeepsMusicAndProtectsOldDocumentHistory(){
  val p=VideoEditProject(5000);val old=VideoSegment(1000,4000,captions=listOf(VideoCaption(1000,1500,"Old")),captionSourceUri="content://synthetic/old");val music=VideoSoundtrack("content://synthetic/music",10000,loop=false)
  p.restore(listOf(old),soundtrack=music);val plan=prepare(p.segments);plan.apply(p){};assertSame(music,p.soundtrack);assertTrue(p.captionSourceUris().containsAll(listOf(doc,"content://synthetic/old")));assertTrue(p.undo());assertEquals(listOf(old),p.segments);assertFalse(p.canUndo);assertTrue(p.redo());assertEquals(plan.segments,p.segments)
 }
 @Test fun perClipSourceLimitRemains200NotPlayback1000(){val clips=listOf(VideoSegment(0,201000));assertEquals(200,prepare(clips,bytes=srt(200),duration=201000).inputCues);rejected{prepare(clips,bytes=srt(201),duration=201000)}}
 @Test fun unchangedSourceCaptionsAreNoOpAndStaleBaseCannotApply(){val p=VideoEditProject(5000);val plan=prepare(p.segments);plan.apply(p){};val same=prepare(p.segments);assertFalse(same.changed);p.split(0,2500);val before=p.segments;rejected{same.apply(p){}};assertSame(before,p.segments)}
 @Test fun projectCueAndTextLimitsIncludeUntouchedClips(){
  val cues=(0 until 200).map{VideoCaption(it*10L,it*10L+5,"a")};val populated=VideoSegment(0,5000,captions=cues)
  rejected{prepare(List(5){populated}+VideoSegment(0,5000),5)}
  val heavy=populated.copy(captions=cues.map{it.copy(text="x".repeat(240))});rejected{prepare(listOf(heavy,VideoSegment(0,5000)),1,srt(100,"y".repeat(240)))}
 }
 @Test fun subtitleDocumentBudgetIncludesOtherClipsAndSourceBoundsRejectEarly(){
  val clips=(0 until 32).map{VideoSegment(0,5000,captionSourceUri="content://synthetic/doc$it")}+VideoSegment(0,5000)
  rejected{prepare(clips,32)};rejected{prepare(listOf(VideoSegment(0,5000)),1)};rejected{prepare(listOf(VideoSegment(0,5000)),duration=4000)};rejected{VideoTimelineCaptionImport.SourceTarget(-1,5000)}
 }
 @Test fun sourceControllerOnlyAdmitsItsOwn25WhilePlaybackKeeps29(){
  val a=activity();val c=unsafe.allocateInstance(VideoTimelineCaptionControls::class.java)as VideoTimelineCaptionControls;set(c,"sourceTarget",VideoTimelineCaptionImport.SourceTarget(0,5000));set(a,"timelineCaptionImport",c);set(a,"importing",true)
  assertEquals(25,c.requestCode);for(code in 22..29)assertEquals("code $code",code==25,call(a,"allowsMediaImportResult",code));assertEquals(true,call(a,"allowsMediaImportResult",99))
 }
 @Test fun sourcePickerDefersReadUntilForegroundAndPauseClosesReviewOnce(){
  var releases=0
  fun control(phase:Int)=(unsafe.allocateInstance(VideoTimelineCaptionControls::class.java)as VideoTimelineCaptionControls).also{set(it,"sourceTarget",VideoTimelineCaptionImport.SourceTarget(0,5000));set(it,"phase",java.util.concurrent.atomic.AtomicInteger(phase));set(it,"worker",java.util.concurrent.Executors.newSingleThreadExecutor());set(it,"isForeground",{false});set(it,"isCurrent",{true});set(it,"canReview",{true});set(it,"release",{releases++});set(it,"message",{_:String->Unit})}
  val c=control(1);c.accept(android.app.Activity.RESULT_OK,android.content.Intent().setData(Uri.parse(doc)));c.onPause();c.resume();assertEquals(2,(callPhase(c)));c.close();c.close();assertEquals(1,releases)
  val review=control(4);review.onPause();assertEquals(5,callPhase(review));assertEquals(2,releases)
 }
 private fun callPhase(c:VideoTimelineCaptionControls)=c.javaClass.getDeclaredField("phase").apply{isAccessible=true}.get(c).let{(it as java.util.concurrent.atomic.AtomicInteger).get()}
}
