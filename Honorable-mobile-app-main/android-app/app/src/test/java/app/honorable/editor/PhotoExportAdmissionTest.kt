package app.honorable.editor

import android.graphics.Bitmap
import android.net.Uri
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

private object ExportFixture {
    private val type=Class.forName("sun.misc.Unsafe")
    private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    val source="content://synthetic/photo";val destination=Uri.parse("content://synthetic/copy");val uuid="11111111-1111-1111-1111-111111111111"
    val scope=PhotoSessionCodec.scope("guest-activity:$uuid","local-only",source)
    fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
    fun set(activity:PhotoEditorActivity,name:String,value:Any?)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)
    fun get(activity:PhotoEditorActivity,name:String)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)
    fun call(activity:PhotoEditorActivity,name:String,vararg args:Any?):Any?=PhotoEditorActivity::class.java.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(activity,*args)
    fun fixture()=(allocate(PhotoEditorActivity::class.java)as PhotoEditorActivity).also{
        set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"guestInstance",uuid);set(it,"source",Uri.parse(source));set(it,"loadedScope",scope)
        set(it,"bitmap",allocate(Bitmap::class.java));set(it,"copyForeground",true);set(it,"copyGeneration",4L);set(it,"exportEdit",PhotoEdit(exposure=.25f));set(it,"pendingDestination",destination)
        set(it,"exportEncoding",SafePhotoCopy.Encoding.JPEG);set(it,"exportQuality",83);set(it,"protectedOriginals",listOf(Uri.parse("content://synthetic/original")))
    }
    fun capture(activity:PhotoEditorActivity)=call(activity,"capturePhotoExport",destination,get(activity,"exportEdit"))as PhotoExportOperation?
    fun active(activity:PhotoEditorActivity):PhotoExportOperation=capture(activity)!!.also{set(activity,"photoExport",it);set(activity,"exportInProgress",true)}
}

class PhotoExportForegroundTest {
    @Test fun pausedExportRefusesBeforeUiDispatchAndKeepsPendingDestination(){
        val activity=ExportFixture.fixture();ExportFixture.set(activity,"copyForeground",false)
        ExportFixture.call(activity,"resumePendingExport")
        assertEquals(false,ExportFixture.get(activity,"exportInProgress"));assertEquals(ExportFixture.destination,ExportFixture.get(activity,"pendingDestination"));assertNotNull(ExportFixture.get(activity,"exportEdit"))
    }
}

