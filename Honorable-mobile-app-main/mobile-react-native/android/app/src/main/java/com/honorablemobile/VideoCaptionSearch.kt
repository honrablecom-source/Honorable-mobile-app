package com.honorablemobile

import java.util.Collections

/** Local text navigation over existing captions, independent of native media/embedding search. */
internal object VideoCaptionSearch {
    data class Match(val clipIndex:Int,val cueIndex:Int,val sourceId:String,val sourceMs:Long,val outputUs:Long,val text:String) {
        fun label():String {
            val flat=text.replace('\n',' ')
            val count=flat.codePointCount(0,flat.length)
            val preview=if(count<=96)flat else flat.substring(0,flat.offsetByCodePoints(0,96))+"…"
            return "Clip ${clipIndex+1} · ${VideoCaptions.formatTime(outputUs/1000)} playback\n$preview"
        }
    }
    fun find(clips:List<VideoSegment>,query:String,matchCase:Boolean=false):List<Match> {
        VideoPlaybackTime.durationUs(clips)
        val text=query.trim()
        require(text.isNotEmpty()&&text.codePointCount(0,text.length)<=80&&text.none{it.isISOControl()}&&text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==text){"Find 1–80 characters on one line"}
        require(clips.sumOf{it.captions.size}<=VideoCaptions.MAX_PROJECT_CUES)
        require(clips.sumOf{clip->clip.captions.sumOf{it.text.toByteArray(Charsets.UTF_8).size}}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES)
        var offset=0L
        val matches=mutableListOf<Match>()
        clips.forEachIndexed{index,clip->
            clip.captions.forEachIndexed{cueIndex,cue->
                val from=maxOf(clip.startMs,cue.startMs);val end=minOf(clip.endMs,cue.endMs)
                if(end>from&&cue.text.replace('\n',' ').contains(text,ignoreCase=!matchCase)){
                    matches.add(Match(index,cueIndex,clip.sourceId,from,offset+((from-clip.startMs)*1000.0/clip.speed).toLong(),cue.text))
                }
            }
            offset+=clip.durationUs
        }
        return Collections.unmodifiableList(matches)
    }
}
