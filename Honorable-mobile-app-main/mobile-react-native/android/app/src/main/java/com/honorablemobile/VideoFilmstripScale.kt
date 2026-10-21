package com.honorablemobile

/** UI coordinates only; source/speed mapping stays in the existing VideoTimeline. */
internal class VideoFilmstripScale(val clips:List<VideoSegment>,val viewport:Float,val density:Float=1f) {
    private val starts=LongArray(clips.size+1).also { offsets -> clips.forEachIndexed { i,clip -> offsets[i+1]=offsets[i]+clip.durationUs } }
    val durationUs=starts.last()
    val width=maxOf(viewport,minOf(16000f*density,maxOf(viewport,durationUs/1000f*.025f*density)))
    init {require(clips.isNotEmpty());require(viewport.isFinite()&&viewport>0);require(density.isFinite()&&density>0);require(durationUs>0)}
    fun x(timeUs:Long)=((timeUs.coerceIn(0,durationUs).toDouble()/durationUs)*width).toFloat()
    fun time(x:Float):Long {require(x.isFinite());return (x.coerceIn(0f,width).toDouble()/width*durationUs).toLong().coerceIn(0,durationUs-1)}
    fun start(index:Int):Long {require(index in 0..clips.size);return starts[index]}
    fun clipAt(x:Float)=VideoTimeline.locate(clips,time(x)).clipIndex
    companion object {
        fun outputTime(clips:List<VideoSegment>,index:Int,sourceMs:Long):Long {
            val clip=clips[index]
            return clips.take(index).sumOf{it.durationUs} + ((sourceMs.coerceIn(clip.startMs,clip.endMs-1)-clip.startMs)*1000/clip.speed.toDouble()).toLong().coerceIn(0,clip.durationUs-1)
        }
    }
}
