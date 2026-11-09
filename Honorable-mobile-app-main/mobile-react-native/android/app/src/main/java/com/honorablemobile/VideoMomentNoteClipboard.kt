package com.honorablemobile

import android.content.ClipData
import android.os.PersistableBundle

/** Metadata creation only; callers obtain explicit approval before touching the clipboard service. */
internal object VideoMomentNoteClipboard {
    const val SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"
    fun create(text: String): ClipData {
        require(text.isNotBlank() && text.length <= 16000)
        return ClipData.newPlainText("Source moment notes", text).also {
            it.description.extras = PersistableBundle().apply { putBoolean(SENSITIVE_EXTRA, true) }
        }
    }
}
