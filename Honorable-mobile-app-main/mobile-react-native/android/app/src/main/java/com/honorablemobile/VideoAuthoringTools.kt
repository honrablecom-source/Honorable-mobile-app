package com.honorablemobile

import kotlin.math.ceil
import kotlin.math.roundToLong

/** Bounded local authoring proposals; the existing project applies and undoes them atomically. */
internal object VideoAuthoringTools {
    private fun checked(clips:List<VideoSegment>) {
        require(clips.size in 1..1000){"Use a project with 1–1000 clips"}
    }
    private fun piece(clip:VideoSegment,from:Long,to:Long)=clip.copy(startMs=from,endMs=to,captions=clip.captions.filter{it.endMs>from&&it.startMs<to})
    private fun replace(clips:List<VideoSegment>,index:Int,pieces:List<VideoSegment>):List<VideoSegment> {
        checked(clips);require(index in clips.indices)
        require(clips.size-1+pieces.size in 1..1000){"This edit exceeds the 1000-clip limit or removes the whole project"}
        return clips.take(index)+pieces+clips.drop(index+1)
    }
    private fun minimum(clip:VideoSegment)=ceil(100*clip.speed.toDouble()).toLong()

    fun removeInterval(clips:List<VideoSegment>,index:Int,from:Long,to:Long):List<VideoSegment> {
        checked(clips);require(index in clips.indices)
        val c=clips[index];require(from>=c.startMs && to<=c.endMs && to>from){"Choose an interval inside the selected source range"}
        val ranges=listOf(c.startMs to from,to to c.endMs).filter{it.second>it.first}
        require(ranges.all{it.second-it.first>=minimum(c)}){"Leave at least 0.1 playback second on each remaining side"}
        return replace(clips,index,ranges.map{piece(c,it.first,it.second)})
    }

    fun captionCut(clips:List<VideoSegment>,index:Int,handlesMs:Long):List<VideoSegment> {
        checked(clips);require(index in clips.indices && handlesMs in 0..1000)
        val c=clips[index];val spans=mutableListOf<Pair<Long,Long>>()
        for(cue in c.captions) {
            val from=maxOf(c.startMs,cue.startMs-handlesMs);val to=minOf(c.endMs,cue.endMs+handlesMs)
            if(to<=from)continue
            val previous=spans.lastOrNull()
            if(previous!=null && from<=previous.second)spans[spans.lastIndex]=previous.first to maxOf(previous.second,to)
            else spans.add(from to to)
        }
        require(spans.isNotEmpty()){"Add captions visible inside this clip first"}
        require(spans.all{it.second-it.first>=minimum(c)}){"A caption range is too short. Add longer handles."}
        return replace(clips,index,spans.map{piece(c,it.first,it.second)})
    }

    /** Manual tempo grid, not detected beats. Existing clip boundaries remain intact. */
    fun beatGrid(clips:List<VideoSegment>,bpm:Int,beatsPerCut:Int=1):List<VideoSegment> {
        checked(clips);require(bpm in 40..240 && beatsPerCut in 1..8)
        val total=clips.fold(0L){sum,c->Math.addExact(sum,c.durationUs)}
        require(total<=604_800_000_000L){"Use a project shorter than seven days"}
        require(total/((60_000_000L*beatsPerCut)/bpm)+clips.size<=1000){"Use fewer cuts or a shorter project to stay within 1000 clips"}
        val result=mutableListOf<VideoSegment>();var offset=0L;var beat=1L
        fun time()=beat*60_000_000L*beatsPerCut/bpm
        for(c in clips) {
            val end=offset+c.durationUs;var from=c.startMs
            while(time()<end) {
                val cut=c.startMs+(((time()-offset)/1000.0)*c.speed).roundToLong()
                if(cut-from>=minimum(c) && c.endMs-cut>=minimum(c)){result.add(piece(c,from,cut));from=cut}
                beat++
            }
            result.add(piece(c,from,c.endMs));offset=end
            while(time()<=offset)beat++
        }
        require(result.size<=1000);return result
    }

    /** Four source-time pieces, using the speeds already supported by preview/export. */
    fun speedRamp(clips:List<VideoSegment>,index:Int,accelerate:Boolean):List<VideoSegment> {
        checked(clips);require(index in clips.indices)
        val c=clips[index];require(c.endMs-c.startMs>=800){"Use at least 0.8 source seconds for this four-step ramp"}
        val speeds=listOf(.5f,1f,1.5f,2f).let{if(accelerate)it else it.reversed()}
        val span=c.endMs-c.startMs
        val pieces=speeds.mapIndexed{i,speed->val from=c.startMs+span/4*i+minOf(i.toLong(),span%4);val to=c.startMs+span/4*(i+1)+minOf((i+1).toLong(),span%4);piece(c,from,to).copy(speed=speed)}
        return replace(clips,index,pieces)
    }

    fun interleaveSources(clips:List<VideoSegment>):List<VideoSegment> {
        checked(clips)
        val queues=clips.groupBy{it.sourceId}.values.map{ArrayDeque(it)}
        require(queues.size>=2){"Add clips from at least two source videos"}
        val result=mutableListOf<VideoSegment>()
        while(result.size<clips.size)for(queue in queues)if(queue.isNotEmpty())result.add(queue.removeFirst())
        return result
    }

