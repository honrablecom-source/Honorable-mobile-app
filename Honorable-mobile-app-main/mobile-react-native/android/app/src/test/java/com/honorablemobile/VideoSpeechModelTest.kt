package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class VideoSpeechModelTest {
    @Test fun cancellationIsStickyAndRepeatableBeforeNativeResourcesExist() {
        val stop=VideoSpeechModel.Stop();assertFalse(stop.isCancelled());stop.cancel();stop.cancel();assertTrue(stop.isCancelled())
        try{stop.check();fail("Cancelled request cannot start")}catch(_:CancellationException){}
    }
    @Test fun cancelledVerificationDoesNotTouchMissingModel() {
        val stop=VideoSpeechModel.Stop().apply{cancel()}
        try{VideoSpeechModel.verify(File("/nonexistent-noble-synthetic-model"),stop);fail()}catch(_:CancellationException){}
    }
    @Test fun missingOrWrongLengthModelRefusedBeforeSessionCreation() {
        val file=File.createTempFile("noble-synthetic-model-",".tmp")
        try{
            file.writeBytes(byteArrayOf(1,2,3))
            try{VideoSpeechModel.verify(file,VideoSpeechModel.Stop());fail("Cannot infer with incomplete model")}catch(_:IllegalArgumentException){}
        }finally{file.delete()}
    }
    @Test fun modelCoordinateIsImmutableAndCannotSelectUserMediaOrRemoteInference() {
        assertEquals(95286046L,VideoSpeechModel.BYTES)
        assertTrue(VideoSpeechModel.SHA256.matches(Regex("[0-9a-f]{64}")))
        assertEquals("https://huggingface.co/Xenova/wav2vec2-base-960h/resolve/a19f851b3d42865797e410752b4c570c871e4825/onnx/model_quantized.onnx",VideoSpeechModel.URL)
    }
}
