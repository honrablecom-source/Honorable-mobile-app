package app.honorable.editor

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.widget.SeekBar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

/** Actual Activity model/admission and real controller ownership without dialogs/media/keystore. */
class PhotoVersionAdmissionTest {
    private val type=Class.forName("sun.misc.Unsafe");private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    private val source="content://synthetic/photo";private val uuid="11111111-1111-1111-1111-111111111111"
    private val scope=PhotoSessionCodec.scope("guest-activity:$uuid","local-only",source);private val versionScope=PhotoVersionScope(PhotoPresetNamespace.GUEST,source)
    private val initial=PhotoEdit(exposure=.1f);private val incoming=PhotoEdit(rotation=90)
    private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
    private fun set(value:Any,name:String,data:Any?)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(value,data)
    private fun get(value:Any,name:String)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(value)
    private fun call(value:Any,name:String,vararg args:Any?)=value.javaClass.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(value,*args)
    private fun fixture()=(allocate(PhotoEditorActivity::class.java)as PhotoEditorActivity).also{
        set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"guestInstance",uuid);set(it,"source",Uri.parse(source));set(it,"loadedScope",scope);set(it,"edit",initial)
        set(it,"undo",mutableListOf<PhotoEdit>());set(it,"redo",mutableListOf<PhotoEdit>());set(it,"sliders",mutableListOf<SeekBar>());set(it,"bitmap",allocate(Bitmap::class.java));set(it,"copyForeground",true);set(it,"copyGeneration",4L);set(it,"revision",3)
        set(it,"protectedOriginals",listOf(Uri.parse("content://synthetic/previous")))
    }
    private fun snapshot(a:PhotoEditorActivity)=call(a,"photoCloseState")as PhotoCloseState
    private fun version(originals:List<String> = listOf("content://synthetic/version-original"))=PhotoSavedVersion.create(versionScope,"Version","a".repeat(64),incoming,originals)
    @Test fun pausedBusyRecoveryPendingExportAndOwnCreatedSourcesRefuseBeforeUi(){
        for(flag in listOf("paused","photoProjectBusy","readingText","enhancing","cuttingOut","exportInProgress","activityClosed","recovery","pending")){
            val a=fixture();when(flag){"paused"->set(a,"copyForeground",false);"recovery"->set(a,"photoRecovery",PhotoSessionRecovery(byteArrayOf(1)));"pending"->set(a,"exportEdit",PhotoEdit());else->set(a,flag,true)}
            assertEquals(false,call(a,"canOpenPhotoVersions"));call(a,"savedPhotoVersions")
        }
        val a=fixture();val context=object:ContextWrapper(null){override fun getPackageName()="app.honorable.test"};ContextWrapper::class.java.getDeclaredField("mBase").apply{isAccessible=true}.set(a,context)
        set(a,"source",Uri.parse("content://app.honorable.test.creation/source"));assertEquals(false,call(a,"canOpenPhotoVersions"));call(a,"savedPhotoVersions")
    }
    @Test fun actualApplyRequiresSameModelSourceOwnerGenerationAndEntryScope(){
        val changes=listOf("copyGeneration" to 5L,"revision" to 4,"edit" to initial.copy(),"source" to Uri.parse("content://synthetic/other"),"loadedScope" to "other", "guestInstance" to "22222222-2222-2222-2222-222222222222","accessMode" to EditorAccessMode.ACCOUNT)
        for((name,value)in changes){val a=fixture();val captured=snapshot(a);assertEquals(true,call(a,"canApplyPhotoVersion",captured,scope,EditorAccessMode.GUEST_BASIC));set(a,name,value);assertEquals(false,call(a,"canApplyPhotoVersion",captured,scope,EditorAccessMode.GUEST_BASIC))}
        val a=fixture();val foreign=PhotoSavedVersion.create(PhotoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/other"),"Foreign","a".repeat(64),incoming,emptyList())
        assertEquals(false,call(a,"applyPhotoVersion",snapshot(a),scope,EditorAccessMode.GUEST_BASIC,foreign,incoming));assertEquals(initial,get(a,"edit"))
    }
    @Test fun openingVersionIsOneActualUndoAndProvenanceStaysProtectedThroughUndo(){
        val a=fixture();assertEquals(true,call(a,"applyPhotoVersion",snapshot(a),scope,EditorAccessMode.GUEST_BASIC,version(),incoming))
        assertEquals(incoming,get(a,"edit"));assertEquals(listOf(initial),get(a,"undo"));val origins=get(a,"protectedOriginals")as List<*>
        assertEquals(listOf(Uri.parse("content://synthetic/previous"),Uri.parse("content://synthetic/version-original")),origins)
        assertEquals(true,call(a,"applyPhotoVersion",snapshot(a),scope,EditorAccessMode.GUEST_BASIC,version(),incoming));assertEquals(listOf(initial),get(a,"undo"))
        val beforeUndo=get(a,"protectedOriginals");assertEquals(origins,beforeUndo)
        set(a,"bitmap",null);call(a,"navigateHistory",false);assertEquals(initial,get(a,"edit"));assertSame(beforeUndo,get(a,"protectedOriginals"))
    }
    @Test fun provenanceOverflowRefusesBeforeChangingModelOrHistory(){
        val a=fixture();val originals=List(16){Uri.parse("content://synthetic/current-$it")};set(a,"protectedOriginals",originals);val captured=snapshot(a)
        assertTrue(runCatching{call(a,"applyPhotoVersion",captured,scope,EditorAccessMode.GUEST_BASIC,version(),incoming)}.isFailure)
        assertEquals(initial,get(a,"edit"));assertEquals(emptyList<PhotoEdit>(),get(a,"undo"));assertSame(originals,get(a,"protectedOriginals"))
    }
    private fun shelf(busy:(Boolean)->Unit)=(allocate(PhotoVersionShelf::class.java)as PhotoVersionShelf).also{set(it,"closed",AtomicBoolean(false));set(it,"worker",Executors.newSingleThreadExecutor());set(it,"busy",busy)}
    @Test fun controllerCloseReleasesOnlyOwnedBusyStateOnce(){
        var releases=0;val idle=shelf{if(!it)releases++};idle.close();assertEquals(0,releases)
        val active=shelf{if(!it)releases++};set(active,"ownsBusy",true);active.close();active.close();assertEquals(1,releases)
        var actions=0;val stopped=shelf{};stopped.close();call(stopped,"job",{actions++;1},{_:Int->actions++});assertEquals(0,actions)
    }
    @Test fun replacedClosedDialogTokensCannotApproveAnotherShelfAction(){
        val value=shelf{};val token=Any();set(value,"dialogToken",token);assertEquals(true,call(value,"owns",token));assertEquals(false,call(value,"owns",Any()))
        set(value,"dialogToken",Any());assertEquals(false,call(value,"owns",token));value.close();assertEquals(false,call(value,"owns",get(value,"dialogToken")?:token))
    }
}
