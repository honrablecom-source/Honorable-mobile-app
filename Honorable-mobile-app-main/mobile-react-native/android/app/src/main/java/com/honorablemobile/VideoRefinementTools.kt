package com.honorablemobile

import java.util.Locale

internal enum class VideoSortOrder { SOURCE_TIME, SHORTEST_FIRST, LONGEST_FIRST }
internal enum class CaptionLetterCase { UPPER, LOWER }

/** Immutable proposals. The existing project validator/review/undo/render paths remain authoritative. */
internal object VideoRefinementTools {
    private fun checked(clips:List<VideoSegment>) {
        require(clips.size in 1..1000) { "Use a project with 1–1000 clips" }
        require(runCatching{clips.fold(0L){total,clip->Math.addExact(total,clip.durationUs)}}.isSuccess) { "Project duration is too large" }
    }
    fun sort(clips:List<VideoSegment>,order:VideoSortOrder):List<VideoSegment> {
        checked(clips)
        return when(order) {
            VideoSortOrder.SOURCE_TIME -> {
                val sources=clips.map{it.sourceId}.distinct().withIndex().associate{it.value to it.index}
                clips.sortedWith(compareBy<VideoSegment>{sources.getValue(it.sourceId)}.thenBy{it.startMs}.thenBy{it.endMs})
            }
            VideoSortOrder.SHORTEST_FIRST -> clips.sortedBy{it.durationUs}
            VideoSortOrder.LONGEST_FIRST -> clips.sortedByDescending{it.durationUs}
        }
    }
    fun removeExactDuplicates(clips:List<VideoSegment>):List<VideoSegment> {
        checked(clips)
        // Grade, source, captions, audio, layout and every other owned edit participate in equality.
        return clips.distinct()
    }
    fun joinMatchingCuts(clips:List<VideoSegment>):List<VideoSegment> {
        checked(clips)
        val result=mutableListOf<VideoSegment>()
        for(clip in clips) {
            val previous=result.lastOrNull()
            val matches=previous!=null && previous.endMs==clip.startMs && !previous.fadeEdges && !previous.audioFade &&
                previous.copy(startMs=clip.startMs,endMs=clip.endMs,captions=clip.captions)==clip
            if(matches) {
                val cues=(previous!!.captions+clip.captions).distinct().sortedBy{it.startMs}
                require(cues.size<=VideoCaptions.MAX_PER_CLIP) { "Joining would exceed the caption limit. Remove some captions first." }
                result[result.lastIndex]=previous.copy(endMs=clip.endMs,captions=cues)
            } else result.add(clip)
        }
        return result
    }
    fun extendCaptionHolds(clips:List<VideoSegment>,index:Int,leadingMs:Long,trailingMs:Long):List<VideoSegment> {
        checked(clips)
        require(index in clips.indices && leadingMs in 0..2000 && trailingMs in 0..2000 && leadingMs+trailingMs>0) { "Use caption holds of 0–2000 source milliseconds, with at least one nonzero value" }
        val clip=clips[index]
        val visible=clip.captions.mapNotNull{cue->
            val start=maxOf(cue.startMs,clip.startMs);val end=minOf(cue.endMs,clip.endMs)
            if(end>start)cue.copy(startMs=start,endMs=end)else null
        }
        require(visible.isNotEmpty()) { "Add visible captions to the selected clip first" }
        val cues=visible.mapIndexed{i,cue->
            val lower=if(i==0)clip.startMs else visible[i-1].endMs+(cue.startMs-visible[i-1].endMs)/2
            val upper=if(i==visible.lastIndex)clip.endMs else cue.endMs+(visible[i+1].startMs-cue.endMs)/2
            cue.copy(startMs=maxOf(lower,cue.startMs-leadingMs),endMs=minOf(upper,cue.endMs+trailingMs))
        }
        return clips.mapIndexed{i,current->if(i==index)current.copy(captions=cues)else current}
    }
    fun captionCase(clips:List<VideoSegment>,index:Int,letterCase:CaptionLetterCase):List<VideoSegment> {
        checked(clips);require(index in clips.indices)
        val clip=clips[index];require(clip.captions.isNotEmpty()) { "Add captions to the selected clip first" }
        val cues=clip.captions.map{cue->cue.copy(text=when(letterCase){CaptionLetterCase.UPPER->cue.text.uppercase(Locale.ROOT);CaptionLetterCase.LOWER->cue.text.lowercase(Locale.ROOT)})}
        return clips.mapIndexed{i,current->if(i==index)current.copy(captions=cues)else current}
    }
    data class Report(val clips:Int,val sources:Int,val durationUs:Long,val visibleCaptions:Int,val captionCoverageUs:Long,val duplicateClips:Int,val mutedClips:Int,val shortClips:Int) {
        fun text():String = String.format(Locale.ROOT,
            "%d clips · %d source videos\n%.2f seconds of playback\n%d visible captions · %.1f%% timeline coverage\n%d exact duplicate clips\n%d clips set to zero volume\n%d clips shorter than 0.25 playback seconds\n\nThis report reads project settings, not image quality or audio loudness. Original media and edits are unchanged.",
            clips,sources,durationUs/1_000_000.0,visibleCaptions,captionCoverageUs*100.0/durationUs,duplicateClips,mutedClips,shortClips)
    }
    fun report(clips:List<VideoSegment>):Report {
        checked(clips)
        var count=0;var coverage=0L
        for(clip in clips)for(cue in clip.captions) {
            val span=minOf(clip.endMs,cue.endMs)-maxOf(clip.startMs,cue.startMs)
            if(span>0){count++;coverage+=(span*1000.0/clip.speed).toLong()}
        }
        val duration=clips.sumOf{it.durationUs}
        return Report(clips.size,clips.map{it.sourceId}.distinct().size,duration,count,minOf(coverage,duration),clips.size-clips.distinct().size,clips.count{it.volume==0f},clips.count{it.durationUs<250_000})
    }
}
