package app.honorable.editor

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import android.util.AtomicFile
import java.nio.file.StandardCopyOption
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PhotoProjectReadAccessTest {
    private val source="content://media/external_primary/images/media/42"
    private val synthetic="Synthetic original image bytes, never uploaded".toByteArray()
    private val edit=PhotoEdit(exposure=.25f)
    /** Plain host JVM cannot call Android FileUtils.sync/getuid. Only that write boundary is controlled;
     * production PhotoProjectStore admission/encoding/readback and real metadata files still execute.
     * This is not Android AtomicFile durability/crash recovery evidence. */
    private class HostCommitFile(private val target:File,private val failCommit:Boolean=false):AtomicFile(target){
        override fun finishWrite(stream:FileOutputStream){
            if(failCommit){stream.close();throw IOException("Synthetic host commit failure")}
            stream.fd.sync();stream.close()
            Files.move(File(target.path+".new").toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        }
        override fun failWrite(stream:FileOutputStream){stream.close();Files.deleteIfExists(File(target.path+".new").toPath())}
    }
    private fun store(root:File)=PhotoProjectStore(root,PhotoPresetNamespace.GUEST,source).also{value->
        val field=PhotoProjectStore::class.java.getDeclaredField("file").apply{isAccessible=true}
        field.set(value,HostCommitFile((field.get(value)as AtomicFile).baseFile))
    }

    private fun read(value:String=source,persisted:Boolean=false,capability:()->Boolean) = PhotoProjectReadAccess.hasExistingRead(Uri.parse(value),persisted,capability)
    @Test fun canonicalImageItemsUseCurrentNativeImageCapabilityOnly(){
        for(volume in listOf("external","external_primary","internal","1111-2222")){
            val uri="content://media/$volume/images/media/42"
            assertTrue(read(uri){true});assertFalse(read(uri){false})
        }
        assertTrue(read(source+"?requireOriginal=1"){true})
        assertTrue(read("content://documents/synthetic",persisted=true){error("Persisted grant must not consult library capability")})
    }
    @Test fun videoAudioCollectionsInvalidItemsOtherProvidersAndSchemesNeverInheritImageCapability(){
        for(uri in listOf("content://media/external/video/media/42","content://media/external/audio/media/42","content://media/external/images/media","content://media/external/images/media/42/extra","content://media/external/images/media/0","content://media/external/images/media/-1","content://media/external/images/media/+1","content://media/external/images/media/9223372036854775808","content://media/external/images/media/x","content://media/external%2Fprivate/images/media/42","content://other/external/images/media/42","content://documents/image/42","file:///synthetic.png","https://example.test/photo")){
            assertFalse(uri,read(uri){error("Not a canonical MediaStore image item")})
        }
        assertFalse(read("file:///synthetic.png",persisted=true){error("Not a content grant")})
    }
    @Test fun permissionSnapshotIsFreshAndDoesNotBecomeAStoredGrant(){
        var capability=true;var checks=0
        val current={checks++;capability}
        assertTrue(read(capability=current));capability=false;assertFalse(read(capability=current));capability=true;assertTrue(read(capability=current))
        assertEquals(3,checks)
    }
    @Test fun selectedCapabilitySavesAndRestoresRealProjectWithoutRequestingOrRevokingDocumentGrant(){
        val root=Files.createTempDirectory("photo-selected-access").toFile()
        try{
            val store=store(root)
            var taken=0;var released=0;var checked=0
            fun <T> operation(action:()->T):T=PhotoProjectAccess.withDurableRead({read{true}},{taken++;error("Native image access already exists")},{released++},{checked++},action)
            val fingerprint=PhotoProjectCodec.fingerprint(ByteArrayInputStream(synthetic))
            operation{store.save(PhotoProjectCodec.fingerprint(ByteArrayInputStream(synthetic)),edit){checked++}}
            assertEquals(edit,operation{store.load(fingerprint)})
            assertEquals(0,taken);assertEquals(0,released);assertTrue(checked>0)
            assertFalse(root.walkTopDown().filter{it.isFile}.any{String(it.readBytes(),Charsets.ISO_8859_1).contains(String(synthetic))})
        }finally{root.deleteRecursively()}
    }
    @Test fun unreadableOriginalAndCancelledSessionKeepExistingProjectAndExistingGrant(){
        val root=Files.createTempDirectory("photo-selected-unavailable").toFile()
        try{
            val store=store(root);val fingerprint=PhotoProjectCodec.fingerprint(ByteArrayInputStream(synthetic))
            store.save(fingerprint,edit){};val before=root.walkTopDown().filter{it.isFile}.associate{it.relativeTo(root).path to it.readBytes().toList()}
            var revoked=0
            val denied=runCatching{PhotoProjectAccess.withDurableRead({read{true}},{error("Existing native access")},{revoked++},{}){throw IOException("Synthetic selected original no longer readable")}}
            assertTrue(denied.exceptionOrNull()is IOException)
            var current=true
            assertTrue(runCatching{PhotoProjectAccess.withDurableRead({read{true}},{error("Existing native access")},{revoked++},{check(current)}){current=false;store.save(fingerprint,PhotoEdit(exposure=.75f)){check(current)}}}.isFailure)
            assertEquals(before,root.walkTopDown().filter{it.isFile}.associate{it.relativeTo(root).path to it.readBytes().toList()})
            assertEquals(edit,store.load(fingerprint));assertEquals(0,revoked)
        }finally{root.deleteRecursively()}
    }
    @Test fun failedControlledCommitPreservesPriorMetadataWithoutRevokingExistingAccess(){
        val root=Files.createTempDirectory("photo-selected-commit-failure").toFile()
        try{
            val store=store(root);val fingerprint=PhotoProjectCodec.fingerprint(ByteArrayInputStream(synthetic));store.save(fingerprint,edit){}
            val field=PhotoProjectStore::class.java.getDeclaredField("file").apply{isAccessible=true}
            field.set(store,HostCommitFile((field.get(store)as AtomicFile).baseFile,true))
            var revoked=0
            assertTrue(runCatching{PhotoProjectAccess.withDurableRead({read{true}},{error("Existing image access")},{revoked++},{}){store.save(fingerprint,PhotoEdit(exposure=.75f)){}}}.isFailure)
            assertEquals(edit,store.load(fingerprint));assertEquals(0,revoked);assertFalse(root.walkTopDown().any{it.name.endsWith(".new")})
        }finally{root.deleteRecursively()}
    }
    @Test fun persistedDocumentsAndNewExplicitDocumentGrantsKeepExistingTransactionRules(){
        val document="content://documents/synthetic-image";var persisted=true;var taken=0;var revoked=0
        fun <T> operation(action:()->T):T=PhotoProjectAccess.withDurableRead({read(document,persisted){error("Document must not use image capability")}}, {taken++;persisted=true},{revoked++;persisted=false},{},action)
        assertEquals("read",operation{"read"});assertEquals(0,taken)
        persisted=false;assertEquals("saved",operation{"saved"});assertEquals(1,taken);assertTrue(persisted)
        persisted=false;assertTrue(runCatching{operation{error("Synthetic failed save")}}.isFailure)
        assertEquals(2,taken);assertEquals(1,revoked);assertFalse(persisted)
    }
}
