package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class VideoCreativeTest {
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    @Test fun titlesAndFadesFollowClipEditsAndHistory() {
        val p = VideoEditProject(20_000)
        p.setTitle(0,"Opening title"); p.setFadeEdges(0,true); p.setSpeed(0,2f)
        p.split(0,10_000);p.trim(1,12_000,18_000);p.duplicate(1);p.move(2,0)
        assertTrue(p.segments.all { it.title == "Opening title" && it.fadeEdges && it.speed == 2f })
        val before = p.segments;p.setTitle(0,"");p.setFadeEdges(0,false)
        assertTrue(p.undo());assertTrue(p.undo());assertEquals(before,p.segments)
        assertTrue(p.redo());assertEquals("",p.segments[0].title)
    }
    @Test fun creativeDraftVersionThreeRoundTripsWithAndWithoutImports() {
        val clip = VideoSegment(5000,15_000,2f,.5f,title="Hello\nworld",fadeEdges=true)
        val draft = VideoDraft(20_000,listOf(clip),0,8000,6000,14_000)
        val bytes = VideoDraftCodec.encode(draft)
        assertEquals(3,ByteBuffer.wrap(bytes).getInt(4));assertEquals(draft,VideoDraftCodec.decode(bytes,20_000))
        val imported = draft.copy(segments=listOf(clip.copy(sourceId="s")),sources=listOf(VideoSource("s","content://videos/2",30_000)))
        assertEquals(imported,VideoDraftCodec.decode(VideoDraftCodec.encode(imported),20_000))
    }
    @Test fun corruptCreativePayloadAndOversizeDataAreRejected() {
        val draft = VideoDraft(1000,listOf(VideoSegment(0,1000,title="Test",fadeEdges=true)),0,0,0,1000)
        val bytes = VideoDraftCodec.encode(draft)
        for(length in bytes.indices) assertNull(VideoDraftCodec.decode(bytes.copyOf(length),1000))
        assertNull(VideoDraftCodec.decode(bytes+byteArrayOf(0),1000))
        assertNull(VideoDraftCodec.decode(bytes.clone().apply { ByteBuffer.wrap(this).putInt(4,99) },1000))
        assertNull(VideoDraftCodec.decode(ByteArray(256001),1000))
        assertNull(VideoDraftCodec.decode(bytes.clone().apply { this[80]=0xC0.toByte() },1000))
        assertNull(VideoDraftCodec.decode(bytes.clone().apply { ByteBuffer.wrap(this).putInt(76,361) },1000))
    }
    @Test fun titleLimitsRejectWithoutMutatingTheProject() {
        val p = VideoEditProject(1000); val original=p.segments
        rejected { p.setTitle(0,"x".repeat(121)) };assertEquals(original,p.segments)
        rejected { p.setTitle(0,"a\nb\nc\nd") };rejected { p.setTitle(0,"bad\u0000title") }
        val cjk = VideoSegment(0,1000,title="中".repeat(120))
        rejected { p.restore(List(400){cjk}) };assertEquals(original,p.segments)
    }
    @Test fun maximumSupportedTitleBudgetFitsTheCodecBound() {
        val clips = List(1000){VideoSegment(0,1000,title="x".repeat(120))}
        val draft = VideoDraft(1000,clips,0,0,0,1000)
        val bytes=VideoDraftCodec.encode(draft);assertTrue(bytes.size<=256_000)
        assertEquals(draft,VideoDraftCodec.decode(bytes,1000))
    }
    @Test fun fadesUseFinalTimelineOffsetsAndNeverOverlapBeyondHalfAClip() {
        val clips=listOf(VideoSegment(0,2000),VideoSegment(0,4000,2f,fadeEdges=true))
        assertEquals(1f,VideoTimeline.fadeBrightness(clips,1_900_000),0f)
        assertEquals(0f,VideoTimeline.fadeBrightness(clips,2_000_000),0f)
        assertEquals(.5f,VideoTimeline.fadeBrightness(clips,2_250_000),.0001f)
        assertEquals(1f,VideoTimeline.fadeBrightness(clips,3_000_000),0f)
        assertEquals(.5f,VideoTimeline.fadeBrightness(clips,3_750_000),.0001f)
        assertEquals(0f,VideoTimeline.fadeBrightness(clips,4_000_000),0f)
        val short=listOf(VideoSegment(0,200,fadeEdges=true))
        assertEquals(1f,VideoTimeline.fadeBrightness(short,100_000),0f)
        assertEquals(.5f,VideoTimeline.fadeBrightness(short,50_000),.0001f)
    }
    @Test fun previewPositionsMapBackToTheCorrectSourceAndSpeed() {
        val clips=listOf(VideoSegment(5000,9000,2f),VideoSegment(1000,5000,.5f,sourceId="second"))
        assertEquals(VideoTimelinePosition(0,"primary",7000),VideoTimeline.locate(clips,1_000_000))
        assertEquals(VideoTimelinePosition(1,"second",1000),VideoTimeline.locate(clips,2_000_000))
        assertEquals(VideoTimelinePosition(1,"second",2000),VideoTimeline.locate(clips,4_000_000))
        assertEquals(4999L,VideoTimeline.locate(clips,Long.MAX_VALUE).sourceTimeMs)
        assertEquals(5000L,VideoTimeline.locate(clips,-10).sourceTimeMs)
    }
    @Test fun unicodeTitlesAndLongSourceReferencesFitTheRecoveryBudget() {
        val emoji=String(Character.toChars(0x1F600))
        val sources=List(31) { VideoSource("s$it","content://documents/"+emoji.repeat(500)+it,1000) }
        val clips=List(1000) { VideoSegment(0,1000,sourceId=if(it%32==0) "primary" else "s${it%32-1}",title=if(it<500) emoji.repeat(60) else "") }
        val draft=VideoDraft(1000,clips,0,0,0,1000,sources,VideoSoundtrack("content://audio/music",1000))
        val bytes=VideoDraftCodec.encode(draft)
        assertTrue(bytes.size <= 256_000);assertEquals(draft,VideoDraftCodec.decode(bytes,1000))
    }

}
