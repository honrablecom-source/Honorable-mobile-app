package app.honorable.editor

import java.util.Collections

/** Output-relative centers only. Does not measure rotated edges, text ink or inter-object gaps. */
internal class PhotoObjectArrangement private constructor(val base:PhotoEdit,val edit:PhotoEdit,val action:Action,selected:List<PhotoObject>,changes:List<Change>){
    enum class Action(val title:String,val horizontal:Boolean,val distribute:Boolean){
        COLUMN("Align centers in a vertical column",true,false),ROW("Align centers in a horizontal row",false,false),
        ACROSS("Space centers evenly left to right",true,true),DOWN("Space centers evenly top to bottom",false,true)
    }
    data class Change(val before:PhotoObject,val after:PhotoObject)
    val selected:List<PhotoObject> = Collections.unmodifiableList(selected.toList())
    val changes:List<Change> = Collections.unmodifiableList(changes.toList())
    val changed:Boolean get()=changes.isNotEmpty()
    companion object {
        fun eligible(value:PhotoObject)=value.visible&&!value.locked&&value.opacity>0f
        fun prepare(base:PhotoEdit,ids:List<String>,action:Action):PhotoObjectArrangement {
            require(ids.size in 2..32&&ids.distinct().size==ids.size){"Choose 2–32 different objects."}
            if(action.distribute)require(ids.size>=3){"Choose at least three objects to space their centers."}
            val selected=ids.map{id->requireNotNull(base.layers.objects.singleOrNull{it.id==id}){"An object is unavailable. Reopen Arrange."}}
            require(selected.all(::eligible)){"Keep locked, hidden and transparent objects unchanged. Choose visible unlocked objects."}
            fun coordinate(value:PhotoObject)=if(action.horizontal)value.x else value.y
            val low=selected.minOf(::coordinate);val high=selected.maxOf(::coordinate)
            // Stable sort makes coincident centers deterministic; ID selection order cannot change the result.
            val ordered=base.layers.objects.filter{it.id in ids}.sortedWith(compareBy<PhotoObject>{coordinate(it)}.thenBy{base.layers.objects.indexOf(it)})
            val positions=if(action.distribute)ordered.mapIndexed{index,value->value.id to when(index){0->low;ordered.lastIndex->high;else->(low.toDouble()+(high.toDouble()-low)*index/ordered.lastIndex).toFloat()}}.toMap()
                else selected.associate{it.id to ((low.toDouble()+high)/2).toFloat()}
            val changes=mutableListOf<Change>()
            val objects=base.layers.objects.map{old->
                val coordinate=positions[old.id]
                if(coordinate==null||(if(action.horizontal)old.x else old.y)==coordinate)old
                else(if(action.horizontal)old.copy(x=coordinate)else old.copy(y=coordinate)).also{changes.add(Change(old,it))}
            }
            return PhotoObjectArrangement(base,if(changes.isEmpty())base else base.copy(layers=PhotoLayers(objects)),action,base.layers.objects.filter{it.id in ids},changes)
        }
    }
}
