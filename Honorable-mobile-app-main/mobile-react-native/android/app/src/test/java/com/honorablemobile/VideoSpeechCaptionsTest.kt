package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class VideoSpeechCaptionsTest {
    private fun scores(tokens: Map<Int, Int> = emptyMap()) = Array(49) { frame -> FloatArray(32) { if (it == (tokens[frame] ?: 0)) 2f else -3f } }
    private fun decode(tokens: Map<Int, Int>, start: Long = 1000) = VideoSpeechCaptions.decode(scores(tokens), 16000, start)
    private fun reject(action: () -> Unit) { try { action(); fail("Expected refusal") } catch (_: IllegalArgumentException) {} }
    @Test fun silenceIsEmptyRatherThanInventedText() { assertEquals(emptyList<VideoCaption>(), decode(emptyMap()).captions) }
    @Test fun repeatedTokensCollapseButBlankSeparatesDoubledLetters() {
        val result = decode(mapOf(0 to 11, 1 to 11, 2 to 5, 3 to 15, 5 to 15, 6 to 8))
        assertEquals("HELLO", result.words.single().text)
        assertEquals(VideoCaption(1000, 1145, "HELLO"), result.captions.single())
    }
    @Test fun approximateTimestampsRetainOriginalSourceOffsetAndSpaces() {
        val result = decode(mapOf(2 to 11, 3 to 10, 4 to 4, 10 to 18, 11 to 5), 50000)
        assertEquals(listOf(VideoSpeechCaptions.Word(50040,50085,"HI"),VideoSpeechCaptions.Word(50200,50245,"WE")),result.words)
        assertEquals(VideoCaption(50040,50245,"HI WE"),result.captions.single())
    }
    @Test fun longPauseCreatesSeparateReviewCues() {
        val result=decode(mapOf(0 to 11, 1 to 10, 2 to 4, 45 to 18, 46 to 5))
        assertEquals(listOf("HI","WE"),result.captions.map { it.text })
        VideoCaptions.validate(result.captions)
    }
    @Test fun malformedModelsAndSourceBoundsRefused() {
        reject { VideoSpeechCaptions.decode(scores().drop(1).toTypedArray(),16000,0) }
        reject { VideoSpeechCaptions.decode(Array(49){FloatArray(31)},16000,0) }
        reject { VideoSpeechCaptions.decode(scores().also { it[0][0]=Float.NaN },16000,0) }
        reject { decode(mapOf(0 to 3)) }
        reject { decode(emptyMap(),-1) }
        reject { decode(emptyMap(),Int.MAX_VALUE.toLong()) }
        reject { VideoSpeechCaptions.decode(scores(),320001,0) }
    }
    @Test fun normalizationMatchesIndependentKnownPopulationVarianceAndKeepsInput() {
        val raw=FloatArray(8000) { if(it%2==0)0.25f else 0.75f };val old=raw.copyOf()
        val result=VideoSpeechCaptions.normalize(raw)
        val expected=(0.25/kotlin.math.sqrt(0.0625+1e-7)).toFloat()
        for(i in result.indices)assertEquals(if(i%2==0)-expected else expected,result[i],1e-6f)
        assertArrayEquals(old,raw,0f)
        assertTrue(VideoSpeechCaptions.normalize(FloatArray(8000){0.4f}).all{it==0f})
        reject { VideoSpeechCaptions.normalize(FloatArray(7999)) }
        reject { VideoSpeechCaptions.normalize(FloatArray(8000){Float.POSITIVE_INFINITY}) }
        reject { VideoSpeechCaptions.normalize(FloatArray(8000){1.01f}) }
    }
    @Test fun stopRefusesPartialNormalizationOrPartialCaptionResult() {
        for(action in listOf<()->Unit>({VideoSpeechCaptions.normalize(FloatArray(16000)){true}},{VideoSpeechCaptions.decode(scores(),16000,0){true}})) {
            try { action();fail("Expected cancellation") } catch (_:CancellationException) {}
        }
    }
    @Test fun maximumDurationFrameCountAndFrameEndStayBounded() {
        val count=320000;val frames=(count-400)/320+1
        val result=VideoSpeechCaptions.decode(Array(frames){frame->FloatArray(32){if(it==if(frame==frames-1)5 else 0)1f else 0f}},count,100000)
        assertTrue(result.captions.single().endMs<=120000)
        assertTrue(result.captions.single().startMs>=100000)
    }
}
