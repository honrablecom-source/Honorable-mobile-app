package com.honorablemobile

/** Approximate sound labels, never speech transcription or calibrated confidence. */
internal object VideoSoundEvents {
    const val RATE=16000
    const val CLASSES=521
    const val MAX_SAMPLES=20*RATE
    data class Choice(val index:Int,val label:String,val score:Float)
    data class Window(val startMs:Long,val endMs:Long,val choices:List<Choice>)

    // Upstream pad_waveform includes the final 25ms STFT window minus its 10ms hop.
    fun frames(samples:Int):Int {
        require(samples in RATE/2..MAX_SAMPLES)
        return 1+(maxOf(0,samples-15600)+7679)/7680
    }

    fun propose(scores:Array<FloatArray>,samples:Int,sourceStartMs:Long,labels:List<String>,current:()->Unit={}):List<Window>{
        require(sourceStartMs in 0..Int.MAX_VALUE.toLong()&&sourceStartMs+samples*1000L/RATE<=Int.MAX_VALUE)
        require(labels.size==CLASSES&&labels.all{it.isNotBlank()&&it.length<=160&&'\n' !in it&&'\r' !in it})
        require(scores.size==frames(samples)){"Unexpected sound-model frame count"}
        val end=sourceStartMs+samples*1000L/RATE
        return scores.mapIndexed{frame,row->
            current();require(row.size==CLASSES&&row.all{it.isFinite()&&it in 0f..1f}){"Invalid sound-model scores"}
            val best=row.indices.sortedWith(compareByDescending<Int>{row[it]}.thenBy{it}).take(3)
                .map{Choice(it,labels[it],row[it])}
            val start=sourceStartMs+frame*480L
            val until=if(frame==scores.lastIndex)end else minOf(end,start+480)
            require(until>start)
            Window(start,until,best)
        }
    }

    /** Explicit index→class choices only; no implicit selection, threshold or omitted interval filling. */
    fun selected(windows:List<Window>,selected:Map<Int,Int>):List<VideoCaption>{
        require(windows.isNotEmpty()&&windows.size<=frames(MAX_SAMPLES))
        require(selected.isNotEmpty()&&selected.keys.all{it in windows.indices}){"Choose at least one sound suggestion"}
        val result=mutableListOf<VideoCaption>();var previousClass:Int?=null
        for((index,window)in windows.withIndex()){
            val id=selected[index]
            if(id==null){previousClass=null;continue}
            val choice=requireNotNull(window.choices.singleOrNull{it.index==id}){"Sound suggestion changed"}
            val text="[${choice.label}]";require(text.length<=240)
            if(previousClass==id&&result.lastOrNull()?.endMs==window.startMs){
                result[result.lastIndex]=result.last().copy(endMs=window.endMs)
            }else result.add(VideoCaption(window.startMs,window.endMs,text))
            previousClass=id
        }
        VideoCaptions.validate(result);return result.toList()
    }
}
