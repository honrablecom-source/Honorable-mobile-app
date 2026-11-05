package com.honorablemobile

import java.util.Collections
import java.util.Locale

/** Editorial hints only: inspect the same trimmed/speed-adjusted cues used by rendering and SRT. */
internal object VideoCaptionReadability {
    enum class Hint(val label:String) {
        FAST("Over 20 characters/second"), BRIEF("Under 1 second on screen"),
        LONG_LINE("Over 42 characters on a line"), MANY_LINES("More than two explicit lines")
    }
    data class Finding(val number:Int,val cue:TimelineCaption,val characters:Int,val charactersPerSecond:Double,val hints:List<Hint>)
    data class Report(val visibleCues:Int,val findings:List<Finding>) {
        fun text():String=buildString {
            append("Caption readability review\n")
            append("$visibleCues visible playback cues · ${findings.size} with review hints\n\n")
            append("Editorial hints, not a language or accessibility certification. Check the actual preview: font size, framing and language affect reading. Counts use Unicode code points including spaces, excluding line breaks; joined emoji may count more than once. Line checks use explicit line breaks, not measured screen wrapping.\n")
            append("Hints: over 20 characters/second, under 1 second, over 42 characters/line, or more than two explicit lines. Playback times include trims and speed. Adjacent identical cues may merge as in render/export. This report changes no captions.\n")
            if(visibleCues==0)append("\nNo captions are visible in the edited timeline. Add captions or check the trimmed ranges.\n")
            else if(findings.isEmpty())append("\nNo threshold hints found. Preview to check readability and speech timing.\n")
            for(finding in findings){
                val cue=finding.cue
                append("\nCue ${finding.number} · ${VideoCaptions.formatTime(cue.startUs/1000)} → ${VideoCaptions.formatTime(cue.endUs/1000)}\n")
                append(String.format(Locale.ROOT,"%.2f s · %.1f characters/s\n",(cue.endUs-cue.startUs)/1_000_000.0,finding.charactersPerSecond))
                append(finding.hints.joinToString("; "){it.label});append('\n');append(cue.text);append('\n')
            }
        }
    }
    fun inspect(clips:List<VideoSegment>):Report {
        VideoPlaybackTime.durationUs(clips) // Reject overflowing/unbounded timelines before resolving timestamps.
        require(clips.sumOf{it.captions.size}<=VideoCaptions.MAX_PROJECT_CUES)
        require(clips.sumOf{clip->clip.captions.sumOf{it.text.toByteArray(Charsets.UTF_8).size}}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES)
        val cues=VideoCaptions.resolve(clips)
        val findings=cues.mapIndexedNotNull{index,cue->
            val lines=cue.text.split('\n')
            val characters=lines.sumOf{it.codePointCount(0,it.length)}
            val duration=cue.endUs-cue.startUs
            val rate=if(duration>0)characters*1_000_000.0/duration else Double.POSITIVE_INFINITY
            val hints=mutableListOf<Hint>()
            if(rate>20.0)hints.add(Hint.FAST)
            if(duration<1_000_000)hints.add(Hint.BRIEF)
            if(lines.any{it.codePointCount(0,it.length)>42})hints.add(Hint.LONG_LINE)
            if(lines.size>2)hints.add(Hint.MANY_LINES)
            if(hints.isEmpty())null else Finding(index+1,cue,characters,rate,Collections.unmodifiableList(hints))
        }
        return Report(cues.size,Collections.unmodifiableList(findings))
    }
}
