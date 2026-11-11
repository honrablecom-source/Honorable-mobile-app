package com.honorablemobile

/** Source-RMS matching with the existing 0..1 clip gain. Not LUFS/perceived/final-mix normalization. */
internal object VideoAudioMatch {
    data class Level(val sourceId:String,val startMs:Long,val endMs:Long,val knownMs:Long,val rms:Double?)
    data class Plan(val clips:List<VideoSegment>,val gain:Float,val targetRms:Double,val predictedRms:Double,val beforeRms:Double,val capped:Boolean)
    fun eligible(clip:VideoSegment)=clip.volume>0f&&clip.endMs<=Int.MAX_VALUE&&clip.endMs-clip.startMs<=120000
    fun measure(clip:VideoSegment,windows:List<VideoQuietWindow>):Level {
        require(eligible(clip)){"Use an unmuted clip of at most two source minutes"}
        val overview=VideoAudioLevels.inspect(windows)
        require(overview.startMs==clip.startMs&&overview.endMs==clip.endMs){"Audio measurement window changed"}
        return Level(clip.sourceId,overview.startMs,overview.endMs,overview.knownMs,overview.averageRms)
    }
    fun prepare(clips:List<VideoSegment>,index:Int,reference:Int,selected:Level,referenceLevel:Level):Plan {
        VideoPlaybackTime.durationUs(clips)
        require(index in clips.indices&&reference in clips.indices&&index!=reference){"Choose another reference clip"}
        val clip=clips[index];val ref=clips[reference]
        fun measured(value:VideoSegment,level:Level):Double {
            require(eligible(value)){"Use unmuted clips with at most two source minutes each"}
            require(level.sourceId==value.sourceId&&level.startMs==value.startMs&&level.endMs==value.endMs){"Audio measurement source or range changed"}
            val span=value.endMs-value.startMs
            require(level.knownMs in 0..span&&level.knownMs*100>=span*95){"At least95% reliable decoded audio coverage is required for both clips"}
            val rms=level.rms
            require(rms!=null&&rms.isFinite()&&rms>.001&&rms<=1.0){"Silent, near-silent or unknown source audio cannot be matched reliably"}
            return rms
        }
        val source=measured(clip,selected);val other=measured(ref,referenceLevel)
        val target=other*ref.volume
        require(target>.001){"Reference volume is too close to silence for reliable matching"}
        val wanted=target/source;val gain=minOf(1.0,wanted).toFloat()
        require(gain!=clip.volume){if(wanted>1.0)"Target needs more than1.0 gain; this clip is already at the limit"else"The clip already has this estimated RMS level"}
        val result=clips.mapIndexed{i,value->if(i==index)value.copy(volume=gain)else value}
        return Plan(result,gain,target,source*gain,source*clip.volume,wanted>1.0)
    }
}
