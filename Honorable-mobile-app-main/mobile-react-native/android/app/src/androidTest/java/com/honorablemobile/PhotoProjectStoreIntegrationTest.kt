package com.honorablemobile

import app.honorable.editor.*
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Synthetic source IDs/state only. This file is API-compiled; Android execution remains required. */
class PhotoProjectStoreIntegrationTest {
    private val source="content://synthetic/photo"
    private val fingerprint="a".repeat(64)
    private fun directory()=File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,"noble-photo-project-${java.util.UUID.randomUUID()}")
    @Test fun reopeningStoreRetainsObjectsAndInterruptedSaveKeepsPriorProject(){
        val root=directory();val original=PhotoEdit(layers=PhotoLayers(listOf(PhotoObject("shape",PhotoObjectType.RECTANGLE))))
        try {
            val store=PhotoProjectStore(root,PhotoPresetNamespace.GUEST,source);store.save(fingerprint,original){}
            assertEquals(original,PhotoProjectStore(root,PhotoPresetNamespace.GUEST,source).load(fingerprint))
            var checks=0
            assertTrue(runCatching{store.save(fingerprint,original.copy(exposure=.5f)){if(++checks==2)error("Editor changed")}}.isFailure)
            assertEquals(original,store.load(fingerprint))
        }finally{root.deleteRecursively()}
    }
    @Test fun ownerIsolationCorruptionAndChangedOriginalFailClosed(){
        val root=directory();val guest=PhotoPresetNamespace.GUEST;val account=PhotoPresetNamespace.account("owner","https://example.test")
        try {
            val store=PhotoProjectStore(root,guest,source);store.save(fingerprint,PhotoEdit()){}
            assertNull(PhotoProjectStore(root,account,source).load(fingerprint))
            assertTrue(runCatching{store.load("b".repeat(64))}.isFailure)
            val file=File(File(root,guest),PhotoProjectCodec.storageName(guest,source));file.writeBytes(byteArrayOf(1,2,3))
            assertTrue(runCatching{store.save(fingerprint,PhotoEdit(exposure=.5f)){}}.isFailure)
            assertArrayEquals(byteArrayOf(1,2,3),file.readBytes())
            store.delete{};assertNull(store.load(fingerprint))
        }finally{root.deleteRecursively()}
    }
}
