package com.honorablemobile

/** Explicit saved source anchors become forward-playing editable excerpts, without media analysis. */
internal object VideoMomentHighlights {
    fun propose(clips:List<VideoSegment>,index:Int,moments:List<Long>,beforeMs:Long,afterMs:Long):List<VideoSegment> {
        VideoPlaybackTime.durationUs(clips)
        require(index in clips.indices)
        require(moments.size in 1..100&&moments.distinct().size==moments.size){"Choose 1–100 different saved moments"}
        require(beforeMs in 0..60_000&&afterMs in 1..60_000){"Use 0–60 seconds before and 0.001–60 seconds after each moment"}
        val clip=clips[index]
        require(moments.all{it>=clip.startMs&&it<clip.endMs}){"Choose moments inside the current clip"}
        val ranges=mutableListOf<Pair<Long,Long>>()
        for(at in moments.sorted()) {
            // Subtract bounded distances rather than adding handles that can overflow source time.
            val start=at-minOf(beforeMs,at-clip.startMs)
            val end=at+minOf(afterMs,clip.endMs-at)
            val previous=ranges.lastOrNull()
            if(previous!=null&&start<=previous.second)ranges[ranges.lastIndex]=previous.first to maxOf(previous.second,end)
            else ranges.add(start to end)
        }
        require(clips.size-1+ranges.size<=1000){"Highlights exceed the 1000-clip project limit"}
        val excerpts=ranges.map{(start,end)->clip.copy(startMs=start,endMs=end,captions=clip.captions.filter{it.startMs<end&&it.endMs>start})}
        val result=clips.take(index)+excerpts+clips.drop(index+1)
        require(result.sumOf{it.captions.size}<=VideoCaptions.MAX_PROJECT_CUES){"Highlights exceed the project caption limit"}
        require(result.sumOf{c->c.captions.sumOf{it.text.toByteArray(Charsets.UTF_8).size}}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES){"Highlights exceed the project caption text limit"}
        require(result.sumOf{it.title.toByteArray(Charsets.UTF_8).size}<=128_000){"Highlights exceed the project title limit"}
        VideoPlaybackTime.durationUs(result)
        return result
    }
}
