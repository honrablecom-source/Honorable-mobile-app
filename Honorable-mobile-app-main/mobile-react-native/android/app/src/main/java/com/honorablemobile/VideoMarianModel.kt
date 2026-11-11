package com.honorablemobile

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Private translation sessions; never closes/reconfigures the existing shared search environment. */
internal class VideoMarianModel private constructor(private val encoder:OrtSession,private val decoder:OrtSession,
    private val tokenizer:VideoMarianTokenizer,private val vocabulary:List<String>):Closeable {
    private var closed=false
    class Stop {
        private var stopped=false
        private var active:OrtSession.RunOptions?=null
        @Synchronized fun cancel(){stopped=true;active?.setTerminate(true)}
        @Synchronized fun check(){if(stopped||Thread.currentThread().isInterrupted)throw CancellationException("Translation stopped")}
        @Synchronized internal fun attach(run:OrtSession.RunOptions){check();check(active==null);active=run}
        @Synchronized internal fun detach(run:OrtSession.RunOptions){check(active===run);active=null}
    }
    data class Artifact(val name:String,val bytes:Long,val sha256:String,val url:String)
    companion object {
        private const val CONVERTED="https://huggingface.co/Xenova/opus-mt-en-es/resolve/4b002a4c7edd54a7ced58877258b87f7efd3f892/"
        private const val ORIGINAL="https://huggingface.co/Helsinki-NLP/opus-mt-en-es/resolve/5bc4493d463cf000c1f0b50f8d56886a392ed4ab/"
        val artifacts=listOf(
            Artifact("encoder_model_quantized.onnx",52899742,"03434edd1147e6c49e22066e9dc5888235e99cc35a6711d1c4ea5b06455d5da9",CONVERTED+"onnx/encoder_model_quantized.onnx"),
            Artifact("decoder_model_merged_quantized.onnx",60212804,"58ee5ddd6e22d1693d8722b90c1486afb93700a4376cf28fd74175052ad530ae",CONVERTED+"onnx/decoder_model_merged_quantized.onnx"),
            Artifact("source.spm",801636,"4dd547c24816a335e7b0b2e63376a8f1b3cbfc671eda5ab808dd44fdadaa8791",ORIGINAL+"source.spm"),
            Artifact("vocab.json",1590040,"257f346d7a6b2ecceafcca8ba05648ce2fd68dfaf105fb0e913dca7198f3f6d5",ORIGINAL+"vocab.json")
        )
        fun verify(file:File,artifact:Artifact,stop:Stop){
            stop.check();require(file.isFile&&file.length()==artifact.bytes){"Complete the optional translation model setup first"}
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use{input->val buffer=ByteArray(65536);while(true){stop.check();val size=input.read(buffer);if(size<0)break;digest.update(buffer,0,size)}}
            require(digest.digest().joinToString(""){"%02x".format(it)}==artifact.sha256){"Translation model integrity check failed"};stop.check()
        }
        fun open(directory:File,tokenizer:VideoMarianTokenizer,joint:Map<String,Int>,stop:Stop):VideoMarianModel{
            for(spec in artifacts)verify(File(directory,spec.name),spec,stop)
            require(joint.size==65001&&joint.values.toSet().size==65001&&joint["</s>"]==0&&joint["<unk>"]==1&&joint["<pad>"]==65000)
            val reverse=Array(65001){""};for((text,id)in joint){require(id in reverse.indices);reverse[id]=text};require(reverse.none{it.isEmpty()})
            val environment=OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use{options->
                options.setIntraOpNumThreads(1);options.setInterOpNumThreads(1);stop.check()
                val encoder=environment.createSession(File(directory,artifacts[0].name).absolutePath,options)
                try{
                    val decoder=environment.createSession(File(directory,artifacts[1].name).absolutePath,options)
                    try{stop.check();return VideoMarianModel(encoder,decoder,tokenizer,reverse.toList())}catch(failure:Throwable){decoder.close();throw failure}
                }catch(failure:Throwable){encoder.close();throw failure}
            }
        }
        internal fun reorder(data:FloatArray,beams:Int,parents:IntArray):FloatArray{
            require(beams in 1..4&&data.size%beams==0&&parents.size in 1..4&&parents.all{it in 0 until beams})
            val width=data.size/beams
            return FloatArray(width*parents.size).also{copy->for((row,parent)in parents.withIndex())data.copyInto(copy,row*width,parent*width,(parent+1)*width)}
        }
    }
    override fun close(){if(!closed){closed=true;try{decoder.close()}finally{encoder.close()}}}

    fun translate(text:String,stop:Stop):String {
        check(!closed);stop.check();require(text.isNotBlank()&&text.length<=240)
        val source=tokenizer.encode(text,512){stop.check()};val mask=LongArray(source.size){1}
        val env=OrtEnvironment.getEnvironment();val beam=VideoMarianBeam();var batch=1
        var nextTokens=longArrayOf(65000);var parents=intArrayOf(0)
        OrtSession.RunOptions().use{run->
            stop.attach(run)
            try{
                OnnxTensor.createTensor(env,LongBuffer.wrap(source),longArrayOf(1,source.size.toLong())).use{ids->
                    OnnxTensor.createTensor(env,LongBuffer.wrap(mask),longArrayOf(1,source.size.toLong())).use{attention->
                        encoder.run(mapOf("input_ids" to ids,"attention_mask" to attention),run).use{encoded->
                            val hidden=floats(tensor(encoded,"last_hidden_state"),longArrayOf(1,source.size.toLong(),512))
                            var initialEncoder:List<FloatArray>?=null;var decoderCache:List<FloatArray>?=null;var pastLength=0
                            for(step in 0 until 511){
                                stop.check();val allocations=mutableListOf<OnnxTensor>()
                                fun floatInput(data:FloatArray,shape:LongArray)=OnnxTensor.createTensor(env,FloatBuffer.wrap(data),shape).also{allocations.add(it)}
                                fun longInput(data:LongArray,shape:LongArray)=OnnxTensor.createTensor(env,LongBuffer.wrap(data),shape).also{allocations.add(it)}
                                try{
                                    val shape=longArrayOf(batch.toLong(),8,pastLength.toLong(),64)
                                    val input=linkedMapOf<String,OnnxTensor>()
                                    input["input_ids"]=longInput(nextTokens,longArrayOf(batch.toLong(),1))
                                    input["encoder_attention_mask"]=longInput(LongArray(batch*mask.size){1},longArrayOf(batch.toLong(),mask.size.toLong()))
                                    input["encoder_hidden_states"]=floatInput(reorder(hidden,1,IntArray(batch){0}),longArrayOf(batch.toLong(),source.size.toLong(),512))
                                    input["use_cache_branch"]=OnnxTensor.createTensor(env,booleanArrayOf(step>0)).also{allocations.add(it)}
                                    for(layer in 0 until 6)for(kv in 0..1){
                                        val suffix=if(kv==0)"key"else"value";val index=layer*2+kv
                                        input["past_key_values.$layer.decoder.$suffix"]=floatInput(decoderCache?.get(index)?:FloatArray(0),shape)
                                        input["past_key_values.$layer.encoder.$suffix"]=floatInput(initialEncoder?.get(index)?:FloatArray(0),longArrayOf(batch.toLong(),8,if(step==0)0L else source.size.toLong(),64))
                                    }
                                    decoder.run(input,run).use{output->
                                        stop.check()
                                        val values=floats(tensor(output,"logits"),longArrayOf(batch.toLong(),1,65001))
                                        val selection=beam.advance((0 until batch).map{values.copyOfRange(it*65001,(it+1)*65001)}){stop.check()}
                                        if(beam.done){
                                            val result=beam.result();check(result.last()==0)
                                            val translated=result.dropLast(1).joinToString(""){vocabulary[it]}.replace("▁"," ").trim()
                                            require(translated.isNotBlank()&&translated.length<=8192){"No complete bounded translation was produced"}
                                            return translated
                                        }
                                        parents=selection.parents;nextTokens=selection.tokens
                                        val oldBatch=batch;pastLength++
                                        decoderCache=(0 until 12).map{index->val layer=index/2;val suffix=if(index%2==0)"key"else"value"
                                            reorder(floats(tensor(output,"present.$layer.decoder.$suffix"),longArrayOf(oldBatch.toLong(),8,pastLength.toLong(),64)),oldBatch,parents)}
                                        if(initialEncoder==null)initialEncoder=(0 until 12).map{index->val layer=index/2;val suffix=if(index%2==0)"key"else"value"
                                            reorder(floats(tensor(output,"present.$layer.encoder.$suffix"),longArrayOf(1,8,source.size.toLong(),64)),1,IntArray(parents.size){0})}
                                        // Encoder KV is identical for all beams and deliberately retained; cached branch emits placeholders.
                                        batch=parents.size
                                    }
                                }finally{allocations.asReversed().forEach{it.close()}}
                            }
                            error("Translation did not reach a complete result")
                        }
                    }
                }
            }finally{stop.detach(run)}
        }
    }
    private fun tensor(result:OrtSession.Result,name:String)=result.get(name).get()as?OnnxTensor?:error("Missing translation tensor")
    private fun floats(tensor:OnnxTensor,shape:LongArray):FloatArray{
        require(tensor.info.type==OnnxJavaType.FLOAT&&tensor.info.shape.contentEquals(shape)){"Unexpected translation model tensor shape"}
        val buffer=tensor.floatBuffer;return FloatArray(buffer.remaining()).also{buffer.get(it);require(it.all{value->value.isFinite()})}
    }
}
