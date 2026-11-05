package com.honorablemobile

import java.util.Collections

/** Move a timeline cut while keeping the two clips' combined playback microseconds. */
internal object VideoRollCut {
    private fun requireOffset(value:Long){
        require(value in -Int.MAX_VALUE.toLong()..Int.MAX_VALUE.toLong()&&value!=0L&&value%2==0L){"Use a nonzero playback offset in 0.002-second steps"}
    }
    fun offset(text:String):Long {
        val clean=text.trim();val negative=clean.startsWith("-")
        val unsigned=if(clean.startsWith("-")||clean.startsWith("+"))clean.drop(1)else clean
        val value=VideoCaptions.seconds(unsigned)
        return (if(negative)-value else value).also(::requireOffset)
    }
    fun bounds(clips:List<VideoSegment>,index:Int,sources:Map<String,Long>):Pair<Long,Long> {
        VideoPlaybackTime.durationUs(clips);require(index>=0&&index<clips.lastIndex){"Select a clip with a following neighbor"}
        val left=clips[index];val right=clips[index+1]
        val leftDuration=requireNotNull(sources[left.sourceId]);val rightDuration=requireNotNull(sources[right.sourceId])
        require(leftDuration>0&&rightDuration>0&&left.endMs<=leftDuration&&right.endMs<=rightDuration){"Original source duration changed"}
        // A 2ms playback step consumes speed*2 whole source milliseconds for every supported rate.
        val a=(left.speed*2).toLong();val b=(right.speed*2).toLong();val maxSteps=Int.MAX_VALUE.toLong()/2
        val earlier=minOf((left.endMs-left.startMs-1)/a,right.startMs/b,maxSteps)
        val later=minOf((leftDuration-left.endMs)/a,(right.endMs-right.startMs-1)/b,maxSteps)
        return -earlier*2 to later*2
    }
    fun propose(clips:List<VideoSegment>,index:Int,deltaPlaybackMs:Long,sources:Map<String,Long>):List<VideoSegment> {
        requireOffset(deltaPlaybackMs);val limits=bounds(clips,index,sources)
        require(deltaPlaybackMs in limits.first..limits.second){"Offset exceeds the available source footage"}
        val left=clips[index];val right=clips[index+1]
        val leftDuration=sources.getValue(left.sourceId)
        fun boundary(value:Long,speed:Float):Long {
            val amount=deltaPlaybackMs.toDouble()*speed.toDouble()
            val integral=amount.toLong();require(integral.toDouble()==amount){"Offset does not map to whole source milliseconds"}
            return try{Math.addExact(value,integral)}catch(failure:ArithmeticException){throw IllegalArgumentException("Cut moves beyond supported source timestamps",failure)}
        }
        val leftEnd=boundary(left.endMs,left.speed);val rightStart=boundary(right.startMs,right.speed)
        require(leftEnd>left.startMs&&leftEnd<=leftDuration&&rightStart>=0&&rightStart<right.endMs){"Leave footage in both clips and source handles for the chosen offset"}
        val first=left.copy(endMs=leftEnd);val second=right.copy(startMs=rightStart)
        require(Math.addExact(left.durationUs,right.durationUs)==Math.addExact(first.durationUs,second.durationUs)){"This offset cannot retain exact playback timing"}
        val result=clips.toMutableList();result[index]=first;result[index+1]=second
        return Collections.unmodifiableList(result)
    }
}
