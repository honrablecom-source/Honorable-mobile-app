package com.honorablemobile

/** Text reuse from existing rendered-timeline captions; no speech recognition or source metadata export. */
internal object VideoCaptionText {
    const val MAX_BYTES=128_000
    const val PREVIEW_POINTS=8000
    data class Result(val cues:Int,val text:String) {
        fun preview(limit:Int=PREVIEW_POINTS):String {
            require(limit in 1..PREVIEW_POINTS)
            if(text.codePointCount(0,text.length)<=limit)return text
            return text.substring(0,text.offsetByCodePoints(0,limit))+"…\n\nPreview limited. Copy all text includes every visible cue."
        }
    }
    fun create(clips:List<VideoSegment>,timed:Boolean=false):Result {
        VideoPlaybackTime.durationUs(clips)
        require(clips.sumOf{it.captions.size}<=VideoCaptions.MAX_PROJECT_CUES)
        require(clips.sumOf{clip->clip.captions.sumOf{it.text.toByteArray(Charsets.UTF_8).size}}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES)
        val cues=VideoCaptions.resolve(clips)
        require(cues.isNotEmpty()){ "No captions are visible. Add captions or check the trimmed ranges." }
        val text=cues.joinToString("\n\n"){cue->
            if(!timed)cue.text else {
                val end=cue.endUs/1000+if(cue.endUs%1000==0L)0 else 1
                "[${VideoCaptions.formatTime(cue.startUs/1000)} --> ${VideoCaptions.formatTime(end)}]\n${cue.text}"
            }
        }+"\n"
        require(text.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"Caption text exceeds the copy limit"}
        return Result(cues.size,text)
    }
}
