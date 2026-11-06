package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoTimelineCaptionImportTest {
    private val document="content://synthetic/subtitles";private val prior="content://synthetic/old-subtitles"
    private fun srt(vararg cues:VideoCaption)=cues.mapIndexed{index,cue->"${index+1}\n${VideoCaptions.formatTime(cue.startMs)} --> ${VideoCaptions.formatTime(cue.endMs)}\n${cue.text}\n"}.joinToString("\n").toByteArray()
    private fun prepare(clips:List<VideoSegment>,vararg cues:VideoCaption)=VideoTimelineCaptionImport.prepare(clips,srt(*cues),document)
    private fun reject(block:()->Unit){assertTrue(runCatching(block).isFailure)}
    @Test fun trimAndFastPlaybackMapBackToOriginalSourceTimes(){
        val clip=VideoSegment(1000,5000,speed=2f,title="Keep title");val plan=prepare(listOf(clip),VideoCaption(500,1000,"Hello"))
        assertEquals(listOf(clip.copy(captions=listOf(VideoCaption(2000,3000,"Hello")),captionSourceUri=document)),plan.segments);assertEquals(1,plan.inputCues);assertEquals(1,plan.mappedCues)
    }
    @Test fun crossingCutsMapsIndependentFragmentsWithoutChangingRenderSettings(){
        val a=VideoSegment(1000,2000,volume=.2f,title="A");val b=VideoSegment(0,4000,speed=2f,sourceId="second",title="B",fadeEdges=true)
        val plan=prepare(listOf(a,b),VideoCaption(500,1500,"Cross cut"));assertEquals(listOf(VideoCaption(1500,2000,"Cross cut")),plan.segments[0].captions);assertEquals(listOf(VideoCaption(0,1000,"Cross cut")),plan.segments[1].captions)
        assertEquals(a.copy(captions=plan.segments[0].captions,captionSourceUri=document),plan.segments[0]);assertEquals(b.copy(captions=plan.segments[1].captions,captionSourceUri=document),plan.segments[1]);assertEquals(2,plan.mappedCues)
    }
    @Test fun repeatedSourceClipsReceiveOnlyTheirOwnPlaybackCues(){
        val base=List(2){VideoSegment(0,2000)};val plan=prepare(base,VideoCaption(0,1000,"First"),VideoCaption(2500,3500,"Second"))
        assertEquals(listOf(VideoCaption(0,1000,"First")),plan.segments[0].captions);assertEquals(listOf(VideoCaption(500,1500,"Second")),plan.segments[1].captions)
    }
    @Test fun halfSpeedUsesSourceGridAndKeepsOrderedNonoverlappingCues(){
        val base=listOf(VideoSegment(0,2000,speed=.5f));val plan=prepare(base,VideoCaption(500,1500,"Slow"),VideoCaption(1500,2000,"Next"))
        assertEquals(listOf(VideoCaption(250,750,"Slow"),VideoCaption(750,1000,"Next")),plan.segments.single().captions)
    }
    @Test fun fractionalCutFragmentsRetainTextWithBoundedSourceMillisecondAdjustment(){
        val base=listOf(VideoSegment(0,1001,speed=1.5f),VideoSegment(0,1000));val plan=prepare(base,VideoCaption(667,668,"Across fractional cut"))
        assertEquals(listOf(VideoCaption(1000,1001,"Across fractional cut")),plan.segments[0].captions);assertEquals(listOf(VideoCaption(0,1,"Across fractional cut")),plan.segments[1].captions);assertEquals(2,plan.adjustedFragments)
    }
    @Test fun unrepresentableDenseFragmentsRejectInsteadOfDroppingTextOrOverlapping(){
        val base=listOf(VideoSegment(0,1,speed=.5f,captions=listOf(VideoCaption(0,1,"Old")),captionSourceUri=prior))
        reject{prepare(base,VideoCaption(0,1,"One"),VideoCaption(1,2,"Two"))};assertEquals("Old",base.single().captions.single().text);assertEquals(prior,base.single().captionSourceUri)
    }
    @Test fun playbackParserHasSeparateProjectBoundWithoutWeakeningClipImport(){
        val cues=Array(201){VideoCaption(it*100L,it*100L+50,"x")};val bytes=srt(*cues)
        reject{VideoCaptions.parseSrt(bytes,50_000)};assertEquals(201,VideoCaptions.parsePlaybackSrt(bytes,50_000).size)
        assertEquals(201,VideoTimelineCaptionImport.prepare(List(5){VideoSegment(0,10_000)},bytes,document).mappedCues)
    }
    @Test fun projectThousandCueBoundAndPerClipTwoHundredAreBothEnforced(){
        val cues=Array(1000){VideoCaption(it*50L,it*50L+20,"x")};val base=List(5){VideoSegment(0,10_000)}
        assertEquals(1000,VideoTimelineCaptionImport.prepare(base,srt(*cues),document).mappedCues)
        reject{VideoCaptions.parsePlaybackSrt(srt(*(cues+VideoCaption(50_000,50_001,"extra"))),60_000)}
        reject{VideoTimelineCaptionImport.prepare(listOf(VideoSegment(0,50_000)),srt(*cues.take(201).toTypedArray()),document)}
    }
    @Test fun duplicatedFragmentTextMustFitTheExistingProjectByteBudget(){
        val cues=Array(200){VideoCaption(it*200L,it*200L+200,"x".repeat(240))};val base=List(400){VideoSegment(0,100)}
        reject{VideoTimelineCaptionImport.prepare(base,srt(*cues),document)};assertTrue(base.all{it.captions.isEmpty()})
    }
    @Test fun cutsCannotMultiplyImportedCuesBeyondProjectLimit(){
        val base=List(1000){VideoSegment(0,6)};val cues=Array(599){VideoCaption(it*10L,it*10L+10,"x")}
        reject{VideoTimelineCaptionImport.prepare(base,srt(*cues),document)};assertTrue(base.all{it.captions.isEmpty()})
    }
    @Test fun invalidDocumentsEncodingOverlapAndOutsideTimelineFailClosed(){
        val base=listOf(VideoSegment(0,1000));val bytes=srt(VideoCaption(0,100,"x"))
        for(uri in listOf("file:///private","https://example.test/srt","content:///missing","content://synthetic/x#fragment"))reject{VideoTimelineCaptionImport.prepare(base,bytes,uri)}
        reject{VideoTimelineCaptionImport.prepare(base,byteArrayOf(0xc3.toByte(),0x28),document)};reject{prepare(base,VideoCaption(0,1001,"Outside"))};reject{prepare(base,VideoCaption(0,100,"One"),VideoCaption(50,150,"Overlap"))}
    }
    @Test fun proposalDefensivelyKeepsImmutableModelsAndCaptionLists(){
        val original=VideoSegment(0,1000,captions=listOf(VideoCaption(0,500,"Old")),captionSourceUri=prior);val base=mutableListOf(original);val plan=prepare(base,VideoCaption(0,500,"New"));base.clear()
        assertEquals(listOf(original),plan.base);assertEquals("New",plan.segments.single().captions.single().text)
        try{(plan.segments as MutableList).clear();fail("Mutable proposal")}catch(_:UnsupportedOperationException){}
        try{(plan.segments.single().captions as MutableList).clear();fail("Mutable cues")}catch(_:UnsupportedOperationException){}
    }
    @Test fun actualProjectApplyPreservesMusicAndOneUndoWithSubtitleProtection(){
        val base=listOf(VideoSegment(0,5000,captions=listOf(VideoCaption(0,500,"Old")),captionSourceUri=prior));val music=VideoSoundtrack("content://synthetic/music",10_000,loop=false,startMs=1000,endMs=9000)
        val project=VideoEditProject(5000);project.restore(base,soundtrack=music);val plan=VideoTimelineCaptionImport.prepare(base,srt(VideoCaption(1000,2000,"New")),document,music)
        plan.apply(project){};assertEquals(plan.segments,project.segments);assertSame(music,project.soundtrack);assertEquals(setOf(prior,document),project.captionSourceUris());assertTrue(project.canUndo)
        assertTrue(project.undo());assertEquals(base,project.segments);assertFalse(project.canUndo);assertTrue(project.redo());assertEquals(plan.segments,project.segments);assertSame(music,project.soundtrack)
    }
    @Test fun changedProjectAndCancelledPreparationCannotCommit(){
        val base=listOf(VideoSegment(0,5000));val plan=prepare(base,VideoCaption(0,500,"x"));val project=VideoEditProject(5000);project.split(0,2000);val changed=project.segments
        reject{plan.apply(project){}};assertSame(changed,project.segments);var checks=0
        reject{VideoTimelineCaptionImport.prepare(base,srt(VideoCaption(0,500,"x")),document){checks++;check(checks<3)}};assertTrue(base.single().captions.isEmpty())
    }
    @Test fun nativeDraftCodecRetainsImportedSourceTimesTextAndDocument(){
        val base=listOf(VideoSegment(1000,5000,speed=1.5f));val plan=prepare(base,VideoCaption(0,1000,"世界\nCaption"))
        val draft=VideoDraft(10_000,plan.segments,0,1000,1000,5000);assertEquals(draft,VideoDraftCodec.decode(VideoDraftCodec.encode(draft),10_000))
    }
    @Test fun finalPlaybackMillisecondRoundingRemainsInsideSourceRange(){
        val plan=prepare(listOf(VideoSegment(0,1000,speed=1.5f)),VideoCaption(666,667,"Last rounded millisecond"))
        assertEquals(listOf(VideoCaption(999,1000,"Last rounded millisecond")),plan.segments.single().captions)
    }
}