class PhotoExportAdmissionTest {
    @Test fun snapshotBindsRealCurrentGuestScopeAndRefusesUnavailableContext(){
        val activity=ExportFixture.fixture();val op=ExportFixture.capture(activity)!!
        assertEquals(ExportFixture.scope,op.scope);assertEquals(83,op.quality);assertEquals(SafePhotoCopy.Encoding.JPEG,op.encoding);assertEquals(ExportFixture.source,op.source)
        for(flag in listOf("paused","exportInProgress","activityClosed","pixels","recovery")){
            val value=ExportFixture.fixture()
            when(flag){"paused"->ExportFixture.set(value,"copyForeground",false);"pixels"->ExportFixture.set(value,"bitmap",null);"recovery"->ExportFixture.set(value,"photoRecovery",PhotoSessionRecovery(byteArrayOf(1)));else->ExportFixture.set(value,flag,true)}
            assertNull(flag,ExportFixture.capture(value))
        }
        ExportFixture.set(activity,"guestInstance","invalid");assertNull(ExportFixture.capture(activity))
        ExportFixture.set(activity,"exportInProgress",true);ExportFixture.set(activity,"exportEdit",null);assertEquals(false,ExportFixture.call(activity,"canFinish"))
    }
    @Test fun activeOwnershipRejectsChangedOwnerSourceGenerationPendingSnapshotAndPhase(){
        val changes=listOf("copyForeground" to false,"copyGeneration" to 5L,"activityClosed" to true,"exportInProgress" to false,"bitmap" to null,"photoRecovery" to PhotoSessionRecovery(byteArrayOf(1)),
            "source" to Uri.parse("content://synthetic/other"),"loadedScope" to "other","accessMode" to EditorAccessMode.ACCOUNT,"guestInstance" to "22222222-2222-2222-2222-222222222222",
            "protectedOriginals" to emptyList<Uri>(),"exportEdit" to PhotoEdit(exposure=.25f))
        for((name,value)in changes){val activity=ExportFixture.fixture();val op=ExportFixture.active(activity);assertEquals(true,ExportFixture.call(activity,"photoExportCurrent",op));ExportFixture.set(activity,name,value);assertEquals(name,false,ExportFixture.call(activity,"photoExportCurrent",op))}
    }
    @Test fun oldCompletionCannotReleaseNewOperationOrItsSnapshot(){
        val activity=ExportFixture.fixture();val old=ExportFixture.active(activity)
        ExportFixture.set(activity,"exportInProgress",false);ExportFixture.set(activity,"exportEdit",PhotoEdit(rotation=90));val current=ExportFixture.active(activity)
        ExportFixture.call(activity,"completePhotoExport",old,"10 × 10",null)
        assertSame(current,ExportFixture.get(activity,"photoExport"));assertSame(current.edit,ExportFixture.get(activity,"exportEdit"));assertEquals(true,ExportFixture.get(activity,"exportInProgress"));assertNull(ExportFixture.get(activity,"photoExportNotice"))
    }
    @Test fun cancelledPausedCompletionReleasesOnlyItsBusyStateAndRetainsTruthfulNotice(){
        val activity=ExportFixture.fixture();val op=ExportFixture.active(activity);op.cancel();ExportFixture.set(activity,"copyForeground",false)
        ExportFixture.call(activity,"completePhotoExport",op,"10 × 10",null)
        assertNull(ExportFixture.get(activity,"photoExport"));assertNull(ExportFixture.get(activity,"exportEdit"));assertEquals(false,ExportFixture.get(activity,"exportInProgress"))
        val notice=ExportFixture.get(activity,"photoExportNotice")as Pair<*,*>;assertSame(op,notice.first);assertTrue((notice.second as String).contains("stopped"));assertFalse((notice.second as String).contains("Copy saved"))
    }
    @Test fun ownedCompletionDistinguishesSuccessFailureAndMemoryAndIsIdempotent(){
        for(error in listOf(null,IOException("Synthetic failure"),OutOfMemoryError("Synthetic OOM"))){
            val activity=ExportFixture.fixture();val op=ExportFixture.active(activity);ExportFixture.call(activity,"completePhotoExport",op,"10 × 10",error)
            val notice=ExportFixture.get(activity,"photoExportNotice")as Pair<*,*>;val message=notice.second as String
            assertTrue(message,when(error){null->message.contains("Copy saved");is OutOfMemoryError->message.contains("memory");else->message.contains("failed")})
            ExportFixture.call(activity,"completePhotoExport",op,null,IOException("Late failure"));assertSame(notice,ExportFixture.get(activity,"photoExportNotice"));assertEquals(false,ExportFixture.get(activity,"exportInProgress"))
        }
    }
    @Test fun activeMissingPixelsAndRecoveryDeferralNeverDispatchAnotherWorker(){
        for(kind in listOf("busy","pixels","recovery")){
            val activity=ExportFixture.fixture()
            when(kind){"busy"->ExportFixture.set(activity,"exportInProgress",true);"pixels"->ExportFixture.set(activity,"bitmap",null);else->ExportFixture.set(activity,"photoRecovery",PhotoSessionRecovery(byteArrayOf(1)))}
            ExportFixture.call(activity,"resumePendingExport");assertEquals(ExportFixture.destination,ExportFixture.get(activity,"pendingDestination"))
        }
    }
}
