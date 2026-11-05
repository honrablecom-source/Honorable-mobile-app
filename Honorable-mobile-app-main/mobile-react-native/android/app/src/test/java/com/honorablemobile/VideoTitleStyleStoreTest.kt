package com.honorablemobile

import app.honorable.editor.PhotoPresetNamespace
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File
import java.util.concurrent.Executors

class VideoTitleStyleStoreTest {
    private val owner=PhotoPresetNamespace.GUEST
    private val a=NamedVideoTitleStyle("Opening",VideoTitleStyle(0))
    private fun reject(block:()->Unit) { try { block(); fail("Expected refusal") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
    private fun folder(block:(File)->Unit) { val dir=Files.createTempDirectory("title-store-test").toFile(); try { block(dir) } finally { dir.deleteRecursively() } }
    @Test fun saveReloadDeleteUndoAndOwnerIsolation() = folder { dir ->
        val shelf=VideoTitleStyleStore(dir,owner); shelf.save(a) {}
        assertEquals(listOf(a),VideoTitleStyleStore(dir,owner).load().styles)
        val other=VideoTitleStyleStore(dir,PhotoPresetNamespace.account("another","https://example.invalid"))
        assertTrue(other.load().styles.isEmpty())
        val deletion=shelf.delete(a) {}; assertTrue(shelf.load().styles.isEmpty())
        reject { other.undo(deletion) {} }
        shelf.undo(deletion) {}; assertEquals(listOf(a),shelf.load().styles)
        val removed=shelf.delete(a) {}; shelf.save(a.copy(name="Changed")) {}
        reject { shelf.undo(removed) {} }
        assertEquals("Changed",shelf.load().styles.single().name)
    }
    @Test fun corruptAndOversizedFilesAreNeverReplaced() = folder { dir ->
        val file=File(dir,"$owner.bin")
        for(bytes in listOf(byteArrayOf(1,2),ByteArray(VideoTitleStyleCodec.MAX_BYTES+1))) {
            file.writeBytes(bytes); val shelf=VideoTitleStyleStore(dir,owner)
            reject { shelf.load() }; reject { shelf.save(a) {} }; assertArrayEquals(bytes,file.readBytes())
        }
    }
    @Test fun cancellationBeforePromotionPreservesPreviousFileAndCleansPending() = folder { dir ->
        val shelf=VideoTitleStyleStore(dir,owner); shelf.save(a) {}; val before=File(dir,"$owner.bin").readBytes()
        var calls=0
        reject { shelf.save(a.copy(name="Cancelled")) { calls++; check(calls<2) } }
        assertEquals(2,calls); assertArrayEquals(before,File(dir,"$owner.bin").readBytes())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".pending") })
    }
    @Test fun symlinkFileAndDirectoryRefused() = folder { dir ->
        val actual=File(dir,"actual").apply { mkdir() }; val alias=File(dir,"alias")
        Files.createSymbolicLink(alias.toPath(),actual.toPath()); reject { VideoTitleStyleStore(alias,owner) }
        val target=File(dir,"target").apply { writeText("untouched") }
        Files.createSymbolicLink(File(actual,"$owner.bin").toPath(),target.toPath())
        reject { VideoTitleStyleStore(actual,owner) }; assertEquals("untouched",target.readText())
    }
    @Test fun concurrentStoreInstancesKeepAllWrites() = folder { dir ->
        val executor=Executors.newFixedThreadPool(4)
        try { val jobs=(0 until 20).map { i -> executor.submit { VideoTitleStyleStore(dir,owner).save(a.copy(name="Style $i")) {} } }; jobs.forEach { it.get() } }
        finally { executor.shutdownNow() }
        assertEquals(20,VideoTitleStyleStore(dir,owner).load().styles.size)
        reject { VideoTitleStyleStore(dir,"../escape") }
    }
}
