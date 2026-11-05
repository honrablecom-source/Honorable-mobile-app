package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
class VideoAudioPlanTest {
 @Test fun silentOrMutedTimelineIsRejected(){val clips=listOf(VideoSegment(0,1000,volume=0f));assertTrue(runCatching{VideoAudioPlan.requireAudio(clips,setOf("primary"),null)}.isFailure);assertTrue(runCatching{VideoAudioPlan.requireAudio(listOf(VideoSegment(0,1000)),emptySet(),null)}.isFailure)}
 @Test fun enabledSourceAudioIsAccepted(){VideoAudioPlan.requireAudio(listOf(VideoSegment(0,1000)),setOf("primary"),null)}
 @Test fun durationFollowsTrimSpeedAndSequence(){val clips=listOf(VideoSegment(1000,3000,speed=2f),VideoSegment(0,1500,speed=.5f));assertEquals(4000000L,VideoAudioPlan.durationUs(clips));VideoAudioPlan.validateDuration(clips,4000)}
 @Test fun truncatedAndEmptyOutputIsRejected(){val clips=listOf(VideoSegment(0,10000));listOf(0L,5000L,12000L).forEach{assertTrue(runCatching{VideoAudioPlan.validateDuration(clips,it)}.isFailure)}}
 @Test fun overflowingSequenceIsRejectedBeforeAudioAcceptance(){
  val huge=VideoSegment(0,Long.MAX_VALUE)
  assertTrue(runCatching{VideoAudioPlan.durationUs(listOf(huge,huge))}.isFailure)
  assertTrue(runCatching{VideoAudioPlan.requireAudio(listOf(huge,huge),setOf("primary"),null)}.isFailure)
 }
 @Test fun ceilingAtMaximumMicrosecondDurationDoesNotWrap(){
  val huge=VideoSegment(0,Long.MAX_VALUE)
  assertEquals(Long.MAX_VALUE,VideoAudioPlan.durationUs(listOf(huge)))
  VideoAudioPlan.validateDuration(listOf(huge),Long.MAX_VALUE/1000+1)
 }
 @Test fun audioValidationUsesCanonicalClipCountBound(){
  val clips=List(1001){VideoSegment(0,1000)}
  assertTrue(runCatching{VideoAudioPlan.durationUs(clips)}.isFailure)
  assertTrue(runCatching{VideoAudioPlan.requireAudio(clips,setOf("primary"),null)}.isFailure)
 }
}
