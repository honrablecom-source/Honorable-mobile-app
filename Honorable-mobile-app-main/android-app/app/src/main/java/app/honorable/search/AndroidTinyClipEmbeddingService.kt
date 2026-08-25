package app.honorable.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.math.sqrt

/** Fully local TinyCLIP inference using the exact validated test-lab model and preprocessing. */
class AndroidTinyClipEmbeddingService(context: Context) : TinyClipEmbeddingService(), AutoCloseable {
    private val environment=OrtEnvironment.getEnvironment()
    private val modelBytes=context.assets.open("tinyclip/model_int8.onnx").use{it.readBytes()}.also{ModelAssetIntegrity.requireModel(it)}
    private val tokenizerBytes=context.assets.open("tinyclip/tokenizer.json").use{it.readBytes()}.also{ModelAssetIntegrity.requireTokenizer(it)}
    private val session=environment.createSession(modelBytes,OrtSession.SessionOptions())
    private val tokenizer=ClipTokenizer(tokenizerBytes.toString(Charsets.UTF_8))
    override fun image(bytes:ByteArray):FloatArray?=runCatching{infer(tokenizer.encode("a photo"),pixels(BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:return null),3)}.getOrNull()
    override fun text(query:String):FloatArray?=runCatching{infer(tokenizer.encode(query),FloatArray(3*224*224),2)}.getOrNull()
    private fun infer(tokens:LongArray,pixels:FloatArray,output:Int):FloatArray {
        val mask=LongArray(77){if(it<=tokens.indexOfLast{v->v!=49407L})1 else 0}
        val inputs=mapOf("input_ids" to OnnxTensor.createTensor(environment,LongBuffer.wrap(tokens),longArrayOf(1,77)),"attention_mask" to OnnxTensor.createTensor(environment,LongBuffer.wrap(mask),longArrayOf(1,77)),"pixel_values" to OnnxTensor.createTensor(environment,FloatBuffer.wrap(pixels),longArrayOf(1,3,224,224)))
        return inputs.values.useAll { session.run(inputs).use { result -> normalize((result[output].value as Array<*>)[0] as FloatArray) } }
    }
    private fun pixels(source:Bitmap):FloatArray { val scale=224f/minOf(source.width,source.height);val width=(source.width*scale).toInt();val height=(source.height*scale).toInt();val resized=Bitmap.createScaledBitmap(source,width,height,true);val data=IntArray(224*224);resized.getPixels(data,0,224,(width-224)/2,(height-224)/2,224,224);val result=FloatArray(3*224*224);val mean=floatArrayOf(.48145466f,.4578275f,.40821073f);val std=floatArrayOf(.26862954f,.26130258f,.27577711f);data.forEachIndexed{i,p->result[i]=((p shr 16 and 255)/255f-mean[0])/std[0];result[224*224+i]=((p shr 8 and 255)/255f-mean[1])/std[1];result[2*224*224+i]=((p and 255)/255f-mean[2])/std[2]};return result }
    private fun normalize(v:FloatArray):FloatArray { val n=sqrt(v.sumOf{it*it.toDouble()}).toFloat();return if(n==0f)v else FloatArray(v.size){v[it]/n} }
    override fun close(){session.close()}
    private inline fun <T:AutoCloseable,R> Collection<T>.useAll(block:()->R):R=try{block()}finally{forEach{it.close()}}
}

/** Detects corrupt or replaced bundled inference assets; this is not DRM. */
internal object ModelAssetIntegrity {
    private const val MODEL_SHA256="10921310ddef06557ec1598d1260470a0a4db53f70ffe0deb60b946dcad6d27a"
    private const val TOKENIZER_SHA256="6d9109cc838977f3ca94a379eec36aecc7c807e1785cd729660ca2fc0171fb35"
    fun requireModel(bytes:ByteArray)=requireHash(bytes,MODEL_SHA256,"TinyCLIP model")
    fun requireTokenizer(bytes:ByteArray)=requireHash(bytes,TOKENIZER_SHA256,"TinyCLIP tokenizer")
    private fun requireHash(bytes:ByteArray,expected:String,label:String){
        val actual=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        check(MessageDigest.isEqual(actual.toByteArray(),expected.toByteArray())){"$label integrity check failed"}
    }
}

private class ClipTokenizer(raw:String) {
    private val root=JSONObject(raw).getJSONObject("model");private val vocab=root.getJSONObject("vocab").let{o->buildMap{for(k in o.keys())put(k,o.getInt(k))}};private val ranks=root.getJSONArray("merges").let{a->buildMap{for(i in 0 until a.length()){val p=a.getString(i).split(' ',limit=2);put(p[0] to p[1],i)}}};private val bytes=byteEncoder();private val cache=mutableMapOf<String,List<String>>()
    private val pattern=Regex("<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+",RegexOption.IGNORE_CASE)
    fun encode(value:String):LongArray { val ids=mutableListOf(49406L);val text=Normalizer.normalize(value,Normalizer.Form.NFC).lowercase().replace(Regex("\\s+")," ");pattern.findAll(text).forEach { m->val encoded=m.value.toByteArray().joinToString(""){bytes[it.toInt() and 255].toString()};bpe(encoded).forEach{vocab[it]?.let{i->ids+=i.toLong()}} };ids+=49407L;return LongArray(77){ids.getOrElse(it){49407L}} }
    private fun bpe(token:String):List<String> = cache.getOrPut(token) { var word=token.map{it.toString()}.toMutableList();if(word.isNotEmpty())word[word.lastIndex]+="</w>";while(word.size>1){val pair=word.zipWithNext().minByOrNull{ranks[it]?:Int.MAX_VALUE}?:break;if(pair !in ranks)break;val merged=mutableListOf<String>();var i=0;while(i<word.size){if(i<word.lastIndex&&word[i]==pair.first&&word[i+1]==pair.second){merged+=word[i]+word[i+1];i+=2}else merged+=word[i++]};word=merged};word }
    private fun byteEncoder():Map<Int,Char>{val values=(33..126).toMutableList().apply{addAll(161..172);addAll(174..255)};val chars=values.map{it.toChar()}.toMutableList();var extra=0;for(b in 0..255)if(b !in values){values+=b;chars+=(256+extra++).toChar()};return values.zip(chars).toMap()}
}
