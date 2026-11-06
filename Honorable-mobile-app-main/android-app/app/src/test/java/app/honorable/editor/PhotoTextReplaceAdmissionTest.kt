package app.honorable.editor

import android.graphics.Bitmap
import android.net.Uri
import android.widget.SeekBar
import org.junit.Assert.*
import org.junit.Test

/** Actual Activity/controller metadata boundaries; no Android UI/render/lifecycle execution. */
class PhotoTextReplaceAdmissionTest {
    private val type=Class.forName("sun.misc.Unsafe");private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    private val uuid="11111111-1111-1111-1111-111111111111";private val scope=PhotoSessionCodec.scope("guest-activity:$uuid","local-only","content://synthetic/photo")
    private val initial=PhotoEdit(exposure=.25f,finish=PhotoFinish(caption=PhotoCaption("Old caption")),layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Old object"))))
    private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
    private fun set(value:Any,name:String,data:Any?)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(value,data)
    private fun get(value:Any,name:String)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(value)
    private fun call(value:Any,name:String,vararg args:Any?)=value.javaClass.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(value,*args)
    private fun fixture()=(allocate(PhotoEditorActivity::class.java)as PhotoEditorActivity).also{
        set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"guestInstance",uuid);set(it,"source",Uri.parse("content://synthetic/photo"));set(it,"loadedScope",scope);set(it,"edit",initial)
        set(it,"undo",mutableListOf<PhotoEdit>());set(it,"redo",mutableListOf<PhotoEdit>());set(it,"sliders",mutableListOf<SeekBar>());set(it,"bitmap",allocate(Bitmap::class.java));set(it,"copyForeground",true);set(it,"copyGeneration",4L);set(it,"revision",3)
        set(it,"protectedOriginals",listOf(Uri.parse("content://synthetic/protected")))
    }
    private fun snapshot(a:PhotoEditorActivity)=call(a,"photoCloseState")as PhotoCloseState
    private fun owner(a:PhotoEditorActivity)=(allocate(PhotoTextReplaceControls::class.java)as PhotoTextReplaceControls).also{set(a,"photoTextReplace",it)}
    private fun apply(a:PhotoEditorActivity,owner:PhotoTextReplaceControls,captured:PhotoCloseState,plan:PhotoTextReplacement)=call(a,"applyPhotoTextReplace",owner,captured,scope,EditorAccessMode.GUEST_BASIC,plan)as Boolean
    @Test fun pausedBusyRecoveryPickerAndClosedEntryNeverReachesUi(){
        for(flag in listOf("paused","photoProjectBusy","readingText","enhancing","cuttingOut","exportInProgress","activityClosed","waitingForPicker","destination","recovery","pending")){
            val a=fixture();when(flag){"paused"->set(a,"copyForeground",false);"destination"->set(a,"pendingDestination",Uri.parse("content://synthetic/output"));"recovery"->set(a,"photoRecovery",PhotoSessionRecovery(byteArrayOf(1)));"pending"->set(a,"exportEdit",PhotoEdit());else->set(a,flag,true)}
            assertEquals(false,call(a,"canRequestPhotoTextReplace"));call(a,"findReplacePhotoText");assertSame(initial,get(a,"edit"))
        }
    }
    @Test fun reviewBindsExactPhotoHistoryOriginalsOwnerModeAndGeneration(){
        val changes=listOf("copyGeneration" to 5L,"revision" to 4,"edit" to initial.copy(),"source" to Uri.parse("content://synthetic/other"),"loadedScope" to "other","guestInstance" to "22222222-2222-2222-2222-222222222222","accessMode" to EditorAccessMode.ACCOUNT,"undo" to mutableListOf(PhotoEdit()),"redo" to mutableListOf(PhotoEdit()),"bitmap" to allocate(Bitmap::class.java),"protectedOriginals" to listOf(Uri.parse("content://synthetic/other-original")))
        for((name,value)in changes){val a=fixture();val captured=snapshot(a);assertEquals(true,call(a,"canApprovePhotoTextReplace",captured,scope,EditorAccessMode.GUEST_BASIC));set(a,name,value);assertEquals(false,call(a,"canApprovePhotoTextReplace",captured,scope,EditorAccessMode.GUEST_BASIC))}
    }
    @Test fun actualBulkCommitIsOneUndoAndRetainsOriginalProtection(){
        val a=fixture();val control=owner(a);set(a,"redo",mutableListOf(PhotoEdit(rotation=90)));val origins=get(a,"protectedOriginals");val plan=PhotoTextReplacement.prepare(initial,"Old","New")
        assertTrue(apply(a,control,snapshot(a),plan));assertEquals(plan.edit,get(a,"edit"));assertEquals(listOf(initial),get(a,"undo"));assertEquals(emptyList<PhotoEdit>(),get(a,"redo"));assertSame(origins,get(a,"protectedOriginals"))
        set(a,"bitmap",null);call(a,"navigateHistory",false);assertEquals(initial,get(a,"edit"));assertSame(origins,get(a,"protectedOriginals"));call(a,"navigateHistory",true);assertEquals(plan.edit,get(a,"edit"))
    }
    @Test fun foreignOwnerNoOpAndEqualButDifferentBaseCannotCommit(){
        val a=fixture();val current=owner(a);val captured=snapshot(a);val plan=PhotoTextReplacement.prepare(initial,"Old","New")
        assertFalse(apply(a,allocate(PhotoTextReplaceControls::class.java)as PhotoTextReplaceControls,captured,plan))
        assertFalse(apply(a,current,captured,PhotoTextReplacement.prepare(initial,"Missing","New")))
        assertFalse(apply(a,current,captured,PhotoTextReplacement.prepare(initial.copy(),"Old","New")))
        set(a,"photoTextReplace",null);assertFalse(apply(a,current,captured,plan));assertSame(initial,get(a,"edit"));assertEquals(emptyList<PhotoEdit>(),get(a,"undo"))
    }
    @Test fun staleApprovedPlanCannotDiscardLaterTextOrHistory(){
        val a=fixture();val current=owner(a);val captured=snapshot(a);val plan=PhotoTextReplacement.prepare(initial,"Old","New");val edited=initial.copy(rotation=90);val history=mutableListOf(initial);set(a,"edit",edited);set(a,"undo",history)
        assertFalse(apply(a,current,captured,plan));assertSame(edited,get(a,"edit"));assertSame(history,get(a,"undo"))
    }
    @Test fun controllerTokensAndClosedOrInvalidEntryDoNotReachUi(){
        val value=allocate(PhotoTextReplaceControls::class.java)as PhotoTextReplaceControls;val token=Any();set(value,"token",token);set(value,"isCurrent",{true})
        assertEquals(true,call(value,"owns",token));assertEquals(false,call(value,"owns",Any()));set(value,"token",Any());assertEquals(false,call(value,"owns",token));value.close();value.close();assertEquals(false,call(value,"owns",get(value,"token")?:token));value.open()
        val invalid=allocate(PhotoTextReplaceControls::class.java)as PhotoTextReplaceControls;set(invalid,"isCurrent",{false});invalid.open();assertEquals(true,get(invalid,"closed"))
    }
}
