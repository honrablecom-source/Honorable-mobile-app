package com.honorablemobile

import kotlin.math.ceil
import kotlin.math.floor

/** Keep edited-playback footage. Soundtrack settings remain project-relative and start again at zero. */
internal object VideoPlaybackExcerpt {
    data class Piece(val originalIndex:Int,val clip:VideoSegment,val originalFromUs:Long,val originalToUs:Long)
    data class Result(val clips:List<VideoSegment>,val pieces:List<Piece>,val requestedFromUs:Long,val requestedToUs:Long,val effectiveToUs:Long,val beforeUs:Long,val afterUs:Long)
    fun prepare(clips:List<VideoSegment>,from:String,to:String,music:VideoSoundtrack?=null):Result {
        val duration=VideoPlaybackTime.durationUs(clips)
        require(duration<=Int.MAX_VALUE.toLong()*1000&&clips.all{it.endMs<=Int.MAX_VALUE}){"Timeline exceeds supported millisecond timing bounds"}
        val start=VideoCaptions.seconds(from)*1000;val requestedEnd=VideoCaptions.seconds(to)*1000
        require(start>=0&&start<duration&&requestedEnd>start&&requestedEnd<=((duration+999)/1000)*1000){"Choose an increasing interval inside edited playback"}
        val end=minOf(requestedEnd,duration);val pieces=mutableListOf<Piece>();var offset=0L
        for((index,clip)in clips.withIndex()){
            val next=offset+clip.durationUs;val lo=maxOf(start,offset);val hi=minOf(end,next)
            if(lo<hi){
                if(lo==offset&&hi==next)pieces+=Piece(index,clip,offset,next)else {
                    val a=if(lo==offset)clip.startMs else clip.startMs+ceil((lo-offset)*clip.speed.toDouble()/1000).toLong()
                    val b=if(hi==next)clip.endMs else clip.startMs+floor((hi-offset)*clip.speed.toDouble()/1000).toLong()
                    require(b>a&&(b-a)*1000.0/clip.speed>=100000){"Each newly trimmed edge piece needs at least0.1 playback second after source rounding"}
                    val selected=clip.copy(startMs=a,endMs=b,captions=clip.captions.filter{it.startMs<b&&it.endMs>a})
                    val actualFrom=offset+((a-clip.startMs)*1000.0/clip.speed).toLong()
                    val actualTo=if(b==clip.endMs)next else offset+((b-clip.startMs)*1000.0/clip.speed).toLong()
                    check(actualFrom>=start&&actualTo<=end){"Rounded excerpt lies outside the requested interval"}
                    pieces+=Piece(index,selected,actualFrom,actualTo)
                }
            }
            offset=next
        }
        require(pieces.isNotEmpty()){"No footage remains after source rounding"}
        val result=pieces.map{it.clip}
        require(result!=clips){"This interval keeps the entire current timeline unchanged"}
        VideoDraftCodec.requireRecoverableCaptions(result,music?.loop==false,music?.hasExcerpt==true)
        return Result(result,pieces.toList(),start,requestedEnd,end,duration,VideoPlaybackTime.durationUs(result))
    }
}
