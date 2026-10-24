package com.honorablemobile

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class VideoCaptionsTest {
    private val cue = VideoCaption(1000, 4000, "Hello\nworld")
    private fun rejected(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun utf8BomAndWindowsSrtKeepUnicodeAndSortTimes() {
        val srt = "\uFEFF2\r\n00:00:03,000 --> 00:00:04,000\r\n世界\r\n\r\n1\r\n00:00:01,000 --> 00:00:02,000\r\nHello\r\n"
        assertEquals(listOf(VideoCaption(1000,2000,"Hello"),VideoCaption(3000,4000,"世界")),VideoCaptions.parseSrt(srt.toByteArray(),5000))
    }

    @Test fun invalidEncodingOverlapOutOfRangeAndBadTimestampAreRejected() {
        rejected { VideoCaptions.parseSrt(byteArrayOf(0xc3.toByte(),0x28),5000) }
        for(srt in listOf("1\n00:61:00,000 --> 00:62:00,000\nx", "1\n00:00:01,000 --> 00:00:06,000\nx", "1\n00:00:01,000 --> 00:00:03,000\nx\n\n2\n00:00:02,000 --> 00:00:04,000\ny")) rejected { VideoCaptions.parseSrt(srt.toByteArray(),5000) }
    }

    @Test fun oversizedInputAndControlTextDoNotReachTheProject() {
        rejected { VideoCaptions.parseSrt(ByteArray(VideoCaptions.MAX_SRT_BYTES+1),5000) }
        rejected { VideoCaption(0,1000,"bad\u0000text") }
        rejected { VideoCaption(0,1000,"\uD800") }
        rejected { VideoCaption(0,1000,"x".repeat(241)) }
        rejected { VideoCaption(0,1000,"a\nb\nc\nd") }
        rejected { VideoCaptions.validate(List(201){VideoCaption(it*10L,it*10L+1,"x")}) }
    }

    @Test fun sourceCuesFollowTrimSpeedReorderAndRepeat() {
        val a=VideoSegment(2000,5000,speed=2f,captions=listOf(cue))
        val b=VideoSegment(0,2000,speed=.5f,captions=listOf(cue.copy(text="Second")))
        assertEquals(listOf(TimelineCaption(0,1_000_000,"Hello\nworld"),TimelineCaption(3_500_000,5_500_000,"Second")),VideoCaptions.resolve(listOf(a,b)))
        assertEquals(listOf(TimelineCaption(2_000_000,4_000_000,"Second"),TimelineCaption(4_000_000,5_000_000,"Hello\nworld")),VideoCaptions.resolve(listOf(b,a)))
    }

    @Test fun splitPreservesCaptionContinuityAndExactEndIsInactive() {
        val clips=listOf(VideoSegment(0,2000,captions=listOf(cue)),VideoSegment(2000,5000,captions=listOf(cue)))
        val resolved=VideoCaptions.resolve(clips)
        assertEquals(listOf(TimelineCaption(1_000_000,4_000_000,cue.text)),resolved)
        assertNull(VideoCaptions.at(resolved,999_999));assertNotNull(VideoCaptions.at(resolved,1_000_000));assertNull(VideoCaptions.at(resolved,4_000_000))
    }

    @Test fun gapsStayEmptyAndCuesOutsideTrimAreNotRendered() {
        val cues=listOf(VideoCaption(0,1000,"before"),VideoCaption(2000,3000,"inside"),VideoCaption(4000,5000,"after"))
        val result=VideoCaptions.resolve(listOf(VideoSegment(1500,3500,captions=cues)))
        assertEquals(listOf(TimelineCaption(500_000,1_500_000,"inside")),result)
        assertNull(VideoCaptions.at(result,0));assertNull(VideoCaptions.at(result,1_500_000))
    }

    @Test fun captionChangesUndoRedoAndSourceProtectionSurviveClipOperations() {
        val p=VideoEditProject(5000);p.setCaptions(0,listOf(cue),"content://subtitles/one")
        p.split(0,2000);p.setSpeed(1,2f);p.move(1,0)
        val expected=p.segments
        assertTrue(p.undo());assertTrue(p.redo());assertEquals(expected,p.segments)
        p.setCaptions(0,emptyList());assertEquals(setOf("content://subtitles/one"),p.captionSourceUris())
        assertTrue(p.undo());assertEquals(listOf(cue),p.segments[0].captions)
    }

    @Test fun invalidCaptionEditAndLimitsAreAtomic() {
        val p=VideoEditProject(5000)
        rejected { p.setCaptions(0,listOf(VideoCaption(0,6000,"outside"))) }
        assertFalse(p.canUndo);assertTrue(p.segments.single().captions.isEmpty())
        val many=List(200){VideoCaption(it*20L,it*20L+10,"x".repeat(120))}
        p.setCaptions(0,many);p.duplicate(0)
        val before=p.segments
        rejected { p.duplicate(0) }
        assertEquals(before,p.segments)
    }

    @Test fun v6DraftRoundTripsCaptionTextAndProtectedImportReference() {
        val clip=VideoSegment(0,5000,captions=listOf(cue),captionSourceUri="content://subtitles/one")
        val draft=VideoDraft(5000,listOf(clip),0,0,0,5000)
        val bytes=VideoDraftCodec.encode(draft)
        assertEquals(6,java.nio.ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(draft,VideoDraftCodec.decode(bytes,5000))
        assertNull(VideoDraftCodec.decode(bytes+byteArrayOf(0),5000))
        assertNull(VideoDraftCodec.decode(bytes.copyOf(bytes.size-2),5000))
    }

    @Test fun oldDraftEncodingAndNoCaptionStateRemainCompatible() {
        val old=VideoDraft(5000,listOf(VideoSegment(0,5000)),0,0,0,5000)
        val bytes=VideoDraftCodec.encode(old)
        assertEquals(1,java.nio.ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(old,VideoDraftCodec.decode(bytes,5000))
    }

    @Test fun repeatedLongSubtitleReferencesRemainRecoverableAndOversizedEditsAreAtomic() {
        val uri="content://subtitles/"+"x".repeat(2048-"content://subtitles/".length)
        val clip=VideoSegment(0,5000,captions=listOf(VideoCaption(0,1000,"x")),captionSourceUri=uri)
        val clips=List(120){clip}
        val p=VideoEditProject(5000);p.restore(clips)
        val draft=VideoDraft(5000,p.segments,0,0,0,5000)
        val bytes=VideoDraftCodec.encode(draft)
        assertTrue(bytes.size<20_000);assertEquals(draft,VideoDraftCodec.decode(bytes,5000))
        val large=VideoSegment(0,5000,title="x".repeat(120),captions=listOf(VideoCaption(0,1000,"字".repeat(120))))
        p.restore(List(120){large})
        val before=p.segments
        rejected { p.applyDraft(List(1000){large.copy(captions=listOf(VideoCaption(0,1000,"x")))}) }
        assertEquals(before,p.segments);assertFalse(p.canUndo)
    }

    @Test fun outputSrtUsesEditedTimelineRatherThanSourceTimes() {
        val clip=VideoSegment(2000,5000,speed=2f,captions=listOf(cue))
        val text=VideoCaptions.exportSrt(listOf(clip)).toString(Charsets.UTF_8)
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\nHello\nworld\n",text)
        assertEquals(listOf(VideoCaption(0,1000,cue.text)),VideoCaptions.parseSrt(text.toByteArray(),1500))
        assertEquals(1234L,VideoCaptions.seconds("1.234"));assertEquals(1010L,VideoCaptions.seconds("1.01"))
        for(value in listOf("NaN","-1","1.0001","1e3"))rejected{VideoCaptions.seconds(value)}
    }

    @Test fun boundedStreamReaderAcceptsExactLimitAndRejectsExtraByte() {
        assertEquals(VideoCaptions.MAX_SRT_BYTES,VideoSubtitleDocuments.readBounded(ByteArrayInputStream(ByteArray(VideoCaptions.MAX_SRT_BYTES))).size)
        rejected { VideoSubtitleDocuments.readBounded(ByteArrayInputStream(ByteArray(VideoCaptions.MAX_SRT_BYTES+1))) }
    }
}
