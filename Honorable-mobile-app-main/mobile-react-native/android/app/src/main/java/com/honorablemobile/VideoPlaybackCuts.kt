package com.honorablemobile

/** Explicit edited-playback markers. Original ranges remain contiguous and in their original order. */
internal object VideoPlaybackCuts {
    data class Cut(val requestedUs:Long,val actualUs:Long,val clipIndex:Int,val sourceId:String,val sourceTimeMs:Long)
    data class Result(val clips:List<VideoSegment>,val cuts:List<Cut>,val existingBoundaries:Int,val durationDriftUs:Long)
    private fun marker(value:String):Long {
        val clean=value.trim()
        if(!clean.contains(':'))return VideoCaptions.seconds(clean)*1000
        val match=Regex("([0-9]{1,3}):([0-5][0-9]):([0-5][0-9])(?:\\.([0-9]{1,3}))?").matchEntire(clean)
            ?:throw IllegalArgumentException("Use decimal seconds or HH:MM:SS.mmm")
        val (h,m,s,frac)=match.destructured
        val ms=h.toLong()*3_600_000+m.toLong()*60_000+s.toLong()*1000+(if(frac.isEmpty())0 else frac.padEnd(3,'0').toLong())
        require(ms<=Int.MAX_VALUE){"Timestamp is too large"};return ms*1000
    }
    fun prepare(clips:List<VideoSegment>,text:String,music:VideoSoundtrack?=null):Result {
        val duration=VideoPlaybackTime.durationUs(clips)
        require(clips.all{it.endMs<=Int.MAX_VALUE}){"Source timing exceeds supported millisecond bounds"}
        require(text.length<=16000){"Use up to 16000 characters"}
        val rows=text.trim().lines().filter{it.isNotBlank()}
        require(rows.size in 1..100){"Paste 1–100 playback times, one per line"}
        val times=rows.mapIndexed{i,row->try{marker(row)}catch(_:IllegalArgumentException){throw IllegalArgumentException("Line ${i+1}: use decimal seconds or HH:MM:SS.mmm")}}
        require(times.all{it>0&&it<duration}){"Each marker must be strictly inside edited playback"}
        require(times.zipWithNext().all{(a,b)->a<b}){"Markers must be unique and in increasing playback order"}
        val out=mutableListOf<VideoSegment>();val cuts=mutableListOf<Cut>();var offset=0L;var existing=0
        clips.forEachIndexed{index,clip->
            val end=offset+clip.durationUs
            existing+=times.count{it==offset}
            val local=times.filter{it>offset&&it<end}
            if(local.isEmpty())out+=clip else {
                val boundaries=local.map{time->
                    val source=clip.startMs+(((time-offset)*clip.speed.toDouble()/1000)+.5).toLong()
                    require(source>clip.startMs&&source<clip.endMs){"A marker rounds to an existing clip edge"}
                    cuts+=Cut(time,offset+((source-clip.startMs)*1000.0/clip.speed).toLong(),index,clip.sourceId,source)
                    source
                }
                require(boundaries.zipWithNext().all{(a,b)->a<b}){"Markers round to the same source millisecond"}
                (listOf(clip.startMs)+boundaries+clip.endMs).zipWithNext().forEach{(from,to)->
                    require((to-from)*1000.0/clip.speed>=100_000){"Each new piece needs at least 0.1 playback second"}
                    out+=clip.copy(startMs=from,endMs=to,captions=clip.captions.filter{it.startMs<to&&it.endMs>from})
                }
            }
            offset=end
        }
        require(cuts.isNotEmpty()){"All markers are existing clip boundaries; no cuts needed"}
        require(out.size<=1000){"Cuts exceed the 1000-clip project limit"}
        require(out.sumOf{it.captions.size}<=VideoCaptions.MAX_PROJECT_CUES){"Cuts exceed the project caption count limit"}
        require(out.sumOf{clip->clip.captions.sumOf{it.text.toByteArray(Charsets.UTF_8).size}}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES){"Cuts exceed the project caption text limit"}
        require(out.sumOf{it.title.toByteArray(Charsets.UTF_8).size}<=128000){"Cuts exceed the project title limit"}
        VideoDraftCodec.requireRecoverableCaptions(out,music?.loop==false,music?.hasExcerpt==true)
        return Result(out.toList(),cuts.toList(),existing,VideoPlaybackTime.durationUs(out)-duration)
    }
}
