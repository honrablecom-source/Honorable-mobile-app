package app.honorable.editor

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import org.junit.Assert.*
import org.junit.Test

/** Actual metadata/controller methods without native UI, source decoding, lifecycle or export writes. */
class PhotoRecoveryAdmissionTest {
    private val type=Class.forName("sun.misc.Unsafe")
    private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    private val source="content://synthetic/photo"
    private val uuid="11111111-1111-1111-1111-111111111111"
    private val scope=PhotoSessionCodec.scope("guest-activity:$uuid","local-only",source)
    private val saved=PhotoSessionSnapshot(scope,PhotoEdit(exposure=.5f),listOf(PhotoEdit(rotation=90)),listOf(PhotoEdit(crop=.8f)),true,1,PhotoEdit(exposure=.75f))
    private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
    private fun set(activity:PhotoEditorActivity,name:String,value:Any?)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)
    private fun get(activity:PhotoEditorActivity,name:String)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)
    private fun call(activity:PhotoEditorActivity,name:String,vararg args:Any?):Any?=PhotoEditorActivity::class.java.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(activity,*args)
    private fun fixture()=(allocate(PhotoEditorActivity::class.java)as PhotoEditorActivity).also{
        set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"guestInstance",uuid);set(it,"source",Uri.parse(source));set(it,"loadedScope",scope)
        set(it,"edit",PhotoEdit(exposure=.1f));set(it,"undo",mutableListOf(PhotoEdit()));set(it,"redo",mutableListOf<PhotoEdit>());set(it,"before",false);set(it,"activeTool",0)
        set(it,"protectedOriginals",listOf(Uri.parse("content://synthetic/protected")));set(it,"copyForeground",true);set(it,"copyGeneration",4L);set(it,"revision",3)
        set(it,"bitmap",allocate(Bitmap::class.java));set(it,"sliders",mutableListOf<SeekBar>())
    }
    private fun recovery(activity:PhotoEditorActivity)=get(activity,"photoRecovery")as PhotoSessionRecovery
    private fun restore(activity:PhotoEditorActivity,bytes:ByteArray=PhotoSessionCodec.encode(saved),invalidated:Boolean=false){call(activity,"restorePhotoRecovery",bytes,invalidated)}
    private fun closeState(activity:PhotoEditorActivity)=call(activity,"photoCloseState")as PhotoCloseState
    @Test fun rawSavedStateWinsOverUnavailableScopeAndNeverBecomesDefaultEdits(){
        val activity=fixture();val bytes=byteArrayOf(1,2,3);restore(activity,bytes);set(activity,"guestInstance","invalid");set(activity,"loadedScope",null)
        val output=Bundle();assertEquals(true,call(activity,"savePhotoRecoveryState",output));assertArrayEquals(bytes,output.getByteArray("photoSession"))
        output.getByteArray("photoSession")!!.fill(0);val next=Bundle();call(activity,"savePhotoRecoveryState",next)
        assertArrayEquals(bytes,next.getByteArray("photoSession"));assertEquals(false,call(activity,"canFinish"));assertNotNull(get(activity,"photoRecovery"))
    }
    @Test fun successfulApplicationReplacesHistoryAndPreservesLivePickerResultOrCancellation(){
        for(cancelled in listOf(false,true)){
            val activity=fixture();restore(activity);val pending=recovery(activity)
            val live=if(cancelled)null else saved.pendingExport;val destination=if(cancelled)null else Uri.parse("content://synthetic/export")
            set(activity,"exportEdit",live);set(activity,"pendingDestination",destination);set(activity,"waitingForPicker",false)
            assertEquals(1,call(activity,"applyPhotoRecovery",pending,scope))
            assertNull(get(activity,"photoRecovery"));assertNull(get(activity,"restoredSession"));assertEquals(saved.edit,get(activity,"edit"))
            assertEquals(saved.undo,get(activity,"undo"));assertEquals(saved.redo,get(activity,"redo"));assertEquals(true,get(activity,"before"));assertEquals(1,get(activity,"activeTool"))
            assertSame(live,get(activity,"exportEdit"));assertEquals(destination,get(activity,"pendingDestination"));assertEquals(false,get(activity,"waitingForPicker"))
            assertEquals(false,call(activity,"savePhotoRecoveryState",Bundle()))
        }
    }
    @Test fun damagedWrongOwnerMissingPixelsAndStaleTicketCannotConsumeRecoveryOrLiveEdits(){
        for(kind in listOf("damaged","owner","pixels","ticket")){
            val activity=fixture();restore(activity,if(kind=="damaged")byteArrayOf(1,2,3)else PhotoSessionCodec.encode(saved));val retained=recovery(activity)
            val original=get(activity,"edit");val history=(get(activity,"undo")as List<*>).toList()
            if(kind=="owner")set(activity,"guestInstance","22222222-2222-2222-2222-222222222222")
            if(kind=="pixels")set(activity,"bitmap",null)
            val argument=if(kind=="ticket")PhotoSessionRecovery(retained.bytesForState())else retained
            assertTrue(kind,runCatching{call(activity,"applyPhotoRecovery",argument,scope)}.isFailure)
            assertSame(retained,get(activity,"photoRecovery"));assertSame(original,get(activity,"edit"));assertEquals(history,get(activity,"undo"))
        }
    }
    @Test fun unresolvedRecoveryBlocksActualEditingHistoryAiExportAndRestoreAdmission(){
        val activity=fixture();restore(activity,byteArrayOf(1,2,3));val pending=recovery(activity);val original=get(activity,"edit");val history=(get(activity,"undo")as List<*>).toList()
        call(activity,"change",PhotoEdit(rotation=90));call(activity,"navigateHistory",false);call(activity,"navigateHistory",true)
        assertEquals(false,call(activity,"allowOnDeviceAi"));assertEquals(false,call(activity,"canFinish"));assertNull(call(activity,"photoControlScope"))
        set(activity,"pendingDestination",Uri.parse("content://synthetic/export"));call(activity,"resumePendingExport");call(activity,"requestPhotoRestore")
        assertSame(original,get(activity,"edit"));assertEquals(history,get(activity,"undo"));assertSame(pending,get(activity,"photoRecovery"));assertEquals(false,get(activity,"exportInProgress"))
    }
    @Test fun recoveryActionsRejectPausedLoadingBusyAndClosedBeforeUi(){
        for(flag in listOf("paused","photoLoading","photoProjectBusy","readingText","enhancing","cuttingOut","exportInProgress","activityClosed")){
            val activity=fixture();restore(activity,byteArrayOf(1));val pending=recovery(activity)
            if(flag=="paused")set(activity,"copyForeground",false)else set(activity,flag,true)
            assertEquals(false,call(activity,"canRequestPhotoRecovery"));call(activity,"retryPhotoRecovery");call(activity,"requestPhotoRecoveryDiscard")
            assertSame(pending,get(activity,"photoRecovery"))
        }
        val missing=fixture();restore(missing,byteArrayOf(1));set(missing,"bitmap",null);assertEquals(true,call(missing,"canRequestPhotoRecovery"))
    }
    @Test fun confirmedDiscardRequiresCurrentSnapshotAndClearsOnlyInMemoryRecoveryEdits(){
        val activity=fixture();restore(activity,byteArrayOf(1));set(activity,"canvasProjectId","saved-project");set(activity,"pendingDestination",Uri.parse("content://synthetic/export"))
        val pending=recovery(activity);val token=Any();set(activity,"photoRecoveryDiscardToken",token);val captured=closeState(activity)
        assertEquals(true,call(activity,"canApprovePhotoRecoveryDiscard",token,pending,captured));assertEquals(false,call(activity,"discardPhotoRecovery",Any(),pending,captured))
        assertEquals(false,call(activity,"canApprovePhotoRecoveryDiscard",token,PhotoSessionRecovery(byteArrayOf(1)),captured))
        set(activity,"copyGeneration",5L);assertEquals(false,call(activity,"discardPhotoRecovery",token,pending,captured));set(activity,"copyGeneration",4L)
        set(activity,"photoRecoveryDiscardToken",Any());assertEquals(false,call(activity,"discardPhotoRecovery",token,pending,captured));set(activity,"photoRecoveryDiscardToken",token)
        assertEquals(true,call(activity,"discardPhotoRecovery",token,pending,captured));assertNull(get(activity,"photoRecovery"));assertNull(get(activity,"restoredSession"))
        assertEquals(PhotoEdit(),get(activity,"edit"));assertEquals(emptyList<PhotoEdit>(),get(activity,"undo"));assertEquals(emptyList<PhotoEdit>(),get(activity,"redo"))
        assertNull(get(activity,"exportEdit"));assertNull(get(activity,"pendingDestination"));assertEquals(false,get(activity,"waitingForPicker"));assertEquals("saved-project",get(activity,"canvasProjectId"))
        assertEquals(listOf(Uri.parse("content://synthetic/protected")),get(activity,"protectedOriginals"));assertEquals(Uri.parse(source),get(activity,"source"));assertEquals(scope,get(activity,"loadedScope"))
    }
    @Test fun pickerResultMarkerAndRawStateRoundtripCannotReviveCancelledExport(){
        val activity=fixture();restore(activity);assertNotNull(get(activity,"exportEdit"));val waiting=Bundle();call(activity,"savePhotoRecoveryState",waiting)
        assertFalse(waiting.getBoolean("photoRecoveryExportInvalidated"))
        call(activity,"markPhotoRecoveryPickerResult");set(activity,"waitingForPicker",false);set(activity,"exportEdit",null)
        val cancelled=Bundle();call(activity,"savePhotoRecoveryState",cancelled);assertTrue(cancelled.getBoolean("photoRecoveryExportInvalidated"))
        val recreated=fixture();restore(recreated,cancelled.getByteArray("photoSession")!!,cancelled.getBoolean("photoRecoveryExportInvalidated"))
        assertNotNull(get(recreated,"photoRecovery"));assertNull(get(recreated,"exportEdit"));assertEquals(false,get(recreated,"waitingForPicker"))
        call(recreated,"applyPhotoRecovery",recovery(recreated),scope);assertNull(get(recreated,"exportEdit"))
    }
    @Test fun closeReviewRetainsUnresolvedIdentityAndClosedDiscardApprovalCannotBeUsed(){
        val activity=fixture();restore(activity,byteArrayOf(1));val pending=recovery(activity);val captured=closeState(activity)
        assertTrue(captured.recoveryPending);assertTrue(captured.needsConfirmation())
        set(activity,"photoRecovery",PhotoSessionRecovery(pending.bytesForState()));assertFalse(captured.stillMatches(closeState(activity)))
        set(activity,"photoRecovery",pending);val token=Any();set(activity,"photoRecoveryDiscardToken",token);call(activity,"closePhotoRecoveryDiscard")
        assertEquals(false,call(activity,"canApprovePhotoRecoveryDiscard",token,pending,captured));assertSame(pending,get(activity,"photoRecovery"))
    }
    @Test fun failedActualRetryKeepsBytesAndLiveHistoryWithoutOpeningUiOrConsumingState(){
        val activity=fixture();val bytes=byteArrayOf(1,2,3);restore(activity,bytes);val pending=recovery(activity);val original=get(activity,"edit")
        call(activity,"retryPhotoRecovery")
        assertSame(pending,get(activity,"photoRecovery"));assertSame(original,get(activity,"edit"));assertArrayEquals(bytes,pending.bytesForState());assertEquals(false,call(activity,"canFinish"))
    }
}
