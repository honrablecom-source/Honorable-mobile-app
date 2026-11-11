package com.honorablemobile

import java.util.Collections
import java.util.Locale

/** Video-only CMX3600 cuts. Source timestamps are zero-based, not embedded timecode. */
internal object VideoEdl {
    data class Cut(val sourceId:String,val startMs:Long,val endMs:Long,val speed:Float=1f)
    data class Result(val text:String,val fps:Int,val events:Int,val frames:Long,val adjustedCuts:Int,val reels:Map<String,String>)

    fun prepare(cuts:List<Cut>,fps:Int):Result {
        require(fps in setOf(24,25,30)){"Choose 24, 25 or 30 non-drop frames per second"}
        require(cuts.size in 1..999){"CMX3600 export supports 1–999 clips"}
        require(cuts.all{it.sourceId.matches(Regex("[A-Za-z0-9_-]{1,64}"))}){"Unsupported source identifier"}
        require(cuts.map{it.sourceId}.distinct().size<=32){"Use up to 32 video sources"}
        require(cuts.all{it.speed==1f}){"CMX3600 cut export requires every clip at 1x speed"}
        require(cuts.all{it.startMs>=0&&it.endMs>it.startMs&&it.endMs<86_400_000}){"Use increasing source times below 24 hours"}
        val reels=linkedMapOf<String,String>()
        cuts.forEach{if(it.sourceId !in reels)reels[it.sourceId]=String.format(Locale.ROOT,"N%03d",reels.size+1)}
        var cursor=0L
        var adjusted=0
        val events=cuts.mapIndexed{index,cut->
            // Snap inward, so an exported cut never requests footage outside its source range.
            val sourceIn=(cut.startMs*fps+999)/1000
            val sourceOut=cut.endMs*fps/1000
            require(sourceOut>sourceIn){"Clip ${index+1} contains no complete frame at $fps fps"}
            if(sourceIn*1000!=cut.startMs*fps||sourceOut*1000!=cut.endMs*fps)adjusted++
            val recordOut=cursor+(sourceOut-sourceIn)
            require(recordOut<86_400L*fps){"The frame-rounded timeline must stay below 24 hours"}
            val line=String.format(Locale.ROOT,"%03d  %-8s V     C        %s %s %s %s",index+1,reels.getValue(cut.sourceId),
                timecode(sourceIn,fps),timecode(sourceOut,fps),timecode(cursor,fps),timecode(recordOut,fps))
            cursor=recordOut
            line
        }
        val text=buildString {
            append("TITLE: NOBLE VIDEO CUTS\nFCM: NON-DROP FRAME\n")
            append("* NOBLE FRAME RATE: $fps\n")
            append("* VIDEO ONLY; ZERO-BASED SOURCE TIME; INWARD FRAME SNAP\n")
            append("* RELINK REELS MANUALLY; EFFECTS, CAPTIONS AND AUDIO OMITTED\n")
            reels.forEach{(source,reel)->append("* NOBLE SOURCE $reel = $source\n")}
            append('\n');append(events.joinToString("\n"));append('\n')
        }
        return Result(text,fps,cuts.size,cursor,adjusted,Collections.unmodifiableMap(reels.toMap()))
    }

    private fun timecode(frame:Long,fps:Int):String {
        val seconds=frame/fps
        return String.format(Locale.ROOT,"%02d:%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60,frame%fps)
    }
}
