package com.honorablemobile

import android.net.Uri
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.PhotoLook
import org.junit.Assert.*
import org.junit.Test

/** Metadata-only admission checks; no model inference, Android widgets or media decoding. */
class VideoAiEntryAdmissionTest {
 private val unsafe=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)as sun.misc.Unsafe
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun get(a:Any,n:String)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun activity(mode:EditorAccessMode=EditorAccessMode.GUEST_BASIC)=(unsafe.allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity).also{set(it,"project",VideoEditProject(5000));set(it,"source",Uri.parse("content://synthetic/video"));set(it,"primaryDurationMs",5000L);set(it,"accessMode",mode);set(it,"workspaceActive",true);set(it,"importedSources",linkedMapOf<String,VideoSource>())}
 private val flags=listOf("pause","saving","analysis","draft","import","range","bytes","blocked","checking","project","source","destroyed","finishing")
 private fun block(a:HonorableVideoEditorActivity,flag:String){when(flag){"pause"->set(a,"workspaceActive",false);"saving"->set(a,"saving",true);"analysis"->set(a,"analyzing",true);"draft"->set(a,"draftBusy",true);"import"->set(a,"importing",true);"range"->set(a,"pendingRange",0L to 1000L);"bytes"->set(a,"recoveryBytes",byteArrayOf(1));"blocked"->set(a,"recoveryBlocked",true);"checking"->set(a,"recoveryChecking",true);"project"->set(a,"project",null);"source"->set(a,"source",null);else->android.app.Activity::class.java.getDeclaredField(if(flag=="destroyed")"mDestroyed"else"mFinished").apply{isAccessible=true}.setBoolean(a,true)}}
 @Test fun currentAccountAndGuestRetainLocalAiPermissionWithoutSideEffects(){for(mode in EditorAccessMode.values()){val a=activity(mode);val p=get(a,"project")as VideoEditProject;val before=p.segments;assertEquals(true,call(a,"allowOnDeviceAi"));assertSame(before,p.segments);assertFalse(p.canUndo);assertEquals(false,get(a,"analyzing"));assertEquals(0L,get(a,"captionReviewGeneration"));assertNull(get(a,"status"))}}
 @Test fun blockedSessionsRefuseBeforeStatusWidgetsForBothModes(){for(mode in EditorAccessMode.values())for(flag in flags){val a=activity(mode);block(a,flag);assertEquals("$mode/$flag",false,call(a,"allowOnDeviceAi"));assertNull(get(a,"status"));assertEquals(0L,get(a,"captionReviewGeneration"))}}
 @Test fun blockedConcreteEntryPointsNeverTouchWidgetsOrBeginAnalysis(){for(name in listOf("suggestScene","videoLookOptions","roughCutOptions","suggestVideoLook","createRoughCut"))for(flag in flags){val a=activity();val p=get(a,"project")as VideoEditProject;val before=p.segments;block(a,flag);when(name){"suggestVideoLook"->call(a,name,PhotoLook.values().first());"createRoughCut"->call(a,name,15000L,RoughCutFocus.values().first());else->call(a,name)};assertSame("$name/$flag",before,p.segments);assertFalse(p.canUndo);assertNull(get(a,"status"));assertEquals(flag=="analysis",get(a,"analyzing"));assertEquals(0L,get(a,"captionReviewGeneration"))}}
 @Test fun refusedAdmissionPreservesPendingReviewAndCancellationState(){val a=activity();val lease=VideoActionReview();val stage=Any();lease.open(stage);set(a,"momentReviewRequest",lease);val cancellation=java.util.concurrent.atomic.AtomicBoolean(true);set(a,"analysisCancellation",cancellation);set(a,"recoveryBlocked",true);assertEquals(false,call(a,"allowOnDeviceAi"));assertTrue(lease.owns(stage));assertSame(lease,get(a,"momentReviewRequest"));assertTrue(cancellation.get());assertEquals(0L,get(a,"captionReviewGeneration"))}
 @Test fun admissionReturnsOnlyAfterForegroundAndRecoveryAreRestored(){val a=activity();set(a,"workspaceActive",false);set(a,"recoveryChecking",true);assertEquals(false,call(a,"allowOnDeviceAi"));set(a,"workspaceActive",true);assertEquals(false,call(a,"allowOnDeviceAi"));set(a,"recoveryChecking",false);assertEquals(true,call(a,"allowOnDeviceAi"));assertFalse((get(a,"project")as VideoEditProject).canUndo);assertNull(get(a,"status"))}
}
