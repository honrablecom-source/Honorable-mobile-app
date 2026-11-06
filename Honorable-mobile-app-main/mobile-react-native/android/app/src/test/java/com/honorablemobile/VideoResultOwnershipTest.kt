package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

/** Real Activity routing predicate/release methods without Android delivery or UI. */
class VideoResultOwnershipTest {
    private val type=Class.forName("sun.misc.Unsafe");private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
    private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
    private fun activity()=allocate(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
    private fun control()=allocate(VideoTimelineCaptionControls::class.java)as VideoTimelineCaptionControls
    private fun set(a:Any,name:String,value:Any?)=a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)
    private fun get(a:Any,name:String)=a.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(a)
    private fun call(a:Any,name:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
    private fun allows(a:HonorableVideoEditorActivity,code:Int)=call(a,"allowsMediaImportResult",code)as Boolean
    private fun ticket(kind:VideoMediaImportKind)=VideoMediaImportTarget(kind,VideoEditProject(5000),"content://synthetic/video",5000,"guest",0,emptyMap())
    @Test fun lateLegacyResultsCannotEnterWhilePlaybackSubtitleControllerOwnsBusy(){
        val a=activity();val owner=control();set(a,"timelineCaptionImport",owner);set(a,"importing",true)
        for(code in 22..28)assertFalse("foreign result $code",allows(a,code))
        assertSame(owner,get(a,"timelineCaptionImport"));assertEquals(true,get(a,"importing"))
    }

    @Test fun staleMediaTicketCannotBypassCurrentPlaybackSubtitleOwner(){
        for(kind in VideoMediaImportKind.entries){val a=activity();set(a,"timelineCaptionImport",control());set(a,"mediaImportTarget",ticket(kind));set(a,"importing",true);assertFalse(allows(a,kind.requestCode));assertEquals(true,get(a,"importing"))}
    }
    @Test fun originalMediaTicketRoutingIsPreservedWithoutPlaybackSubtitleOwner(){
        for(kind in VideoMediaImportKind.entries){val a=activity();set(a,"mediaImportTarget",ticket(kind));for(code in 22..28)assertEquals("$kind/$code",code==kind.requestCode,allows(a,code));assertTrue(allows(a,99))}
    }
    @Test fun onlyCurrentControllerReleaseReopensTheLegacyResultGate(){
        val a=activity();val old=control();val current=control();set(a,"timelineCaptionImport",current);set(a,"importing",true)
        call(a,"releaseTimelineCaptionImport",old);assertFalse(allows(a,25));assertSame(current,get(a,"timelineCaptionImport"));assertEquals(true,get(a,"importing"))
        call(a,"releaseTimelineCaptionImport",current);assertTrue(allows(a,25));assertNull(get(a,"timelineCaptionImport"));assertEquals(false,get(a,"importing"))
    }
    @Test fun noOwnerAndOutOfBandResultsKeepExistingRouting(){
        val a=activity();for(code in 22..29)assertTrue(allows(a,code));set(a,"timelineCaptionImport",control());assertTrue(allows(a,VideoTimelineCaptionControls.REQUEST_CODE));for(code in listOf(0,21,30,99))assertTrue(allows(a,code))
    }
}
