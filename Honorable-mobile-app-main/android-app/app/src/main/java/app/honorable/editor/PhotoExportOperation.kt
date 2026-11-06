package app.honorable.editor

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/** A chosen export is immutable; later editor Undo or format settings cannot change its pixels/format. */
internal class PhotoExportOperation(val edit:PhotoEdit,val source:String,val destination:String,val scope:String,val mode:EditorAccessMode,
    val generation:Long,val encoding:SafePhotoCopy.Encoding,val quality:Int,originals:List<String>){
    val originals:List<String> = Collections.unmodifiableList(originals.toList())
    private val cancelled=AtomicBoolean(false)
    val isCancelled:Boolean get()=cancelled.get()
    init{require(source.isNotBlank()&&destination.isNotBlank()&&scope.isNotBlank());require(quality in 1..100)}
    fun cancel(){cancelled.set(true)}
    fun matches(edit:PhotoEdit?,source:String?,loadedScope:String?,currentScope:String?,mode:EditorAccessMode?,generation:Long,originals:List<String>)=
        !isCancelled&&this.edit===edit&&this.source==source&&scope==loadedScope&&scope==currentScope&&this.mode==mode&&this.generation==generation&&this.originals==originals
    fun requireCurrent(current:()->Boolean){if(isCancelled||!current())throw Cancelled()}
    class Cancelled:IllegalStateException("Photo export stopped because its editor session changed")
}
