package com.honorablemobile

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Own local YAMNet session; shared native search environment is never closed or reconfigured. */
internal object VideoSoundModel {
    const val REVISION="f25b741c2f0bdc6d7e6db24b5fddda23347dbafd"
    const val BYTES=16093355L
    const val SHA256="d3835ffbbd4a1bb3e777f0ca217b5007907f5171dd5d17c4236b95b2af8f908e"
    const val LABEL_BYTES=14096L
    const val LABEL_SHA256="cdf24d193e196d9e95912a2667051ae203e92a2ba09449218ccb40ef787c6df2"
    const val BASE_URL="https://huggingface.co/audiomagic/yamnet-onnx/resolve/$REVISION/"
    class Stop {
        private var stopped=false
        private var active:OrtSession.RunOptions?=null
        @Synchronized fun cancel(){stopped=true;active?.setTerminate(true)}
        @Synchronized fun check(){if(stopped||Thread.currentThread().isInterrupted)throw CancellationException("Sound analysis stopped")}
        @Synchronized internal fun attach(run:OrtSession.RunOptions){check();check(active==null);active=run}
        @Synchronized internal fun detach(run:OrtSession.RunOptions){check(active===run);active=null}
    }
    fun verify(file:File,bytes:Long,sha256:String,stop:Stop){
        stop.check();require(file.isFile&&file.length()==bytes){"Complete the optional sound-model setup first"}
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use{input->val buffer=ByteArray(65536);while(true){stop.check();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
        require(digest.digest().joinToString(""){"%02x".format(it)}==sha256){"Sound model integrity check failed"};stop.check()
    }
    fun labels(file:File,stop:Stop):List<String>{
        verify(file,LABEL_BYTES,LABEL_SHA256,stop)
        return parseLabels(file.readText(Charsets.UTF_8))
    }
    internal fun parseLabels(text:String):List<String>{
        require(text.length<=20000)
        val lines=text.lineSequence().filter{it.isNotEmpty()}.toList()
        require(lines.size==522&&lines.first()=="index,mid,display_name")
        val mids=mutableSetOf<String>()
        return lines.drop(1).mapIndexed{index,line->
            val first=line.indexOf(',');val second=line.indexOf(',',first+1)
            require(first>0&&second>first&&line.substring(0,first)==index.toString())
            val mid=line.substring(first+1,second);require(mid.matches(Regex("/[mtg]/[a-zA-Z0-9_]+"))&&mids.add(mid))
            val raw=line.substring(second+1)
            val label=if(raw.startsWith('"')){
                require(raw.endsWith('"')&&raw.length>=2)
                val inner=raw.substring(1,raw.lastIndex);val decoded=inner.replace("\"\"","\"")
                require(decoded.replace("\"","\"\"")==inner);decoded
            }else{require('"' !in raw&&',' !in raw);raw}
            require(label.isNotBlank()&&label.length<=160&&'\r' !in label&&'\n' !in label);label
        }
    }
    fun recognize(model:File,pcm:FloatArray,sourceStartMs:Long,labels:List<String>,stop:Stop):List<VideoSoundEvents.Window>{
        stop.check();VideoSoundEvents.frames(pcm.size)
        require(pcm.all{it.isFinite()&&it in -1f..1f}){"Invalid original audio samples"}
        // Preserve waveform amplitude: Wav2Vec2-style mean/variance normalization is incorrect here.
        val owned=pcm.copyOf();verify(model,BYTES,SHA256,stop)
        val env=OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use{options->
            options.setIntraOpNumThreads(1);options.setInterOpNumThreads(1);stop.check()
            env.createSession(model.absolutePath,options).use{session->
                require(session.inputNames==setOf("waveform")&&session.outputNames==setOf("output_0","output_1","output_2"))
                OnnxTensor.createTensor(env,FloatBuffer.wrap(owned),longArrayOf(owned.size.toLong())).use{input->
                    OrtSession.RunOptions().use{run->
                        stop.attach(run)
                        try{
                            session.run(mapOf("waveform" to input),setOf("output_0"),run).use{result->
                                stop.check();val output=result.get("output_0").get()as?OnnxTensor?:error("Missing sound scores")
                                require(output.info.type==OnnxJavaType.FLOAT&&output.info.shape.contentEquals(longArrayOf(VideoSoundEvents.frames(owned.size).toLong(),521)))
                                val values=output.floatBuffer;val rows=Array(VideoSoundEvents.frames(owned.size)){FloatArray(521).also{values.get(it)}}
                                return VideoSoundEvents.propose(rows,owned.size,sourceStartMs,labels){stop.check()}
                            }
                        }finally{stop.detach(run)}
                    }
                }
            }
        }
    }
}
