package com.honorablemobile

import java.util.Collections

/** Export preparation is a proposal, never an edit or history operation. */
internal object VideoExportSnapshot {
    fun clips(clips: List<VideoSegment>, selected: Int, fromMs: Long, toMs: Long): List<VideoSegment> {
        val clip=clips.getOrNull(selected) ?: throw IllegalArgumentException("Select a clip before exporting")
        require(fromMs>=clip.startMs && toMs<=clip.endMs && toMs>fromMs) { "Choose a valid range inside the clip" }
        return Collections.unmodifiableList(clips.mapIndexed { index,value ->
            if(index!=selected) value else value.copy(startMs=fromMs,endMs=toMs,
                captions=Collections.unmodifiableList(value.captions.filter { it.endMs>fromMs && it.startMs<toMs }))
        })
    }
}
