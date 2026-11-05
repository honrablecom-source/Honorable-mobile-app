package com.honorablemobile

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class VideoMomentNoteStoreIntegrationTest {
    @Test fun ownerSourceShelvesAreIsolatedAndCancelledWritesPreserveNotes() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"moment-notes-${System.nanoTime()}")
        try{
            val store=VideoMomentNoteStore(root,"guest-basic","content://fixture/a",5000);val note=VideoMomentNote(1000,"First");store.add(note){}
            assertTrue(VideoMomentNoteStore(root,"account-"+"a".repeat(64),"content://fixture/a",5000).load().notes.isEmpty())
            assertTrue(VideoMomentNoteStore(root,"guest-basic","content://fixture/b",5000).load().notes.isEmpty())
            var checks=0;assertTrue(runCatching{store.add(VideoMomentNote(2000,"Cancelled")){checks++;check(checks<2)}}.isFailure)
            assertEquals(listOf(note),store.load().notes)
        }finally{root.deleteRecursively()}
    }
    @Test fun sourceDurationMismatchAndCorruptionCannotOverwritePriorNotes() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"moment-notes-invalid-${System.nanoTime()}")
        try{
            val uri="content://fixture/a";val store=VideoMomentNoteStore(root,"guest-basic",uri,5000);store.add(VideoMomentNote(1000,"Keep")){}
            assertTrue(runCatching{VideoMomentNoteStore(root,"guest-basic",uri,6000).add(VideoMomentNote(2000,"Other")){}}.isFailure)
            assertEquals("Keep",store.load().notes.single().label)
            val file=File(root,VideoMomentCodec.storageKey("guest-basic",uri)+".bin");val damaged=byteArrayOf(1,2,3);file.writeBytes(damaged)
            assertTrue(runCatching{store.add(VideoMomentNote(2000,"No overwrite")){}}.isFailure);assertArrayEquals(damaged,file.readBytes())
        }finally{root.deleteRecursively()}
    }
}
