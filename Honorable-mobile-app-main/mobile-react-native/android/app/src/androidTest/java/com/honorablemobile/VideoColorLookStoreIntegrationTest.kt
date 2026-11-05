package com.honorablemobile

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class VideoColorLookStoreIntegrationTest {
    @Test fun ownerShelvesAreIsolatedAndCancelledWritesRetainPriorData() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"video-looks-test-${System.nanoTime()}")
        try {
            val guest=VideoColorLookStore(root,"guest-basic");val account=VideoColorLookStore(root,"account-"+"a".repeat(64))
            val look=NamedVideoColorLook("First",VideoGrade(exposure=.5f));guest.add(look){}
            assertTrue(account.load().looks.isEmpty())
            var checks=0
            assertTrue(runCatching{guest.add(look.copy(name="Cancelled")){checks++;check(checks<2)}}.isFailure)
            assertEquals(listOf(look),guest.load().looks)
        } finally { root.deleteRecursively() }
    }
    @Test fun corruptShelfCannotBeOverwrittenAsEmpty() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"video-looks-corrupt-${System.nanoTime()}")
        try {
            root.mkdirs();val file=File(root,"guest-basic.bin");val damaged=byteArrayOf(1,2,3);file.writeBytes(damaged)
            val store=VideoColorLookStore(root,"guest-basic")
            assertTrue(runCatching{store.load()}.isFailure)
            assertTrue(runCatching{store.add(NamedVideoColorLook("New",VideoGrade())){}}.isFailure)
            assertArrayEquals(damaged,file.readBytes())
        } finally { root.deleteRecursively() }
    }
}
