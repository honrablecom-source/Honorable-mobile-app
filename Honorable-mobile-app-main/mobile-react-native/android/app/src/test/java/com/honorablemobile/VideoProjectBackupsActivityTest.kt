package com.honorablemobile

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import app.honorable.editor.EditorAccessMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Actual new Activity and Android widgets with synthetic/no media; not a physical-device UI proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class VideoProjectBackupsActivityTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun choose(activity: VideoProjectBackupsActivity): Button? =
        descendants(activity.window.decorView).filterIsInstance<Button>().firstOrNull { it.text.toString() == "Choose Noble video backup ZIP" }
    private fun awaitHome(activity: VideoProjectBackupsActivity, previous: Button? = null): Button {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            choose(activity)?.takeIf { it !== previous }?.let { return it }
            Thread.sleep(20)
        }
        error("Synthetic backup home did not complete within 30 seconds")
    }
    private fun guestIntent() = Intent().putExtra(EditorAccessMode.EXTRA, EditorAccessMode.GUEST_BASIC.name)
    @Test fun missingOrAccountAccessCannotOpenTheGuestBackupScreen() {
        for (intent in listOf(Intent(), Intent().putExtra(EditorAccessMode.EXTRA, EditorAccessMode.ACCOUNT.name))) {
            val controller = Robolectric.buildActivity(VideoProjectBackupsActivity::class.java, intent).create()
            try { assertTrue(controller.get().isFinishing); assertNull(choose(controller.get())) } finally { controller.destroy() }
        }
    }
    @Test fun homeButtonsFromBeforePauseCannotLaunchAgainstTheResumedScreen() {
        val controller = Robolectric.buildActivity(VideoProjectBackupsActivity::class.java, guestIntent()).setup()
        try {
            val activity = controller.get(); val previous = awaitHome(activity)
            controller.pause(); assertTrue(previous.performClick()); assertNull(shadowOf(activity).nextStartedActivityForResult)
            controller.resume(); val current = awaitHome(activity, previous)
            assertTrue(previous.performClick()); assertNull(shadowOf(activity).nextStartedActivityForResult)
            assertTrue(current.performClick()); val picker = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.intent.action); assertEquals("application/zip", picker.intent.type)
            assertEquals(7301, picker.requestCode); assertTrue(picker.intent.hasCategory(Intent.CATEGORY_OPENABLE))
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, picker.intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun repeatedChoosePressesCannotStartMultipleDocumentPickers() {
        val controller = Robolectric.buildActivity(VideoProjectBackupsActivity::class.java, guestIntent()).setup()
        try {
            val activity = controller.get(); val button = awaitHome(activity)
            assertTrue(button.performClick()); assertNotNull(shadowOf(activity).nextStartedActivityForResult)
            repeat(3) { button.performClick() }; assertNull(shadowOf(activity).nextStartedActivityForResult)
        } finally { controller.pause().stop().destroy() }
    }
}
