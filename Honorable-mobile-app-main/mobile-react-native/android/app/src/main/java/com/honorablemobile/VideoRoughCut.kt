package com.honorablemobile

import kotlin.math.ceil

enum class RoughCutFocus { BALANCED, PEOPLE, SCENERY }

/** Bounded model-assisted assembly from real clips; no new images/audio/video are synthesized. */
internal object VideoRoughCut {
    const val MAX_CLIPS=8
    fun sampleTimes(clip:VideoSegment):List<Long> {
        require(clip.endMs<=Long.MAX_VALUE/1000)
        return List(4){clip.startMs+((it+.5)*(clip.endMs-clip.startMs)/4).toLong()}
    }
    fun score(labels:List<Pair<String,Float>>,focus:RoughCutFocus):Float {
        val useful=labels.filter{it.second.isFinite() && it.second in .5f..1f && it.first.lowercase() !in setOf("photography","video","image","snapshot")}
        val base=useful.map{it.second}.sortedDescending().take(5).sum()
        val terms=when(focus){RoughCutFocus.BALANCED->emptyList();RoughCutFocus.PEOPLE->listOf("person","face","portrait","crowd","baby");RoughCutFocus.SCENERY->listOf("nature","landscape","beach","mountain","sky","water")}
        return base+(useful.filter{(text,_)->terms.any{text.lowercase().contains(it)}}.maxOfOrNull{it.second}?:0f)*2
    }
    fun assemble(clips:List<VideoSegment>,centers:List<Long>,targetMs:Long):List<VideoSegment> {
        require(clips.size in 1..MAX_CLIPS && centers.size==clips.size && targetMs in 1_000L..60_000L)
        require(clips.all{it.durationMs>0})
        val budgets=LongArray(clips.size)
        var remaining=minOf(targetMs,clips.sumOf{it.durationMs})
        var active=clips.indices.toList()
        while(remaining>0 && active.isNotEmpty()) {
            val share=(remaining/active.size).coerceAtLeast(1)
            for(index in active){val amount=minOf(share,clips[index].durationMs-budgets[index],remaining);budgets[index]+=amount;remaining-=amount}
            active=active.filter{budgets[it]<clips[it].durationMs}
        }
        return clips.mapIndexed{index,clip->
            val span=ceil(budgets[index]*clip.speed.toDouble()).toLong().coerceIn(1,clip.endMs-clip.startMs)
            val center=centers[index].coerceIn(clip.startMs,clip.endMs-1)
            val start=(center-span/2).coerceIn(clip.startMs,clip.endMs-span)
            clip.copy(startMs=start,endMs=start+span)
        }
    }
}