    fun wordCaptions(clips:List<VideoSegment>,index:Int,wordsPerCue:Int):List<VideoSegment> {
        checked(clips);require(index in clips.indices && wordsPerCue in 1..4)
        val c=clips[index];require(c.captions.isNotEmpty()){"Add captions first"}
        val cues=mutableListOf<VideoCaption>()
        for(cue in c.captions) {
            val chunks=cue.text.trim().split(Regex("(?U)\\s+")).chunked(wordsPerCue).map{it.joinToString(" ")}
            require(cues.size+chunks.size<=VideoCaptions.MAX_PER_CLIP){"Word captions exceed 200 cues; choose more words per cue"}
            val minimum=ceil(80*c.speed.toDouble()).toLong();val span=cue.endMs-cue.startMs
            require(span>=chunks.size*minimum){"A caption is too short for these word timings; choose more words per cue"}
            val weights=chunks.map{it.codePointCount(0,it.length).toLong()};val total=weights.sum();val spare=span-minimum*chunks.size
            var consumed=0L;var from=cue.startMs
            chunks.forEachIndexed{i,text->consumed+=weights[i];val to=cue.startMs+minimum*(i+1)+spare*consumed/total;cues.add(VideoCaption(from,to,text));from=to}
        }
        return replace(clips,index,listOf(c.copy(captions=cues,captionSourceUri=null)))
    }

    fun mergeCaptions(clips:List<VideoSegment>,index:Int,maximumGapMs:Long):List<VideoSegment> {
        checked(clips);require(index in clips.indices && maximumGapMs in 0..1000)
        val c=clips[index];require(c.captions.isNotEmpty()){"Add captions first"}
        val merged=mutableListOf<VideoCaption>()
        for(cue in c.captions) {
            val previous=merged.lastOrNull();val text=previous?.let{it.text.replace('\n',' ') + " " + cue.text.replace('\n',' ')}
            if(previous!=null && cue.startMs-previous.endMs<=maximumGapMs && cue.endMs-previous.startMs<=8_000 && text!!.length<=160)
                merged[merged.lastIndex]=VideoCaption(previous.startMs,cue.endMs,text)
            else merged.add(cue)
        }
        return replace(clips,index,listOf(c.copy(captions=merged,captionSourceUri=null)))
    }

    fun sourceTimecodes(clips:List<VideoSegment>,index:Int,playbackStepMs:Long):List<VideoSegment> {
        checked(clips);require(index in clips.indices && playbackStepMs in 500..10_000)
        val c=clips[index];require(c.endMs<=Int.MAX_VALUE.toLong()){"Source exceeds supported caption timestamps"}
        val step=(playbackStepMs*c.speed).roundToLong().coerceAtLeast(1)
        val count=(c.endMs-c.startMs+step-1)/step
        require(count<=VideoCaptions.MAX_PER_CLIP){"Use a larger interval or a shorter clip (up to 200 timecodes)"}
        val cues=(0 until count.toInt()).map{i->val from=c.startMs+i*step;VideoCaption(from,minOf(c.endMs,from+step),"TC "+VideoCaptions.formatTime(from).replace(',','.'))}
        return replace(clips,index,listOf(c.copy(captions=cues,captionSourceUri=null)))
    }

    fun captionDucking(clips:List<VideoSegment>,index:Int,level:Float):List<VideoSegment> {
        checked(clips);require(index in clips.indices && level.isFinite() && level in 0f.. .75f)
        val c=clips[index]
        require(!c.fadeEdges && !c.audioFade){"Turn off this clip's picture/audio fades before caption ducking"}
        val visible=c.captions.filter{it.endMs>c.startMs && it.startMs<c.endMs}
        require(visible.isNotEmpty()){"Add captions visible inside this clip first"}
        val boundaries=(listOf(c.startMs,c.endMs)+visible.flatMap{listOf(maxOf(c.startMs,it.startMs),minOf(c.endMs,it.endMs))}).distinct().sorted()
        require(clips.size+boundaries.size-2<=1000){"This ducking edit exceeds 1000 clips"}
        val pieces=boundaries.zipWithNext().map{(from,to)->val middle=from+(to-from)/2;val active=visible.any{middle>=it.startMs&&middle<it.endMs};piece(c,from,to).copy(volume=if(active)c.volume*level else c.volume)}
        return replace(clips,index,pieces)
    }

    fun storyboardTitles(clips:List<VideoSegment>,script:String):List<VideoSegment> {
        checked(clips);require(script.length<=16_000){"Use at most 16000 characters"}
        val lines=script.replace("\r\n","\n").replace('\r','\n').trim().lines().map{it.trim()}
        require(lines.size==clips.size && lines.all{it.isNotBlank()}){"Write exactly one nonempty title line per clip (${clips.size} lines)"}
        return clips.mapIndexed{i,c->c.copy(title=lines[i])}
    }
}
