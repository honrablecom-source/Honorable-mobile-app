package com.honorablemobile

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

internal enum class VideoMediaImportKind(val requestCode:Int,val mime:String,val action:String) {
    VIDEO(23,"video/*","Add video"), SOUNDTRACK(24,"audio/*","Add music")
}

/** Metadata-only, single-use ownership of an external picker and its validation worker. */
internal class VideoMediaImportTarget(
    val kind:VideoMediaImportKind,val project:VideoEditProject,
    private val source:String,private val duration:Long,private val mode:String,
    private val selected:Int,sources:Map<String,VideoSource>
) {
    private val clips=Collections.unmodifiableList(project.segments.map { it.copy(captions=Collections.unmodifiableList(it.captions.toList())) })
    private val music=project.soundtrack
    private val catalog=Collections.unmodifiableMap(LinkedHashMap(sources))
    private val phase=AtomicInteger(0) // 0 picker;1 validating;2 cancelled/finished
    val isChecking:Boolean get()=phase.get()==1
    val isPending:Boolean get()=phase.get()==0
    fun beginValidation():Boolean=phase.compareAndSet(0,1)
    fun cancel(){phase.set(2)}
    fun requireChecking(){check(isChecking){"Media import cancelled"}}
    fun matches(current:VideoEditProject?,source:String,duration:Long,mode:String,selected:Int,sources:Map<String,VideoSource>):Boolean =
        phase.get()!=2 && current!=null && current===project && current.segments==clips && current.soundtrack==music &&
            source==this.source && duration==this.duration && mode==this.mode && selected==this.selected && sources==catalog
}
