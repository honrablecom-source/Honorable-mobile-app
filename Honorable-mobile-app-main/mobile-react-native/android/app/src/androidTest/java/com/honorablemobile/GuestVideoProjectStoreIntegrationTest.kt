package com.honorablemobile

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue

class GuestVideoProjectStoreIntegrationTest {
    private fun directory()=File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,"guest-project-test-${java.util.UUID.randomUUID()}")
    private fun draft()=VideoDraft(5000,listOf(VideoSegment(100,4000)),0,500,100,4000)
    @Test fun saveSurvivesStoreRecreationAndFailedCommitPreservesPriorFile() {
        val dir=directory();val uri="content://test/original"
        try {
            GuestVideoProjectStore(dir,uri).save(draft()){}
            val file=File(dir,GuestVideoProjectCodec.storageName(uri));val before=file.readBytes()
            assertEquals(draft(),GuestVideoProjectStore(dir,uri).load(5000))
            var checks=0
            assertTrue(runCatching{GuestVideoProjectStore(dir,uri).save(draft().copy(previewPositionMs=700)){if(++checks==2)error("Editor closed")}}.isFailure)
            assertArrayEquals(before,file.readBytes())
            file.writeBytes(ByteArray(GuestVideoProjectCodec.MAX_BYTES+1))
            assertTrue(runCatching{GuestVideoProjectStore(dir,uri).load(5000)}.isFailure)
        } finally {dir.deleteRecursively()}
    }
    @Test fun boundedShelfAndDeleteAllNeverTouchUnrelatedFiles() {
        val dir=directory()
        try {
            repeat(GuestVideoProjectStore.MAX_PROJECTS){GuestVideoProjectStore(dir,"content://test/$it").save(draft()){}}
            assertTrue(runCatching{GuestVideoProjectStore(dir,"content://test/extra").save(draft()){}}.isFailure)
            val unrelated=File(dir,"account-draft.bin").apply{writeText("keep")}
            GuestVideoProjectStore.deleteAll(dir){}
            assertEquals("keep",unrelated.readText())
            assertNull(GuestVideoProjectStore(dir,"content://test/0").load(5000))
        } finally {dir.deleteRecursively()}
    }
    @Test fun loggedAtomicRenameFailureIsNotReportedAsASuccessfulSave() {
        // Android 11+ AtomicFile uses a .new staging file; older platform implementations differ.
        assumeTrue(android.os.Build.VERSION.SDK_INT>=30)
        val dir=directory();val uri="content://test/rename-failure"
        try {
            val store=GuestVideoProjectStore(dir,uri);store.save(draft()){}
            val file=File(dir,GuestVideoProjectCodec.storageName(uri));val before=file.readBytes()
            var checks=0
            val result=runCatching{store.save(draft().copy(previewPositionMs=700)){
                if(++checks==2)check(File(file.path+".new").delete())
            }}
            assertTrue(result.isFailure);assertArrayEquals(before,file.readBytes())
            // A stale uncommitted staging file must also disappear when this save is deleted.
            File(file.path+".new").writeText("incomplete")
            store.delete{}
            assertFalse(file.exists());assertFalse(File(file.path+".new").exists())
        } finally {dir.deleteRecursively()}
    }
}
