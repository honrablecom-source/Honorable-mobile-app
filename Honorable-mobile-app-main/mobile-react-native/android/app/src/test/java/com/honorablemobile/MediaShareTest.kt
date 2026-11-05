package com.honorablemobile

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

class MediaShareTest {
    private val raw = "content://synthetic.provider/media/selected"
    private fun fails(action: () -> Unit) {
        try { action(); fail("Expected refusal") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
    @Test fun rejectsRemoteFileMissingAndCredentialUris() {
        for (value in listOf("", "https://example.test/photo", "file:///private/photo", "content:///photo", "content://provider", "content://name:secret@provider/photo", "$raw#part")) {
            fails { MediaShare.source(value, "IMAGE") }
        }
        fails { MediaShare.source(raw, "AUDIO") }
        fails { MediaShare.source("content://provider/" + "x".repeat(8192), "IMAGE") }
    }
    @Test fun streamAndClipContainOnlyTheSelectedUriWithTemporaryReadPermission() {
        for ((kind, mime) in listOf("IMAGE" to "image/png", "VIDEO" to "video/mp4")) {
            val intent = MediaShare.send(Uri.parse(raw), kind, mime)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals(mime, intent.type)
            assertEquals(Uri.parse(raw), intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals(1, intent.clipData!!.itemCount)
            assertEquals(Uri.parse(raw), intent.clipData!!.getItemAt(0).uri)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
            assertFalse(intent.hasExtra(Intent.EXTRA_TEXT))
            assertFalse(intent.hasExtra(Intent.EXTRA_HTML_TEXT))
        }
    }
    @Test fun mismatchedUnknownAndWildcardTypesRefuseBeforeRead() {
        for (mime in listOf("video/mp4", "application/octet-stream", "image/*", "image/", "image/png\n", "image/p ng")) {
            var reads = 0
            fails { MediaShare.prepare(raw, "IMAGE", { mime }, { reads++ }) }
            assertEquals(0, reads)
        }
        var reads = 0
        fails { MediaShare.prepare(raw, "IMAGE", { null }, { reads++ }) }
        assertEquals(0, reads)
    }
    @Test fun chooserCarriesTheSameStreamAndReadOnlyGrantAfterSuccessfulRead() {
        val read = mutableListOf<Uri>()
        val chooser = MediaShare.prepare(raw, "IMAGE", { "image/jpeg" }, { read += it })
        assertEquals(listOf(Uri.parse(raw)), read)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Uri.parse(raw), send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(Uri.parse(raw), chooser.clipData!!.getItemAt(0).uri)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, chooser.flags)
        assertEquals("Share original photo", chooser.getStringExtra(Intent.EXTRA_TITLE))
    }
    @Test fun unreadableProviderDoesNotReturnAChooser() {
        var calls = 0
        fails { MediaShare.prepare(raw, "VIDEO", { "video/mp4" }, { calls++; error("Synthetic read denied") }) }
        assertEquals(1, calls)
    }
}
