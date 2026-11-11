package com.honorablemobile

/** Same span and speed, different registered source. Never relabel old source-bound annotations. */
internal object VideoClipReplacement {
    data class Target(val id:String,val durationMs:Long,val latestStartMs:Long)
    private fun checked(clips:List<VideoSegment>,index:Int,durations:Map<String,Long>){
        VideoPlaybackTime.durationUs(clips)
        require(index in clips.indices){"Select a clip first"}
        require(durations.size in 1..32&&durations.all{(id,d)->id.matches(Regex("[A-Za-z0-9_-]{1,64}"))&&!id.startsWith("__")&&d in 1..Int.MAX_VALUE.toLong()}){"Source catalog is invalid"}
        require(clips.all{it.endMs<=(durations[it.sourceId]?:0)}){"An original source duration changed"}
    }
    fun targets(clips:List<VideoSegment>,index:Int,durations:Map<String,Long>):List<Target>{
        checked(clips,index,durations)
        val clip=clips[index];val span=clip.endMs-clip.startMs
        return durations.filter{(id,d)->id!=clip.sourceId&&d>=span}.map{(id,d)->Target(id,d,d-span)}
    }
    fun propose(clips:List<VideoSegment>,index:Int,durations:Map<String,Long>,targetId:String,startMs:Long,music:VideoSoundtrack?=null):List<VideoSegment>{
        val target=targets(clips,index,durations).firstOrNull{it.id==targetId}?:throw IllegalArgumentException("Choose a different added source long enough for the entire clip")
        require(startMs in 0..target.latestStartMs){"Keep the whole replacement window inside the chosen source"}
        val old=clips[index]
        val replacement=old.copy(sourceId=targetId,startMs=startMs,endMs=startMs+(old.endMs-old.startMs),captions=emptyList(),captionSourceUri=null,motion=VideoMotion())
        val result=clips.mapIndexed{i,clip->if(i==index)replacement else clip}
        check(VideoPlaybackTime.durationUs(result)==VideoPlaybackTime.durationUs(clips)){"Replacement changed playback duration"}
        VideoDraftCodec.requireRecoverableCaptions(result,music?.loop==false,music?.hasExcerpt==true)
        return result
    }
}
