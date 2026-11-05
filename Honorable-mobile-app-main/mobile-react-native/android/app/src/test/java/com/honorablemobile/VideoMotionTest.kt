package com.honorablemobile

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class VideoMotionTest{
    private fun motion()=VideoMotion(true,1f,3f,-1f,0f,1f,0f,0,10_000,MotionEase.LINEAR)
    @Test fun sourceAnchoredMotionSurvivesTrimSplitSpeedAndUndo(){
        val model=VideoEditProject(10_000);model.setMotion(0,motion());assertEquals(motion(),model.segments.single().motion)
        assertEquals(2f,VideoMotionTimeline(model.segments).at(5_000_000).zoom,0f)
        model.split(0,5_000);assertEquals(motion(),model.segments[1].motion);assertEquals(2f,VideoMotionTimeline(model.segments).at(5_000_000).zoom,0f)
        assertEquals(3f,VideoMotionTimeline(model.segments).at(10_000_000).zoom,0f)
        assertTrue(model.undo());assertEquals(1,model.segments.size);model.trim(0,2_500,7_500)
        assertEquals(1.5f,VideoMotionTimeline(model.segments).at(0).zoom,0f);assertEquals(2.5f,VideoMotionTimeline(model.segments).at(5_000_000).zoom,0f)
        model.setSpeed(0,2f);assertEquals(2f,VideoMotionTimeline(model.segments).at(1_250_000).zoom,0f)
    }
    @Test fun easedAndDisabledStatesAndViewportCoverage(){
        val linear=motion();val smooth=linear.copy(ease=MotionEase.SMOOTH)
        assertEquals(1.5f,linear.atSource(2500.0).zoom,0f);assertEquals(1.3125f,smooth.atSource(2500.0).zoom,0f)
        assertEquals(1f,smooth.copy(enabled=false).atSource(9000.0).zoom,0f)
        for(x in listOf(-1f,0f,1f))for(y in listOf(-1f,0f,1f))for(step in 0..100){
            val frame=linear.copy(startX=x,endX=-x,startY=y,endY=-y).atSource(step*100.0)
            assertTrue(-frame.zoom+frame.translateX<=-1f+.000001f);assertTrue(frame.zoom+frame.translateX>=1f-.000001f)
            assertTrue(-frame.zoom+frame.translateY<=-1f+.000001f);assertTrue(frame.zoom+frame.translateY>=1f-.000001f)
        }
        assertThrows(IllegalArgumentException::class.java){linear.copy(startZoom=.9f)};assertThrows(IllegalArgumentException::class.java){linear.copy(endX=Float.NaN)}
        assertThrows(IllegalArgumentException::class.java){linear.copy(anchorEndMs=0)}
    }
    @Test fun versionSevenAndLegacyDefaultsRoundTrip(){
        val clip=VideoSegment(0,10_000,motion=motion(),captions=listOf(VideoCaption(500,1000,"Keep caption")))
        val draft=VideoDraft(10_000,listOf(clip),0,0,0,10_000)
        val bytes=VideoDraftCodec.encode(draft);assertEquals(7,DataInputStream(bytes.inputStream()).run{readInt();readInt()});assertEquals(draft,VideoDraftCodec.decode(bytes,10_000))
        val plain=draft.copy(segments=listOf(VideoSegment(0,10_000)));val encoded=VideoDraftCodec.encode(plain);assertEquals(1,DataInputStream(encoded.inputStream()).run{readInt();readInt()});assertEquals(VideoMotion(),VideoDraftCodec.decode(encoded,10_000)!!.segments.single().motion)
        val raw=ByteArrayOutputStream();DataOutputStream(raw).use{motion().write(it)};val damaged=raw.toByteArray();damaged[damaged.lastIndex]=127
        assertThrows(IllegalArgumentException::class.java){VideoMotion.read(DataInputStream(damaged.inputStream()))}
    }
    @Test fun rejectsSourceMismatchAndOversizeBeforeHistoryMutation(){
        assertThrows(IllegalArgumentException::class.java){VideoDraft(1000,listOf(VideoSegment(0,1000,motion=motion())),0,0,0,1000)}
        val project=VideoEditProject(10_000);project.restore(List(1000){VideoSegment(0,10_000,title="x".repeat(120))});val before=project.segments
        assertThrows(IllegalArgumentException::class.java){project.setMotion(0,motion())};assertEquals(before,project.segments);assertFalse(project.undo())
    }
    @Test fun boundariesAndIndependentSourceAnchors(){
        val clips=listOf(VideoSegment(0,10_000,motion=motion()),VideoSegment(0,5000,sourceId="other"))
        val timeline=VideoMotionTimeline(clips);assertEquals(1f,timeline.at(10_000_000).zoom,0f);assertEquals(1f,timeline.at(Long.MAX_VALUE).zoom,0f)
        val split=listOf(VideoSegment(0,5000,motion=motion()),VideoSegment(5000,10_000,motion=motion()))
        assertEquals(1,VideoRefinementTools.joinMatchingCuts(split).size);assertEquals(motion(),VideoRefinementTools.joinMatchingCuts(split).single().motion)
        val separate=split.mapIndexed{i,clip->clip.copy(motion=motion().copy(anchorStartMs=i*5000L,anchorEndMs=(i+1)*5000L))}
        assertEquals(2,VideoRefinementTools.joinMatchingCuts(separate).size)
    }
    @Test fun editorDisplaysEffectiveClipEndpointsWithoutLosingTiming(){
        val current=motion().copy(ease=MotionEase.SMOOTH);val shown=current.forEditing(2500,7500)
        assertEquals(current.atSource(2500.0).zoom,shown.startZoom,0f);assertEquals(current.atSource(7500.0).zoom,shown.endZoom,0f)
        assertEquals(2500L,shown.anchorStartMs);assertEquals(7500L,shown.anchorEndMs)
        assertEquals(1.2f,VideoMotion().forEditing(0,1000).endZoom,0f)
    }
}
