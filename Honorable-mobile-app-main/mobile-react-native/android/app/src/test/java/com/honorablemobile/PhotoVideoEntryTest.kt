package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class PhotoVideoEntryTest {
    private val first="content://synthetic/photos/one"
    private val second="content://synthetic/photos/two"
    private val component=ComponentName("synthetic.app","synthetic.app.Creator")
    private fun payload(items:List<Pair<String,String>>) = JSONArray().apply { items.forEach { (uri,kind)->put(JSONObject().put("mediaUri",uri).put("mediaType",kind)) } }.toString()
    private fun incoming(vararg values:String) = Intent().putStringArrayListExtra(PhotoVideoEntry.EXTRA_PHOTOS,ArrayList(values.toList()))
    private fun refuses(action:()->Unit) {try{action();fail("Expected refusal")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    @Test fun explicitInternalIntentCarriesOnlyExactOrderedPhotoReferences() {
        val reads=mutableListOf<Uri>()
        val launch=PhotoVideoEntry.prepare(payload(listOf(second to "IMAGE",first to "IMAGE")),component,{"image/jpeg"},{reads+=it})
        assertEquals(component,launch.component);assertNull(launch.action)
        assertEquals(listOf(second,first),launch.getStringArrayListExtra(PhotoVideoEntry.EXTRA_PHOTOS))
        assertEquals(listOf(Uri.parse(second),Uri.parse(first)),reads)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,launch.flags)
        assertEquals(2,launch.clipData!!.itemCount)
        assertEquals(Uri.parse(second),launch.clipData!!.getItemAt(0).uri)
        assertFalse(launch.hasExtra(Intent.EXTRA_STREAM));assertFalse(launch.hasExtra(Intent.EXTRA_TEXT))
        assertEquals(listOf(second,first),PhotoVideoEntry.initial(null,launch).items)
    }
    @Test fun mixedOrInvalidSelectionsAreRefusedBeforeProviderMetadataAndReads() {
        var metadata=0;var reads=0
        for(items in listOf(emptyList(),listOf(first to "VIDEO"),listOf(first to "IMAGE",second to "VIDEO"),listOf(first to "IMAGE",first to "IMAGE"),List(21){"$first/$it" to "IMAGE"},listOf("https://example.test/photo" to "IMAGE"),listOf(("content://synthetic/"+"x".repeat(2100)) to "IMAGE"))) {
            refuses { PhotoVideoEntry.prepare(payload(items),component,{metadata++;"image/jpeg"},{reads++}) }
        }
        assertEquals(0,metadata);assertEquals(0,reads)
    }
    @Test fun allImageMimeValidationPrecedesAnyFileRead() {
        var reads=0
        refuses { PhotoVideoEntry.prepare(payload(listOf(first to "IMAGE",second to "IMAGE")),component,{if(it.toString()==first) "image/png" else "video/mp4"},{reads++}) }
        assertEquals(0,reads)
    }
    @Test fun inaccessiblePhotoCannotReturnAReviewIntent() {
        var reads=0
        refuses { PhotoVideoEntry.prepare(payload(listOf(first to "IMAGE")),component,{"image/jpeg"},{reads++;error("Synthetic denied")}) }
        assertEquals(1,reads)
    }
    @Test fun recreatedSelectionAndHoldsOverrideTheInitialLibraryIntent() {
        val state=Bundle().apply { putStringArrayList("photos",arrayListOf(second));putLongArray("photoHolds",longArrayOf(8000)) }
        val restored=PhotoVideoEntry.initial(state,incoming(first,second))
        assertEquals(listOf(second),restored.items);assertEquals(listOf(8000L),restored.durations(5000))
    }
    @Test fun intentionallyEmptyOrMalformedSavedStateNeverReplaysIncomingPhotos() {
        assertTrue(PhotoVideoEntry.initial(Bundle(),incoming(first)).items.isEmpty())
        val state=Bundle().apply { putStringArrayList("photos",arrayListOf(second));putLongArray("photoHolds",longArrayOf(8000,3000)) }
        refuses {PhotoVideoEntry.initial(state,incoming(first))}
        assertTrue(PhotoVideoEntry.initial(null,Intent()).items.isEmpty())
    }
    @Test fun twentyPhotosKeepTheirOrderWithoutStartingAnyPreparation() {
        val photos=List(20){"$first/$it"};var reads=0
        val intent=PhotoVideoEntry.prepare(payload(photos.map{it to "IMAGE"}),component,{"image/png"},{reads++})
        assertEquals(20,reads);assertEquals(photos,PhotoVideoEntry.initial(null,intent).items)
        assertEquals(20,intent.clipData!!.itemCount)
    }
}
