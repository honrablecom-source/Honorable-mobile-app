package com.honorablemobile

import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, manifest = Config.NONE)
class CreativeEditorShareTest {
    @Test fun exportIsAnExactSeparateCopyAndRejectsFurtherWrites() {
        val root = Files.createTempDirectory("creative-share-test").toFile()
        try {
            val bytes = ByteArray(50000) { (it % 251).toByte() }
            val task = CreativeEditorShare(root, 1, bytes.size, "Edited.png")
            for(chunk in bytes.asList().chunked(32768)) assertTrue(task.append(Base64.encodeToString(chunk.toByteArray(), Base64.NO_WRAP)))
            val copy = task.finish()
            assertEquals(root.canonicalFile, copy.canonicalFile.parentFile)
            assertArrayEquals(bytes, copy.readBytes())
            assertFalse(task.append("YQ=="))
            assertTrue(copy.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun invalidOrCancelledStreamLeavesNoPartialFile() {
        val root = Files.createTempDirectory("creative-share-test").toFile()
        try {
            val task = CreativeEditorShare(root, 2, 1, "Edited.mp4")
            assertFalse(task.append("YWI="))
            assertEquals(0, root.listFiles()!!.size)
            val stopped = CreativeEditorShare(root, 3, 2, "Edited.png")
            assertTrue(stopped.append("YQ==")); stopped.cancel(); stopped.cancel()
            assertEquals(0, root.listFiles()!!.size)
            assertTrue(runCatching { stopped.finish() }.isFailure)
            for(name in listOf("../secret.png", "private/file.png", "no-extension", "bad.exe", "bad\n.png")) assertTrue(runCatching { CreativeEditorShare.title(name) }.isFailure)
        } finally { root.deleteRecursively() }
    }
}
