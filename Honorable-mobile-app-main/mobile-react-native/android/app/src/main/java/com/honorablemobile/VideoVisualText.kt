package com.honorablemobile

/** OCR sample windows, not exact subtitle boundaries or speech recognition. */
internal object VideoVisualText {
    const val STEP_MS=500L
    const val MAX_MS=10_000L
    data class Window(val from:Long,val to:Long){val requestedMs:Long get()=from+(to-from)/2}
    data class Crop(val top:Int=60,val bottom:Int=100){
        init{require(top in 0..90&&bottom in 10..100&&bottom-top>=10){"Choose a vertical crop at least 10 percent tall"}}
        fun pixels(height:Int):IntRange{require(height in 1..8192);val start=height*top/100;val end=(height*bottom+99)/100;return start until end}
    }
    fun windows(from:Long,to:Long):List<Window>{
        require(from>=0&&to<=Int.MAX_VALUE&&to-from in 500..MAX_MS){"Choose 0.5–10 source seconds"}
        val result=mutableListOf<Window>();var cursor=from
        while(cursor<to){val end=minOf(to,cursor+STEP_MS);result.add(Window(cursor,end));cursor=end}
        return result
    }
    fun captions(from:Long,to:Long,observed:List<String>,current:()->Unit={}):List<VideoCaption>{
        val windows=windows(from,to);require(observed.size==windows.size){"Every requested frame must be recognized"}
        val result=mutableListOf<VideoCaption>()
        for((index,window)in windows.withIndex()){
            current()
            val raw=observed[index]
            require(raw.length<=4096){"Too much visible text; choose a smaller crop"}
            val text=raw.replace("\r\n","\n").replace('\r','\n').lines().map{it.trim()}.filter{it.isNotEmpty()}.joinToString("\n")
            if(text.isEmpty())continue
            val cue=VideoCaption(window.from,window.to,text)
            val last=result.lastOrNull()
            if(last!=null&&last.endMs==cue.startMs&&last.text==text)result[result.lastIndex]=last.copy(endMs=cue.endMs)
            else result.add(cue)
        }
        VideoCaptions.validate(result);current();return result.toList()
    }
}
