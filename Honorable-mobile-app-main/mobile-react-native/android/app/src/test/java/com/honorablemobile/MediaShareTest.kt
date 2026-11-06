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
    private fun payload(items: List<Pair<String,String>>) = org.json.JSONArray().apply {
        items.forEach { (uri,kind) -> put(org.json.JSONObject().put("mediaUri",uri).put("mediaType",kind)) }
    }.toString()
    @Test fun batchPayloadBoundsSchemaAndLocalSourcesAreEnforced() {
        val valid=payload(listOf(raw to "IMAGE"))
        assertEquals(listOf(raw to "IMAGE"),MediaShare.batchSources(valid))
        for(value in listOf("[]","{}",valid+" trailing","[null]",payload(List(21){"$raw/$it" to "IMAGE"}),payload(listOf(raw to "AUDIO")),payload(listOf("https://example.test/image" to "IMAGE")),payload(listOf(raw to "IMAGE",raw to "IMAGE")))) {
            try { MediaShare.batchSources(value); fail("Expected invalid bundle refusal") } catch (_: IllegalArgumentException) {} catch (_: org.json.JSONException) {}
        }
        fails { MediaShare.batchSources(" ".repeat(170001)) }
        for(value in listOf("[{\"mediaUri\":7,\"mediaType\":\"IMAGE\"}]", "[{\"mediaUri\":\"$raw\",\"mediaType\":\"IMAGE\",\"extra\":true}]")) {
            fails { MediaShare.batchSources(value) }
        }
    }
    @Test fun mixedBatchHasOnlyOrderedSelectedStreamsAndReadOnlyGrants() {
        val items=listOf(raw to "IMAGE","$raw/video" to "VIDEO")
        val reads=mutableListOf<Uri>()
        val chooser=MediaShare.prepareBatch(items,{if(it.toString()==raw) "image/jpeg" else "video/mp4"},{reads+=it})
        val send=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        val uris=items.map{Uri.parse(it.first)}
        assertEquals(uris,reads);assertEquals(Intent.ACTION_SEND_MULTIPLE,send.action);assertEquals("*/*",send.type)
        assertEquals(uris,send.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
        for(intent in listOf(send,chooser)) {
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,intent.flags)
            assertEquals(2,intent.clipData!!.itemCount)
            for(i in uris.indices) assertEquals(uris[i],intent.clipData!!.getItemAt(i).uri)
            assertFalse(intent.hasExtra(Intent.EXTRA_TEXT));assertFalse(intent.hasExtra(Intent.EXTRA_HTML_TEXT))
        }
        assertEquals("Share 2 original files",chooser.getStringExtra(Intent.EXTRA_TITLE))
    }
    @Test fun allMimeChecksPrecedeAnyFileReadAndRejectTypeMismatch() {
        var reads=0
        fails { MediaShare.prepareBatch(listOf(raw to "IMAGE","$raw/other" to "VIDEO"),{"image/png"},{reads++}) }
        assertEquals(0,reads)
        fails { MediaShare.prepareBatch(listOf(raw to "IMAGE"),{null},{reads++}) }
        assertEquals(0,reads)
    }
    @Test fun exactAndFamilyMimeTypesAreNarrowedWithoutChangingStreams() {
        for((mimes,expected) in listOf(listOf("image/png","image/png") to "image/png",listOf("image/png","image/jpeg") to "image/*",listOf("video/mp4","video/webm") to "video/*")) {
            val kind=if(mimes.first().startsWith("image")) "IMAGE" else "VIDEO"
            val chooser=MediaShare.prepareBatch(listOf(raw to kind,"$raw/other" to kind),{if(it.toString()==raw)mimes[0] else mimes[1]},{})
            assertEquals(expected,chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.type)
        }
    }
    @Test fun unreadableMemberDoesNotReturnAnyChooser() {
        var reads=0
        fails { MediaShare.prepareBatch(listOf(raw to "IMAGE","$raw/other" to "IMAGE"),{"image/png"},{reads++;error("Synthetic access denied")}) }
        assertEquals(1,reads)
    }
    @Test fun twentyOriginalsAreRetainedAndOversizeOrDuplicatesNeverRead() {
        val items=List(20){"$raw/$it" to "IMAGE"};var reads=0
        assertEquals(items,MediaShare.batchSources(payload(items)))
        val chooser=MediaShare.prepareBatch(items,{"image/jpeg"},{reads++})
        assertEquals(20,reads);assertEquals(20,chooser.clipData!!.itemCount)
        fails { MediaShare.prepareBatch(items+items.first(),{"image/jpeg"},{reads++}) }
        fails { MediaShare.prepareBatch(listOf(items.first(),items.first()),{"image/jpeg"},{reads++}) }
        assertEquals(20,reads)
    }

}
