package com.honorablemobile

import java.io.File

/** Source-clock request, independent of video compositing and any account project. */
internal data class VideoFramePhotoPlan(val clip:VideoSegment,val sourceMs:Long) {
    init { require(sourceMs>=clip.startMs&&sourceMs<clip.endMs){"Seek inside the selected clip first"};require(sourceMs<=Long.MAX_VALUE/1000){"Source timestamp is too large"} }
    val sourceUs:Long get()=sourceMs*1000
    companion object {
        const val MAX_EDGE=1920
        const val MAX_LEGACY_PIXELS=8_294_400L
        fun legacyDecodeAllowed(width:Int,height:Int)=width>0&&height>0&&width.toLong()*height<=MAX_LEGACY_PIXELS
        fun size(width:Int,height:Int):Pair<Int,Int> {
            require(width>0&&height>0)
            val scale=minOf(1.0,MAX_EDGE.toDouble()/maxOf(width,height))
            return (width*scale).toInt().coerceAtLeast(1) to (height*scale).toInt().coerceAtLeast(1)
        }
    }
}

/** Creates/deletes only this attempt's owned cache file; never traverses or cleans source media. */
internal object VideoFramePhotoFiles {
    const val MAX_BYTES=16L*1024*1024
    fun write(cache:File,requireCurrent:()->Unit,encode:(File)->Unit):File {
        requireCurrent()
        val directory=File(cache,"created-images")
        check(directory.canonicalFile==File(cache.canonicalFile,"created-images")){"Invalid image cache"}
        check(directory.isDirectory||directory.mkdirs()){ "Image cache is unavailable" }
        val target=File.createTempFile("noble-video-frame-",".png",directory)
        try {
            requireCurrent();encode(target);requireCurrent()
            require(target.isFile&&target.length() in 1..MAX_BYTES){"The frame image could not be prepared within limits"}
            return target
        }catch(failure:Throwable){target.delete();throw failure}
    }
}
