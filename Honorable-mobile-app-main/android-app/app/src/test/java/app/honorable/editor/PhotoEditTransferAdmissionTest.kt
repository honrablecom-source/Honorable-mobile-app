package app.honorable.editor

import android.graphics.Bitmap
import android.net.Uri
import android.widget.SeekBar
import org.junit.Assert.*
import org.junit.Test

/** Actual metadata/review/Undo path without native clipboard service, dialogs or bitmap rendering. */
class PhotoEditTransferAdmissionTest {
    private val type=Class.forName("sun.misc.Unsafe")
    private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    private val uuid="11111111-1111-1111-1111-111111111111"
    private val initial=PhotoEdit(exposure=.1f,finish=PhotoFinish(caption=PhotoCaption("Target caption")))
    private val incoming=PhotoEdit(rotation=90,exposure=.5f,layers=PhotoLayers(listOf(PhotoObject("copied",PhotoObjectType.TEXT,text="Copied object"))))
    private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
    private fun set(activity:PhotoEditorActivity,name:String,value:Any?)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)
    private fun get(activity:PhotoEditorActivity,name:String)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)
    private fun call(activity:PhotoEditorActivity,name:String,vararg args:Any?):Any?=PhotoEditorActivity::class.java.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(activity,*args)
    private fun fixture(source:String="content://synthetic/target")=(allocate(PhotoEditorActivity::class.java)as PhotoEditorActivity).also{
        set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"guestInstance",uuid);set(it,"source",Uri.parse(source));set(it,"loadedScope",PhotoSessionCodec.scope("guest-activity:$uuid","local-only",source))
        set(it,"edit",initial);set(it,"undo",mutableListOf<PhotoEdit>());set(it,"redo",mutableListOf<PhotoEdit>());set(it,"sliders",mutableListOf<SeekBar>())
        set(it,"bitmap",allocate(Bitmap::class.java));set(it,"copyForeground",true);set(it,"copyGeneration",4L);set(it,"revision",3);set(it,"protectedOriginals",listOf(Uri.parse("content://synthetic/protected")))
    }
    private fun snapshot(activity:PhotoEditorActivity)=call(activity,"photoCloseState")as PhotoCloseState
    private fun approve(activity:PhotoEditorActivity,token:Any,captured:PhotoCloseState,scope:String=get(activity,"loadedScope")as String)=call(activity,"canApprovePhotoSettings",token,captured,scope,EditorAccessMode.GUEST_BASIC)as Boolean
    private fun apply(activity:PhotoEditorActivity,token:Any,captured:PhotoCloseState,value:PhotoEdit=incoming)=call(activity,"applyPhotoSettings",token,captured,get(activity,"loadedScope")as String,EditorAccessMode.GUEST_BASIC,value)as Boolean
    @Test fun pausedBusyRecoveryAndPendingExportsRefuseBeforeClipboardOrUi(){
        for(flag in listOf("paused","photoProjectBusy","readingText","enhancing","cuttingOut","exportInProgress","activityClosed","recovery","pending")){
            val activity=fixture()
            when(flag){"paused"->set(activity,"copyForeground",false);"recovery"->set(activity,"photoRecovery",PhotoSessionRecovery(byteArrayOf(1)));"pending"->set(activity,"exportEdit",PhotoEdit());else->set(activity,flag,true)}
            assertEquals(false,call(activity,"canRequestPhotoSettings"));call(activity,"copyPhotoSettings");call(activity,"pastePhotoSettings")
        }
    }
    @Test fun reviewBindsOwnerSourceModelHistoryGenerationAndCurrentToken(){
        val changes=listOf("source" to Uri.parse("content://synthetic/other"),"guestInstance" to "22222222-2222-2222-2222-222222222222","copyGeneration" to 5L,"revision" to 4,
            "edit" to initial.copy(),"undo" to mutableListOf(PhotoEdit()),"protectedOriginals" to emptyList<Uri>(),"accessMode" to EditorAccessMode.ACCOUNT,"loadedScope" to "other")
        for((name,value)in changes){val activity=fixture();val token=Any();set(activity,"photoSettingsToken",token);val captured=snapshot(activity);val scope=get(activity,"loadedScope")as String
            assertTrue(approve(activity,token,captured));assertFalse(approve(activity,Any(),captured));set(activity,name,value);assertFalse(name,approve(activity,token,captured,scope))}
    }
    @Test fun crossOriginalFullSettingsReplaceIsOneActualUndoAndKeepsTargetSources(){
        val prepared=PhotoEditTransfer.decode(PhotoEditTransfer.encode(incoming))
        for(source in listOf("content://synthetic/first-target","content://synthetic/second-target")){
            val activity=fixture(source);val token=Any();set(activity,"photoSettingsToken",token);val captured=snapshot(activity);val originals=get(activity,"protectedOriginals")
            assertTrue(apply(activity,token,captured,prepared));assertEquals(prepared,get(activity,"edit"));assertEquals(listOf(initial),get(activity,"undo"));assertEquals(emptyList<PhotoEdit>(),get(activity,"redo"))
            assertEquals(Uri.parse(source),get(activity,"source"));assertSame(originals,get(activity,"protectedOriginals"))
            set(activity,"bitmap",null);call(activity,"navigateHistory",false);assertEquals(initial,get(activity,"edit"));call(activity,"navigateHistory",true);assertEquals(prepared,get(activity,"edit"))
        }
    }
    @Test fun identicalSettingsAndDuplicateApprovalDoNotAddUndoRows(){
        val activity=fixture();val token=Any();set(activity,"photoSettingsToken",token);val captured=snapshot(activity)
        assertFalse(apply(activity,token,captured,initial.copy()));assertEquals(emptyList<PhotoEdit>(),get(activity,"undo"))
        assertTrue(apply(activity,token,captured));assertFalse(apply(activity,token,captured,PhotoEdit()));assertEquals(listOf(initial),get(activity,"undo"));assertEquals(incoming,get(activity,"edit"))
    }
    @Test fun replacedClosedOrPausedApprovalLeavesModelAndClipboardWorkUntouched(){
        val activity=fixture();val token=Any();set(activity,"photoSettingsToken",token);val captured=snapshot(activity)
        set(activity,"copyForeground",false);assertFalse(apply(activity,token,captured));set(activity,"copyForeground",true)
        set(activity,"photoSettingsToken",Any());assertFalse(apply(activity,token,captured));set(activity,"photoSettingsToken",token)
        call(activity,"closePhotoSettingsReview");assertFalse(apply(activity,token,captured));assertEquals(initial,get(activity,"edit"));assertEquals(emptyList<PhotoEdit>(),get(activity,"undo"))
    }
    @Test fun sharedMetadataCommitRetainsHistoryCapAndExistingNoChangeBehavior(){
        val activity=fixture();val history=MutableList(50){PhotoEdit(brightness=it.toFloat())};set(activity,"undo",history);set(activity,"redo",mutableListOf(PhotoEdit(rotation=180)));set(activity,"before",true)
        assertEquals(true,call(activity,"commitPhotoChange",incoming));val undo=get(activity,"undo")as List<*>
        assertEquals(50,undo.size);assertEquals(initial,undo.last());assertEquals(PhotoEdit(brightness=1f),undo.first());assertEquals(emptyList<PhotoEdit>(),get(activity,"redo"));assertEquals(false,get(activity,"before"))
        val redo=mutableListOf(PhotoEdit(rotation=180));set(activity,"redo",redo);set(activity,"before",true)
        assertEquals(true,call(activity,"commitPhotoChange",incoming.copy()));assertSame(redo,get(activity,"redo"));assertEquals(50,(get(activity,"undo")as List<*>).size);assertEquals(false,get(activity,"before"))
    }
}
