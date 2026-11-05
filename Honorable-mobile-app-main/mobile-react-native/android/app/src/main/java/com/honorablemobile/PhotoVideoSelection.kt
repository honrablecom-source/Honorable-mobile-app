package com.honorablemobile

import java.util.Collections

/** Ordered, transient document references; removing a selection never deletes an original. */
internal class PhotoVideoSelection private constructor(val items:List<String>,val holds:Map<String,Long>) {
    fun append(incoming:List<String>):PhotoVideoSelection {
        require(incoming.size<=20){"Choose up to 20 photos at a time"}
        return of((items+incoming).distinct(),holds)
    }
    fun move(index:Int,delta:Int):PhotoVideoSelection {
        require(index in items.indices&&delta in listOf(-1,1)&&index+delta in items.indices)
        val next=items.toMutableList();val value=next.removeAt(index);next.add(index+delta,value)
        return of(next,holds)
    }
    fun remove(index:Int):PhotoVideoSelection {
        require(index in items.indices)
        val next=items.filterIndexed{i,_->i!=index}
        return of(next,holds.filterKeys{it in next})
    }
    fun withHold(index:Int,durationMs:Long?):PhotoVideoSelection {
        require(index in items.indices)
        val next=holds.toMutableMap()
        if(durationMs==null)next.remove(items[index])else next[items[index]]=durationMs
        return of(items,next)
    }
    fun durations(defaultMs:Long):List<Long> {
        require(defaultMs in 1000..10000)
        return Collections.unmodifiableList(items.map{holds[it]?:defaultMs})
    }
    fun fitTotalRange():LongRange {
        require(items.isNotEmpty()){ "Choose photos before fitting timing" }
        val flexible=items.count{it !in holds};val fixed=holds.values.sum()
        return (fixed+flexible*1000L)..(fixed+flexible*10000L)
    }
    fun fitTotal(totalMs:Long):PhotoVideoSelection {
        require(totalMs in fitTotalRange()){ "Requested total cannot fit these custom holds and 1–10 second photo limits" }
        val flexible=items.filter{it !in holds}
        if(flexible.isEmpty())return this
        val budget=totalMs-holds.values.sum();val each=budget/flexible.size;val extra=budget%flexible.size
        val next=holds.toMutableMap()
        flexible.forEachIndexed{index,uri->next[uri]=each+if(index<extra)1 else 0}
        return of(items,next)
    }
    fun savedHolds():LongArray=items.map{holds[it]?:0L}.toLongArray()
    companion object {
        fun requirePreparedDuration(requestedMs:Long,actualMs:Long) {
            require(requestedMs in 1000..10000&&actualMs>0&&kotlin.math.abs(actualMs-requestedMs)<=100){"Prepared clip duration differs from its requested photo hold"}
        }

        fun restore(items:List<String>,saved:LongArray?):PhotoVideoSelection {
            if(saved==null)return of(items)
            require(saved.size==items.size){"Saved photo timing is incomplete"}
            return of(items,items.zip(saved.toList()).filter{it.second!=0L}.toMap())
        }

        fun of(items:List<String>,holds:Map<String,Long> = emptyMap()):PhotoVideoSelection {
            require(items.size<=20&&items.distinct().size==items.size){"Choose up to 20 different photos"}
            require(items.all{it.startsWith("content://")&&it.removePrefix("content://").substringBefore('/').isNotBlank()&&it.none{c->c.isISOControl()}&&it.toByteArray(Charsets.UTF_8).size<=2048&&it.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==it}){"Choose local photo documents"}
            require(holds.keys.all{it in items}&&holds.values.all{it in 1000..10000}){"Choose photo holds from 1 to 10 seconds"}
            return PhotoVideoSelection(Collections.unmodifiableList(items.toList()),Collections.unmodifiableMap(holds.toMap()))
        }
        fun displayName(value:String?):String? {
            if(value==null)return null
            val label=StringBuilder(80)
            var index=0
            while(index<value.length&&label.length<80) {
                val raw=value[index++]
                val character=if(raw.isISOControl())' ' else raw
                if(label.isEmpty()&&character.isWhitespace())continue
                if(Character.isHighSurrogate(character)&&index<value.length&&Character.isLowSurrogate(value[index])) {
                    if(label.length>78)break
                    label.append(character).append(value[index++])
                } else label.append(if(Character.isSurrogate(character))'\uFFFD' else character)
            }
            return label.toString().trim().takeIf{it.isNotBlank()}
        }
    }
}
