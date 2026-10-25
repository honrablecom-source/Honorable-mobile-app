package com.honorablemobile

import kotlin.math.ceil
import kotlin.random.Random

internal enum class VideoBatchTool(val title:String) {
    DIVIDE("Divide clip evenly"), LIMIT("Limit clip lengths"), REVERSE("Reverse clip order"),
    SHUFFLE("Shuffle clip order"), REPEAT("Repeat sequence"), SPEED("Set all clip speeds"),
    CAPTION_SHIFT("Shift caption timing"), CAPTION_WRAP("Reflow caption lines")
}

/** Pure proposals applied by the existing project validator as one undoable change. */
internal object VideoBatchTools {
    private fun checked(clips:List<VideoSegment>){require(clips.size in 1..1000){"Use a project with 1–1000 clips"}}
    fun divide(clips:List<VideoSegment>,index:Int,parts:Int):List<VideoSegment> {
        checked(clips);require(index in clips.indices && parts in 2..20)
        require(clips.size+parts-1<=1000){"Project clip limit reached"}
        val clip=clips[index];val span=clip.endMs-clip.startMs
        require(span/parts>=ceil(100*clip.speed.toDouble()).toLong()){"Allow at least 0.1 playback second per piece"}
        val pieces=(0 until parts).map{i->
            val from=clip.startMs+(span/parts)*i+minOf(i.toLong(),span%parts)
            val to=clip.startMs+(span/parts)*(i+1)+minOf((i+1).toLong(),span%parts)
            clip.copy(startMs=from,endMs=to,captions=clip.captions.filter{it.endMs>from&&it.startMs<to})
        }
        return clips.take(index)+pieces+clips.drop(index+1)
    }
    fun limitLengths(clips:List<VideoSegment>,maximumPlaybackMs:Long):List<VideoSegment> {
        checked(clips);require(maximumPlaybackMs in 500..60_000)
        return clips.map{clip->
            val maximum=(maximumPlaybackMs*clip.speed.toDouble()).toLong().coerceAtLeast(1)
            if(clip.endMs-clip.startMs<=maximum)clip else {
                val start=clip.startMs+(clip.endMs-clip.startMs-maximum)/2;val end=start+maximum
                clip.copy(startMs=start,endMs=end,captions=clip.captions.filter{it.endMs>start&&it.startMs<end})
            }
        }
    }
    fun reverseOrder(clips:List<VideoSegment>):List<VideoSegment> {
        checked(clips);require(clips.size>=2){"Add at least two clips"};return clips.reversed()
    }
    fun shuffleOrder(clips:List<VideoSegment>,seed:Int):List<VideoSegment> {
        checked(clips);require(clips.size>=2){"Add at least two clips"}
        val result=clips.shuffled(Random(seed))
        return if(result==clips)clips.drop(1)+clips.first() else result
    }
    fun repeatSequence(clips:List<VideoSegment>,copies:Int):List<VideoSegment> {
        checked(clips);require(copies in 2..4 && clips.size*copies<=1000){"Use 2–4 copies within the 1000-clip limit"}
        return List(copies){clips}.flatten()
    }
    fun allSpeeds(clips:List<VideoSegment>,speed:Float):List<VideoSegment> {
        checked(clips);require(speed in setOf(.5f,1f,1.5f,2f));return clips.map{it.copy(speed=speed)}
    }
    fun shiftCaptions(clips:List<VideoSegment>,index:Int,deltaSourceMs:Long):List<VideoSegment> {
        checked(clips);require(index in clips.indices && deltaSourceMs in -10_000..10_000 && deltaSourceMs!=0L)
        val clip=clips[index];require(clip.captions.isNotEmpty()){"Add captions to the selected clip first"}
        val shifted=clip.captions.map{cue->
            val start=cue.startMs+deltaSourceMs;val end=cue.endMs+deltaSourceMs
            require(start>=clip.startMs && end<=clip.endMs){"Offset moves captions outside this clip. Choose a smaller offset or extend the clip."}
            cue.copy(startMs=start,endMs=end)
        }
        return clips.mapIndexed{i,c->if(i==index)c.copy(captions=shifted)else c}
    }
    fun reflowCaptions(clips:List<VideoSegment>,index:Int,columns:Int):List<VideoSegment> {
        checked(clips);require(index in clips.indices && columns in 20..80)
        val clip=clips[index];require(clip.captions.isNotEmpty()){"Add captions to the selected clip first"}
        val cues=clip.captions.map{cue->
            val lines=mutableListOf<String>();var current=""
            for(word in cue.text.trim().split(Regex("\\s+"))){
                val points=word.codePoints().toArray()
                val chunks=points.toList().chunked(columns).map{chunk->String(chunk.toIntArray(),0,chunk.size)}
                for(chunk in chunks){
                    val combined=if(current.isEmpty())chunk else "$current $chunk"
                    if(combined.codePointCount(0,combined.length)<=columns)current=combined
                    else{if(current.isNotEmpty())lines.add(current);current=chunk}
                }
            }
            if(current.isNotEmpty())lines.add(current)
            require(lines.size in 1..3){"A caption needs more than three lines. Choose wider lines or shorten its text."}
            cue.copy(text=lines.joinToString("\n"))
        }
        return clips.mapIndexed{i,c->if(i==index)c.copy(captions=cues)else c}
    }
}
