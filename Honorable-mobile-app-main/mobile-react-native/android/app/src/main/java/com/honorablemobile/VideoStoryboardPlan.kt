package com.honorablemobile

import java.util.Collections
import kotlin.math.ceil
import kotlin.math.sqrt

/** Deterministic representative source-frame proof, independent of composited video effects. */
internal object VideoStoryboardPlan {
    const val MAX_SHOTS=12
    const val CELL_WIDTH=480
    const val CELL_HEIGHT=360
    const val HEADER=76
    const val FOOTER=44
    data class Shot(val clipIndex:Int,val request:VideoFramePhotoPlan,val outputUs:Long,val title:String)
    class Plan internal constructor(val totalClips:Int,shots:List<Shot>,val columns:Int,val rows:Int) {
        val shots:List<Shot> = Collections.unmodifiableList(shots.toList())
        val width:Int get()=columns*CELL_WIDTH
        val height:Int get()=HEADER+rows*CELL_HEIGHT+FOOTER
        fun cell(index:Int):Pair<Int,Int>{require(index in shots.indices);return index%columns*CELL_WIDTH to HEADER+index/columns*CELL_HEIGHT}
        val representative:Boolean get()=shots.size<totalClips
    }
    fun create(clips:List<VideoSegment>):Plan {
        VideoPlaybackTime.durationUs(clips)
        val count=minOf(MAX_SHOTS,clips.size)
        val indices=(0 until count).map{if(count==1)0 else it*(clips.size-1)/(count-1)}.toSet()
        val shots=mutableListOf<Shot>();var offset=0L
        clips.forEachIndexed{index,clip->
            if(index in indices){
                val source=clip.startMs+(clip.endMs-clip.startMs-1)/2
                val request=VideoFramePhotoPlan(clip,source)
                val output=offset+((source-clip.startMs)*1000.0/clip.speed).toLong()
                shots.add(Shot(index,request,output,clip.title.replace('\n',' ')))
            }
            offset+=clip.durationUs
        }
        val columns=ceil(sqrt(count.toDouble())).toInt()
        val rows=(count+columns-1)/columns
        return Plan(clips.size,shots,columns,rows)
    }
}
