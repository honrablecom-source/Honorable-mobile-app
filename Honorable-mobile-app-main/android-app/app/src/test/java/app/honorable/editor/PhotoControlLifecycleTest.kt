package app.honorable.editor

import android.graphics.Bitmap
import android.net.Uri
import android.widget.SeekBar
import org.junit.Assert.*
import org.junit.Test

/** Actual Activity boundary methods; Android Context deliberately unavailable. */
class PhotoControlLifecycleTest {
    private val type=Class.forName("sun.misc.Unsafe");private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    private val uuid="11111111-1111-1111-1111-111111111111";private val scope=PhotoSessionCodec.scope("guest-activity:$uuid","local-only","content://synthetic/photo")
    private val initial=PhotoEdit(exposure=.25f)
    private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
    private fun set(value:Any,name:String,data:Any?)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(value,data)
    private fun get(value:Any,name:String)=value.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(value)
    private fun call(value:Any,name:String,vararg args:Any?)=value.javaClass.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(value,*args)
    private fun fixture()=(allocate(PhotoEditorActivity::class.java)as PhotoEditorActivity).also{
        set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"guestInstance",uuid);set(it,"source",Uri.parse("content://synthetic/photo"));set(it,"loadedScope",scope);set(it,"edit",initial)
        set(it,"undo",mutableListOf<PhotoEdit>());set(it,"redo",mutableListOf<PhotoEdit>());set(it,"sliders",mutableListOf<SeekBar>());set(it,"bitmap",allocate(Bitmap::class.java));set(it,"copyForeground",false);set(it,"copyGeneration",4L);set(it,"revision",3)
        set(it,"protectedOriginals",listOf(Uri.parse("content://synthetic/protected")))
    }
    @Test fun pausedAlphaGradientDoesNotReachAndroidContext(){val a=fixture();call(a,"manageAlphaGradient");assertSame(initial,get(a,"edit"))}
    @Test fun pausedMonochromeDoesNotReachAndroidContext(){val a=fixture();call(a,"manageMonochrome");assertSame(initial,get(a,"edit"))}
    @Test fun pausedSelectiveColorDoesNotReachAndroidContext(){val a=fixture();call(a,"manageSelectiveColor");assertSame(initial,get(a,"edit"))}
    @Test fun pausedRegionRecipesDoesNotReachAndroidContext(){val a=fixture();call(a,"manageRegionRecipes");assertSame(initial,get(a,"edit"))}
    @Test fun pausedRegionsDoesNotReachAndroidContext(){val a=fixture();call(a,"manageRegions");assertSame(initial,get(a,"edit"))}
    @Test fun pausedObjectsDoesNotReachAndroidContext(){val a=fixture();call(a,"manageObjects");assertSame(initial,get(a,"edit"))}
    @Test fun pausedRatioDoesNotReachAndroidContext(){val a=fixture();call(a,"chooseRatio");assertSame(initial,get(a,"edit"))}
    @Test fun pausedMirrorDoesNotReachAndroidContext(){val a=fixture();call(a,"chooseMirror");assertSame(initial,get(a,"edit"))}
    @Test fun pausedCaptionDoesNotReachAndroidContext(){val a=fixture();call(a,"editCaption");assertSame(initial,get(a,"edit"))}
    @Test fun pausedFinishDoesNotReachAndroidContext(){val a=fixture();call(a,"finishAdjust","Exposure",0);assertSame(initial,get(a,"edit"))}
    @Test fun pausedCreativeDoesNotReachAndroidContext(){val a=fixture();call(a,"creativeAdjust",PhotoCreativeTool.entries.first());assertSame(initial,get(a,"edit"))}
    @Test fun pausedEffectDoesNotReachAndroidContext(){val a=fixture();call(a,"effectAdjust",PhotoEffectTool.entries.first());assertSame(initial,get(a,"edit"))}
    @Test fun pausedCompositionDoesNotReachAndroidContext(){val a=fixture();call(a,"compositionAdjust",PhotoCompositionTool.entries.first());assertSame(initial,get(a,"edit"))}
    @Test fun pausedExportChooserDoesNotReachAndroidContext(){val a=fixture();call(a,"chooseExport");assertSame(initial,get(a,"edit"))}

    private fun snapshot(a:PhotoEditorActivity)=call(a,"photoCloseState")as PhotoCloseState
    private fun foreground()=fixture().also{set(it,"copyForeground",true)}
    private fun valid(a:PhotoEditorActivity,captured:PhotoCloseState)=call(a,"photoControlUnchanged",initial,scope,captured)as Boolean
    private fun commit(a:PhotoEditorActivity,captured:PhotoCloseState,next:PhotoEdit)=call(a,"commitPhotoControl",initial,scope,captured,next)as Boolean
    @Test fun pausedAndPostResumeCallbacksRejectEvenWithUnchangedEditValues(){
        val a=foreground();val captured=snapshot(a);assertTrue(valid(a,captured));set(a,"copyForeground",false);assertFalse(valid(a,captured))
        set(a,"copyForeground",true);set(a,"copyGeneration",5L);assertEquals(true,call(a,"photoControlUnchanged",initial,scope));assertFalse(valid(a,captured));assertFalse(commit(a,captured,initial.copy(rotation=90)));assertSame(initial,get(a,"edit"));assertEquals(emptyList<PhotoEdit>(),get(a,"undo"))
    }
    @Test fun fullSnapshotRejectsEquivalentModelsHistoryPixelsSourcesOwnerAndCanvas(){
        val changes=listOf("edit" to initial.copy(),"revision" to 4,"undo" to mutableListOf(PhotoEdit()),"redo" to mutableListOf(PhotoEdit()),"bitmap" to allocate(Bitmap::class.java),"protectedOriginals" to listOf(Uri.parse("content://synthetic/other-original")),"canvasProjectId" to "other","source" to Uri.parse("content://synthetic/other"),"loadedScope" to "other","guestInstance" to "22222222-2222-2222-2222-222222222222","accessMode" to EditorAccessMode.ACCOUNT)
        for((name,value)in changes){val a=foreground();val captured=snapshot(a);set(a,name,value);assertFalse("stale $name",valid(a,captured))}
    }
    @Test fun busyRecoveryPendingExportAndPickerCannotCommit(){
        for(flag in listOf("photoProjectBusy","readingText","enhancing","cuttingOut","exportInProgress","activityClosed","waitingForPicker","pending","destination","recovery")){
            val a=foreground();val captured=snapshot(a);when(flag){"pending"->set(a,"exportEdit",PhotoEdit());"destination"->set(a,"pendingDestination",Uri.parse("content://synthetic/output"));"recovery"->set(a,"photoRecovery",PhotoSessionRecovery(byteArrayOf(1)));else->set(a,flag,true)}
            assertFalse(commit(a,captured,initial.copy(rotation=90)));assertSame(initial,get(a,"edit"));assertEquals(emptyList<PhotoEdit>(),get(a,"undo"))
        }
    }
    @Test fun validActualCommitAddsOneUndoAndPreservesOriginalProtection(){
        val a=foreground();set(a,"redo",mutableListOf(PhotoEdit(rotation=90)));val captured=snapshot(a);val originals=get(a,"protectedOriginals");val next=initial.copy(contrast=.8f)
        assertTrue(commit(a,captured,next));assertSame(next,get(a,"edit"));assertEquals(listOf(initial),get(a,"undo"));assertEquals(emptyList<PhotoEdit>(),get(a,"redo"));assertSame(originals,get(a,"protectedOriginals"))
        set(a,"bitmap",null);call(a,"navigateHistory",false);assertEquals(initial,get(a,"edit"));assertSame(originals,get(a,"protectedOriginals"))
    }
    @Test fun rejectedCommitPreservesLaterModelHistoryAndSources(){
        val a=foreground();val captured=snapshot(a);val next=initial.copy(rotation=90);val history=mutableListOf(initial);val originals=get(a,"protectedOriginals");set(a,"edit",next);set(a,"undo",history)
        assertFalse(commit(a,captured,initial.copy(rotation=180)));assertSame(next,get(a,"edit"));assertSame(history,get(a,"undo"));assertSame(originals,get(a,"protectedOriginals"))
    }
    @Test fun initialSourceLoadMayCompleteInBackgroundButControlsRemainClosed(){
        val a=fixture();set(a,"bitmap",null);set(a,"loadedScope",null);assertEquals(true,call(a,"photoLoadCurrent",scope))
        set(a,"bitmap",allocate(Bitmap::class.java));set(a,"loadedScope",scope);assertNull(call(a,"photoControlScope"));assertEquals(true,call(a,"photoLoadCurrent",scope));assertSame(initial,get(a,"edit"))
    }
}
