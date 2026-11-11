package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln
import java.util.concurrent.CancellationException

class VideoMarianBeamTest {
    private fun row(prefix:List<Int>):FloatArray=when(prefix){
        emptyList<Int>()->floatArrayOf(-3f,-.1f,-.3f,100f)
        listOf(1)->floatArrayOf(-.1f,-3f,-4f,100f)
        listOf(2)->floatArrayOf(-4f,-.01f,-4f,100f)
        else->floatArrayOf(-.01f,-8f,-8f,100f)
    }
    @Test fun smallIndependentExhaustiveTreeMatchesBeamAndLengthNormalizedEos(){
        val choices=mutableListOf<Pair<List<Int>,Double>>()
        fun visit(prefix:List<Int>,score:Double){
            val logits=row(prefix);val normal=ln((0..2).sumOf{exp(logits[it].toDouble())})
            choices.add((prefix+0)to((score+logits[0]-normal)/(prefix.size+1)))
            if(prefix.size<2)for(id in 1..2)visit(prefix+id,score+logits[id]-normal)
        }
        visit(emptyList(),0.0)
        val expected=choices.maxBy{it.second}.first
        val beam=VideoMarianBeam(2,3,0,3);var alive=listOf(emptyList<Int>())
        repeat(3){if(!beam.done){val next=beam.advance(alive.map{row(it)});alive=next.parents.indices.map{i->alive[next.parents[i]]+next.tokens[i].toInt()}}}
        assertEquals(listOf(2,1,0),expected);assertArrayEquals(expected.toIntArray(),beam.result())
    }
    @Test fun impossibleEosRefusesInsteadOfPublishingTruncatedWords(){
        val beam=VideoMarianBeam(1,2,0,3)
        try{repeat(2){beam.advance(listOf(floatArrayOf(-100f,2f,1f,1000f)))};fail()}catch(_:IllegalStateException){}
        try{beam.result();fail()}catch(_:IllegalStateException){}
    }
    @Test fun invalidScoresBatchAndCancellationDoNotReturnTokens(){
        for(rows in listOf(emptyList(),listOf(floatArrayOf(Float.NaN,0f,0f,0f)),listOf(floatArrayOf(0f,0f)))){
            try{VideoMarianBeam(2,3,0,3).advance(rows);fail()}catch(_:IllegalArgumentException){}
        }
        try{VideoMarianBeam(2,3,0,3).advance(listOf(floatArrayOf(0f,0f,0f,0f))){throw CancellationException()};fail()}catch(_:CancellationException){}
    }
}
