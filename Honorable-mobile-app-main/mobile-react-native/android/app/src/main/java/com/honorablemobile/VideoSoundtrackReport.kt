package com.honorablemobile

import java.util.Locale

/** Scheduled soundtrack timing only; no media inspection or inference about original clip audio. */
internal object VideoSoundtrackReport {
    data class Report(val projectUs:Long,val excerptUs:Long,val passes:Long,val lastPassUs:Long,val uncoveredUs:Long,val loop:Boolean,val volume:Float) {
        fun text():String {
            fun seconds(us:Long):String {
                val ms=us/1000+if(us%1000>=500)1 else 0
                return String.format(Locale.ROOT,"%d.%03d",ms/1000,ms%1000)
            }
            val timing=if(loop)"$passes playback pass${if(passes==1L)""else"es"}; final pass ${seconds(lastPassUs)} s of ${seconds(excerptUs)} s."
                else if(lastPassUs<excerptUs)"Plays once; project end cuts it to ${seconds(lastPassUs)} s."
                else "Plays once for ${seconds(lastPassUs)} s."
            val tail=if(uncoveredUs>0)"No soundtrack for the final ${seconds(uncoveredUs)} s; original clip audio may still play."else"Soundtrack is scheduled through project end."
            val level=if(volume==0f)"Muted (0%). Timing below describes the scheduled range."else"Volume ${String.format(Locale.ROOT,"%.1f",volume*100)}%."
            return "Project ${seconds(projectUs)} s · excerpt ${seconds(excerptUs)} s\n$level\n$timing\n$tail\nTimes are rounded to milliseconds. Original clip audio is separate."
        }
    }
    fun inspect(clips:List<VideoSegment>,track:VideoSoundtrack):Report {
        val passes=VideoTimeline.soundtrackRepeatCount(clips,track)
        val total=VideoPlaybackTime.durationUs(clips);val excerpt=track.playbackDurationMs*1000
        val covered=if(track.loop)total else minOf(total,excerpt)
        return Report(total,excerpt,passes,covered-(passes-1)*excerpt,total-covered,track.loop,track.volume)
    }
}
