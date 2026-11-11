package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Synthetic media payloads: real ZIP/binary/codec/CRC/hash/URI tests, not Android media decoder tests. */
class VideoProjectBackupTest {
    private val primary = "content://synthetic/private-primary"
    private val camera = "content://synthetic/private-camera"
    private val music = "content://synthetic/private-music"
    private val captions = "content://synthetic/private-captions"
    private fun draft(): VideoDraft {
        val cues = listOf(VideoCaption(200, 400, "Keep user text"))
        return VideoDraft(5000, listOf(VideoSegment(0, 2000, speed = 1.5f, volume = .4f, title = "Snapshot", fadeEdges = true, audioFade = true, captions = cues, captionSourceUri = captions), VideoSegment(100, 1000, sourceId = "camera", title = "Second", captions = cues, captionSourceUri = captions)), 1, 500, 100, 1000, listOf(VideoSource("camera", camera, 3000)), VideoSoundtrack(music, 9000, volume = .3f, loop = false, startMs = 100, endMs = 8000))
    }
    private fun temp() = Files.createTempFile("noble-video-backup-test-", ".zip").toFile()
    private fun payload(index: Int) = ByteArray(101 + index * 19) { (it * 17 + index).toByte() }
    private fun create(file: File = temp(), plan: VideoProjectBackup.Plan = VideoProjectBackup.plan(primary, draft())): VideoProjectBackup.Verified =
        VideoProjectBackup.create(file, plan, List(plan.originals.size) { "application/octet-stream" }, { index, use -> payload(index).inputStream().use(use) })
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun header(value: VideoProjectBackup.Verified, project: ByteArray, entries: List<VideoProjectBackup.Entry> = value.entries): ByteArray {
        val bytes = ByteArrayOutputStream(); DataOutputStream(bytes).use { out -> out.writeInt(0x4e564231); out.writeInt(1); out.writeLong(value.draft.sourceDurationMs); out.writeInt(project.size); out.writeUTF(sha(project)); out.writeInt(entries.size); entries.forEach { out.writeUTF(it.name); out.writeUTF(it.mime); out.writeLong(it.bytes); out.writeUTF(it.sha256) } }; return bytes.toByteArray()
    }
    private fun rewrite(file: File, entries: LinkedHashMap<String, ByteArray>) { ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name).apply { time = 0 }); zip.write(bytes); zip.closeEntry() } } }
    private fun entries(file: File): LinkedHashMap<String, ByteArray> = ZipFile(file).use { zip -> linkedMapOf<String, ByteArray>().apply { zip.entries().toList().forEach { item -> put(item.name, zip.getInputStream(item).use { it.readBytes() }) } } }
    @Test fun completeSnapshotReferencesAreNeutralAndAllBytePayloadsHashesCrcAndCodecSurviveRoundtrip() {
        val plan = VideoProjectBackup.plan(primary, draft()); assertEquals(listOf(primary, camera, music, captions), plan.originals)
        val file = temp(); try {
            val value = create(file, plan); assertEquals(4, value.entries.size); assertEquals(sha(file.readBytes()), value.sha256)
            ZipFile(file).use { zip ->
                assertEquals((0..3).map { "media/00$it.bin" } + listOf("project.bin", "manifest.bin"), zip.entries().toList().map { it.name })
                value.entries.forEachIndexed { index, entry -> val bytes = zip.getInputStream(zip.getEntry(entry.name)).use { it.readBytes() }; assertArrayEquals(payload(index), bytes); assertEquals(sha(bytes), entry.sha256); assertEquals(bytes.size.toLong(), entry.bytes) }
                val metadata = (zip.getInputStream(zip.getEntry("project.bin")).use { it.readBytes() } + zip.getInputStream(zip.getEntry("manifest.bin")).use { it.readBytes() }).toString(Charsets.ISO_8859_1)
                for (reference in plan.originals) assertFalse(metadata.contains(reference))
            }
            val uris = (0..3).map { "content://owned/import/$it" }; val restored = VideoProjectBackup.resolve(value, uris)
            val expected = draft().copy(sources = listOf(VideoSource("camera", uris[1], 3000)), soundtrack = draft().soundtrack!!.copy(uri = uris[2]), segments = draft().segments.map { it.copy(captionSourceUri = uris[3]) })
            assertEquals(expected, restored); assertEquals(restored, VideoDraftCodec.decode(VideoDraftCodec.encode(restored), 5000))
            val decoded = mutableListOf<ByteArray>(); VideoProjectBackup.extract(value, { index, use -> val out = ByteArrayOutputStream(); use(out); assertEquals(index, decoded.size); decoded += out.toByteArray() }); decoded.forEachIndexed { index, bytes -> assertArrayEquals(payload(index), bytes) }
            System.getProperty("noble.backupEvidence")?.let { directory -> File(directory).mkdirs(); file.copyTo(File(directory, "synthetic-complete-backup.zip"), overwrite = true) }
        } finally { file.delete() }
    }
    @Test fun primaryOnlyAndMaximum65UniqueReferencedAssetsAreAcceptedWithoutDroppingTheSourcePool() {
        val simple = VideoDraft(5000, listOf(VideoSegment(0, 5000)), 0, 0, 0, 5000); val first = create(plan = VideoProjectBackup.plan(primary, simple)); try { assertEquals(1, first.entries.size); assertEquals(simple, VideoProjectBackup.resolve(first, listOf("content://owned/primary"))) } finally { first.file.delete() }
        val sources = List(31) { VideoSource("s$it", "content://synthetic/v$it", 1000) }
        val clips = List(32) { index -> VideoSegment(0, 1000, sourceId = if (index == 0) "primary" else "s${index-1}", captions = listOf(VideoCaption(0, 500, "x")), captionSourceUri = "content://synthetic/c$index") }
        val max = VideoDraft(1000, clips, 0, 0, 0, 1000, sources, VideoSoundtrack(music, 2000)); val value = create(plan = VideoProjectBackup.plan(primary, max)); try { assertEquals(65, value.entries.size); assertEquals(67, ZipFile(value.file).use { it.size() }); assertEquals(32, value.draft.segments.size); assertEquals(31, value.draft.sources.size) } finally { value.file.delete() }
    }
    @Test fun sharedCaptionReferenceIsCopiedOnceAndMutableOriginalsCannotChangeCapturedPlan() {
        val mutable = mutableListOf(VideoCaption(0, 500, "Original")); val clips = mutableListOf(VideoSegment(0, 1000, captions = mutable, captionSourceUri = captions))
        val source = VideoDraft(5000, clips, 0, 0, 0, 1000); val plan = VideoProjectBackup.plan(primary, source); mutable.clear(); clips.clear()
        assertEquals(1, plan.original.segments.single().captions.size); assertEquals(2, plan.originals.size)
        assertThrows(UnsupportedOperationException::class.java) { (plan.originals as MutableList).clear() }; assertThrows(UnsupportedOperationException::class.java) { (plan.original.segments[0].captions as MutableList).clear() }
    }
    @Test fun nonemptyOutputsArePreservedAndFailedOrCancelledFreshOutputsAreDeleted() {
        val existing = temp(); existing.writeBytes(byteArrayOf(1, 2, 3)); assertThrows(IllegalArgumentException::class.java) { create(existing) }; assertArrayEquals(byteArrayOf(1, 2, 3), existing.readBytes()); existing.delete()
        val plan = VideoProjectBackup.plan(primary, draft())
        for (failure in listOf("missing", "empty", "twice", "throw", "cancel")) {
            val file = temp(); var checks = 0
            assertThrows(Exception::class.java) { VideoProjectBackup.create(file, plan, List(4) { null }, { index, use -> when (failure) { "missing" -> Unit; "empty" -> use(ByteArrayInputStream(byteArrayOf())); "twice" -> { use(payload(index).inputStream()); use(payload(index).inputStream()) }; "throw" -> throw IOException("Synthetic provider failure"); else -> use(payload(index).inputStream()) } }, { if (failure == "cancel" && ++checks == 10) error("Synthetic cancel") }) }
            assertFalse(file.exists())
        }
    }
    @Test fun latchedArchiveBudgetRejectsOverflowInvalidRangesStorageFailureAndCancellation() {
        val out = ByteArrayOutputStream(); val budget = VideoProjectBackup.Budget(out, {}, 3); budget.write(1); budget.write(byteArrayOf(2, 3)); assertArrayEquals(byteArrayOf(1, 2, 3), out.toByteArray())
        assertThrows(IllegalStateException::class.java) { budget.write(4) }; assertThrows(IllegalStateException::class.java) { budget.write(1) }
        val bad = VideoProjectBackup.Budget(ByteArrayOutputStream(), {}); assertThrows(IllegalArgumentException::class.java) { bad.write(byteArrayOf(1), -1, 1) }; assertThrows(IllegalStateException::class.java) { bad.write(1) }
        val broken = VideoProjectBackup.Budget(object : OutputStream() { override fun write(value: Int) { throw IOException("Synthetic disk failure") } }, {}); assertThrows(IOException::class.java) { broken.write(1) }; assertThrows(IllegalStateException::class.java) { broken.write(1) }
        val cancel = VideoProjectBackup.Budget(ByteArrayOutputStream(), { error("Cancelled") }); assertThrows(IllegalStateException::class.java) { cancel.write(1) }; assertThrows(IllegalStateException::class.java) { cancel.write(1) }
    }
    @Test fun wrongExpectedDigestMissingFileAndTrailingCommentOrHugeDirectoryNeverPassInspection() {
        val value = create(); val file = value.file; try {
            assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file, expectedSha = "0".repeat(64)) }
            val original = file.readBytes(); file.appendBytes(byteArrayOf(0)); assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file) }; file.writeBytes(original)
            val bytes = original.copyOf(); bytes[bytes.size - 22 + 12] = -1; bytes[bytes.size - 22 + 13] = -1; file.writeBytes(bytes); assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file) }
            file.delete(); assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file) }
        } finally { file.delete() }
    }
    @Test fun noncanonicalEntrySetsTraversalAndExtraPayloadsAreRejectedWithoutExtraction() {
        val value = create(); val file = value.file; try {
            val original = entries(file)
            val traversal = linkedMapOf<String, ByteArray>(); original.forEach { (name, bytes) -> traversal[if (name == "media/000.bin") "../outside.bin" else name] = bytes }; rewrite(file, traversal)
            assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file) }
            val extra = LinkedHashMap(original); extra["extra.bin"] = byteArrayOf(1); rewrite(file, extra); assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file) }
            val missing = LinkedHashMap(original); missing.remove("media/001.bin"); rewrite(file, missing); assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file) }
        } finally { file.delete() }
    }
    @Test fun recomputedManifestCannotAuthorizeExternalUriMissingAssetOrChangedMediaContent() {
        val value = create(); val file = value.file; try {
            val original = entries(file)
            val outside = VideoDraftCodec.encode(value.draft.copy(sources = value.draft.sources.map { it.copy(uri = "content://outside/private") })); val modified = LinkedHashMap(original); modified["project.bin"] = outside; modified["manifest.bin"] = header(value, outside); rewrite(file, modified)
            assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file) }
            val mediaChanged = LinkedHashMap(original); mediaChanged["media/000.bin"] = payload(0).also { it[0] = (it[0].toInt() xor 1).toByte() }; rewrite(file, mediaChanged); assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file) }
            val oversized = LinkedHashMap(original); oversized["manifest.bin"] = header(value, original.getValue("project.bin"), value.entries.mapIndexed { index, entry -> if (index == 0) entry.copy(bytes = VideoProjectBackup.MAX_MEDIA) else entry }); rewrite(file, oversized); assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(file) }
        } finally { file.delete() }
    }
    @Test fun changedArchivesAndDestinationFailureStopExtractionAndResolverRejectsAliasOrPlaceholderUris() {
        val value = create(); val file = value.file; try {
            var opened = false; file.appendBytes(byteArrayOf(1)); assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.extract(value, { _, _ -> opened = true }) }; assertFalse(opened)
            for (uris in listOf(listOf("content://only/one"), List(4) { "content://same" }, (0..3).map { VideoProjectBackup.placeholder(it) })) assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.resolve(value, uris) }
        } finally { file.delete() }
        val next = create(); try { assertThrows(IOException::class.java) { VideoProjectBackup.extract(next, { _, _ -> throw IOException("Synthetic storage full") }) } } finally { next.file.delete() }
    }
    @Test fun metadataAndPerEntryCopyCancellationDoNotReturnAnApprovedBackup() {
        val value = create(); try {
            var calls = 0; VideoProjectBackup.inspect(value.file, { calls++ })
            for (at in listOf(1, 3, calls)) { var current = 0; assertThrows(IllegalStateException::class.java) { VideoProjectBackup.inspect(value.file, { if (++current == at) error("Synthetic stale owner") }) } }
            var copied = 0; assertThrows(IllegalStateException::class.java) { VideoProjectBackup.extract(value, { _, use -> use(ByteArrayOutputStream()); copied++ }, { if (copied == 1) error("Synthetic cancellation") }) }; assertEquals(1, copied)
        } finally { value.file.delete() }
    }
}
