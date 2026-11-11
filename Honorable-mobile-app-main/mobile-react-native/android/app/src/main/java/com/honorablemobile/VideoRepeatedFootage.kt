package com.honorablemobile

import java.util.PriorityQueue

/** Source-identity/range inspection, not visual similarity or automatic deletion. */
internal object VideoRepeatedFootage {
    const val LIMIT=100
    data class Occurrence(val clipIndex:Int,val sourceId:String,val sourceMs:Long,val outputUs:Long,val overlapPlaybackUs:Long)
    data class Pair(val sourceId:String,val startMs:Long,val endMs:Long,val first:Occurrence,val second:Occurrence)
    data class Result(val pairs:List<Pair>,val totalPairs:Int){val omittedPairs:Int get()=totalPairs-pairs.size}
    fun inspect(clips:List<VideoSegment>,current:()->Unit={}):Result {
        VideoPlaybackTime.durationUs(clips)
        require(clips.all{it.endMs<=Int.MAX_VALUE}){"Source timing exceeds supported millisecond bounds"}
        val starts=LongArray(clips.size);var offset=0L
        clips.forEachIndexed{i,clip->starts[i]=offset;offset+=clip.durationUs}
        val order=compareByDescending<Pair>{it.endMs-it.startMs}.thenBy{it.first.clipIndex}.thenBy{it.second.clipIndex}
        val best=PriorityQueue(LIMIT,order.reversed());var total=0
        fun occurrence(index:Int,from:Long,to:Long):Occurrence {
            val clip=clips[index]
            return Occurrence(index,clip.sourceId,from,starts[index]+((from-clip.startMs)*1000.0/clip.speed).toLong(),((to-from)*1000.0/clip.speed).toLong())
        }
        for(i in clips.indices){
            current();val a=clips[i]
            for(j in i+1 until clips.size){
                if(j%256==0)current()
                val b=clips[j];if(a.sourceId!=b.sourceId)continue
                val from=maxOf(a.startMs,b.startMs);val to=minOf(a.endMs,b.endMs)
                if(from>=to)continue
                total++
                // Pairs arrive in clip-order tie order. Equal later spans cannot improve the heap.
                if(best.size==LIMIT&&to-from<=best.peek().endMs-best.peek().startMs)continue
                // Allocate only a candidate that can enter the bounded retained list.
                val pair=Pair(a.sourceId,from,to,occurrence(i,from,to),occurrence(j,from,to))
                if(best.size<LIMIT)best.add(pair)else if(order.compare(pair,best.peek())<0){best.poll();best.add(pair)}
            }
        }
        current();return Result(best.toList().sortedWith(order),total)
    }
}
