package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoBatchToolsTest {
    private val styled=VideoSegment(1000,10001,speed=1.5f,volume=.4f,title="Keep",grade=VideoGrade(exposure=.4f),
        captions=listOf(VideoCaption(3000,5000,"Across a cut")),captionSourceUri="content://subtitles/source")
    private val clips=listOf(VideoSegment(0,4000,title="A"),VideoSegment(4000,7000,title="B"),VideoSegment(7000,10000,title="C"))
    @Test fun equalDivisionCoversTheOriginalExactlyAndKeepsClipProperties(){
        val result=VideoBatchTools.divide(listOf(styled),0,3)
        assertEquals(3,result.size);assertEquals(styled.startMs,result.first().startMs);assertEquals(styled.endMs,result.last().endMs)
        assertTrue(result.zipWithNext().all{(a,b)->a.endMs==b.startMs})
        assertTrue(result.all{it.speed==styled.speed&&it.volume==styled.volume&&it.grade==styled.grade&&it.captionSourceUri==styled.captionSourceUri})
        assertTrue(result[0].captions.isNotEmpty());assertTrue(result[1].captions.isNotEmpty())
    }
    @Test fun divisionRejectsTinyPiecesAndProjectOverflow(){
        assertTrue(runCatching{VideoBatchTools.divide(listOf(VideoSegment(0,100)),0,2)}.isFailure)
        assertTrue(runCatching{VideoBatchTools.divide(List(1000){styled},0,2)}.isFailure)
        assertEquals(9001L,VideoBatchTools.divide(listOf(styled),0,7).sumOf{it.endMs-it.startMs})
    }
    @Test fun clipLengthLimitAccountsForSpeedAndKeepsCenteredVisibleCaptions(){
        val clip=VideoSegment(0,20000,speed=2f,captions=listOf(VideoCaption(0,1000,"Drop"),VideoCaption(9000,11000,"Keep")))
        val out=VideoBatchTools.limitLengths(listOf(clip),3000).single()
        assertEquals(7000L,out.startMs);assertEquals(13000L,out.endMs);assertEquals(3000L,out.durationMs)
        assertEquals(listOf("Keep"),out.captions.map{it.text});assertEquals(clips,VideoBatchTools.limitLengths(clips,60000))
    }
    @Test fun reverseOrderNeverReversesFramesWithinClips(){val out=VideoBatchTools.reverseOrder(clips);assertEquals(clips.reversed(),out);assertEquals(7000L,out.first().startMs);assertEquals("A",clips.first().title)}
    @Test fun shuffleIsDeterministicAndPreservesEveryClipExactlyOnce(){
        val a=VideoBatchTools.shuffleOrder(clips,41);assertEquals(a,VideoBatchTools.shuffleOrder(clips,41))
        assertEquals(clips.toSet(),a.toSet());assertEquals(clips.size,a.size);assertNotEquals(clips,a)
    }
    @Test fun repeatedSequenceHasWholeCopiesAndBoundedSize(){
        val result=VideoBatchTools.repeatSequence(clips,3);assertEquals(9,result.size)
        assertEquals(clips,result.take(3));assertEquals(clips,result.takeLast(3))
        assertTrue(runCatching{VideoBatchTools.repeatSequence(List(251){styled},4)}.isFailure)
    }
    @Test fun batchSpeedPreservesEveryOtherClipField(){
        val out=VideoBatchTools.allSpeeds(listOf(styled),2f).single();assertEquals(styled.copy(speed=2f),out)
        assertTrue(runCatching{VideoBatchTools.allSpeeds(clips,3f)}.isFailure)
        assertTrue(runCatching{VideoBatchTools.allSpeeds(clips,Float.NaN)}.isFailure)
    }
    @Test fun captionOffsetMovesTimingOnlyAndKeepsProvenance(){
        val result=VideoBatchTools.shiftCaptions(listOf(styled),0,500).single()
        assertEquals(listOf(VideoCaption(3500,5500,"Across a cut")),result.captions)
        assertEquals(styled.captionSourceUri,result.captionSourceUri);assertEquals(styled.copy(captions=result.captions),result)
    }
    @Test fun impossibleCaptionOffsetsFailWithoutDroppingCues(){
        assertTrue(runCatching{VideoBatchTools.shiftCaptions(listOf(styled),0,-2500)}.isFailure)
        assertTrue(runCatching{VideoBatchTools.shiftCaptions(listOf(styled),0,6000)}.isFailure)
        assertEquals(3000L,styled.captions.single().startMs)
    }
    @Test fun captionReflowKeepsWordsAndTimestamps(){
        val cue=VideoCaption(1000,2000,"A small story with several words for a readable caption")
        val clip=VideoSegment(0,3000,captions=listOf(cue))
        val out=VideoBatchTools.reflowCaptions(listOf(clip),0,20).single().captions.single()
        assertEquals(cue.startMs,out.startMs);assertEquals(cue.endMs,out.endMs)
        assertEquals(cue.text.replace(Regex("\\s"),""),out.text.replace(Regex("\\s"),""))
        assertTrue(out.text.lines().size<=3);assertTrue(out.text.lines().all{it.length<=20})
    }
    @Test fun reflowHandlesUnicodeAndRefusesTextThatNeedsTooManyLines(){
        val text="\uD83D\uDE00".repeat(40)
        val clip=VideoSegment(0,3000,captions=listOf(VideoCaption(0,1000,text)))
        val result=VideoBatchTools.reflowCaptions(listOf(clip),0,20).single().captions.single()
        assertEquals(listOf(20,20),result.text.lines().map{it.codePointCount(0,it.length)})
        assertEquals(text,result.text.replace("\n",""))
        val long=clip.copy(captions=listOf(VideoCaption(0,1000,"word ".repeat(40).trim())))
        assertTrue(runCatching{VideoBatchTools.reflowCaptions(listOf(long),0,20)}.isFailure)
    }
    @Test fun batchApplicationsAreSingleUndoableChangesAndKeepMusic(){
        val project=VideoEditProject(10001);project.restore(listOf(styled))
        val music=VideoSoundtrack("content://audio/music",3000);project.setSoundtrack(music)
        val before=project.segments;val proposal=VideoBatchTools.divide(before,0,3)
        project.applyDraft(proposal,soundtrack=music);assertEquals(music,project.soundtrack)
        assertTrue(project.undo());assertEquals(before,project.segments);assertEquals(music,project.soundtrack)
        assertTrue(project.redo());assertEquals(proposal,project.segments)
    }
    @Test fun validationRejectsCaptionExplosionWithoutMutatingProject(){
        val project=VideoEditProject(200000)
        val cueList=(0 until 200).map{VideoCaption(it*1000L,it*1000L+100,"a")}
        val before=List(5){VideoSegment(0,200000,captions=cueList)}
        project.restore(before)
        val proposal=VideoBatchTools.repeatSequence(before,2)
        assertTrue(runCatching{project.applyDraft(proposal)}.isFailure)
        assertEquals(before,project.segments);assertFalse(project.canUndo)
    }
    @Test fun existingDraftFormatKeepsBatchedProjectWithoutNewSchema(){
        val proposal=VideoBatchTools.allSpeeds(VideoBatchTools.divide(listOf(styled),0,3),2f)
        val draft=VideoDraft(10001,proposal,0,proposal.first().startMs,proposal.first().startMs,proposal.first().endMs)
        assertEquals(draft,VideoDraftCodec.decode(VideoDraftCodec.encode(draft),10001))
    }
}
