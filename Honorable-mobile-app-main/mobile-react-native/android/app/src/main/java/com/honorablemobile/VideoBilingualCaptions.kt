package com.honorablemobile

/** Flatten two nonoverlapping source-time text tracks without inventing translation or speech timing. */
internal object VideoBilingualCaptions {
    data class Result(val clips:List<VideoSegment>,val primaryCount:Int,val secondaryCount:Int,val outputCount:Int,val overlapSourceMs:Long)
    fun prepare(clips:List<VideoSegment>,index:Int,sourceDurationMs:Long,srt:String,music:VideoSoundtrack?=null):Result {
        VideoPlaybackTime.durationUs(clips)
        require(index in clips.indices){"Select a clip first"}
        val clip=clips[index];val primary=clip.captions
        require(primary.isNotEmpty()){"Add the first caption language to this clip before combining"}
        require(sourceDurationMs in 1..Int.MAX_VALUE.toLong()&&clip.endMs<=sourceDurationMs&&primary.all{it.endMs<=sourceDurationMs}){"Original source timing changed"}
        require(srt.length<=VideoCaptions.MAX_SRT_BYTES){"Use a second SRT up to128KB"}
        val bytes=srt.toByteArray(Charsets.UTF_8)
        require(bytes.toString(Charsets.UTF_8)==srt){"Use valid Unicode SRT text"}
        val secondary=VideoCaptions.parseSrt(bytes,sourceDurationMs)
        val edges=(primary.flatMap{listOf(it.startMs,it.endMs)}+secondary.flatMap{listOf(it.startMs,it.endMs)}).distinct().sorted()
        val merged=mutableListOf<VideoCaption>();var a=0;var b=0;var overlap=0L
        for((from,to) in edges.zipWithNext()){
            while(a<primary.size&&primary[a].endMs<=from)a++
            while(b<secondary.size&&secondary[b].endMs<=from)b++
            val first=primary.getOrNull(a)?.takeIf{it.startMs<=from&&it.endMs>=to}
            val second=secondary.getOrNull(b)?.takeIf{it.startMs<=from&&it.endMs>=to}
            if(first==null&&second==null)continue
            if(first!=null&&second!=null)overlap+=to-from
            val text=listOfNotNull(first?.text,second?.text).joinToString("\n")
            // Construct first, so all existing cue Unicode/line/length rules apply before coalescing.
            val cue=VideoCaption(from,to,text)
            val last=merged.lastOrNull()
            if(last!=null&&last.endMs==from&&last.text==text)merged[merged.lastIndex]=last.copy(endMs=to)else merged+=cue
        }
        VideoCaptions.validate(merged)
        val result=clips.mapIndexed{i,value->if(i==index)value.copy(captions=merged.toList())else value}
        require(result.sumOf{it.captions.size}<=VideoCaptions.MAX_PROJECT_CUES){"Combined track exceeds project caption count"}
        require(result.sumOf{c->c.captions.sumOf{it.text.toByteArray(Charsets.UTF_8).size}}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES){"Combined track exceeds project caption text limit"}
        VideoDraftCodec.requireRecoverableCaptions(result,music?.loop==false,music?.hasExcerpt==true)
        require(result!=clips){"The combined captions leave this project unchanged"}
        return Result(result,primary.size,secondary.size,merged.size,overlap)
    }
}
