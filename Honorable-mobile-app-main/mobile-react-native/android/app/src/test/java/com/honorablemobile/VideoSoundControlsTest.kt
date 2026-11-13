package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=android.app.Application::class)
class VideoSoundControlsTest {
    private val fixtures=mutableListOf<Fixture>()
    @After fun cleanup(){for(f in fixtures){f.control.close();if(f.job!=null)f.finish();f.controller.pause().stop().destroy()};shadowOf(Looper.getMainLooper()).idle()}
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
    private fun dialog():AlertDialog{shadowOf(Looper.getMainLooper()).idle();return ShadowAlertDialog.getLatestAlertDialog().also{assertTrue(it.isShowing)}}
    private fun checks(d:AlertDialog)=views(d.window!!.decorView).filterIsInstance<CheckBox>()
    private fun positive(d:AlertDialog){d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()}
    private fun chooseFirst(){val chooser=dialog();views(chooser.window!!.decorView).filterIsInstance<RadioButton>().single{it.contentDescription=="Interval 1: Dog"}.performClick();positive(chooser)}
    private inner class Fixture {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val activity=controller.get()
        var current=true;var releases=0;var commits=0;var calls=0;var applied:List<VideoCaption>?=null;var job:(()->Unit)?=null
        val messages=mutableListOf<String>()
        val control=VideoSoundControls(activity,Uri.parse("content://synthetic/video"),1000,2000,5000,listOf(VideoCaption(1000,1200,"Old caption")),
            {current},{applied=it},{commits++},{releases++},{messages.add(it)},
            work={download,stop->calls++;assertTrue(download);stop.check();val labels=List(521){if(it==7)"Dog" else "Sound $it"};VideoSoundEvents.propose(Array(2){FloatArray(521).apply{this[7]=.8f;this[8]=.2f;this[9]=.1f}},16000,1000,labels)},launch={job=it})
        init{fixtures.add(this);java.io.File(activity.filesDir,"optional-yamnet-sounds").deleteRecursively()}
        fun start(){control.open();shadowOf(Looper.getMainLooper()).idle();val setup=dialog();checks(setup).single().isChecked=true;positive(setup)}
        fun finish(){val pending=requireNotNull(job);job=null;pending.invoke();shadowOf(Looper.getMainLooper()).idle()}
    }
    @Test fun downloadConsentStartsUncheckedAndRefusesWorkerWithoutIt(){
        val f=Fixture();f.control.open();val setup=dialog();assertFalse(checks(setup).single().isChecked);positive(setup)
        assertNull(f.job);assertEquals(0,f.calls);assertTrue(setup.isShowing);assertNotNull(checks(setup).single().error)
    }
    @Test fun everyIntervalStartsSkipAndRequiresAnExplicitChoice(){
        val f=Fixture();f.start();f.finish();val chooser=dialog();val all=views(chooser.window!!.decorView)
        val groups=all.filterIsInstance<RadioGroup>();assertEquals(2,groups.size)
        assertTrue(groups.all{it.findViewById<RadioButton>(it.checkedRadioButtonId).text.startsWith("Skip interval")})
        assertEquals(8,all.filterIsInstance<RadioButton>().size);positive(chooser);assertSame(chooser,dialog());assertNull(f.applied)
        chooseFirst();assertEquals(1,views(dialog().window!!.decorView).filterIsInstance<EditText>().size)
    }
    @Test fun actualEditableSrtFullReviewUncheckedConfirmationAndSingleApply(){
        val f=Fixture();f.start();f.finish();chooseFirst();val editor=dialog();val text=views(editor.window!!.decorView).filterIsInstance<EditText>().single()
        assertTrue(text.text.toString().contains("[Dog]"));text.setText("1\n00:00:01,100 --> 00:00:01,400\n[Dog barking]\n")
        positive(editor);val review=dialog();assertFalse(checks(review).single().isChecked);positive(review);assertNull(f.applied);assertTrue(review.isShowing)
        checks(review).single().isChecked=true;positive(review)
        assertEquals(listOf(VideoCaption(1100,1400,"[Dog barking]")),f.applied);assertEquals(1,f.commits);assertEquals(1,f.releases)
        positive(review);f.control.close();assertEquals(1,f.commits);assertEquals(1,f.releases)
    }
    @Test fun outsideAnalyzedRangeAndBadSrtCannotReachReview(){
        val f=Fixture();f.start();f.finish();chooseFirst();val editor=dialog();val text=views(editor.window!!.decorView).filterIsInstance<EditText>().single()
        for(value in listOf("bad SRT","1\n00:00:00,900 --> 00:00:01,500\nOutside\n","1\n00:00:01,100 --> 00:00:02,100\nOutside\n")){
            text.setText(value);positive(editor);assertSame(editor,dialog());assertNotNull(text.error);assertNull(f.applied)
        }
    }
    @Test fun stoppedWorkerRetainsLeaseUntilSettlementAndCannotPublishLate(){
        val f=Fixture();f.start();f.control.close();f.control.close();assertEquals(0,f.releases)
        f.finish();assertEquals(1,f.releases);assertEquals(0,f.commits);assertNull(f.applied);assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
    }
    @Test fun changedOwnerBeforeApplyCannotMutate(){
        val f=Fixture();f.start();f.finish();chooseFirst();positive(dialog());val review=dialog();checks(review).single().isChecked=true
        f.current=false;positive(review);assertNull(f.applied);assertEquals(0,f.commits);assertEquals(1,f.releases)
    }
    @Test fun dismissedCorrectionAndReviewAreTerminal(){
        for(review in listOf(false,true)){val f=Fixture();f.start();f.finish();chooseFirst();if(review)positive(dialog());dialog().dismiss();shadowOf(Looper.getMainLooper()).idle();assertEquals(1,f.releases);assertNull(f.applied)}
    }
}
