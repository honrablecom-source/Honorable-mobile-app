package com.honorablemobile

import java.util.Collections

/** Whole edited-playback SRT mapped back onto each source clip; no original media I/O. */
internal class VideoTimelineCaptionImport private constructor(base:List<VideoSegment>,segments:List<VideoSegment>,val document:String,val inputCues:Int,val mappedCues:Int,val adjustedFragments:Int){
    val base:List<VideoSegment> = immutable(base)
    val segments:List<VideoSegment> = immutable(segments)
    val changedClips:Int get()=base.indices.count{base[it]!=segments[it]}
    val changed:Boolean get()=base!=segments
    fun apply(project:VideoEditProject,requireCurrent:()->Unit){requireCurrent();require(project.segments==base){"Timeline changed. Import playback subtitles again."};project.applyDraft(segments,soundtrack=project.soundtrack)}
    companion object {
        private fun immutable(values:List<VideoSegment>)=Collections.unmodifiableList(values.map{it.copy(captions=Collections.unmodifiableList(it.captions.toList()))})
        fun validateDocument(value:String){
            require(value.toByteArray(Charsets.UTF_8).size<=2048&&value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==value&&value.none{it.isISOControl()}){"Choose a readable subtitle document."}
            val uri=java.net.URI(value);require(uri.scheme=="content"&&!uri.rawAuthority.isNullOrEmpty()&&uri.rawFragment==null){"Choose a local subtitle document."}
        }
        fun prepare(clips:List<VideoSegment>,bytes:ByteArray,document:String,soundtrack:VideoSoundtrack?=null,requireCurrent:()->Unit={}):VideoTimelineCaptionImport {
            validateDocument(document);requireCurrent();val duration=VideoPlaybackTime.durationUs(clips)
            val durationMs=duration/1000+if(duration%1000==0L)0 else 1
            require(durationMs in 1..Int.MAX_VALUE.toLong()){"This timeline exceeds subtitle timing limits."}
            val cues=VideoCaptions.parsePlaybackSrt(bytes,durationMs)
            var offset=0L;var first=0;var mapped=0;var textBytes=0L;var adjusted=0
            val proposed=clips.map{clip->
                requireCurrent();val end=Math.addExact(offset,clip.durationUs)
                while(first<cues.size&&cues[first].endMs*1000<=offset)first++
                val result=mutableListOf<VideoCaption>();var index=first;var previous=clip.startMs
                fun bounds(at:Long):Pair<Long,Long> {
                    if(at==offset)return clip.startMs to clip.startMs
                    if(at==end)return clip.endMs to clip.endMs
                    // Allowed speeds are exact half-integers. Integer arithmetic avoids floating edge drift.
                    val numerator=Math.multiplyExact(at-offset,(clip.speed*2).toLong());val whole=numerator/2000;val extra=if(numerator%2000==0L)0 else 1
                    return (clip.startMs+whole).coerceIn(clip.startMs,clip.endMs) to (clip.startMs+whole+extra).coerceIn(clip.startMs,clip.endMs)
                }
                while(index<cues.size&&cues[index].startMs*1000<end){
                    requireCurrent();val cue=cues[index++];val from=maxOf(offset,cue.startMs*1000);val to=minOf(end,cue.endMs*1000)
                    if(to<=from)continue
                    val a=bounds(from);val b=bounds(to);val start=maxOf(a.first,previous);val finish=maxOf(b.first,start+1)
                    require(start<=a.second&&finish<=b.second){"A subtitle fragment is too short for source-millisecond timing. Adjust its timing or nearby cuts; nothing was imported."}
                    if(a.first!=a.second||b.first!=b.second)adjusted++
                    result.add(VideoCaption(start,finish,cue.text));previous=finish
                    mapped++;textBytes+=cue.text.toByteArray(Charsets.UTF_8).size
                    require(result.size<=VideoCaptions.MAX_PER_CLIP&&mapped<=VideoCaptions.MAX_PROJECT_CUES&&textBytes<=VideoCaptions.MAX_PROJECT_TEXT_BYTES){"Mapped subtitles exceed the existing clip/project caption limits. Nothing was imported."}
                }
                offset=end;clip.copy(captions=result.toList(),captionSourceUri=if(result.isEmpty())null else document)
            }
            require(mapped>0){"No subtitles fall inside this edited timeline."}
            VideoDraftCodec.requireRecoverableCaptions(proposed,soundtrack?.loop==false,soundtrack?.hasExcerpt==true)
            requireCurrent();return VideoTimelineCaptionImport(clips,proposed,document,cues.size,mapped,adjusted)
        }
    }
}
