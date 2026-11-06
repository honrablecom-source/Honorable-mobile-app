package com.honorablemobile

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Selection-only entry: preparation/encoding still requires the creator's explicit Create action. */
internal object PhotoVideoEntry {
    const val EXTRA_PHOTOS = "com.honorablemobile.LIBRARY_PHOTOS"

    fun prepare(payload: String, component: ComponentName, mimeOf: (Uri) -> String?, read: (Uri) -> Unit): Intent {
        val records = MediaShare.batchSources(payload)
        require(records.all { it.second == "IMAGE" }) { "Choose photos only to make a video" }
        val selection = PhotoVideoSelection.of(records.map { it.first })
        val uris = selection.items.map { raw ->
            val uri = MediaShare.source(raw, "IMAGE")
            val mime = mimeOf(uri) ?: error("A selected photo's type is unavailable")
            MediaShare.send(uri, "IMAGE", mime) // Reuse exact existing MIME/type validation; never launch it.
            uri
        }
        uris.forEach(read)
        val clips = ClipData.newRawUri("Selected library photos", uris.first())
        uris.drop(1).forEach { clips.addItem(ClipData.Item(it)) }
        return Intent().apply {
            this.component = component
            putStringArrayListExtra(EXTRA_PHOTOS, ArrayList(selection.items))
            clipData = clips
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Saved state is authoritative, including an explicitly empty or malformed saved selection. */
    fun initial(state: Bundle?, incoming: Intent): PhotoVideoSelection = if (state != null) {
        PhotoVideoSelection.restore(state.getStringArrayList("photos") ?: emptyList(), state.getLongArray("photoHolds"))
    } else {
        PhotoVideoSelection.of(incoming.getStringArrayListExtra(EXTRA_PHOTOS) ?: emptyList())
    }
}
