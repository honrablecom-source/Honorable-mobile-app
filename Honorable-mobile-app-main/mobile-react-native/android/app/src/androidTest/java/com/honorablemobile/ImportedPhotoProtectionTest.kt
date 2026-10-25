package com.honorablemobile

import android.graphics.Bitmap
import android.net.Uri
import android.system.Os
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.SafePhotoCopy
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImportedPhotoProtectionTest {
    @Test fun flattenedPhotoCannotOverwriteAnImportedOriginalThroughAnAlias() {
        checkExport(true)
    }

    @Test fun flattenedPhotoCanExportANewCopyWithoutChangingItsOriginals() {
        checkExport(false)
    }

    private fun checkExport(aliasOriginal: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "created-images").apply { mkdirs() }
        val original = File.createTempFile("imported-", ".png", folder)
        val flattened = File.createTempFile("flattened-", ".png", folder)
        val destination = File.createTempFile("painting-copy-", ".png", folder)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            original.writeBytes(byteArrayOf(10, 20, 30))
            flattened.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val originalBytes = original.readBytes()
            val flattenedBytes = flattened.readBytes()
            val originalUri = FileProvider.getUriForFile(context, context.packageName + ".creation", original)
            if (aliasOriginal) {
                check(destination.delete())
                Os.link(original.absolutePath, destination.absolutePath)
            }
            val result = runCatching {
                SafePhotoCopy.save(context, Uri.fromFile(flattened), Uri.fromFile(destination), bitmap,
                    protectedOriginals = listOf(originalUri))
            }
            assertArrayEquals(originalBytes, original.readBytes())
            assertArrayEquals(flattenedBytes, flattened.readBytes())
            if (aliasOriginal) {
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
                assertArrayEquals(originalBytes, destination.readBytes())
            } else {
                result.getOrThrow()
                val decoded = android.graphics.BitmapFactory.decodeFile(destination.absolutePath)
                try { assertEquals(8, decoded.width); assertEquals(8, decoded.height) }
                finally { decoded.recycle() }
            }
        } finally {
            bitmap.recycle()
            destination.delete(); flattened.delete(); original.delete()
        }
    }
}
