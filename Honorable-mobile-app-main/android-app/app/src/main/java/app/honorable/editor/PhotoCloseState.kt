package app.honorable.editor

import java.util.Collections

/** Conservative session review; a separate project save or export does not remove this guard. */
internal class PhotoCloseState private constructor(
    private val edit:PhotoEdit?,private val undo:List<PhotoEdit>,private val redo:List<PhotoEdit>,
    private val operations:Int,private val recovery:Any?,private val source:String?,
    private val scope:String?,private val mode:EditorAccessMode?,private val canvasId:String?,
    val unsavedCreatedSource:Boolean,private val originals:List<String>,private val generation:Long,
    private val revision:Int,private val pixels:Any?,private val pendingExport:PhotoEdit?,private val destination:String?
){
    val working:Boolean get()=operations!=0||pendingExport!=null||destination!=null
    val recoveryPending:Boolean get()=recovery!=null
    fun needsConfirmation()=working||recoveryPending||unsavedCreatedSource||edit?.let{it!=PhotoEdit()}==true||undo.isNotEmpty()||redo.isNotEmpty()
    private fun sameHistory(first:List<PhotoEdit>,second:List<PhotoEdit>)=first.size==second.size&&first.indices.all{first[it]===second[it]}
    fun stillMatches(other:PhotoCloseState)=edit===other.edit&&sameHistory(undo,other.undo)&&sameHistory(redo,other.redo)&&operations==other.operations&&
        recovery===other.recovery&&source==other.source&&scope==other.scope&&mode==other.mode&&canvasId==other.canvasId&&unsavedCreatedSource==other.unsavedCreatedSource&&
        originals==other.originals&&generation==other.generation&&revision==other.revision&&pixels===other.pixels&&pendingExport===other.pendingExport&&destination==other.destination
    companion object {
        fun capture(edit:PhotoEdit?,undo:List<PhotoEdit> = emptyList(),redo:List<PhotoEdit> = emptyList(),operations:Int=0,recovery:Any?=null,
            source:String?=null,scope:String?=null,mode:EditorAccessMode?=null,canvasId:String?=null,unsavedCreatedSource:Boolean=false,
            originals:List<String> = emptyList(),generation:Long=0,revision:Int=0,pixels:Any?=null,pendingExport:PhotoEdit?=null,destination:String?=null)=
            PhotoCloseState(edit,Collections.unmodifiableList(undo.toList()),Collections.unmodifiableList(redo.toList()),operations,recovery,source,scope,mode,canvasId,
                unsavedCreatedSource,Collections.unmodifiableList(originals.toList()),generation,revision,pixels,pendingExport,destination)
    }
}
