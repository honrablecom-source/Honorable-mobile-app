package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoCloseStateTest {
    private val edit=PhotoEdit(exposure=.25f)
    private val pixels=Any()
    private fun state(edit:PhotoEdit?=this.edit,undo:List<PhotoEdit> = emptyList(),redo:List<PhotoEdit> = emptyList(),operations:Int=0,
        recovery:PhotoSessionSnapshot?=null,source:String?="content://synthetic/photo",scope:String?="scope",mode:EditorAccessMode?=EditorAccessMode.GUEST_BASIC,
        canvasId:String?=null,unsavedCreatedSource:Boolean=false,originals:List<String> = emptyList(),generation:Long=3,revision:Int=4,pixels:Any?=this.pixels,pendingExport:PhotoEdit?=null,destination:String?=null)=
        PhotoCloseState.capture(edit,undo,redo,operations,recovery,source,scope,mode,canvasId,unsavedCreatedSource,originals,generation,revision,pixels,pendingExport,destination)
    @Test fun untouchedOriginalAndSavedIdentityCanvasCanLeaveDirectly(){
        assertFalse(state(edit=PhotoEdit()).needsConfirmation())
        assertFalse(state(edit=null,pixels=null).needsConfirmation())
        assertFalse(state(edit=PhotoEdit(),canvasId="saved").needsConfirmation())
    }
    @Test fun adjustmentsObjectsCaptionsAndTransparencyNeedReview(){
        for(value in listOf(PhotoEdit(rotation=90),PhotoEdit(crop=.8f),PhotoEdit(brightness=10f),edit,PhotoEdit(personCutout=true),
            PhotoEdit(finish=PhotoFinish(caption=PhotoCaption("Keep text"))),PhotoEdit(alphaGradient=PhotoAlphaGradient(enabled=true)),
            PhotoEdit(layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Keep object"))))))assertTrue(state(edit=value).needsConfirmation())
    }
    @Test fun identityWithHistoryAndUnsavedCreatedImageStillNeedsReview(){
        assertTrue(state(edit=PhotoEdit(),undo=listOf(edit)).needsConfirmation())
        assertTrue(state(edit=PhotoEdit(),redo=listOf(edit)).needsConfirmation())
        assertTrue(state(edit=PhotoEdit(),unsavedCreatedSource=true).needsConfirmation())
    }
    @Test fun eachPendingOperationRecoveryAndExportRequiresReview(){
        for(bit in 0..7)assertTrue(state(edit=PhotoEdit(),operations=1 shl bit).needsConfirmation())
        assertTrue(state(edit=PhotoEdit(),recovery=PhotoSessionSnapshot("scope",edit)).needsConfirmation())
        assertTrue(state(edit=PhotoEdit(),pendingExport=edit).needsConfirmation())
        assertTrue(state(edit=PhotoEdit(),destination="content://synthetic/copy").needsConfirmation())
    }
    @Test fun approvalBindsEveryCapturedIdentityAndOperationField(){
        val captured=state();assertTrue(captured.stillMatches(state()))
        for(changed in listOf(state(edit=edit.copy()),state(undo=listOf(edit)),state(redo=listOf(edit)),state(operations=1),state(source="content://synthetic/other"),
            state(scope="changed"),state(mode=EditorAccessMode.ACCOUNT),state(canvasId="saved"),state(unsavedCreatedSource=true),state(originals=listOf("content://synthetic/original")),
            state(generation=4),state(revision=5),state(pixels=Any()),state(pendingExport=edit),state(destination="content://synthetic/copy"),state(recovery=PhotoSessionSnapshot("scope",edit))))assertFalse(captured.stillMatches(changed))
        assertFalse(state(operations=1).stillMatches(state(operations=2)))
    }
    @Test fun historyAndProtectedOriginalSnapshotsDetachMutableMembership(){
        val history=mutableListOf(edit);val origins=mutableListOf("content://synthetic/original")
        val captured=state(undo=history,originals=origins);val unchanged=state(undo=listOf(edit),originals=origins.toList())
        history.clear();origins.clear()
        assertTrue(captured.stillMatches(unchanged));assertFalse(captured.stillMatches(state(undo=history,originals=origins)))
        assertFalse(captured.stillMatches(state(undo=listOf(edit.copy()),originals=listOf("content://synthetic/original"))))
    }
    @Test fun separateSaveDoesNotSuppressEditedWorkOrRecoveryIdentityReview(){
        assertTrue(state(canvasId="saved").needsConfirmation())
        val recovery=PhotoSessionSnapshot("scope",edit)
        assertTrue(state(recovery=recovery).stillMatches(state(recovery=recovery)))
        assertFalse(state(recovery=recovery).stillMatches(state(recovery=recovery.copy())))
    }
}
