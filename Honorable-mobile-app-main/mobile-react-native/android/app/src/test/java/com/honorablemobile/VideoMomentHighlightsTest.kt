package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoMomentHighlightsTest {
    @Test fun sortedNotesMergeOverlapsAndTouchingRangesWithoutRepeatingFootage(){
        val clip=VideoSegment(1000,10000)
        val result=VideoMomentHighlights.propose(listOf(clip),0,listOf(8000,2500,2000),500,1000)
        assertEquals(listOf(1500L to 3500L,7500L to 9000L),result.map{it.startMs to it.endMs})
        assertEquals(listOf(1000L to 4000L),VideoMomentHighlights.propose(listOf(clip),0,listOf(1000,3000),0,1000).let{parts->
            // Non-touching handles keep their source gap.
            assertEquals(2,parts.size)
            VideoMomentHighlights.propose(listOf(clip),0,listOf(2000,3000),1000,1000).map{it.startMs to it.endMs}
        })
        assertEquals(1000L,clip.startMs)
    }
    @Test fun clipEdgesAndMinimumTailAlwaysIncludeTheSelectedAnchor(){
        val clip=VideoSegment(1000,10000,speed=2f)
        val result=VideoMomentHighlights.propose(listOf(clip),0,listOf(1000,9999),0,1)
        assertEquals(listOf(1000L to 1001L,9999L to 10000L),result.map{it.startMs to it.endMs})
        assertEquals(1000L,VideoPlaybackTime.durationUs(result))
        val full=VideoMomentHighlights.propose(listOf(clip),0,listOf(5000),60000,60000)
        assertEquals(listOf(clip),full)
    }
    @Test fun renderSettingsMotionAndSubtitleProvenanceStayWithVisibleCues(){
        val clip=VideoSegment(0,10000,speed=1.5f,volume=.5f,title="Keep",fadeEdges=true,grade=VideoGrade(.2f,1.2f,.8f),audioFade=true,
            captions=listOf(VideoCaption(0,1000,"outside"),VideoCaption(2500,5500,"crosses"),VideoCaption(8000,9000,"later")),captionSourceUri="content://fixture/subs",motion=VideoMotion(enabled=true,endZoom=2f,anchorStartMs=0,anchorEndMs=10000))
        val result=VideoMomentHighlights.propose(listOf(clip),0,listOf(3000,6000),500,500)
        assertEquals(clip.copy(startMs=2500,endMs=3500,captions=listOf(clip.captions[1])),result[0])
        assertEquals(clip.copy(startMs=5500,endMs=6500,captions=emptyList()),result[1])
        assertEquals(clip.motion.atSource(6000.0),result[1].motion.atSource(6000.0))
        assertEquals(listOf("crosses"),VideoCaptions.resolve(result).map{it.text})
    }
    @Test fun actualProjectAppliesAsOneUndoWithNeighborsAndMusicPreserved(){
        val p=VideoEditProject(10000)
        val clips=listOf(VideoSegment(0,1000,title="before"),VideoSegment(0,10000,sourceId="second",speed=2f),VideoSegment(9000,10000,title="after"))
        val music=VideoSoundtrack("content://fixture/music",20000,loop=false,startMs=1000,endMs=15000)
        p.restore(clips,mapOf("second" to 10000L),music)
        val result=VideoMomentHighlights.propose(p.segments,1,listOf(2000,8000),500,500)
        p.applyDraft(result,soundtrack=music)
        assertEquals(4,p.segments.size);assertEquals(clips.first(),p.segments.first());assertEquals(clips.last(),p.segments.last())
        assertEquals(music,p.soundtrack);assertEquals(3000L,p.totalDurationMs)
        assertTrue(p.undo());assertEquals(clips,p.segments);assertFalse(p.canUndo)
        assertTrue(p.redo());assertEquals(result,p.segments);assertEquals(music,p.soundtrack)
    }
    @Test fun sourceExcerptsRecoverThroughDraftAndGuestProjectWithoutNoteMetadata(){
        val clips=VideoMomentHighlights.propose(listOf(VideoSegment(0,10000,sourceId="second")),0,listOf(2000,8000),500,500)
        val draft=VideoDraft(10000,clips,0,1500,1500,2500,listOf(VideoSource("second","content://fixture/two",10000)))
        assertEquals(draft,VideoDraftCodec.decode(VideoDraftCodec.encode(draft),10000))
        val source="content://fixture/one"
        assertEquals(draft,GuestVideoProjectCodec.decode(GuestVideoProjectCodec.encode(source,draft),source,10000))
    }
    @Test fun invalidAnchorsHandlesAndClipBudgetsFailWithoutChangingProjectOrRedo(){
        val p=VideoEditProject(10000);p.setSpeed(0,2f);p.undo();val before=p.segments
        val requests=listOf(emptyList(),listOf(-1L),listOf(10000L),listOf(1000L,1000L),List(101){it.toLong()})
        for(notes in requests)assertTrue(runCatching{VideoMomentHighlights.propose(before,0,notes,500,500)}.isFailure)
        for(pair in listOf(-1L to 1L,0L to 0L,60001L to 1L,0L to Long.MAX_VALUE))assertTrue(runCatching{VideoMomentHighlights.propose(before,0,listOf(1000),pair.first,pair.second)}.isFailure)
        assertTrue(runCatching{VideoMomentHighlights.propose(before,1,listOf(1000),0,1)}.isFailure)
        assertTrue(runCatching{VideoMomentHighlights.propose(List(1000){VideoSegment(0,10000)},0,listOf(1000,8000),0,1)}.isFailure)
        assertEquals(before,p.segments);assertTrue(p.canRedo);assertFalse(p.canUndo)
    }
    @Test fun captionBudgetRefusesDuplicatedLongCueBeforePublication(){
        val cues=List(200){VideoCaption(it*50L,it*50L+25,"x")}
        val captioned=VideoSegment(0,10000,captions=cues)
        val target=List(4){captioned}+captioned.copy(captions=cues.dropLast(1))+VideoSegment(0,10000,captions=listOf(VideoCaption(0,10000,"spanning")))
        val p=VideoEditProject(10000);p.restore(target)
        assertEquals(1000,p.segments.sumOf{it.captions.size})
        // A valid 1000-cue project would become 1001 when its spanning cue is copied.
        assertTrue(runCatching{VideoMomentHighlights.propose(p.segments,5,listOf(1000,8000),0,1)}.isFailure)
        assertEquals(target,p.segments);assertFalse(p.canUndo)
    }
    @Test fun boundedDistanceMathWorksNearMaximumSourceTimestamp(){
        val clip=VideoSegment(Long.MAX_VALUE-10000,Long.MAX_VALUE)
        val result=VideoMomentHighlights.propose(listOf(clip),0,listOf(Long.MAX_VALUE-1),60000,60000)
        assertEquals(listOf(clip),result)
    }
}
