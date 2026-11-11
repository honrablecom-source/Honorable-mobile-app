package com.honorablemobile

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Local keyed text exchange. Never accepts timing, source, style or soundtrack replacements. */
internal object VideoCaptionWorksheet {
    const val LIMIT=196000
    data class Result(val clips:List<VideoSegment>,val changedCues:Int)
    private fun checked(clips:List<VideoSegment>){
        VideoPlaybackTime.durationUs(clips)
        require(clips.sumOf{it.captions.size} in 1..VideoCaptions.MAX_PROJECT_CUES){"Use a project with 1–1000 captions"}
        require(clips.sumOf{c->c.captions.sumOf{it.text.toByteArray(Charsets.UTF_8).size}}<=VideoCaptions.MAX_PROJECT_TEXT_BYTES){"Project caption text limit reached"}
    }
    private fun header(clips:List<VideoSegment>):String {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use{out->
            fun text(value:String){val data=value.toByteArray(Charsets.UTF_8);out.writeInt(data.size);out.write(data)}
            out.writeInt(clips.size)
            for(clip in clips){text(clip.sourceId);out.writeLong(clip.startMs);out.writeLong(clip.endMs);out.writeFloat(clip.speed);text(clip.captionSourceUri?:"");out.writeInt(clip.captions.size)
                for(cue in clip.captions){out.writeLong(cue.startMs);out.writeLong(cue.endMs);text(cue.text)}
            }
        }
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString(""){"%02x".format(it)}
        return "NOBLE-CAPTION-TEXT/1 $digest"
    }
    private fun escape(text:String)=buildString{for(c in text)when(c){'\\'->append("\\\\");'\n'->append("\\n");else->append(c)}}
    private fun unescape(text:String):String=buildString{
        var i=0
        while(i<text.length){val c=text[i++];if(c!='\\')append(c)else{
            require(i<text.length){"A text escape is incomplete"}
            when(text[i++]){'\\'->append('\\');'n'->append('\n');else->throw IllegalArgumentException("Only \\\\ and \\n escapes are supported")}
        }}
    }
    fun export(clips:List<VideoSegment>):String {
        checked(clips)
        return buildString{append(header(clips));for((i,clip)in clips.withIndex())for((j,cue)in clip.captions.withIndex()){append('\n');append("${i+1}.${j+1}\t");append(escape(cue.text))}}.also{require(it.toByteArray(Charsets.UTF_8).size<=LIMIT){"Worksheet exceeds transfer limit"}}
    }
    fun prepare(clips:List<VideoSegment>,worksheet:String,music:VideoSoundtrack?=null):Result {
        checked(clips)
        require(worksheet.length<=LIMIT&&worksheet.toByteArray(Charsets.UTF_8).size<=LIMIT){"Use a worksheet up to196000 UTF-8 bytes"}
        require(worksheet.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==worksheet){"Use valid Unicode text"}
        val normalized=worksheet.replace("\r\n","\n");require(!normalized.contains('\r')){"Use LF or CRLF line endings"}
        val rows=normalized.split('\n');require(rows.first()==header(clips)){"Worksheet does not match these original captions and timings. Copy a fresh worksheet"}
        val expected=clips.flatMapIndexed{i,clip->clip.captions.indices.map{j->"${i+1}.${j+1}"}}.toSet()
        val values=linkedMapOf<String,String>()
        for(row in rows.drop(1).filter{it.isNotBlank()}){
            val columns=row.split('\t');require(columns.size==2){"Use one key, a tab and escaped text per row"}
            val key=columns[0];require(key in expected&&!values.containsKey(key)){"Worksheet has an unknown or repeated caption key"}
            values[key]=unescape(columns[1])
        }
        require(values.keys==expected){"Keep every original caption key exactly once"}
        var changed=0
        val result=clips.mapIndexed{i,clip->
            val cues=clip.captions.mapIndexed{j,cue->val text=values.getValue("${i+1}.${j+1}");if(text==cue.text)cue else{changed++;cue.copy(text=text)}}
            if(cues==clip.captions)clip else clip.copy(captions=cues)
        }
        require(changed>0){"No caption text changed"}
        checked(result);VideoDraftCodec.requireRecoverableCaptions(result,music?.loop==false,music?.hasExcerpt==true)
        return Result(result,changed)
    }
}
