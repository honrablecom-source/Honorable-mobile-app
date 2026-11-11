package com.honorablemobile

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.util.concurrent.CancellationException

/** Original-source speech PCM, before playback speed, mute, effects or soundtrack. Owns/recycles every codec/extractor and output buffer. */
internal object VideoSpeechAudio {
    fun decode(context:Context,uri:Uri,startMs:Long,endMs:Long,cancelled:()->Boolean):FloatArray{
        require(uri.scheme=="content"){"Choose a local source video"}
        require(startMs>=0&&endMs>startMs&&endMs-startMs in 500..20_000&&endMs<=Int.MAX_VALUE){"Choose 0.5–20 source seconds"}
        val startedAt=System.nanoTime()
        fun current(){if(cancelled()||Thread.currentThread().isInterrupted)throw CancellationException("Speech audio decode stopped");require(System.nanoTime()-startedAt<45_000_000_000L){"Speech audio decode took too long. Try a shorter range."}}
        current();val extractor=MediaExtractor();var decoder:MediaCodec?=null;var started=false
        try{
            extractor.setDataSource(context,uri,null);current()
            val audioTracks=(0 until extractor.trackCount).filter{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}
            require(audioTracks.isNotEmpty()){"This video has no readable original audio track"}
            require(audioTracks.size==1){"Choose a source with one audio track; multi-track analysis is not supported"}
            val track=audioTracks.single()
            val inputFormat=extractor.getTrackFormat(track)
            val mime=requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
            if(inputFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))require(inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) in 1..1_048_576){"Audio packets exceed analysis limits"}
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
            extractor.selectTrack(track);extractor.seekTo(startMs*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);current()
            val codec=MediaCodec.createDecoderByType(mime);decoder=codec;codec.configure(inputFormat,null,null,0);codec.start();started=true
            val info=MediaCodec.BufferInfo();var inputDone=false;var outputDone=false
            var energy:VideoSpeechPcm?=null;var sampleRate=0;var channels=0;var pcmEncoding=AudioFormat.ENCODING_PCM_16BIT
            fun format(){
                val output=codec.outputFormat;val rate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE);val count=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                require(rate in 8_000..48_000&&count in 1..8){"Unsupported audio rate or channel count"}
                val encoding=if(output.containsKey(MediaFormat.KEY_PCM_ENCODING))output.getInteger(MediaFormat.KEY_PCM_ENCODING)else AudioFormat.ENCODING_PCM_16BIT
                require(encoding==AudioFormat.ENCODING_PCM_16BIT||encoding==AudioFormat.ENCODING_PCM_FLOAT){"Unsupported decoded audio sample format"}
                require(energy==null||(sampleRate==rate&&channels==count&&pcmEncoding==encoding)){"Audio format changed inside this clip"}
                sampleRate=rate;channels=count;pcmEncoding=encoding
                if(energy==null)energy=VideoSpeechPcm(startMs,endMs,rate)
            }
            while(!outputDone){
                current()
                if(!inputDone){
                    val slot=codec.dequeueInputBuffer(10_000)
                    if(slot>=0){
                        val input=requireNotNull(codec.getInputBuffer(slot));input.clear()
                        val time=extractor.sampleTime
                        if(time<0||time>=endMs*1000){codec.queueInputBuffer(slot,0,0,endMs*1000,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                        else{
                            require(time>=startMs*1000-5_000_000){"Audio seek starts too far before this range. Choose a shorter source."}
                            require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED==0){"Encrypted audio cannot be decoded"}
                            require(input.remaining()<=1_048_576){"Audio packet exceeds decoder limits"}
                            val bytes=extractor.readSampleData(input,0);current();require(bytes<=input.capacity()&&bytes<=1_048_576){"Audio packet exceeds decoder limits"}
                            if(bytes<0){codec.queueInputBuffer(slot,0,0,endMs*1000,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                            else{codec.queueInputBuffer(slot,0,bytes,time,0);extractor.advance()}
                        }
                    }
                }
                val slot=codec.dequeueOutputBuffer(info,10_000)
                when{
                    slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->format()
                    slot>=0->{
                        try{
                            current()
                            if(info.size>0&&info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0){
                                if(energy==null)format()
                                val output=requireNotNull(codec.getOutputBuffer(slot))
                                requireNotNull(energy).append(output,info.offset,info.size,info.presentationTimeUs,channels,pcmEncoding==AudioFormat.ENCODING_PCM_FLOAT){current()}
                            }
                            outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
                        }finally{codec.releaseOutputBuffer(slot,false)}
                    }
                }
            }
            current();return requireNotNull(energy){"No original audio could be decoded"}.finish{current()}
        }finally{
            if(started)runCatching{decoder?.stop()}
            runCatching{decoder?.release()};runCatching{extractor.release()}
        }
    }
}
