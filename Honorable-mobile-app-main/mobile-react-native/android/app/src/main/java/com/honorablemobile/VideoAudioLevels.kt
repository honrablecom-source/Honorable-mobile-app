package com.honorablemobile

import kotlin.math.log10
import kotlin.math.sqrt

/** Duration-weighted source RMS overview, not a waveform, beat detector or final audio mix. */
internal object VideoAudioLevels {
    data class Bar(val startMs:Long,val endMs:Long,val rms:Double?,val coverage:Double)
    data class Overview(val startMs:Long,val endMs:Long,val bars:List<Bar>,val knownMs:Long,val averageRms:Double?)
    fun inspect(windows:List<VideoQuietWindow>,maximumBars:Int=96):Overview{
        require(maximumBars in 1..320&&windows.size in 1..6000)
        val start=windows.first().startMs;val end=windows.last().endMs
        require(start>=0&&end>start&&end<=Int.MAX_VALUE&&end-start<=120_000)
        require(windows.zipWithNext().all{(a,b)->a.endMs==b.startMs}&&windows.all{it.endMs>it.startMs&&it.endMs-it.startMs<=20&&(it.rms==null||(it.rms.isFinite()&&it.rms in 0.0..1.0))}){"Invalid source audio windows"}
        var known=0L;var power=0.0
        for(window in windows)window.rms?.let{val duration=window.endMs-window.startMs;known+=duration;power+=it*it*duration}
        val count=minOf(maximumBars,windows.size);var first=0
        val bars=(0 until count).map{index->
            val from=start+(end-start)*index/count;val to=start+(end-start)*(index+1)/count
            while(first<windows.size&&windows[first].endMs<=from)first++
            var i=first;var duration=0L;var energy=0.0
            while(i<windows.size&&windows[i].startMs<to){
                val window=windows[i];val overlap=minOf(to,window.endMs)-maxOf(from,window.startMs)
                if(overlap>0)window.rms?.let{duration+=overlap;energy+=it*it*overlap};i++
            }
            val coverage=duration.toDouble()/(to-from)
            Bar(from,to,if(coverage>=.9)sqrt(energy/duration)else null,coverage)
        }
        return Overview(start,end,bars,known,if(known>0)sqrt(power/known)else null)
    }
    fun selectedTime(startMs:Long,endMs:Long,fraction:Double):Long{
        require(startMs>=0&&endMs>startMs&&endMs<=Int.MAX_VALUE&&fraction.isFinite())
        return (startMs+((endMs-startMs)*fraction.coerceIn(0.0,1.0)).toLong()).coerceIn(startMs,endMs-1)
    }
    fun fraction(overview:Overview,timeMs:Long):Double{
        require(timeMs>=overview.startMs&&timeMs<overview.endMs){"Choose a time inside this selected source range"}
        return (timeMs-overview.startMs).toDouble()/(overview.endMs-overview.startMs)
    }
    fun barAt(overview:Overview,timeMs:Long):Bar{
        fraction(overview,timeMs)
        return overview.bars.first{timeMs>=it.startMs&&timeMs<it.endMs}
    }
    fun height(rms:Double?):Double?{
        if(rms==null)return null
        require(rms.isFinite()&&rms in 0.0..1.0)
        return if(rms==0.0)0.0 else ((20*log10(rms)+60)/60).coerceIn(0.0,1.0)
    }
    fun topRegions(overview:Overview,limit:Int=3):List<Bar>{
        require(limit in 1..10)
        return overview.bars.filter{it.rms!=null&&it.rms>0}.sortedWith(compareByDescending<Bar>{it.rms}.thenBy{it.startMs}).take(limit)
    }
    fun levelText(rms:Double?):String{
        if(rms==null)return "Unknown"
        require(rms.isFinite()&&rms in 0.0..1.0)
        return if(rms<=.001)"At or below -60 dBFS" else String.format(java.util.Locale.ROOT,"%.1f dBFS",20*log10(rms))
    }
}
