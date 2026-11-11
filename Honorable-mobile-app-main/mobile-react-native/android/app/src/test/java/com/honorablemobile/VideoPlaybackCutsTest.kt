package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
class VideoPlaybackCutsTest {
 private fun one(speed:Float=1f)=listOf(VideoSegment(1000,11000,speed=speed))
 @Test fun decimalAndClockMarkersSplitWithoutLosingOrRepeatingSourceRanges(){
  val result=VideoPlaybackCuts.prepare(one(),"1.250\n00:00:03.500\n9")
  assertEquals(listOf(1000L to 2250L,2250L to 4500L,4500L to 10000L,10000L to 11000L),result.clips.map{it.startMs to it.endMs});assertEquals(0,result.existingBoundaries);assertEquals(0L,result.durationDriftUs)
 }
 @Test fun mapsAcrossMixedSourcesAndSpeedsInWholeEditedPlayback(){
  val clips=listOf(VideoSegment(2000,6000,speed=2f,sourceId="first"),VideoSegment(100,2100,speed=.5f,sourceId="second"))
  val r=VideoPlaybackCuts.prepare(clips,"1\n2\n3")
  assertEquals(listOf(4000L,600L),r.cuts.map{it.sourceTimeMs});assertEquals(listOf(0,1),r.cuts.map{it.clipIndex});assertEquals(1,r.existingBoundaries);assertEquals(listOf("first","first","second","second"),r.clips.map{it.sourceId});assertEquals(0L,r.durationDriftUs)
 }
 @Test fun sourceMillisecondRoundingAndMicrosecondDriftAreReported(){
  val clip=VideoSegment(100,1100,speed=1.5f);val r=VideoPlaybackCuts.prepare(listOf(clip),"0.333")
  assertEquals(600L,r.cuts.single().sourceTimeMs);assertEquals(333333L,r.cuts.single().actualUs);assertEquals(333000L,r.cuts.single().requestedUs);assertEquals(0L,r.durationDriftUs)
  val fractional=VideoPlaybackCuts.prepare(listOf(VideoSegment(0,1000,speed=1.5f)),"0.301\n0.500")
  assertEquals(-1L,fractional.durationDriftUs)
 }
 @Test fun keepsAllAppearanceMotionAndSourceAnchoredCaptionProvenance(){
  val cue=VideoCaption(1500,3500,"Across cut")
  val c=VideoSegment(1000,5000,title="Original title",fadeEdges=true,volume=.4f,audioFade=true,grade=VideoGrade(exposure=.5f),layout=VideoLayout(rotation=90),titleStyle=VideoTitleStyle(size=.08f),motion=VideoMotion(enabled=true,anchorStartMs=0,anchorEndMs=6000),captions=listOf(cue),captionSourceUri="content://synthetic/subtitles")
  val r=VideoPlaybackCuts.prepare(listOf(c),"1\n3")
  for(piece in r.clips){assertEquals(c.copy(startMs=piece.startMs,endMs=piece.endMs,captions=piece.captions),piece);assertEquals("content://synthetic/subtitles",piece.captionSourceUri)}
  assertEquals(listOf(listOf(cue),listOf(cue),emptyList<VideoCaption>()),r.clips.map{it.captions});assertEquals(listOf(cue),c.captions)
 }
 @Test fun crossingCaptionIsRetainedOnBothSidesAndBoundaryTouchingCueIsNotDuplicated(){
  val a=VideoCaption(0,1000,"Before");val b=VideoCaption(1000,2000,"After");val cross=VideoCaption(500,1500,"Cross")
  val c=VideoSegment(0,3000,captions=listOf(a,b));val r=VideoPlaybackCuts.prepare(listOf(c),"1")
  assertEquals(listOf(a),r.clips[0].captions);assertEquals(listOf(b),r.clips[1].captions)
  val crossing=VideoPlaybackCuts.prepare(listOf(c.copy(captions=listOf(cross))),"1");assertEquals(listOf(cross),crossing.clips[0].captions);assertEquals(listOf(cross),crossing.clips[1].captions)
 }
 @Test fun acceptsWhitespaceCrLfBlankLinesAndOneToThreeDecimals(){assertEquals(3,VideoPlaybackCuts.prepare(one()," \r\n 1.0 \r\n\r\n00:00:02.01\r\n3.001\n").cuts.size)}
 @Test fun invalidSyntaxOrderingBoundsAndNoOpsAreRefusedAtomically(){
  val clips=one();for(text in listOf("","\n","0","10","11","-1","NaN","1e0","1,2","1.0001","00:60:00","00:00:60","00:00:01,000","1\n1","2\n1","+1","999:59:59.999")){
   assertThrows(text,IllegalArgumentException::class.java){VideoPlaybackCuts.prepare(clips,text)};assertEquals(one(),clips)
  }
  assertThrows(IllegalArgumentException::class.java){VideoPlaybackCuts.prepare(listOf(VideoSegment(0,1000),VideoSegment(1000,2000)),"1")}
 }
 @Test fun enforcesTextRowsAndProjectClipLimits(){
  assertThrows(IllegalArgumentException::class.java){VideoPlaybackCuts.prepare(one()," ".repeat(16001))}
  assertThrows(IllegalArgumentException::class.java){VideoPlaybackCuts.prepare(one(),(1..101).joinToString("\n"){"${it}.001"})}
  val clips=List(1000){VideoSegment(0,1000)};assertThrows(IllegalArgumentException::class.java){VideoPlaybackCuts.prepare(clips,"0.5")}
  assertEquals(100,VideoPlaybackCuts.prepare(listOf(VideoSegment(0,200000)),(1..100).joinToString("\n"){it.toString()}).cuts.size)
 }
 @Test fun newPiecesMustReachMinimumAfterSourceRounding(){
  assertThrows(IllegalArgumentException::class.java){VideoPlaybackCuts.prepare(one(2f),"0.099")}
  assertEquals(1200L,VideoPlaybackCuts.prepare(one(2f),"0.1").clips[0].endMs)
  assertThrows(IllegalArgumentException::class.java){VideoPlaybackCuts.prepare(one(2f),"0.100\n0.101")}
  assertThrows(IllegalArgumentException::class.java){VideoPlaybackCuts.prepare(one(.5f),"0.001")}
 }
 @Test fun leavesUnsplitShortClipsUnchanged(){val tiny=VideoSegment(0,10);val r=VideoPlaybackCuts.prepare(listOf(tiny,VideoSegment(0,3000)),"1");assertSame(tiny,r.clips.first())}
 @Test fun rejectsCaptionGrowthAndPreservesOriginalList(){
  val cues=listOf(VideoCaption(0,50000,"Before"),VideoCaption(50000,100000,"After"));val clips=List(500){VideoSegment(0,100000,captions=cues)}
  assertThrows(IllegalArgumentException::class.java){VideoPlaybackCuts.prepare(clips,(1..100).joinToString("\n"){it.toString()})};assertEquals(500,clips.size)
 }
 @Test fun actualBatchReviewConsumesApprovalAndOneUndoRestoresWholeTimeline(){
  val p=VideoEditProject(11000);val music=VideoSoundtrack("content://synthetic/music",20000,loop=false);p.restore(one(),soundtrack=music)
  val before=p.segments;val result=VideoPlaybackCuts.prepare(before,"1\n3",music);val request=VideoBatchReview(before,music);val dialog=Any();assertTrue(request.open(dialog));assertTrue(request.propose(dialog,result.clips));assertTrue(request.apply(dialog,p){});assertEquals(result.clips,p.segments);assertEquals(music,p.soundtrack);assertFalse(request.apply(dialog,p){});assertTrue(p.undo());assertEquals(before,p.segments);assertEquals(music,p.soundtrack);assertFalse(p.canUndo);assertTrue(p.redo());assertEquals(result.clips,p.segments)
  val bytes=VideoDraftCodec.encode(VideoDraft(11000,p.segments,0,1000,1000,p.segments[0].endMs,soundtrack=music));assertEquals(p.segments,VideoDraftCodec.decode(bytes,11000)!!.segments)
 }
 @Test fun hiddenForeignStaleOrChangedApprovalCannotApply(){
  for(mode in 0..3){val p=VideoEditProject(11000);p.restore(one());val before=p.segments;val r=VideoBatchReview(before,null);val d=Any();r.open(d);r.propose(d,VideoPlaybackCuts.prepare(before,"1").clips)
   when(mode){0->{r.dismiss(d);assertFalse(r.apply(d,p){})};1->assertFalse(r.apply(Any(),p){});2->assertThrows(IllegalStateException::class.java){r.apply(d,p){error("paused")}};3->{p.setTitle(0,"Changed");assertThrows(IllegalArgumentException::class.java){r.apply(d,p){}}}}
   if(mode<3)assertEquals(before,p.segments);if(mode!=1)assertTrue(r.isClosed)
  }
 }
 @Test fun randomMixedSpeedCutsPreserveEverySourceInterval(){
  val random=Random(122);repeat(100){val speed=listOf(.5f,1f,1.5f,2f)[random.nextInt(4)];val start=random.nextLong(0,100000);val c=VideoSegment(start,start+20000,speed=speed,title="Retained")
   val time=1000+random.nextInt(8000);val r=VideoPlaybackCuts.prepare(listOf(c),"${time/1000}.${(time%1000).toString().padStart(3,'0')}");assertEquals(start,r.clips.first().startMs);assertEquals(c.endMs,r.clips.last().endMs);assertEquals(c.endMs-c.startMs,r.clips.sumOf{it.endMs-it.startMs});assertTrue(r.clips.zipWithNext().all{(a,b)->a.endMs==b.startMs});assertTrue(r.durationDriftUs in -1..0);assertTrue(kotlin.math.abs(r.cuts.single().actualUs-r.cuts.single().requestedUs)<=1000)
  }
 }
}
