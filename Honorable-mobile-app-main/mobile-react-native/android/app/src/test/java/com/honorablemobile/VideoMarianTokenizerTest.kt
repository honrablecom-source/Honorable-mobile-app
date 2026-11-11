package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

class VideoMarianTokenizerTest {
    private fun variable(value:Long):ByteArray{var v=value;val out=ByteArrayOutputStream();do{val next=(v and 127).toInt();v=v ushr 7;out.write(if(v==0L)next else next or 128)}while(v!=0L);return out.toByteArray()}
    private fun integer(id:Int,value:Long)=variable((id*8).toLong())+variable(value)
    private fun bytes(id:Int,value:ByteArray)=variable((id*8+2).toLong())+variable(value.size.toLong())+value
    private fun piece(value:String,score:Float)=bytes(1,bytes(1,value.toByteArray())+variable(21)+ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(score).array())
    private val joint=mapOf("</s>" to 0,"<unk>" to 1,"<pad>" to 65000,"▁" to 2,"A" to 3,"B" to 4,"▁AB" to 5,"AB" to 6,"😊" to 7)
    private fun model()=piece("▁",-5f)+piece("A",-4f)+piece("B",-4f)+piece("▁AB",-1f)+piece("AB",-2f)+bytes(2,integer(3,1))+bytes(3,bytes(1,"identity".toByteArray()))
    private fun tokenizer()=VideoMarianTokenizer(model(),joint)
    @Test fun highestScoringPathAndEosMatchTheOriginalUnigramContract(){
        assertArrayEquals(longArrayOf(5,5,0),tokenizer().encode("AB AB"))
        assertArrayEquals(longArrayOf(2,3,2,4,0),tokenizer().encode(" A  B "))
    }
    @Test fun unknownSurfaceIsFusedThenMappedThroughJointMarianVocabulary(){
        assertArrayEquals(longArrayOf(2,7,0),tokenizer().encode("😊"))
        assertArrayEquals(longArrayOf(2,1,0),tokenizer().encode("😊😊"))
    }
    @Test fun blankMarkersAndLimitsRefuseRatherThanTruncateInput(){
        for(action in listOf<()->Unit>({tokenizer().encode(" ")},{tokenizer().encode("<pad>")},{tokenizer().encode("A B",3)},{tokenizer().encode("A",0)})){
            try{action();fail("Must refuse unsupported input")}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun malformedProtobufAndWrongVocabularyCannotInitialize(){
        for(raw in listOf(byteArrayOf(),byteArrayOf(0),byteArrayOf(8,0x80.toByte()),byteArrayOf(26,127))){
            try{VideoMarianTokenizer(raw,joint);fail("Must refuse malformed model")}catch(_:RuntimeException){}
        }
        try{VideoMarianTokenizer(model(),joint-"AB");fail("Source piece cannot disappear")}catch(_:IllegalArgumentException){}
    }
    @Test fun constructorDoesNotRetainMutableCallerVocabularyOrModelBytes(){
        val raw=model();val vocab=joint.toMutableMap();val tokenizer=VideoMarianTokenizer(raw,vocab)
        raw.fill(0);vocab["😊"]=999
        assertArrayEquals(longArrayOf(2,7,0),tokenizer.encode("😊"))
    }
    @Test fun cancellationDoesNotPublishPartialTokens(){
        try{tokenizer().encode("AB AB",256){throw CancellationException()};fail()}catch(_:CancellationException){}
    }
}
