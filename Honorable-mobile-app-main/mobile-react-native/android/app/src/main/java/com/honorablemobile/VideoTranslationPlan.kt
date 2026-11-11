package com.honorablemobile

/** Caption timing/count remain source-owned; translation never silently drops/truncates a cue. */
internal object VideoTranslationPlan {
    fun source(cues:List<VideoCaption>):List<VideoCaption>{
        require(cues.size in 1..VideoCaptions.MAX_PER_CLIP){"Add captions before translating"};VideoCaptions.validate(cues)
        require(cues.sumOf{it.text.toByteArray(Charsets.UTF_8).size}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES)
        return cues.toList()
    }
    fun draft(original:List<VideoCaption>,translated:List<String>):String {
        source(original);require(translated.size==original.size){"Every caption must finish before review"}
        require(translated.all{it.isNotBlank()&&it.length<=8192&&it.none{c->c.isISOControl()&&c!='\n'}})
        val srt=original.mapIndexed{index,cue->"${index+1}\n${VideoCaptions.formatTime(cue.startMs)} --> ${VideoCaptions.formatTime(cue.endMs)}\n${translated[index]}\n"}.joinToString("\n")
        require(srt.toByteArray(Charsets.UTF_8).size<=VideoCaptions.MAX_SRT_BYTES){"Translation draft exceeds review limits"};return srt
    }
    fun corrected(original:List<VideoCaption>,srt:String,duration:Long):List<VideoCaption>{
        source(original);val after=VideoCaptions.parseSrt(srt.toByteArray(Charsets.UTF_8),duration)
        require(after.size==original.size&&after.indices.all{after[it].startMs==original[it].startMs&&after[it].endMs==original[it].endMs}){"Keep every original caption and exact source timestamps; edit text only"}
        require(after.none{it.text.contains("<unk>")}){"Correct the model's unknown-word markers before applying"}
        return after
    }
}
