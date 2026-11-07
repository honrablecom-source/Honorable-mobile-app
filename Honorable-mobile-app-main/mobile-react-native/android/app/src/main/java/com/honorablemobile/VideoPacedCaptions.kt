package com.honorablemobile

import java.util.Collections

/** Owner-supplied words at an even source-time pace; never transcription or speech alignment. */
internal class VideoPacedCaptions private constructor(clips:List<VideoSegment>,val index:Int,captions:List<VideoCaption>,val music:VideoSoundtrack?){
    val base:List<VideoSegment> = Collections.unmodifiableList(clips.map{it.copy(captions=Collections.unmodifiableList(it.captions.toList()))})
    val cues:List<VideoCaption> = Collections.unmodifiableList(captions.toList())
    val changed:Boolean get()=base[index].captions!=cues
    fun apply(project:VideoEditProject,requireCurrent:()->Unit){
        requireCurrent();require(project.segments==base&&project.soundtrack==music){"Project changed. Prepare captions again."}
        require(changed){"These captions already match the clip."}
        project.setCaptions(index,cues)
    }
    companion object {
        const val MAX_CHARS=8000
        const val MAX_BYTES=16000
        const val MAX_WORDS=1000
        fun prepare(clips:List<VideoSegment>,index:Int,text:String,wordsPerCue:Int,music:VideoSoundtrack?=null,requireCurrent:()->Unit={}):VideoPacedCaptions {
            requireCurrent();require(clips.size in 1..1000)
            val frozen=clips.map{it.copy(captions=Collections.unmodifiableList(it.captions.toList()))};VideoPlaybackTime.durationUs(frozen)
            val clip=requireNotNull(frozen.getOrNull(index)){"Select a clip first."}
            require(text.length in 1..MAX_CHARS&&text.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"Paste up to 8,000 characters and 16 KB of text."}
            require(text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==text){"Use valid Unicode text."}
            require(text.none{it.isISOControl()&&it!='\n'&&it!='\r'&&it!='\t'}){"Use plain text without control characters."}
            require(wordsPerCue in 1..12){"Choose 1–12 words per caption."}
            val words=text.splitToSequence(Regex("[\\s\\p{Z}]+" )).filter{it.isNotBlank()}.toList()
            require(words.size in 1..MAX_WORDS){"Paste 1–1,000 whitespace-separated words."}
            val groups=words.chunked(wordsPerCue)
            require(groups.size<=VideoCaptions.MAX_PER_CLIP){"Use larger word groups or less text: up to 200 captions per clip."}
            val span=clip.endMs-clip.startMs
            var consumed=0
            val cues=groups.map{group->
                requireCurrent();val from=clip.startMs+span*consumed/words.size;consumed+=group.size;val to=clip.startMs+span*consumed/words.size
                require((to-from)*1000.0/clip.speed>=250_000.0){"Each caption needs at least 0.25 seconds of playback. Use larger word groups, less text or a longer/slower clip."}
                val line=group.joinToString(" ");require(line.length<=240){"Use shorter words or fewer words per caption: up to 240 characters each."}
                VideoCaption(from,to,line)
            }
            VideoCaptions.validate(cues)
            val proposed=frozen.mapIndexed{i,value->if(i==index)value.copy(captions=cues)else value}
            require(proposed.sumOf{it.captions.size}<=VideoCaptions.MAX_PROJECT_CUES){"Project caption count exceeds its limit."}
            require(proposed.sumOf{it.captions.sumOf{cue->cue.text.toByteArray(Charsets.UTF_8).size.toLong()}}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES){"Project caption text exceeds its limit."}
            VideoDraftCodec.requireRecoverableCaptions(proposed,music?.loop==false,music?.hasExcerpt==true)
            requireCurrent();return VideoPacedCaptions(frozen,index,cues,music)
        }
    }
}
