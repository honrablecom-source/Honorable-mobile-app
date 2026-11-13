package app.honorable.editor

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Official MIT MI-GAN pipeline includes its crop/resize/normalization/reconstruction/postprocessing. */
internal object PhotoEraseModel {
    const val REVISION="406830d0fa60666da0071c342ad2fbc8f30c5c64"
    const val BYTES=28079181L
    const val SHA256="6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b"
    const val ASSET="inpaint/migan_pipeline_v2.onnx"
    class Stop {
        private var stopped=false
        private var active:OrtSession.RunOptions?=null
        @Synchronized fun cancel(){stopped=true;active?.setTerminate(true)}
        @Synchronized fun check(){if(stopped||Thread.currentThread().isInterrupted)throw CancellationException("Object removal stopped")}
        @Synchronized internal fun attach(options:OrtSession.RunOptions){check();check(active==null);active=options}
        @Synchronized internal fun detach(options:OrtSession.RunOptions){check(active===options);active=null}
    }
    data class Input(val pixels:IntArray,val selected:BooleanArray,val image:ByteArray,val mask:ByteArray)
    fun prepare(pixels:IntArray,width:Int,height:Int,painted:BooleanArray,current:()->Unit={}):Input{
        require(width in 16..1024&&height in 16..1024&&pixels.size==width*height&&painted.size==pixels.size)
        val owned=pixels.copyOf();val selection=painted.copyOf();val chosen=selection.count{it}
        require(chosen>=1&&chosen.toLong()*4<=selection.size.toLong()*3){"Paint an object while leaving some surrounding image visible"}
        val count=owned.size;val image=ByteArray(count*3);val mask=ByteArray(count)
        for(i in owned.indices){
            if(i%4096==0)current();val pixel=owned[i];val alpha=pixel ushr 24
            for(channel in 0..2){val value=(pixel ushr (16-channel*8)) and 255;image[channel*count+i]=((value*alpha+255*(255-alpha)+127)/255).toByte()}
            mask[i]=if(selection[i])0 else 255.toByte() // Official pipeline: zero is the hole.
        }
        current();return Input(owned,selection,image,mask)
    }
    fun compose(input:Input,generated:ByteArray,current:()->Unit={}):IntArray{
        val count=input.pixels.size;require(generated.size==count*3&&input.selected.size==count)
        return IntArray(count){i->
            if(i%4096==0)current()
            if(!input.selected[i])input.pixels[i] else (255 shl 24) or ((generated[i].toInt() and 255) shl 16) or ((generated[count+i].toInt() and 255) shl 8) or (generated[2*count+i].toInt() and 255)
        }
    }
    fun verify(file:File,stop:Stop){
        stop.check();require(file.isFile&&file.length()==BYTES){"The bundled eraser model is unavailable"};val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use{input->val buffer=ByteArray(65536);while(true){stop.check();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
        require(digest.digest().joinToString(""){"%02x".format(it)}==SHA256){"Eraser model integrity check failed"};stop.check()
    }
    fun erase(model:File,pixels:IntArray,width:Int,height:Int,painted:BooleanArray,stop:Stop):IntArray{
        stop.check();val input=prepare(pixels,width,height,painted){stop.check()};verify(model,stop)
        val environment=OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use{options->
            options.setIntraOpNumThreads(1);options.setInterOpNumThreads(1);stop.check()
            environment.createSession(model.absolutePath,options).use{session->
                require(session.inputNames==setOf("image","mask")&&session.outputNames==setOf("result"))
                OnnxTensor.createTensor(environment,ByteBuffer.wrap(input.image),longArrayOf(1,3,height.toLong(),width.toLong()),OnnxJavaType.UINT8).use{image->
                    OnnxTensor.createTensor(environment,ByteBuffer.wrap(input.mask),longArrayOf(1,1,height.toLong(),width.toLong()),OnnxJavaType.UINT8).use{mask->
                        OrtSession.RunOptions().use{run->
                            stop.attach(run)
                            try{
                                session.run(mapOf("image" to image,"mask" to mask),run).use{result->
                                    stop.check();val output=result.get("result").get()as?OnnxTensor?:error("Missing reconstructed image")
                                    require(output.info.type==OnnxJavaType.UINT8&&output.info.shape.contentEquals(longArrayOf(1,3,height.toLong(),width.toLong())))
                                    val generated=ByteArray(input.image.size);output.byteBuffer.get(generated)
                                    return compose(input,generated){stop.check()}
                                }
                            }finally{stop.detach(run)}
                        }
                    }
                }
            }
        }
    }
}
