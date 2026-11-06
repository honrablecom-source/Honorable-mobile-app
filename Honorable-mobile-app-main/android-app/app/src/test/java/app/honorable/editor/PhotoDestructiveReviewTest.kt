package app.honorable.editor

import android.graphics.Bitmap
import android.net.Uri
import android.widget.SeekBar
import org.junit.Assert.*
import org.junit.Test

/** Real Activity methods/metadata; no dialogs, rendering, storage or lifecycle delivery. */
class PhotoDestructiveReviewTest {
    private val type=Class.forName("sun.misc.Unsafe")
    private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    private val uuid="11111111-1111-1111-1111-111111111111"
    private val scope=PhotoSessionCodec.scope("guest-activity:$uuid","local-only","content://synthetic/photo")
    private val initial=PhotoEdit(exposure=.25f)
    private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
    private fun set(value:Any,name:String,data:Any?)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(value,data)
    private fun get(value:Any,name:String)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(value)
    private fun call(value:Any,name:String,vararg args:Any?)=value.javaClass.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(value,*args)
    private fun fixture()=(allocate(PhotoEditorActivity::class.java)as PhotoEditorActivity).also{
        set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"guestInstance",uuid);set(it,"source",Uri.parse("content://synthetic/photo"));set(it,"loadedScope",scope);set(it,"edit",initial)
        set(it,"sliders",mutableListOf<SeekBar>());set(it,"undo",mutableListOf<PhotoEdit>());set(it,"redo",mutableListOf<PhotoEdit>());set(it,"bitmap",allocate(Bitmap::class.java));set(it,"copyForeground",true);set(it,"copyGeneration",4L);set(it,"revision",3)
        set(it,"protectedOriginals",listOf(Uri.parse("content://synthetic/protected")))
    }
    @Test fun pausedResetDoesNotReachDialogOrContext(){val a=fixture();set(a,"copyForeground",false);call(a,"confirmPhotoReset");assertEquals(initial,get(a,"edit"))}
    @Test fun pausedDeleteDoesNotReachDialogOrContext(){val a=fixture();set(a,"copyForeground",false);call(a,"deletePhotoProject");assertEquals(initial,get(a,"edit"))}

    private fun snapshot(a:PhotoEditorActivity)=call(a,"photoCloseState")as PhotoCloseState
    private fun approve(a:PhotoEditorActivity,token:Any,captured:PhotoCloseState)=call(a,"canApprovePhotoDestructive",token,captured,scope,EditorAccessMode.GUEST_BASIC)as Boolean
    @Test fun workingRecoveryClosedAndPendingPickerEntriesRefuseBeforeUi(){
        for(flag in listOf("photoProjectBusy","readingText","enhancing","cuttingOut","exportInProgress","activityClosed","waitingForPicker","recovery","destination","pending")){
            val a=fixture();when(flag){"recovery"->set(a,"photoRecovery",PhotoSessionRecovery(byteArrayOf(1)));"destination"->set(a,"pendingDestination",Uri.parse("content://synthetic/output"));"pending"->set(a,"exportEdit",PhotoEdit());else->set(a,flag,true)}
            assertEquals(false,call(a,"canRequestPhotoDestructive"));call(a,"confirmPhotoReset");call(a,"deletePhotoProject")
        }
    }
    @Test fun fullCurrentSnapshotBindsModelHistorySourcesPixelsOwnerAndGeneration(){
        val changes=listOf("copyGeneration" to 5L,"revision" to 4,"edit" to initial.copy(),"source" to Uri.parse("content://synthetic/other"),"loadedScope" to "other", "guestInstance" to "22222222-2222-2222-2222-222222222222","accessMode" to EditorAccessMode.ACCOUNT,"undo" to mutableListOf(PhotoEdit()),"redo" to mutableListOf(PhotoEdit()),"bitmap" to allocate(Bitmap::class.java),"canvasProjectId" to "other", "protectedOriginals" to listOf(Uri.parse("content://synthetic/other-original")),"copyForeground" to false)
        for((name,value)in changes){val a=fixture();val token=Any();set(a,"photoDestructiveToken",token);val captured=snapshot(a);assertTrue(approve(a,token,captured));set(a,name,value);assertFalse("stale $name",approve(a,token,captured))}
    }
    @Test fun replacedAndForeignDialogTokensCannotApprove(){
        val a=fixture();val token=Any();set(a,"photoDestructiveToken",token);val captured=snapshot(a)
        assertTrue(approve(a,token,captured));assertFalse(approve(a,Any(),captured));set(a,"photoDestructiveToken",Any());assertFalse(approve(a,token,captured))
    }
    @Test fun actualResetAddsOneUndoClearsRedoAndKeepsOriginalProtection(){
        val a=fixture();val token=Any();set(a,"photoDestructiveToken",token);set(a,"redo",mutableListOf(PhotoEdit(rotation=90)));val originals=get(a,"protectedOriginals")
        assertEquals(true,call(a,"applyPhotoReset",token,snapshot(a),scope,EditorAccessMode.GUEST_BASIC));assertEquals(PhotoEdit(),get(a,"edit"));assertEquals(listOf(initial),get(a,"undo"));assertEquals(emptyList<PhotoEdit>(),get(a,"redo"));assertSame(originals,get(a,"protectedOriginals"))
        set(a,"bitmap",null);call(a,"navigateHistory",false);assertEquals(initial,get(a,"edit"));assertSame(originals,get(a,"protectedOriginals"))
    }
    @Test fun staleResetCannotDiscardNewEditsOrHistories(){
        val a=fixture();val token=Any();set(a,"photoDestructiveToken",token);val captured=snapshot(a);val updated=PhotoEdit(rotation=90);val history=mutableListOf(initial);set(a,"edit",updated);set(a,"undo",history)
        assertEquals(false,call(a,"applyPhotoReset",token,captured,scope,EditorAccessMode.GUEST_BASIC));assertSame(updated,get(a,"edit"));assertSame(history,get(a,"undo"));assertEquals(emptyList<PhotoEdit>(),get(a,"redo"))
    }
    @Test fun closingReviewInvalidatesApprovalAndIsIdempotent(){
        val a=fixture();val token=Any();set(a,"photoDestructiveToken",token);val captured=snapshot(a);call(a,"closePhotoDestructiveReview");call(a,"closePhotoDestructiveReview")
        assertNull(get(a,"photoDestructiveToken"));assertNull(get(a,"photoDestructiveDialog"));assertFalse(approve(a,token,captured))
    }
    @Test fun deleteAndRestoreWorkersNeedSameForegroundGenerationWhileSavePolicyStays(){
        val a=fixture();for(action in listOf("delete","restore")){assertEquals(true,call(a,"photoProjectForegroundMatches",action,4L));assertEquals(false,call(a,"photoProjectForegroundMatches",action,3L))}
        set(a,"copyForeground",false);for(action in listOf("delete","restore"))assertEquals(false,call(a,"photoProjectForegroundMatches",action,4L))
        assertEquals(true,call(a,"photoProjectForegroundMatches","save",3L))
    }
    @Test fun directDeleteDispatchRefusesPausedOrBusyBeforeStorageContext(){
        val paused=fixture();set(paused,"copyForeground",false);call(paused,"photoProjectOperation","delete")
        val busy=fixture();set(busy,"photoProjectBusy",true);call(busy,"photoProjectOperation","delete")
        assertEquals(initial,get(paused,"edit"));assertEquals(initial,get(busy,"edit"))
    }
}
