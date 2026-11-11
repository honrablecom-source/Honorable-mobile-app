package com.honorablemobile

import java.util.PriorityQueue
import kotlin.math.exp
import kotlin.math.ln

/** Four-beam log-probability search, length penalty1; only genuinely EOS-complete output is returned. */
internal class VideoMarianBeam(private val width:Int=4,private val maximumTokens:Int=511,private val eos:Int=0,private val pad:Int=65000) {
    data class Selection(val parents:IntArray,val tokens:LongArray)
    private data class Beam(val ids:List<Int>,val score:Double)
    private data class Candidate(val parent:Int,val token:Int,val score:Double)
    private var alive=listOf(Beam(emptyList(),0.0))
    private val finished=mutableListOf<Beam>()
    private var steps=0
    private var vocabulary=0
    var done=false;private set
    init{require(width in 1..4&&maximumTokens in 1..511&&eos>=0&&pad>=0&&eos!=pad)}
    fun advance(rows:List<FloatArray>,current:()->Unit={}):Selection {
        check(!done&&steps<maximumTokens){"Translation did not finish within model limits"}
        require(rows.size==alive.size)
        val best=compareBy<Candidate>{it.score}.thenByDescending{it.parent}.thenByDescending{it.token}
        val heap=PriorityQueue(best)
        for((parent,row)in rows.withIndex()){
            current();require(row.size in 2..65001&&eos in row.indices&&pad in row.indices)
            if(vocabulary==0)vocabulary=row.size else require(vocabulary==row.size)
            require(row.all{it.isFinite()}){"Invalid translation logits"}
            val maximum=row.indices.filter{it!=pad}.maxOf{row[it].toDouble()}
            var sum=0.0;for(i in row.indices)if(i!=pad)sum+=exp(row[i]-maximum)
            val denominator=maximum+ln(sum)
            for(i in row.indices){
                if(i%4096==0)current()
                if(i==pad)continue
                val candidate=Candidate(parent,i,alive[parent].score+row[i]-denominator)
                if(heap.size<2*width)heap.add(candidate)else if(best.compare(candidate,heap.peek())>0){heap.poll();heap.add(candidate)}
            }
        }
        val candidates=heap.toList().sortedWith(best.reversed());val parents=mutableListOf<Int>();val tokens=mutableListOf<Long>();val next=mutableListOf<Beam>()
        for((rank,candidate)in candidates.withIndex()){
            val beam=Beam(alive[candidate.parent].ids+candidate.token,candidate.score)
            if(candidate.token==eos){if(rank<width)finished.add(beam)}
            else if(next.size<width){next.add(beam);parents.add(candidate.parent);tokens.add(candidate.token.toLong())}
        }
        finished.sortByDescending{it.score/it.ids.size};while(finished.size>width)finished.removeAt(finished.lastIndex)
        steps++;alive=next
        // Match the model's default early_stopping=false heuristic (Transformers4.34),
        // including the decoder-start position in current length. Never synthesize EOS.
        done=finished.size>=width&&(next.isEmpty()||finished.last().score/finished.last().ids.size>=candidates.first().score/(steps+1))
        if(next.isEmpty()){check(finished.isNotEmpty());done=true}
        if(steps==maximumTokens){check(finished.isNotEmpty()){"Translation produced no complete sentence"};done=true}
        current();return Selection(parents.toIntArray(),tokens.toLongArray())
    }
    fun result():IntArray{check(done&&finished.isNotEmpty());return finished.first().ids.toIntArray()}
}
