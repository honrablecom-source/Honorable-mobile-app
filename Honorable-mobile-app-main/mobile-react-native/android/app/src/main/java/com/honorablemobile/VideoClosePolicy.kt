package com.honorablemobile

import java.util.Collections

/** Conservative session confirmation, independent of whether a separate saved draft already exists. */
internal class VideoCloseState private constructor(
    private val project:VideoEditProject?, private val clips:List<VideoSegment>?,
    private val music:VideoSoundtrack?, private val duration:Long, private val selected:Int,
    private val from:Long?, private val to:Long?, private val busy:Boolean,
    private val recoveryPending:Boolean, private val source:String?
) {
    fun needsConfirmation():Boolean {
        if(busy || recoveryPending)return true
        val values=clips ?: return false
        val clip=values.getOrNull(selected) ?: return true
        if(duration<=0 || values!=listOf(VideoSegment(0,duration)) || music!=null)return true
        return from==null || to==null || from!=clip.startMs || to!=clip.endMs
    }
    fun stillMatches(other:VideoCloseState):Boolean =
        project===other.project && clips==other.clips && music==other.music && duration==other.duration &&
            selected==other.selected && from==other.from && to==other.to && busy==other.busy &&
            recoveryPending==other.recoveryPending && source==other.source

    companion object {
        fun capture(project:VideoEditProject?,duration:Long,selected:Int,from:Long?,to:Long?,busy:Boolean,recoveryPending:Boolean,source:String?):VideoCloseState =
            VideoCloseState(project,project?.segments?.map { it.copy(captions=Collections.unmodifiableList(it.captions.toList())) }
                ?.let { Collections.unmodifiableList(it) },project?.soundtrack,duration,selected,from,to,busy,recoveryPending,source)
    }
}
