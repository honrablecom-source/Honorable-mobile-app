package com.honorablemobile

import java.util.Collections

/** Metadata only. Match first/last cue starts to original-source times; never trim or drop cues. */
internal class VideoCaptionSync private constructor(clips:List<VideoSegment>,proposed:List<VideoSegment>,val index:Int,val firstMs:Long,val lastMs:Long){
    val base:List<VideoSegment> = immutable(clips)
    val segments:List<VideoSegment> = immutable(proposed)
    val before:List<VideoCaption> get()=base[index].captions
    val after:List<VideoCaption> get()=segments[index].captions
    val changed:Boolean get()=base!=segments
    val changedCues:Int get()=before.indices.count{before[it]!=after[it]}
    fun apply(project:VideoEditProject,requireCurrent:()->Unit){
        requireCurrent();require(project.segments==base){"Timeline changed. Review subtitle timing again."}
        project.applyDraft(segments,soundtrack=project.soundtrack)
    }
    companion object {
        private fun immutable(clips:List<VideoSegment>)=Collections.unmodifiableList(clips.map{it.copy(captions=Collections.unmodifiableList(it.captions.toList()))})
        fun prepare(clips:List<VideoSegment>,index:Int,sourceDurationMs:Long,firstMs:Long,lastMs:Long,music:VideoSoundtrack?=null,requireCurrent:()->Unit={}):VideoCaptionSync {
            requireCurrent();val clip=requireNotNull(clips.getOrNull(index)){"Choose a current clip"};val cues=clip.captions
            require(cues.size>=2){"Add at least two captions to synchronize their timing."};VideoCaptions.validate(cues)
            require(sourceDurationMs in 1..Int.MAX_VALUE.toLong()&&clip.endMs<=sourceDurationMs&&cues.all{it.endMs<=sourceDurationMs}){"Check the original source duration."}
            require(firstMs>=0&&lastMs>firstMs&&lastMs<=sourceDurationMs){"Use increasing anchor times inside the original video."}
            val origin=cues.first().startMs;val span=cues.last().startMs-origin;val targetSpan=lastMs-firstMs
            // Every operand is bounded by Int.MAX_VALUE; product plus rounding remains within Long.
            fun map(at:Long):Long=firstMs+((at-origin)*targetSpan+span/2)/span
            val mapped=cues.map{cue->
                val start=map(cue.startMs);val end=map(cue.endMs)
                require(end<=sourceDurationMs&&end>start){"Adjusted cues exceed the source or collapse below one millisecond. Change the anchors; nothing was changed."}
                cue.copy(startMs=start,endMs=end)
            }
            VideoCaptions.validate(mapped)
            val proposed=clips.mapIndexed{i,value->if(i==index)value.copy(captions=mapped)else value}
            VideoDraftCodec.requireRecoverableCaptions(proposed,music?.loop==false,music?.hasExcerpt==true)
            requireCurrent();return VideoCaptionSync(clips,proposed,index,firstMs,lastMs)
        }
    }
}
