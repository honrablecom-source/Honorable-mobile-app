package com.honorablemobile

import android.app.Application
import android.content.pm.ProviderInfo
import android.media.MediaMetadataRetriever
import android.os.Process
import android.provider.OpenableColumns
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel

/** Real isolated filesystem/provider/transactions with controlled SDK metadata, not real video decoding. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class VideoProjectBackupStoreTest {
    private lateinit var context: Application
    private lateinit var store: VideoProjectBackupStore
    private val zips = mutableListOf<File>()
    private val primary = "content://synthetic/original"
    // Robolectric's ShadowLinux opens paths with RandomAccessFile, which cannot open directories.
    // Exercise real host directory fsync here; the production Android Os bridge remains device-unverified.
    @Before fun setup() { context = RuntimeEnvironment.getApplication(); store = VideoProjectBackupStore(context) { directory -> FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) } }; ShadowBinder.setCallingUid(Process.myUid()) }
    @After fun cleanup() { ShadowBinder.setCallingUid(Process.myUid()); store.list().forEach { store.delete(it.id) }; store.clearInterrupted(); zips.forEach { it.delete() }; zips.clear() }
    private fun root() = File(context.noBackupFilesDir.canonicalFile, "video-project-backups")
    private fun backup(): VideoProjectBackup.Verified {
        val draft = VideoDraft(5000, listOf(VideoSegment(0, 5000, title = "Synthetic imported snapshot")), 0, 0, 0, 5000)
        val plan = VideoProjectBackup.plan(primary, draft); val file = Files.createTempFile("noble-store-test-", ".zip").toFile(); zips += file
        return VideoProjectBackup.create(file, plan, listOf("video/mp4"), { _, use -> "synthetic bytes; metadata supplied by the SDK shadow".toByteArray().inputStream().use(use) })
    }
    private fun metadata() {
        root().listFiles()?.filter { it.name.startsWith(".pending-") }?.forEach { pending ->
            val file = File(pending, "asset-000.bin")
            if (file.isFile) { ShadowMediaMetadataRetriever.addMetadata(file.absolutePath, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO, "yes"); ShadowMediaMetadataRetriever.addMetadata(file.absolutePath, MediaMetadataRetriever.METADATA_KEY_DURATION, "5000") }
        }
    }
    private fun stage(): VideoProjectBackupStore.Staged = store.stage(backup(), ::metadata)
    private fun committed(): VideoProjectBackupStore.Document { val value = stage(); return store.commit(value) {} }
    @Test fun completeCommitOwnsOnlyPrivateCopiedBytesAndStoredDraftSurvivesOriginalZipDeletion() {
        val value = stage(); val zip = value.verified.file; val copy = store.commit(value) {}; zip.delete()
        assertTrue(value.consumed); assertEquals(1, store.list().size); val again = store.verify(copy.id) {}
        assertEquals(copy, again); assertEquals(store.mediaUri(copy.id, 0).toString(), copy.primary); assertTrue(store.owned(android.net.Uri.parse(copy.primary)))
        assertTrue(store.matches(copy.id, copy.primary, VideoDraftCodec.encode(copy.draft))); assertFalse(store.matches(copy.id, primary, VideoDraftCodec.encode(copy.draft)))
        assertFalse(store.matches(copy.id, copy.primary, VideoDraftCodec.encode(copy.draft.copy(segments = copy.draft.segments.map { it.copy(title = "forged") }))))
        assertTrue(root().canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath)); assertFalse(File(root(), ".pending-${copy.id}").exists())
        assertThrows(IllegalArgumentException::class.java) { store.commit(value) {} }
    }
    @Test fun unsupportedMediaAndEarlyOrLateCancellationRemoveOnlyTheirFreshStaging() {
        val existing = committed(); val old = store.resolve(android.net.Uri.parse(existing.primary)).first.readBytes()
        assertThrows(IllegalArgumentException::class.java) { store.stage(backup()) {} }; assertEquals(listOf(existing.id), store.list().map { it.id })
        var calls = 0; assertThrows(IllegalStateException::class.java) { store.stage(backup()) { if (++calls == 5) error("Synthetic cancellation") } }
        assertTrue(root().listFiles().orEmpty().none { it.name.startsWith(".pending-") }); assertArrayEquals(old, store.resolve(android.net.Uri.parse(existing.primary)).first.readBytes())
        val pending = stage(); var commitCalls = 0; assertThrows(IllegalStateException::class.java) { store.commit(pending) { if (++commitCalls == 3) error("Synthetic late cancellation") } }
        assertEquals(1, store.list().size); assertTrue(root().listFiles().orEmpty().none { it.name.startsWith(".pending-") })
    }
    @Test fun abandonedStagesAndInterruptedProcessFilesCleanWithoutRemovingCompletedSnapshots() {
        val copy = committed(); val pending = stage(); store.clearInterrupted(); assertTrue(pending.directory.isDirectory)
        store.abandon(pending); store.abandon(pending); assertFalse(pending.directory.exists()); assertEquals(copy, store.load(copy.id))
        val interrupted = File(root(), ".pending-00000000-0000-0000-0000-000000000001"); assertTrue(interrupted.mkdir()); File(interrupted, "partial.bin").writeBytes(byteArrayOf(1))
        store.clearInterrupted(); assertFalse(interrupted.exists()); assertEquals(copy, store.load(copy.id))
    }
    @Test fun payloadTamperingMetadataDamageAndMissingCopiesFailClosedAndRemainListedUnreadable() {
        val copy = committed(); val file = store.resolve(android.net.Uri.parse(copy.primary)).first; val original = file.readBytes()
        file.writeBytes(original.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }); assertThrows(IllegalArgumentException::class.java) { store.verify(copy.id) {} }
        file.writeBytes(original); val draft = File(File(root(), copy.id), "draft.bin"); draft.appendBytes(byteArrayOf(0)); assertThrows(IllegalArgumentException::class.java) { store.load(copy.id) }
        assertTrue(store.list().single().error); assertNull(store.list().single().document)
        assertTrue(file.delete()); assertFalse(store.owned(android.net.Uri.parse(copy.primary))); assertTrue(store.list().single().error)
    }
    @Test fun readOnlyProviderRefusesForeignUidWritesTraversalAliasesAndUnknownFields() {
        val copy = committed(); val uri = android.net.Uri.parse(copy.primary)
        val provider = VideoProjectBackupMediaProvider(); provider.attachInfo(context, ProviderInfo().apply { authority = context.packageName + ".video-project-media"; exported = false; grantUriPermissions = false })
        assertEquals("video/mp4", provider.getType(uri)); provider.query(uri, null, null, null, null).use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("Imported media copy", cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))); assertEquals(copy.bytes, cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))) }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri, "r")).use { input -> assertEquals(copy.bytes, input.readBytes().size.toLong()) }
        for (mode in listOf("w", "rw", "wa", "rwt")) assertThrows(IllegalArgumentException::class.java) { provider.openFile(uri, mode) }
        assertThrows(UnsupportedOperationException::class.java) { provider.delete(uri, null, null) }; assertThrows(UnsupportedOperationException::class.java) { provider.update(uri, null, null, null) }; assertThrows(UnsupportedOperationException::class.java) { provider.insert(uri, null) }
        assertThrows(IllegalArgumentException::class.java) { provider.query(uri, arrayOf("private_original_name"), null, null, null) }
        for (bad in listOf(copy.primary + "?x=1", copy.primary + "/extra", copy.primary.replace("/0", "/00"), copy.primary.replace(copy.id, ".."), "content://other.authority/${copy.id}/0")) assertFalse(store.owned(android.net.Uri.parse(bad)))
        ShadowBinder.setCallingUid(Process.myUid() + 100); assertThrows(IllegalArgumentException::class.java) { provider.getType(uri) }; ShadowBinder.setCallingUid(Process.myUid())
    }
    @Test fun symlinkedAssetsCannotEscapeThePrivateRootOrDeleteTheirExternalTarget() {
        val copy = committed(); val source = store.resolve(android.net.Uri.parse(copy.primary)).first
        val canary = File(context.filesDir, "backup-test-canary"); canary.writeBytes(source.readBytes()); assertTrue(source.delete()); Files.createSymbolicLink(source.toPath(), canary.toPath())
        assertFalse(store.owned(android.net.Uri.parse(copy.primary))); assertThrows(IllegalArgumentException::class.java) { store.delete(copy.id) }; assertTrue(canary.isFile)
        Files.delete(source.toPath()); canary.delete()
    }
    @Test fun eightCopyQuotaAndCanonicalIdsRefuseWithoutOverwritingAnyExistingData() {
        val copies = List(8) { committed() }; assertEquals(8, store.list().size)
        assertThrows(IllegalArgumentException::class.java) { stage() }; assertEquals(copies.map { it.id }.toSet(), store.list().map { it.id }.toSet())
        for (id in listOf("../escape", "", "00000000-0000-0000-0000-00000000000g", "00000000-0000-0000-0000-000000000000/extra")) { assertFalse(VideoProjectBackupStore.validId(id)); assertThrows(IllegalArgumentException::class.java) { store.load(id) } }
        store.delete(copies.first().id); assertEquals(7, store.list().size); assertEquals(copies.last(), store.verify(copies.last().id) {})
    }
    @Test fun directorySyncFailureBeforeOrAfterRenameRollsBackOnlyTheNewCopy() {
        val existing = committed()
        for (failAt in listOf(1, 2)) {
            var calls = 0
            val failing = VideoProjectBackupStore(context) { if (++calls == failAt) throw java.io.IOException("Synthetic directory fsync failure") }
            val pending = failing.stage(backup(), ::metadata)
            assertThrows(java.io.IOException::class.java) { failing.commit(pending) {} }
            assertTrue(pending.consumed); assertFalse(pending.directory.exists())
            assertEquals(listOf(existing.id), store.list().map { it.id }); assertEquals(existing, store.verify(existing.id) {})
        }
    }
}
