package com.honorablemobile

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.PhotoProjectCodec
import java.io.File
import java.io.FileInputStream
import org.junit.Assert.*
import org.junit.Test

/** Synthetic storage bytes only; these tests do not claim Media3 encoding or playback. */
class PhotoVideoProjectStoreIntegrationTest {
    @Test fun privateProviderOwnershipAndIntegritySurviveStoreRecreation(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=PhotoVideoProjectStore(context)
        val staging=store.begin("Storage test ${java.util.UUID.randomUUID().toString().take(8)}",PhotoVideoFormat.SQUARE){}
        var committed=false
        try {
            val bytes="synthetic persistence fixture, not encoded video".toByteArray()
            File(staging.directory,PhotoVideoProjectCodec.filename(0)).writeBytes(bytes)
            val digest=PhotoProjectCodec.fingerprint(bytes.inputStream()){}
            val project=store.commit(staging,listOf(PhotoVideoAsset(3000,digest))){};committed=true
            assertEquals(project,PhotoVideoProjectStore(context).load(project.id))
            val uri=store.sourceUri(project.id,0);assertTrue(store.ownsReadableSource(uri))
            assertFalse(store.ownsReadableSource(Uri.parse(uri.toString().replace(".creation/",".other/"))))
            assertFalse(store.ownsReadableSource(Uri.parse(uri.toString().replace("clip-00.mp4","clip-20.mp4"))))
            context.contentResolver.openInputStream(uri).use{assertArrayEquals(bytes,requireNotNull(it).readBytes())}
            store.sourceFile(project.id,0).appendText("damage")
            assertTrue(runCatching{store.load(project.id)}.isFailure)
            assertTrue(store.list().any{it.id==project.id})
        }finally{if(committed)store.delete(staging.id){}else store.abandon(staging)}
    }
    @Test fun cancellationAbandonsOnlyOwnedStaging(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val store=PhotoVideoProjectStore(context)
        val staging=store.begin("Cancel test ${java.util.UUID.randomUUID().toString().take(8)}",PhotoVideoFormat.PORTRAIT){}
        try {
            val file=File(staging.directory,PhotoVideoProjectCodec.filename(0)).apply{writeText("storage fixture")}
            val digest=FileInputStream(file).use{PhotoProjectCodec.fingerprint(it){}}
            assertTrue(runCatching{store.commit(staging,listOf(PhotoVideoAsset(5000,digest))){error("Creator closed")}}.isFailure)
            assertFalse(store.list().any{it.id==staging.id})
            assertTrue(runCatching{store.sourceFile("../outside",0)}.isFailure)
        }finally{store.abandon(staging)}
        assertFalse(staging.directory.exists())
    }
    @Test fun renameKeepsPreparedBytesSourceUrisAndSavedGuestTimeline(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val store=PhotoVideoProjectStore(context)
        val token=java.util.UUID.randomUUID().toString().take(8)
        val staging=store.begin("Rename source $token",PhotoVideoFormat.SQUARE){}
        val guestDirectory=File(context.cacheDir,"rename-guest-test-"+staging.id)
        var committed=false
        try{
            val bytes="synthetic rename source; not encoded video".toByteArray()
            File(staging.directory,PhotoVideoProjectCodec.filename(0)).writeBytes(bytes)
            val project=store.commit(staging,listOf(PhotoVideoAsset(3000,PhotoProjectCodec.fingerprint(bytes.inputStream()){}))){};committed=true
            val draft=project.initialDraft().let{it.copy(segments=it.segments.map{clip->clip.copy(title="Saved edit")})}
            GuestVideoProjectStore(guestDirectory,project.source(0)).save(draft){}
            val renamed=store.rename(project.id,project.name,"Renamed story $token"){}
            assertEquals(project.id,renamed.id);assertEquals(project.authority,renamed.authority)
            assertEquals(project.format,renamed.format);assertEquals(project.assets,renamed.assets)
            assertEquals(project.source(0),renamed.source(0))
            assertEquals(renamed,PhotoVideoProjectStore(context).load(project.id))
            assertArrayEquals(bytes,store.sourceFile(project.id,0).readBytes())
            assertEquals(draft,GuestVideoProjectStore(guestDirectory,renamed.source(0)).load(3000))
            assertTrue(store.list().any{it.id==project.id&&it.name==renamed.name&&it.readable})
        }finally{guestDirectory.deleteRecursively();if(committed)store.delete(staging.id){}else store.abandon(staging)}
    }
    @Test fun invalidDuplicateStaleAndCancelledRenamePreserveExistingMetadata(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val store=PhotoVideoProjectStore(context)
        val token=java.util.UUID.randomUUID().toString().take(8);val owned=mutableListOf<String>()
        fun fixture(name:String):PhotoVideoProject{
            val staging=store.begin(name,PhotoVideoFormat.PORTRAIT){}
            try{
                val bytes="synthetic rename rejection fixture".toByteArray()
                File(staging.directory,PhotoVideoProjectCodec.filename(0)).writeBytes(bytes)
                return store.commit(staging,listOf(PhotoVideoAsset(3000,PhotoProjectCodec.fingerprint(bytes.inputStream()){}))){}.also{owned.add(it.id)}
            }catch(failure:Throwable){store.abandon(staging);throw failure}
        }
        try{
            val first=fixture("First rename $token");val second=fixture("Second rename $token")
            val metadata=File(store.sourceFile(first.id,0).parentFile,"project.bin");val before=metadata.readBytes()
            assertTrue(runCatching{store.rename(first.id,first.name," ") {}}.isFailure)
            assertTrue(runCatching{store.rename(first.id,first.name,second.name.uppercase(java.util.Locale.ROOT)) {}}.isFailure)
            assertTrue(runCatching{store.rename(first.id,"Stale name","New story $token") {}}.isFailure)
            var checks=0
            assertTrue(runCatching{store.rename(first.id,first.name,"Cancelled $token"){check(++checks<3){"Creator left after metadata write"}}}.isFailure)
            assertEquals(3,checks);assertArrayEquals(before,metadata.readBytes())
            assertEquals(first,store.load(first.id));assertEquals(second,store.load(second.id))
        }finally{owned.forEach{store.delete(it){}}}
    }
}
