package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
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
class VideoSpeechControlsTest {
    private val fixtures=mutableListOf<Fixture>()
    @After fun cleanup(){for(f in fixtures){f.control.close();if(f.job!=null)f.finish();f.controller.pause().stop().destroy()};shadowOf(Looper.getMainLooper()).idle()}
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
    private fun dialog():AlertDialog{shadowOf(Looper.getMainLooper()).idle();return ShadowAlertDialog.getLatestAlertDialog().also{assertTrue(it.isShowing)}}
    private fun checks(d:AlertDialog)=views(d.window!!.decorView).filterIsInstance<CheckBox>()
    private fun positive(d:AlertDialog){d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()}
    private inner class Fixture {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val activity=controller.get()
        var current=true;var releases=0;var commits=0;var calls=0;var applied:List<VideoCaption>?=null;var job:(()->Unit)?=null
        val messages=mutableListOf<String>()
        val cues=listOf(VideoCaption(1000,1450,"HELLO"),VideoCaption(1600,1950,"NOBLE"))
        val control=VideoSpeechControls(activity,Uri.parse("content://synthetic/video"),1000,2000,5000,3,
            {current},{applied=it},{commits++},{releases++},{messages.add(it)},
            work={download,stop->calls++;assertTrue(download);stop.check();VideoSpeechCaptions.Draft(emptyList(),cues)},launch={job=it})
        init{fixtures.add(this)}
        fun start(){control.open();shadowOf(Looper.getMainLooper()).idle();val setup=dialog();checks(setup).single().isChecked=true;setup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()}
        fun finish(){val pending=requireNotNull(job);job=null;pending.invoke();shadowOf(Looper.getMainLooper()).idle()}
    }
    @Test fun noDownloadOrWorkerBeforeExplicitUncheckedConsent() {
        val f=Fixture();f.control.open();val setup=dialog();assertFalse(checks(setup).single().isChecked)
        positive(setup);assertNull(f.job);assertEquals(0,f.calls);assertTrue(setup.isShowing)
        f.control.close();assertEquals(1,f.releases)
    }
    @Test fun actualEditableSrtFullReviewUncheckedConfirmationAndSingleApply() {
        val f=Fixture();f.start();assertNull(f.applied);f.finish()
        val editor=dialog();val text=views(editor.window!!.decorView).filterIsInstance<EditText>().single()
        text.setText("1\n00:00:01,100 --> 00:00:01,800\nCorrected words\n")
        positive(editor);val review=dialog();assertFalse(checks(review).single().isChecked)
        positive(review);assertNull(f.applied);assertTrue(review.isShowing)
        checks(review).single().isChecked=true;positive(review)
        assertEquals(listOf(VideoCaption(1100,1800,"Corrected words")),f.applied);assertEquals(1,f.commits);assertEquals(1,f.releases)
        positive(review);f.control.close();assertEquals(1,f.commits);assertEquals(1,f.releases)
    }
    @Test fun badOrOutsideSourceSrtCannotReachReview() {
        val f=Fixture();f.start();f.finish();val editor=dialog();val text=views(editor.window!!.decorView).filterIsInstance<EditText>().single()
        text.setText("1\n00:00:00,100 --> 00:00:01,800\nOutside\n");positive(editor)
        assertSame(editor,dialog());assertNotNull(text.error);assertNull(f.applied);f.control.close()
    }
    @Test fun stoppedWorkerKeepsLeaseUntilSettlementAndCannotPublishLateDraft() {
        val f=Fixture();f.start();f.control.close();f.control.close();assertEquals(0,f.releases)
        f.finish();assertEquals(1,f.releases);assertEquals(0,f.commits);assertNull(f.applied)
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
    }
    @Test fun changedOwnerAfterInferenceAndBeforeApplyCannotMutate() {
        val f=Fixture();f.start();f.finish();positive(dialog());val review=dialog();checks(review).single().isChecked=true
        f.current=false;positive(review);assertNull(f.applied);assertEquals(0,f.commits);assertEquals(1,f.releases)
    }
    @Test fun dismissedCorrectionAndReviewAreTerminal() {
        for(review in listOf(false,true)) {
            val f=Fixture();f.start();f.finish();if(review)positive(dialog())
            dialog().dismiss();shadowOf(Looper.getMainLooper()).idle();assertEquals(1,f.releases);assertNull(f.applied)
        }
    }
}
