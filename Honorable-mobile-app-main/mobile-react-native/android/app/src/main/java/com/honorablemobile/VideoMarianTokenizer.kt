package com.honorablemobile

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads original SentencePiece data, not the incompatible converted fast-tokenizer JSON.
 * Normalization format: Google SentencePiece v0.2.1 (Apache 2.0), Darts-clone (BSD).
 * https://github.com/google/sentencepiece/tree/v0.2.1
 * No JNI, model mutation, network or shared search-tokenizer changes.
 */
internal class VideoMarianTokenizer(model:ByteArray,jointVocabulary:Map<String,Int>) {
    private data class Field(val id:Int,val wire:Int,val integer:Long=0,val bytes:ByteArray=byteArrayOf())
    private data class Piece(val text:String,val score:Float,val id:Int)
    private val byFirst:Map<Char,List<Piece>>
    private val unknownScore:Float
    private val trie:IntArray
    private val replacement:ByteArray
    private val joint=jointVocabulary.toMap()
    init {
        require(model.size in 1..2_000_000)
        require(jointVocabulary["</s>"]==0&&jointVocabulary["<unk>"]==1&&jointVocabulary["<pad>"]==65000)
        require(jointVocabulary.values.all{it in 0..65000}&&jointVocabulary.values.toSet().size==jointVocabulary.size)
        val root=fields(model)
        val trainer=fields(root.single{it.id==2}.bytes)
        require((trainer.firstOrNull{it.id==3}?.integer?:1L)==1L){"Expected original Unigram model"}
        require((trainer.firstOrNull{it.id==24}?.integer?:0L)==0L){"Suffix whitespace models are unsupported"}
        require((trainer.firstOrNull{it.id==35}?.integer?:0L)==0L){"Byte fallback models are unsupported"}
        val normal=fields(root.single{it.id==3}.bytes)
        require(normal.firstOrNull{it.id==1}?.bytes?.toString(Charsets.UTF_8) in listOf("nmt_nfkc","identity"))
        for(flag in 3..5)require((normal.firstOrNull{it.id==flag}?.integer?:1L)==1L){"Expected prefix/space-normalizing SentencePiece settings"}
        val blob=normal.firstOrNull{it.id==2}?.bytes?:byteArrayOf()
        if(blob.isEmpty()){trie=intArrayOf();replacement=byteArrayOf()}
        else {
            require(blob.size>=1029)
            val buffer=ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);val size=buffer.int
            require(size>=1024&&size%1024==0&&size<blob.size-4)
            trie=IntArray(size/4){buffer.int};replacement=blob.copyOfRange(4+size,blob.size)
            require(replacement.last()==0.toByte())
        }
        val pieces=root.filter{it.id==1}.mapNotNull{entry->
            val values=fields(entry.bytes);val kind=values.firstOrNull{it.id==3}?.integer?:1L
            if(kind!=1L)return@mapNotNull null
            val text=values.single{it.id==1}.bytes.toString(Charsets.UTF_8)
            val score=Float.fromBits(values.single{it.id==2}.integer.toInt())
            require(text.isNotEmpty()&&text.length<=128&&score.isFinite())
            Piece(text,score,requireNotNull(jointVocabulary[text]){"Original source piece absent from model vocabulary"})
        }
        require(pieces.size in 1..100_000&&pieces.map{it.text}.toSet().size==pieces.size)
        byFirst=pieces.groupBy{it.text[0]};unknownScore=pieces.minOf{it.score}-10f
    }

    fun normalize(text:String):String {
        require(text.length<=4096&&text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==text)
        val bytes=text.toByteArray(Charsets.UTF_8);val output=ByteArrayOutputStream();var at=0
        while(at<bytes.size){
            var longest=0;var value=0
            if(trie.isNotEmpty()){
                var node=offset(trie[0]);var cursor=at
                while(cursor<bytes.size){
                    node=node xor (bytes[cursor].toInt()and 255)
                    if(node !in trie.indices)break
                    val unit=trie[node]
                    if(unit and (Int.MIN_VALUE or 255)!=(bytes[cursor].toInt()and 255))break
                    node=node xor offset(unit);cursor++
                    if(unit and 256!=0){require(node in trie.indices);longest=cursor-at;value=trie[node]and Int.MAX_VALUE}
                }
            }
            if(longest>0){
                require(value in replacement.indices);var end=value
                while(end<replacement.size&&replacement[end]!=0.toByte())end++
                require(end<replacement.size);output.write(replacement,value,end-value);at+=longest
            }else{
                val first=bytes[at].toInt()and 255
                val width=when{first<128->1;first<224->2;first<240->3;else->4}
                require(at+width<=bytes.size);output.write(bytes,at,width);at+=width
            }
            require(output.size()<=32768){"Normalized text exceeds limits"}
        }
        // Original remove_extra_whitespaces/escape_whitespaces/add_dummy_prefix settings.
        val normalized=output.toString("UTF-8").split(' ').filter{it.isNotEmpty()}.joinToString("▁")
        return if(normalized.isEmpty())""else"▁$normalized"
    }

    fun encode(text:String,maximumTokens:Int=256,current:()->Unit={}):LongArray {
        require(maximumTokens in 1..512)
        require(!text.contains("</s>")&&!text.contains("<unk>")&&!text.contains("<pad>")){"Reserved model markers are unsupported in caption input"}
        current();val input=normalize(text)
        require(input.isNotEmpty()&&input.length<=8192){"Choose nonempty bounded caption text"}
        val best=FloatArray(input.length+1){Float.NEGATIVE_INFINITY};best[0]=0f
        val previous=IntArray(input.length+1){-1};val ids=IntArray(input.length+1){-1}
        var at=0
        while(at<input.length){
            current()
            if(best[at].isFinite()){
                val width=Character.charCount(input.codePointAt(at));var single=false
                for(piece in byFirst[input[at]].orEmpty()){
                    val end=at+piece.text.length
                    if(end>input.length||!input.regionMatches(at,piece.text,0,piece.text.length))continue
                    if(piece.text.length==width)single=true
                    val score=best[at]+piece.score
                    if(score>best[end]){best[end]=score;previous[end]=at;ids[end]=piece.id}
                }
                if(!single){val end=at+width;val score=best[at]+unknownScore;if(score>best[end]){best[end]=score;previous[end]=at;ids[end]=-2}}
            }
            at+=Character.charCount(input.codePointAt(at))
        }
        data class Segment(val from:Int,val to:Int,val id:Int)
        val reverse=mutableListOf<Segment>();var end=input.length
        while(end>0){require(previous[end]>=0);reverse.add(Segment(previous[end],end,ids[end]));end=previous[end]}
        reverse.reverse();val result=mutableListOf<Long>();var index=0
        while(index<reverse.size){
            val segment=reverse[index++]
            if(segment.id!=-2)result.add(segment.id.toLong())
            else{
                var until=segment.to
                while(index<reverse.size&&reverse[index].id==-2)until=reverse[index++].to
                // Marian maps SentencePiece's fused unknown *surface* through its joint vocabulary.
                // A source-unknown accented character can still be a valid target-vocabulary token.
                result.add((joint[input.substring(segment.from,until)]?:1).toLong())
            }
        }
        result.add(0);require(result.size<=maximumTokens){"Caption exceeds translation token limits"};current();return result.toLongArray()
    }

    private fun offset(unit:Int)=(unit ushr 10) shl ((unit and 512) ushr 6)
    private fun fields(bytes:ByteArray):List<Field>{
        require(bytes.size<=2_000_000);var at=0
        fun variable():Long{var result=0L;var shift=0
            while(shift<64){require(at<bytes.size);val next=bytes[at++].toInt()and 255;result=result or ((next and 127).toLong()shl shift);if(next<128)return result;shift+=7}
            error("Invalid SentencePiece integer")}
        val result=mutableListOf<Field>()
        while(at<bytes.size){
            require(result.size<100_000);val key=variable();val id=(key ushr 3).toInt();val wire=(key and 7).toInt();require(id>0)
            result.add(when(wire){
                0->Field(id,wire,variable())
                2->{val size=variable();require(size>=0&&size<=bytes.size-at);val value=bytes.copyOfRange(at,at+size.toInt());at+=size.toInt();Field(id,wire,bytes=value)}
                5->{require(at+4<=bytes.size);val bits=ByteBuffer.wrap(bytes,at,4).order(ByteOrder.LITTLE_ENDIAN).int;at+=4;Field(id,wire,bits.toLong())}
                1->{require(at+8<=bytes.size);at+=8;Field(id,wire)}
                else->error("Unsupported SentencePiece wire type")
            })
        }
        return result
    }
}
