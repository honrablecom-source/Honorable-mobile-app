package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import app.honorable.editor.EditorAccessMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp", application = Application::class, manifest = Config.NONE)
class PhotoVideoStoryboardControlsTest {
    @Test
    fun pickerReorderAndCustomHoldsSurviveActivityRecreation() {
        val guest = Intent().putExtra(EditorAccessMode.EXTRA, EditorAccessMode.GUEST_BASIC.name)
        val off = Uri.parse("content://noble.story.fixture/lamp-off.jpg")
        val switch = Uri.parse("content://noble.story.fixture/hand-on-switch.jpg")
        val on = Uri.parse("content://noble.story.fixture/lamp-on.jpg")
        Robolectric.buildActivity(PhotoVideoCreatorActivity::class.java, guest).setup().use { controller ->
            val activity = controller.get()
            assertFalse(button(activity, "Create editable video project").isEnabled)
            assertTrue(button(activity, "Choose photos").performClick())
            val launch = shadowOf(activity).peekNextStartedActivityForResult()
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, launch.intent.action)
            assertEquals("image/*", launch.intent.type)
            assertTrue(launch.intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
            assertTrue(launch.intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))

            val result = Intent().apply {
                clipData = ClipData.newRawUri("Story photos", switch).apply {
                    addItem(ClipData.Item(off))
                    addItem(ClipData.Item(on))
                }
            }
            shadowOf(activity).receiveResult(launch.intent, Activity.RESULT_OK, result)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(button(activity, "Create editable video project").isEnabled)

            val earlier = views(activity.window.decorView).filterIsInstance<Button>()
                .single { it.contentDescription?.startsWith("Earlier photo 2:") == true }
            assertTrue(earlier.performClick())
            for ((index, seconds) in listOf(2, 3, 4).withIndex()) {
                val hold = holdButton(activity, index + 1)
                assertTrue(hold.performClick())
                shadowOf(Looper.getMainLooper()).idle()
                val dialog: AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
                val field = views(dialog.window!!.decorView).filterIsInstance<EditText>().single()
                field.setText(seconds.toString())
                assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick())
                shadowOf(Looper.getMainLooper()).idle()
                assertFalse(dialog.isShowing)
                assertTrue(holdButton(activity, index + 1).contentDescription.endsWith("Current hold: $seconds seconds."))
            }
            assertStoryboard(activity)
            controller.recreate()
            val restored = controller.get()
            assertStoryboard(restored)
            assertFalse(button(restored, "Undo photo edit").isEnabled)

            val state = Bundle()
            controller.saveInstanceState(state)
            assertEquals(listOf(off.toString(), switch.toString(), on.toString()), state.getStringArrayList("photos"))
            assertArrayEquals(longArrayOf(2000, 3000, 4000), state.getLongArray("photoHolds"))
        }
    }

    private fun assertStoryboard(activity: Activity) {
        val labels = views(activity.window.decorView).filterIsInstance<TextView>()
            .map { it.text.toString() }.filter { it.contains("\nStarts at ") }.toList()
        assertEquals(listOf("Starts at 0 s", "Starts at 2 s", "Starts at 5 s"), labels.map { it.substringAfter('\n') })
        assertTrue(button(activity, "Create editable video project").isEnabled)
    }

    private fun button(activity: Activity, text: String) =
        views(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString() == text }

    private fun holdButton(activity: Activity, position: Int) =
        views(activity.window.decorView).filterIsInstance<Button>()
            .single { it.contentDescription?.startsWith("Set photo $position hold:") == true }

    private fun views(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) yieldAll(views(root.getChildAt(index)))
        }
    }
}
