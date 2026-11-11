package com.honorablemobile

import java.util.Locale

/** Positional edit metadata comparison; no media I/O, semantic alignment or content-quality judgment. */
internal object VideoVersionDiff {
    const val LIMIT=100
    enum class Field(val label:String){SOURCE("source binding"),WINDOW("source window / speed"),TITLE("title / appearance"),COLOR("color"),FRAMING("framing"),MOTION("motion"),EDGES("edge fades"),AUDIO("source audio"),CAPTIONS("captions / subtitle binding")}
    data class Change(val index:Int,val fields:List<Field>,val before:VideoSegment?,val saved:VideoSegment?)
    data class Report(val currentCount:Int,val savedCount:Int,val currentUs:Long,val savedUs:Long,val changedPositions:Int,val changes:List<Change>,val catalogChanged:Boolean,val primaryDurationChanged:Boolean,val soundtrackChanged:Boolean,val selectionChanged:Boolean){
        val omittedPositions:Int get()=changedPositions-changes.size
        val identical:Boolean get()=changedPositions==0&&!catalogChanged&&!primaryDurationChanged&&!soundtrackChanged&&!selectionChanged
        fun text():String=buildString{
            append(if(identical)"No metadata differences found.\n\n" else "Current → saved metadata\n\n")
            append(String.format(Locale.ROOT,"%d → %d clips · %.6f → %.6f playback seconds\n%d changed clip positions; %d shown, %d omitted\n",currentCount,savedCount,currentUs/1_000_000.0,savedUs/1_000_000.0,changedPositions,changes.size,omittedPositions))
            append("Imported source catalog: ${if(catalogChanged)"different"else"same"}\nPrimary duration: ${if(primaryDurationChanged)"different"else"same"}\nSoundtrack settings: ${if(soundtrackChanged)"different"else"same"}\nWorking selection/range/playhead: ${if(selectionChanged)"different"else"same"}\n\n")
            append("Compares clips by timeline position, not identity: inserting or reordering clips can mark later positions changed. Only the first100changed positions are expanded. Source IDs and settings are compared; source URI metadata is not displayed. This does not open media, verify original availability, render pictures/audio or prove export equivalence. Caption text/full appearance parameters are not expanded. Nothing is applied.\n")
            fun clip(label:String,value:VideoSegment?){
                if(value==null){append("$label: no clip\n");return}
                append(String.format(Locale.ROOT,"%s: source %s · %.3f–%.3fs · %.1fx · %d captions\n",label,value.sourceId,value.startMs/1000.0,value.endMs/1000.0,value.speed,value.captions.size))
                append("$label title: ${value.title.replace('\n',' ').ifEmpty{"(none)"}}\n")
            }
            for(change in changes){append("\nPosition ${change.index+1}: ${if(change.before==null)"saved has an added position"else if(change.saved==null)"saved has no position"else change.fields.joinToString{it.label}}\n");clip("Current",change.before);clip("Saved",change.saved)}
        }
    }
    fun compare(current:VideoDraft,saved:VideoDraft):Report {
        val beforeSources=current.sources.associateBy{it.id};val afterSources=saved.sources.associateBy{it.id}
        val details=mutableListOf<Change>();var count=0
        for(i in 0 until maxOf(current.segments.size,saved.segments.size)){
            val a=current.segments.getOrNull(i);val b=saved.segments.getOrNull(i)
            val fields=if(a==null||b==null)emptyList()else buildList{
                if(a.sourceId!=b.sourceId||beforeSources[a.sourceId]!=afterSources[b.sourceId]||(a.sourceId=="primary"&&current.sourceDurationMs!=saved.sourceDurationMs))add(Field.SOURCE)
                if(a.startMs!=b.startMs||a.endMs!=b.endMs||a.speed!=b.speed)add(Field.WINDOW)
                if(a.title!=b.title||a.titleStyle!=b.titleStyle)add(Field.TITLE)
                if(a.grade!=b.grade)add(Field.COLOR)
                if(a.layout!=b.layout)add(Field.FRAMING)
                if(a.motion!=b.motion)add(Field.MOTION)
                if(a.fadeEdges!=b.fadeEdges)add(Field.EDGES)
                if(a.volume!=b.volume||a.audioFade!=b.audioFade)add(Field.AUDIO)
                if(a.captions!=b.captions||a.captionSourceUri!=b.captionSourceUri)add(Field.CAPTIONS)
            }
            if(a==null||b==null||fields.isNotEmpty()){count++;if(details.size<LIMIT)details+=Change(i,fields,a,b)}
        }
        return Report(current.segments.size,saved.segments.size,VideoPlaybackTime.durationUs(current.segments),VideoPlaybackTime.durationUs(saved.segments),count,details.toList(),beforeSources!=afterSources,current.sourceDurationMs!=saved.sourceDurationMs,current.soundtrack!=saved.soundtrack,current.selectedClip!=saved.selectedClip||current.previewPositionMs!=saved.previewPositionMs||current.trimStartMs!=saved.trimStartMs||current.trimEndMs!=saved.trimEndMs)
    }
}
