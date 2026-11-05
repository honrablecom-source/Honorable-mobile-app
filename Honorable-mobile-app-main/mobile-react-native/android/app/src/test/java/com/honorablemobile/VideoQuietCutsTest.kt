package com.honorablemobile

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class VideoQuietCutsTest {
    private fun windows(end:Long=4000,quietStart:Long=1000,quietEnd:Long=3000)= (0 until (end/20).toInt()).map{i->val at=i*20L;VideoQuietWindow(at,at+20,if(at>=quietStart&&at<quietEnd)0.0 else .5)}
    private fun options(minimum:Long=500,pad:Long=100)=VideoQuietCuts.Options(-38,minimum,pad)
    private fun fails(part:String,block:()->Unit){try{block();fail("Expected $part")}catch(error:IllegalArgumentException){assertTrue(error.message.orEmpty(),error.message.orEmpty().contains(part))}}
    @Test fun analyticQuietPauseKeepsEdgesAndProducesComplementarySourceRanges(){
        val r=VideoQuietCuts.plan(0,4000,1f,windows(),options());assertEquals(listOf(VideoQuietCuts.Range(1100,2900)),r.removed);assertEquals(listOf(VideoQuietCuts.Range(0,1100),VideoQuietCuts.Range(2900,4000)),r.retained);assertEquals(200,r.knownWindows)
    }
    @Test fun pauseAndPaddingUsePlaybackTimeAtBothFastAndSlowSpeeds(){
        val fast=VideoQuietCuts.plan(0,4000,2f,windows(),options());assertEquals(VideoQuietCuts.Range(1200,2800),fast.removed.single())
        val slow=VideoQuietCuts.plan(0,4000,.5f,windows(),options());assertEquals(VideoQuietCuts.Range(1050,2950),slow.removed.single())
        fails("No interior"){VideoQuietCuts.plan(0,4000,2f,windows(4000,1000,1800),options())}
    }
    @Test fun leadingTrailingAndWhollyQuietAudioAreKept(){
        fails("No interior"){VideoQuietCuts.plan(0,4000,1f,windows(4000,0,4000),options())}
        fails("No interior"){VideoQuietCuts.plan(0,4000,1f,windows(4000,0,2000),options())}
        fails("No interior"){VideoQuietCuts.plan(0,4000,1f,windows(4000,2000,4000),options())}
    }
    @Test fun missingCoverageIsUnknownAndCannotBeCutThrough(){
        val partial=windows().mapIndexed{i,w->if(i==100)w.copy(rms=null)else w};fails("No interior"){VideoQuietCuts.plan(0,4000,1f,partial,options())}
        fails("enough"){VideoQuietCuts.plan(0,4000,1f,windows().mapIndexed{i,w->if(i<21)w.copy(rms=null)else w},options())}
    }
    @Test fun thresholdAndMinimumAreInclusiveAndPaddingCanRefuseAnEmptyCut(){
        val threshold=Math.pow(10.0,-40/20.0);val exact=windows(2000,500,1000).map{if(it.rms==0.0)it.copy(rms=threshold)else it}
        assertEquals(VideoQuietCuts.Range(600,900),VideoQuietCuts.plan(0,2000,1f,exact,VideoQuietCuts.Options(-40,500,100)).removed.single())
        fails("No interior"){VideoQuietCuts.plan(0,2000,1f,exact,VideoQuietCuts.Options(-41,500,100))}
        fails("No interior"){VideoQuietCuts.plan(0,2000,1f,exact,VideoQuietCuts.Options(-40,500,300))}
    }
    @Test fun shortRetainedPiecesAndManyPausesAreRefused(){
        fails("0.1"){VideoQuietCuts.plan(0,1000,1f,windows(1000,20,980),options(200,0))}
        val many=(0 until 572).map{i->VideoQuietWindow(i*20L,(i+1)*20L,if(i%11==0).5 else 0.0)}
        fails("Too many"){VideoQuietCuts.plan(0,many.last().endMs,1f,many,options(200,0))}
    }
    @Test fun sixteenBitPcmUsesAllChannelsWithoutPhaseCancellationOrChangingTheInput(){
        val buffer=ByteBuffer.allocate(8+160*4).order(ByteOrder.nativeOrder());buffer.putInt(123);repeat(160){buffer.putShort(16384);buffer.putShort(-16384)};buffer.putInt(456);val original=buffer.array().clone();buffer.rewind()
        val energy=VideoQuietEnergy(0,20,8000);energy.pcm(buffer,4,640,0,2,false);assertEquals(.5,energy.finish().single().rms!!,1e-12);assertEquals(0,buffer.position());assertArrayEquals(original,buffer.array())
    }
    @Test fun floatPcmClipsFiniteOvershootAndRejectsNanOrIncompleteFrames(){
        val buffer=ByteBuffer.allocate(160*4).order(ByteOrder.nativeOrder());repeat(160){buffer.putFloat(2f)};buffer.rewind();val energy=VideoQuietEnergy(0,20,8000);energy.pcm(buffer,0,640,0,1,true);assertEquals(1.0,energy.finish().single().rms!!,0.0)
        buffer.putFloat(0,Float.NaN);fails("floating-point"){VideoQuietEnergy(0,20,8000).pcm(buffer,0,640,0,1,true)}
        fails("Incomplete"){VideoQuietEnergy(0,20,8000).pcm(buffer,0,3,0,1,false)}
    }
    @Test fun sourceTimestampCroppingIncludesPartialLastWindowAndIgnoresPreroll(){
        val buffer=ByteBuffer.allocate(216*2).order(ByteOrder.nativeOrder());repeat(216){buffer.putShort(8192)};buffer.rewind();val energy=VideoQuietEnergy(1000,1025,8000);energy.pcm(buffer,0,432,999000,1,false);val r=energy.finish();assertEquals(2,r.size);assertEquals(1025L,r.last().endMs);assertTrue(r.all{it.rms==.25})
    }
    @Test fun missingSamplesDoNotBecomeSilenceAndBackwardTimestampsRefuse(){
        val energy=VideoQuietEnergy(0,40,8000);repeat(160){energy.frame(it*125L,0.0)};assertEquals(0.0,energy.finish()[0].rms!!,0.0);assertNull(energy.finish()[1].rms)
        fails("backward"){energy.frame(0,0.0)}
    }
    @Test fun pcmLoopHonorsCancellationAndValidatesItsBufferBounds(){
        val buffer=ByteBuffer.allocate(5000*2).order(ByteOrder.nativeOrder());var calls=0;try{VideoQuietEnergy(0,1000,8000).pcm(buffer,0,10000,0,1,false){if(++calls==2)throw CancellationException("stop")};fail("Expected cancellation")}catch(expected:CancellationException){assertEquals(2,calls)}
        fails("buffer"){VideoQuietEnergy(0,20,8000).pcm(buffer,-1,100,0,1,false)};fails("buffer"){VideoQuietEnergy(0,20,8000).pcm(buffer,0,100,0,9,false)}
    }
    @Test fun cutPiecesPreserveSettingsSourceCaptionsAndOtherClips(){
        val cue=VideoCaption(500,3500,"A caption across the pause");val clip=VideoSegment(0,4000,volume=.4f,title="Keep",fadeEdges=true,audioFade=true,captions=listOf(cue),captionSourceUri="content://test/subtitles")
        val neighbor=VideoSegment(4000,5000);val source=listOf(clip,neighbor);val plan=VideoQuietCuts.plan(0,4000,1f,windows(),options());val result=VideoQuietCuts.apply(source,0,plan)
        assertEquals(3,result.size);assertSame(neighbor,result.last());assertEquals(0L,result[0].startMs);assertEquals(1100L,result[0].endMs);assertEquals(2900L,result[1].startMs);assertEquals(4000L,result[1].endMs)
        for(piece in result.take(2)){assertEquals(.4f,piece.volume,0f);assertEquals("Keep",piece.title);assertTrue(piece.fadeEdges);assertTrue(piece.audioFade);assertSame(clip.grade,piece.grade);assertSame(clip.motion,piece.motion);assertSame(cue,piece.captions.single());assertEquals(clip.captionSourceUri,piece.captionSourceUri)}
        assertEquals(listOf(clip,neighbor),source)
    }
    @Test fun realProjectHistoryRestoresClipsAndSoundtrackInOneUndo(){
        val project=VideoEditProject(4000);val music=VideoSoundtrack("content://test/music",10000,.2f,false,1000,9000);project.setSoundtrack(music);val before=project.segments.toList();val plan=VideoQuietCuts.plan(0,4000,1f,windows(),options());project.applyDraft(VideoQuietCuts.apply(before,0,plan),soundtrack=music);assertEquals(2200L,project.totalDurationMs);assertEquals(music,project.soundtrack);assertTrue(project.undo());assertEquals(before,project.segments);assertEquals(music,project.soundtrack);assertTrue(project.redo());assertEquals(2,project.segments.size)
    }
    @Test fun limitsAndForgedRangesAreRefusedBeforeAnyProjectChange(){
        val plan=VideoQuietCuts.plan(0,4000,1f,windows(),options());val clips=List(1000){VideoSegment(0,4000)};fails("limit"){VideoQuietCuts.apply(clips,0,plan)}
        fails("inconsistent"){VideoQuietCuts.apply(listOf(VideoSegment(0,4000)),0,plan.copy(removed=emptyList()))}
        fails("two source"){VideoQuietCuts.plan(0,120001,1f,windows(),options())}
        fails("cover"){VideoQuietCuts.plan(0,4000,1f,windows().drop(1),options())}
        for(block in listOf<()->Unit>({VideoQuietCuts.Options(-61)},{VideoQuietCuts.Options(-14)},{VideoQuietCuts.Options(minimumPauseMs=199)},{VideoQuietCuts.Options(paddingMs=501)}))try{block();fail("Expected limit")}catch(expected:IllegalArgumentException){}
    }
    @Test fun projectCaptionBudgetRefusesTheWholeEditAndKeepsHistoryState(){
        val captions=(0 until 99).map{VideoCaption(it.toLong(),it+1L,"a")}+VideoCaption(99,3900,"cross")+(3900 until 4000).map{VideoCaption(it.toLong(),it+1L,"b")}
        val clip=VideoSegment(0,4000,captions=captions);val project=VideoEditProject(4000);project.applyDraft(List(5){clip});val before=project.segments.toList()
        val proposed=VideoQuietCuts.apply(before,0,VideoQuietCuts.plan(0,4000,1f,windows(),options()))
        fails("caption count"){project.applyDraft(proposed)};assertEquals(before,project.segments)
    }

}
