package com.honorablemobile

import java.util.Collections

/** A stable permutation of whole clips. Original boundary refers to the pre-move timeline. */
internal class VideoClipBlockMove private constructor(clips:List<VideoSegment>,selected:List<Int>,val before:Int,order:List<Int>,val insertionIndex:Int){
    val base:List<VideoSegment> = immutable(clips)
    val selected:List<Int> = Collections.unmodifiableList(selected.toList())
    val order:List<Int> = Collections.unmodifiableList(order.toList())
    val segments:List<VideoSegment> = immutable(order.map{clips[it]})
    val changed:Boolean get()=base!=segments
    val durationUs:Long get()=VideoPlaybackTime.durationUs(segments)
    fun apply(project:VideoEditProject,requireCurrent:()->Unit){
        requireCurrent();require(project.segments==base){"Timeline changed. Select the clip group again."}
        project.applyDraft(segments,soundtrack=project.soundtrack)
    }
    companion object {
        const val MAX_SELECTION=200
        private fun immutable(clips:List<VideoSegment>)=Collections.unmodifiableList(clips.map{it.copy(captions=Collections.unmodifiableList(it.captions.toList()))})
        fun prepare(clips:List<VideoSegment>,selected:List<Int>,before:Int,requireCurrent:()->Unit={}):VideoClipBlockMove {
            requireCurrent();require(clips.size in 1..1000);VideoPlaybackTime.durationUs(clips)
            require(selected.size in 1..MAX_SELECTION&&selected.distinct().size==selected.size&&selected.all{it in clips.indices}){"Select 1–200 different current clips."}
            val sorted=selected.sorted();val chosen=sorted.toSet()
            require(before in 0..clips.size&&(before==clips.size||before !in chosen)){"Choose an unselected original clip as the destination, or the timeline end."}
            val remaining=clips.indices.filter{it !in chosen};val insertion=remaining.count{it<before}
            val order=remaining.take(insertion)+sorted+remaining.drop(insertion)
            check(order.size==clips.size&&order.toSet()==clips.indices.toSet())
            requireCurrent();return VideoClipBlockMove(clips,sorted,before,order,insertion)
        }
    }
}
