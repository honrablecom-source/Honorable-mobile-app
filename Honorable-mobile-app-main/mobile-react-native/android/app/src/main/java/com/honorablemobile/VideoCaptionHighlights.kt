package com.honorablemobile

import java.util.Collections

/** Caption-selected source excerpts; no media reading, transcription, search ranking or source mutation. */
internal class VideoCaptionHighlights private constructor(clips:List<VideoSegment>,proposed:List<VideoSegment>,excerpts:List<Excerpt>,val selectedCues:Int){
    data class Selection(val clipIndex:Int,val cueIndex:Int)
    data class Excerpt(val clipIndex:Int,val startMs:Long,val endMs:Long)
    val base:List<VideoSegment> = immutable(clips)
    val segments:List<VideoSegment> = immutable(proposed)
    val excerpts:List<Excerpt> = Collections.unmodifiableList(excerpts.toList())
    val changed:Boolean get()=base!=segments
    val beforeUs:Long get()=VideoPlaybackTime.durationUs(base)
    val afterUs:Long get()=VideoPlaybackTime.durationUs(segments)
    fun apply(project:VideoEditProject,requireCurrent:()->Unit){
        requireCurrent();require(project.segments==base){"Timeline changed. Select caption moments again."}
        project.applyDraft(segments,soundtrack=project.soundtrack)
    }
    companion object {
        const val MAX_SELECTION=200
        private fun immutable(clips:List<VideoSegment>)=Collections.unmodifiableList(clips.map{it.copy(captions=Collections.unmodifiableList(it.captions.toList()))})
        fun prepare(clips:List<VideoSegment>,selected:List<Selection>,music:VideoSoundtrack?=null,requireCurrent:()->Unit={}):VideoCaptionHighlights {
            requireCurrent();require(clips.size in 1..1000);VideoPlaybackTime.durationUs(clips)
            require(selected.size in 1..MAX_SELECTION&&selected.distinct().size==selected.size){"Select 1–200 different caption moments."}
            clips.forEach{VideoCaptions.validate(it.captions)}
            val windows=selected.map{choice->
                val clip=requireNotNull(clips.getOrNull(choice.clipIndex)){"Selected clip changed"}
                val cue=requireNotNull(clip.captions.getOrNull(choice.cueIndex)){"Selected caption changed"}
                val from=maxOf(clip.startMs,cue.startMs);val to=minOf(clip.endMs,cue.endMs)
                require(to>from){"Select captions visible inside their current clip trims."}
                Excerpt(choice.clipIndex,from,to)
            }.sortedWith(compareBy<Excerpt>{it.clipIndex}.thenBy{it.startMs})
            val excerpts=mutableListOf<Excerpt>()
            for(window in windows){val previous=excerpts.lastOrNull()
                if(previous!=null&&previous.clipIndex==window.clipIndex&&window.startMs<=previous.endMs)excerpts[excerpts.lastIndex]=previous.copy(endMs=maxOf(previous.endMs,window.endMs))else excerpts.add(window)
            }
            val proposed=excerpts.map{window->val clip=clips[window.clipIndex];clip.copy(startMs=window.startMs,endMs=window.endMs,captions=clip.captions.filter{it.endMs>window.startMs&&it.startMs<window.endMs})}
            require(proposed.sumOf{it.captions.size}<=VideoCaptions.MAX_PROJECT_CUES&&proposed.sumOf{it.captions.sumOf{cue->cue.text.toByteArray(Charsets.UTF_8).size.toLong()}}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES){"Highlight captions exceed project limits."}
            require(proposed.mapNotNull{it.captionSourceUri}.distinct().size<=32){"Highlight subtitle documents exceed project limits."}
            VideoDraftCodec.requireRecoverableCaptions(proposed,music?.loop==false,music?.hasExcerpt==true)
            VideoPlaybackTime.durationUs(proposed);music?.let{VideoTimeline.soundtrackRepeatCount(proposed,it)}
            requireCurrent();return VideoCaptionHighlights(clips,proposed,excerpts,selected.size)
        }
    }
}
