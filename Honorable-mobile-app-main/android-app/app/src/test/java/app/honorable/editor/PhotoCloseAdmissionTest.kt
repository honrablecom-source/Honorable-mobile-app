package app.honorable.editor

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

/** Constructor-free real Activity state/admission. No dialogs, gestures, finish or lifecycle execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PhotoCloseAdmissionTest {
    private val type=Class.forName("sun.misc.Unsafe")
    private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
    private fun set(activity:PhotoEditorActivity,name:String,value:Any?)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)
    private fun get(activity:PhotoEditorActivity,name:String)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)
    private fun call(activity:PhotoEditorActivity,name:String,vararg args:Any?)=PhotoEditorActivity::class.java.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(activity,*args)
    private fun fixture()=(uncreatedPhotoActivity()as PhotoEditorActivity).also{
        set(it,"copyForeground",true);set(it,"copyGeneration",5L);set(it,"revision",9);set(it,"edit",PhotoEdit())
        set(it,"undo",mutableListOf<PhotoEdit>());set(it,"redo",mutableListOf<PhotoEdit>());set(it,"protectedOriginals",emptyList<Uri>())
        set(it,"source",Uri.parse("content://synthetic/photo"));set(it,"loadedScope","scope");set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"bitmap",allocate(Bitmap::class.java))
    }
    private fun snapshot(activity:PhotoEditorActivity)=call(activity,"photoCloseState")as PhotoCloseState
    private fun approves(activity:PhotoEditorActivity,token:Any,captured:PhotoCloseState)=call(activity,"canApprovePhotoClose",token,captured)as Boolean
    @Test fun pausedOrClosedHeaderAndLegacyBackRefuseBeforeReadingUi(){
        for(flag in listOf("paused","activityClosed")){
            val activity=fixture();if(flag=="paused")set(activity,"copyForeground",false)else set(activity,flag,true)
            assertEquals(false,call(activity,"canRequestPhotoClose"));call(activity,"requestPhotoClose");call(activity,"onBackPressed")
        }
    }
    @Test fun actualStateRecognizesHistoryUnsavedCreationAndAllPendingFlags(){
        val activity=fixture();assertFalse(snapshot(activity).needsConfirmation())
        set(activity,"undo",mutableListOf(PhotoEdit(exposure=.2f)));assertTrue(snapshot(activity).needsConfirmation());set(activity,"undo",mutableListOf<PhotoEdit>())
        for(flag in listOf("photoProjectBusy","readingText","enhancing","cuttingOut","exportInProgress","waitingForPicker")){
            set(activity,flag,true);assertTrue(snapshot(activity).needsConfirmation());set(activity,flag,false)
        }
        set(activity,"exportEdit",PhotoEdit());assertTrue(snapshot(activity).needsConfirmation());set(activity,"exportEdit",null)
        set(activity,"pendingDestination",Uri.parse("content://synthetic/copy"));assertTrue(snapshot(activity).needsConfirmation());set(activity,"pendingDestination",null)
        val context=object:ContextWrapper(null){override fun getPackageName()="app.honorable.test"}
        ContextWrapper::class.java.getDeclaredField("mBase").apply{isAccessible=true}.set(activity,context)
        set(activity,"source",Uri.parse("content://app.honorable.test.creation/synthetic"));assertTrue(snapshot(activity).needsConfirmation())
        set(activity,"canvasProjectId","saved");assertFalse(snapshot(activity).needsConfirmation())
    }
    @Test fun ownedReviewRejectsChangedSourceModelHistoryGenerationOrSession(){
        val changes=listOf("copyGeneration" to 6L,"revision" to 10,"edit" to PhotoEdit(),"source" to Uri.parse("content://synthetic/other"),"loadedScope" to "other", "accessMode" to EditorAccessMode.ACCOUNT,
            "canvasProjectId" to "saved","protectedOriginals" to listOf(Uri.parse("content://synthetic/original")),"bitmap" to allocate(Bitmap::class.java),"undo" to mutableListOf(PhotoEdit()),
            "redo" to mutableListOf(PhotoEdit()),"pendingDestination" to Uri.parse("content://synthetic/copy"),"restoredSession" to PhotoSessionSnapshot("scope",PhotoEdit()))
        for((name,value)in changes){val activity=fixture();val token=Any();set(activity,"photoCloseToken",token);val captured=snapshot(activity);assertTrue(approves(activity,token,captured));set(activity,name,value);assertFalse(name,approves(activity,token,captured))}
    }
    @Test fun workingReviewAllowsCurrentApprovalButRejectsCompletionReplacedOrClosedDialog(){
        val activity=fixture();set(activity,"photoProjectBusy",true);val token=Any();set(activity,"photoCloseToken",token);val captured=snapshot(activity)
        assertTrue(captured.needsConfirmation());assertTrue(approves(activity,token,captured));assertFalse(approves(activity,Any(),captured))
        set(activity,"photoProjectBusy",false);set(activity,"readingText",true);assertFalse(approves(activity,token,captured))
        set(activity,"readingText",false);set(activity,"photoProjectBusy",true);set(activity,"photoCloseToken",Any());assertFalse(approves(activity,token,captured))
        set(activity,"photoCloseToken",token);set(activity,"copyForeground",false);assertFalse(approves(activity,token,captured));set(activity,"copyForeground",true)
        call(activity,"closePhotoCloseReview");assertNull(get(activity,"photoCloseToken"));assertFalse(approves(activity,token,captured))
    }
}
