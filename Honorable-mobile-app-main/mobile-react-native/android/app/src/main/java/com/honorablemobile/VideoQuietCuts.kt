package com.honorablemobile

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

internal data class VideoQuietWindow(val startMs:Long,val endMs:Long,val rms:Double?)

/** Streaming 20 ms source-time energy windows. Missing coverage is unknown, never silence. */
internal class VideoQuietEnergy(val startMs:Long,val endMs:Long,val sampleRate:Int){
    init{require(startMs>=0&&endMs>startMs&&endMs-startMs<=120_000&&endMs<=Int.MAX_VALUE);require(sampleRate in 8_000..192_000)}
    private val size=((endMs-startMs+19)/20).toInt()
    private val counts=IntArray(size)
    private val powers=DoubleArray(size)
    private var lastUs=Long.MIN_VALUE
    fun frame(timeUs:Long,meanSquare:Double){
        require(meanSquare.isFinite()&&meanSquare in 0.0..1.0){"Invalid decoded audio sample"}
        if(timeUs<startMs*1000||timeUs>=endMs*1000)return
        require(timeUs>lastUs){"Audio timestamps overlap or run backward"};lastUs=timeUs
        val i=((timeUs-startMs*1000)/20_000).toInt();counts[i]++;powers[i]+=meanSquare
    }
    fun pcm(buffer:ByteBuffer,offset:Int,bytes:Int,presentationUs:Long,channels:Int,floatPcm:Boolean,checkCurrent:()->Unit={}){
        require(channels in 1..8&&offset>=0&&bytes in 0..1_048_576&&offset.toLong()+bytes<=buffer.limit()){"Unsupported audio buffer size"}
        val frameBytes=channels*if(floatPcm)4 else 2
        require(bytes%frameBytes==0){"Incomplete decoded audio frame"}
        val input=buffer.duplicate().order(ByteOrder.nativeOrder());input.position(offset);input.limit(offset+bytes)
        repeat(bytes/frameBytes){i->
            if(i%1024==0)checkCurrent()
            var energy=0.0
            repeat(channels){
                val sample=if(floatPcm)input.float.toDouble().also{require(it.isFinite()){"Invalid floating-point audio"}}.coerceIn(-1.0,1.0)else input.short.toDouble()/32768.0
                energy+=sample*sample
            }
            frame(Math.addExact(presentationUs,i*1_000_000L/sampleRate),energy/channels)
        }
    }
    fun finish():List<VideoQuietWindow> =(0 until size).map{i->
        val start=startMs+i*20L;val end=minOf(endMs,start+20)
        val expected=(end-start)*sampleRate/1000.0
        val known=counts[i]>=ceil(expected*.85).toInt()&&counts[i]<=ceil(expected*1.15).toInt()+2
        VideoQuietWindow(start,end,if(known)sqrt(powers[i]/counts[i])else null)
    }
}

internal object VideoQuietCuts {
    data class Options(val thresholdDb:Int=-38,val minimumPauseMs:Long=500,val paddingMs:Long=100){
        init{require(thresholdDb in -60..-15){"Use a threshold from -60 to -15 dBFS"};require(minimumPauseMs in 200..5000){"Use a minimum pause of 0.2–5 playback seconds"};require(paddingMs in 0..500){"Use edge padding of 0–0.5 playback seconds"}}
    }
    data class Range(val startMs:Long,val endMs:Long)
    data class Plan(val removed:List<Range>,val retained:List<Range>,val knownWindows:Int,val totalWindows:Int)
    fun plan(startMs:Long,endMs:Long,speed:Float,windows:List<VideoQuietWindow>,options:Options):Plan{
        require(startMs>=0&&endMs>startMs&&endMs-startMs<=120_000&&endMs<=Int.MAX_VALUE){"Select a clip range of at most two source minutes"}
        require(speed in setOf(.5f,1f,1.5f,2f))
        require(windows.isNotEmpty()&&windows.size<=6000&&windows.first().startMs==startMs&&windows.last().endMs==endMs&&windows.zipWithNext().all{(a,b)->a.endMs==b.startMs}){"Audio windows do not cover this clip"}
        require(windows.all{it.endMs>it.startMs&&it.endMs-it.startMs<=20&&(it.rms==null||(it.rms.isFinite()&&it.rms in 0.0..1.0))}){"Invalid audio windows"}
        val known=windows.count{it.rms!=null};require(known>=ceil(windows.size*.9).toInt()){"Audio timestamps did not cover enough of this clip. Try a smaller range or another source."}
        val threshold=10.0.pow(options.thresholdDb/20.0)
        val minimum=ceil(options.minimumPauseMs*speed.toDouble()).toLong();val pad=ceil(options.paddingMs*speed.toDouble()).toLong()
        val removed=mutableListOf<Range>();var i=0
        while(i<windows.size){
            if(windows[i].rms?.let{it<=threshold}!=true){i++;continue}
            val first=i;while(i<windows.size&&windows[i].rms?.let{it<=threshold}==true)i++
            // Preserve leading/trailing quiet and any region adjacent to unknown audio.
            val before=windows.getOrNull(first-1)?.rms;val after=windows.getOrNull(i)?.rms
            val from=windows[first].startMs;val to=windows[i-1].endMs
            if(before!=null&&before>threshold&&after!=null&&after>threshold&&to-from>=minimum&&to-from>pad*2)removed.add(Range(from+pad,to-pad))
        }
        require(removed.isNotEmpty()){"No interior quiet pauses match these settings. Leading/trailing quiet is kept."}
        require(removed.size<50){"Too many pauses. Increase minimum pause length."}
        val retained=mutableListOf<Range>();var at=startMs
        for(range in removed){if(range.startMs>at)retained.add(Range(at,range.startMs));at=range.endMs}
        if(at<endMs)retained.add(Range(at,endMs))
        require(retained.isNotEmpty()&&retained.all{(it.endMs-it.startMs)*1000.0/speed>=100_000}){"Keep at least 0.1 playback second in each piece. Increase edge padding."}
        return Plan(removed.toList(),retained.toList(),known,windows.size)
    }
    fun apply(clips:List<VideoSegment>,index:Int,plan:Plan):List<VideoSegment>{
        require(index in clips.indices&&clips.size in 1..1000)
        val clip=clips[index]
        require(plan.retained.size in 2..50&&plan.retained.first().startMs==clip.startMs&&plan.retained.last().endMs==clip.endMs&&plan.retained.all{it.startMs>=clip.startMs&&it.endMs>it.startMs&&it.endMs<=clip.endMs}&&plan.retained.zipWithNext().all{(a,b)->a.endMs<b.startMs}){"Quiet-cut proposal no longer matches this clip"}
        require(plan.removed==plan.retained.zipWithNext().map{(a,b)->Range(a.endMs,b.startMs)}&&plan.retained.all{(it.endMs-it.startMs)*1000.0/clip.speed>=100_000}){"Quiet-cut ranges are inconsistent or too short"}
        require(clips.size+plan.retained.size-1<=1000){"Project clip limit reached"}
        val pieces=plan.retained.map{range->clip.copy(startMs=range.startMs,endMs=range.endMs,captions=clip.captions.filter{it.endMs>range.startMs&&it.startMs<range.endMs})}
        return clips.take(index)+pieces+clips.drop(index+1)
    }
}
