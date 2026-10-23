package com.honorablemobile

import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.PhotoEditorActivity
import org.junit.Assert.assertFalse
import org.junit.Test

class GuestEditorIntegrationTest {
    @Test fun editorModesCannotBeInjectedByAnExternalActivityLaunch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for(editor in listOf(PhotoEditorActivity::class.java,HonorableVideoEditorActivity::class.java)) {
            val info = context.packageManager.getActivityInfo(ComponentName(context,editor),0)
            assertFalse("Guest/account editor mode must remain internal",info.exported)
        }
    }
}
