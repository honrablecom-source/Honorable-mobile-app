package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

class VideoSpeechPcmTest {
    private fun floats(vararg values: Float) = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putFloat(it) }; flip() }
    private fun reject(action: () -> Unit) { try { action();fail("Expected invalid PCM refusal") } catch (_:IllegalArgumentException){} }
    @Test fun exactSelectedSourceCropAndStereoMixKeepOriginalBufferPosition() {
        val pcm=VideoSpeechPcm(1000,1500,16000)
        val input=ByteBuffer.allocate(16002*4*2+12).order(ByteOrder.LITTLE_ENDIAN)
        input.position(12);repeat(16002){input.putFloat(0.25f);input.putFloat(0.75f)};input.flip();input.position(5)
        pcm.append(input,12,16002*8,500000,2,true)
        assertEquals(5,input.position());val result=pcm.finish()
        assertEquals(8000,result.size);assertTrue(result.all{it==0.5f})
    }
    @Test fun sourceTimePacketsJoinExactlyAndPcm16ExtremesStayInRange() {
        val pcm=VideoSpeechPcm(2000,2500,8000)
        fun packet()=ByteBuffer.allocate(4000).order(ByteOrder.LITTLE_ENDIAN).apply{repeat(2000){putShort(if(it%2==0)Short.MIN_VALUE else Short.MAX_VALUE)};flip()}
        pcm.append(packet(),0,4000,2000000,1,false);pcm.append(packet(),0,4000,2250000,1,false)
        val result=pcm.finish();assertEquals(8000,result.size)
        assertEquals(-1f,result[0],0f);assertEquals(32767/32768f,result[2],0f)
        assertTrue(result.all{it in -1f..1f})
    }
    @Test fun missingFramesOverlapBackwardsAndBrokenFramesRefused() {
        val pcm=VideoSpeechPcm(0,500,16000)
        reject{pcm.finish()}
        val block=floats(*FloatArray(4000){0.1f});pcm.append(block,0,16000,0,1,true)
        reject{pcm.finish()};reject{pcm.append(block,0,16000,0,1,true)}
        val later=VideoSpeechPcm(0,500,16000);later.append(block,0,16000,250000,1,true)
        reject{later.append(block,0,16000,0,1,true)}
        reject{VideoSpeechPcm(0,500,16000).append(block,0,3,0,1,true)}
    }
    @Test fun malformedFormatsAndNonfiniteSamplesCannotReachModel() {
        reject{VideoSpeechPcm(0,20001,16000)};reject{VideoSpeechPcm(0,499,16000)};reject{VideoSpeechPcm(0,500,192000)}
        reject{VideoSpeechPcm(0,500,16000).append(floats(Float.NaN),0,4,0,1,true)}
        reject{VideoSpeechPcm(0,500,16000).append(floats(1.1f),0,4,0,1,true)}
    }
    @Test fun cancellationStopsPacketAndFinishWork() {
        val pcm=VideoSpeechPcm(0,500,16000)
        try{pcm.append(floats(0f),0,4,0,1,true){throw CancellationException()};fail()}catch(_:CancellationException){}
        try{pcm.finish{throw CancellationException()};fail()}catch(_:CancellationException){}
    }
}
