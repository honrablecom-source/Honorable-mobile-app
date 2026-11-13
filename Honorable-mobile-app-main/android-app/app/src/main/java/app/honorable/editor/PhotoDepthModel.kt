package app.honorable.editor

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt

/** Pinned Apache2 Depth Anything V2 Small. Separate sessions, private image input, no network. */
internal object PhotoDepthModel {
    const val REVISION="4472b7362082ad9968fee890ca0f1e5aca36b93d"
    const val BYTES=27258801L
    const val SHA256="fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d"
    const val ASSET="depth/model_quantized.onnx"
    class Stop {
        private var stopped=false
        private var active:OrtSession.RunOptions?=null
        @Synchronized fun cancel(){stopped=true;active?.setTerminate(true)}
        @Synchronized fun check(){if(stopped||Thread.currentThread().isInterrupted)throw CancellationException("Depth creation stopped")}
        @Synchronized internal fun attach(options:OrtSession.RunOptions){check();check(active==null);active=options}
        @Synchronized internal fun detach(options:OrtSession.RunOptions){check(active===options);active=null}
    }
    data class Input(val width:Int,val height:Int,val values:FloatArray)
    fun verify(file:File,stop:Stop){
        stop.check();require(file.isFile&&file.length()==BYTES){"The bundled depth model is unavailable"}
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use{input->val buffer=ByteArray(65536);while(true){stop.check();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
        require(digest.digest().joinToString(""){"%02x".format(it)}==SHA256){"Depth model integrity check failed"};stop.check()
    }
    /** Aspect-preserving mobile input, 14px patch multiples, at most518px on either edge. */
    fun prepare(pixels:IntArray,width:Int,height:Int,current:()->Unit={}):Input{
        require(width in 1..512&&height in 1..512&&pixels.size==width*height)
        require(width.toDouble()/height in .25..4.0){"Use a photo between1:4 and4:1 for depth animation"}
        val scale=518.0/maxOf(width,height)
        fun edge(value:Int)=((value*scale/14).roundToInt()*14).coerceIn(14,518)
        val outWidth=edge(width);val outHeight=edge(height);val owned=pixels.copyOf();val count=outWidth*outHeight
        val means=doubleArrayOf(.485,.456,.406);val std=doubleArrayOf(.229,.224,.225)
        fun channel(pixel:Int,index:Int):Double{
            val alpha=(pixel ushr 24)/255.0;val value=(pixel ushr (16-index*8)) and 255
            return value*alpha+255*(1-alpha) // White analysis matte; exported PNG retains its own alpha.
        }
        val result=FloatArray(count*3)
        for(y in 0 until outHeight){
            current();val sy=((y+.5)*height/outHeight-.5).coerceIn(0.0,(height-1).toDouble());val top=sy.toInt();val bottom=minOf(top+1,height-1);val fy=sy-top
            for(x in 0 until outWidth){
                val sx=((x+.5)*width/outWidth-.5).coerceIn(0.0,(width-1).toDouble());val left=sx.toInt();val right=minOf(left+1,width-1);val fx=sx-left
                for(c in 0..2){
                    val a=channel(owned[top*width+left],c)*(1-fx)+channel(owned[top*width+right],c)*fx
                    val b=channel(owned[bottom*width+left],c)*(1-fx)+channel(owned[bottom*width+right],c)*fx
                    result[c*count+y*outWidth+x]=(((a*(1-fy)+b*fy)/255-means[c])/std[c]).toFloat()
                }
            }
        }
        current();return Input(outWidth,outHeight,result)
    }
    fun estimate(model:File,pixels:IntArray,width:Int,height:Int,stop:Stop):PhotoDepthMesh.Grid{
        stop.check();val input=prepare(pixels,width,height){stop.check()};verify(model,stop)
        val environment=OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use{options->
            options.setIntraOpNumThreads(1);options.setInterOpNumThreads(1);stop.check()
            environment.createSession(model.absolutePath,options).use{session->
                require(session.inputNames==setOf("pixel_values")&&session.outputNames==setOf("predicted_depth"))
                OnnxTensor.createTensor(environment,FloatBuffer.wrap(input.values),longArrayOf(1,3,input.height.toLong(),input.width.toLong())).use{tensor->
                    OrtSession.RunOptions().use{run->
                        stop.attach(run)
                        try{
                            session.run(mapOf("pixel_values" to tensor),run).use{result->
                                stop.check();val output=result.get("predicted_depth").get()as?OnnxTensor?:error("Missing depth surface")
                                require(output.info.type==OnnxJavaType.FLOAT&&output.info.shape.contentEquals(longArrayOf(1,input.height.toLong(),input.width.toLong())))
                                val values=output.floatBuffer;val depth=FloatArray(input.width*input.height);values.get(depth)
                                return PhotoDepthMesh.create(depth,input.width,input.height,width,height){stop.check()}
                            }
                        }finally{stop.detach(run)}
                    }
                }
            }
        }
    }
}
