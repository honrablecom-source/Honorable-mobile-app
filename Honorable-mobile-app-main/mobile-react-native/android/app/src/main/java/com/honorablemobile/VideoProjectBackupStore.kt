package com.honorablemobile

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Guest-only durable copies in no-backup storage. No account, permission or private-ledger state is imported. */
internal class VideoProjectBackupStore(
    private val context: Context,
    private val directorySync: (File) -> Unit = { directory ->
        val descriptor = android.system.Os.open(directory.absolutePath, android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(descriptor) } finally { android.system.Os.close(descriptor) }
    },
) {
    companion object {
        const val EXTRA = "noble.video.backup.copy"
        const val DRAFT_EXTRA = "noble.video.backup.draft"
        private val lock = Any()
        private val active = mutableSetOf<String>()
        private const val MAX_COPIES = 8
        private const val MAX_STORED = 512L * 1024 * 1024
        fun validId(id: String) = id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
    }
    data class Document(val id: String, val primary: String, val draft: VideoDraft, val bytes: Long)
    data class Row(val id: String, val document: Document?, val error: Boolean)
    class Staged(val id: String, val directory: File, val verified: VideoProjectBackup.Verified, val draft: VideoDraft) {
        var consumed = false
    }
    private fun root(): File {
        val base = context.noBackupFilesDir.canonicalFile; val root = File(base, "video-project-backups")
        require(root.canonicalFile == root.absoluteFile && (root.isDirectory || root.mkdirs())); return root
    }
    private fun directory(id: String): File { require(validId(id)); val root = root(); return File(root, id).also { require(it.canonicalFile == File(root, id)) } }
    fun mediaUri(id: String, index: Int): Uri { require(validId(id) && index in 0 until VideoProjectBackup.MAX_ASSETS); return Uri.Builder().scheme("content").authority(context.packageName + ".video-project-media").appendPath(id).appendPath(index.toString()).build() }
    private fun file(directory: File, name: String): File = File(directory, name).also { require(it.canonicalFile == File(directory.canonicalFile, name) && it.parentFile?.canonicalFile == directory.canonicalFile) }
    private fun asset(directory: File, index: Int) = file(directory, "asset-" + index.toString().padStart(3, '0') + ".bin")
    private fun small(file: File, maximum: Int): ByteArray {
        require(file.isFile && file.length() in 1..maximum.toLong()); val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(n > 0 && n <= maximum - output.size()); output.write(buffer, 0, n) }; output.toByteArray()
        }; require(bytes.size.toLong() == file.length()); return bytes
    }
    private fun write(file: File, bytes: ByteArray) { FileOutputStream(file).use { it.write(bytes); it.fd.sync() } }
    private fun ownedDirectories(): List<File> = root().listFiles()?.filter { (validId(it.name) || (it.name.startsWith(".pending-") && validId(it.name.removePrefix(".pending-")))) && it.isDirectory && it.canonicalFile == File(root(), it.name) }.orEmpty()
    private fun usage(directory: File): Long {
        val actual = directory.listFiles()?.sumOf { child -> require(child.isFile && child.canonicalFile == File(directory.canonicalFile, child.name)); child.length() } ?: 0
        val reserve = File(directory, "reserve.bin").takeIf { it.isFile }?.let { java.io.DataInputStream(it.inputStream()).use { input -> input.readLong() } } ?: 0L
        require(reserve in 0..VideoProjectBackup.MAX_MEDIA + 1024 * 1024); return maxOf(actual, reserve)
    }
    fun stage(value: VideoProjectBackup.Verified, current: () -> Unit): Staged {
        current(); val id = UUID.randomUUID().toString(); val pending: File
        synchronized(lock) {
            val dirs = ownedDirectories(); val needed = value.entries.sumOf { it.bytes } + 1024 * 1024
            require(dirs.size < MAX_COPIES && needed <= MAX_STORED - dirs.sumOf(::usage)) { "Imported-copy storage limit reached; delete a copy or clear interrupted imports" }
            require(context.noBackupFilesDir.usableSpace >= needed + 16L * 1024 * 1024) { "Not enough device storage for this private copy" }
            pending = File(root(), ".pending-$id"); require(!pending.exists() && pending.mkdir() && pending.canonicalFile == File(root(), pending.name))
            active += id
            try { FileOutputStream(file(pending, "reserve.bin")).use { output -> java.io.DataOutputStream(output).apply { writeLong(needed); flush() }; output.fd.sync() } }
            catch (error: Throwable) { active -= id; deleteFiles(pending); throw error }
        }
        try {
            VideoProjectBackup.extract(value, { index, use -> current(); FileOutputStream(asset(pending, index)).use { output -> use(output); output.fd.sync() } }, current)
            val uris = value.entries.indices.map { mediaUri(id, it).toString() }; val draft = VideoProjectBackup.resolve(value, uris)
            validateMedia(value, pending, current)
            write(file(pending, "draft.bin"), VideoDraftCodec.encode(draft))
            write(file(pending, "receipt.bin"), encodeReceipt(value, VideoDraftCodec.encode(draft)))
            current(); return Staged(id, pending, value, draft)
        } catch (error: Throwable) { synchronized(lock) { active -= id; deleteFiles(pending) }; throw error }
    }
    private fun validateMedia(value: VideoProjectBackup.Verified, pending: File, current: () -> Unit) {
        fun media(reference: String) = asset(pending, VideoProjectBackup.referenceIndex(reference, value.entries.size))
        fun duration(reference: String, video: Boolean): Long {
            current(); val retriever = MediaMetadataRetriever()
            try { retriever.setDataSource(media(reference).absolutePath); current(); require(retriever.extractMetadata(if (video) MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO else MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") { "A bundled media role is unsupported" }; return requireNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()) }
            finally { runCatching { retriever.release() } }
        }
        // Existing complete source/media validation policy, on staged private files rather than arbitrary URI authority.
        GuestVideoProjectMedia.validate(VideoProjectBackup.placeholder(0), value.draft, { true }, { duration(it, true) }, { duration(it, false) }) { reference, sourceDuration ->
            current(); val bytes = small(media(reference), VideoCaptions.MAX_SRT_BYTES); VideoCaptions.parseSrt(bytes, sourceDuration)
        }
        current()
    }
    private fun encodeReceipt(value: VideoProjectBackup.Verified, draft: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(output).use { data -> data.writeInt(1); data.writeLong(value.draft.sourceDurationMs); data.writeUTF(VideoProjectBackup.sha(draft)); data.writeInt(value.entries.size); value.entries.forEach { data.writeUTF(it.mime); data.writeLong(it.bytes); data.writeUTF(it.sha256) } }
        return output.toByteArray().also { require(it.size <= VideoProjectBackup.MAX_MANIFEST) }
    }
    private data class Receipt(val duration: Long, val draftSha: String, val entries: List<VideoProjectBackup.Entry>)
    private fun receipt(directory: File): Receipt {
        java.io.DataInputStream(small(file(directory, "receipt.bin"), VideoProjectBackup.MAX_MANIFEST).inputStream()).use { input ->
            require(input.readInt() == 1); val duration = input.readLong(); require(duration in 1..Int.MAX_VALUE.toLong()); val sha = input.readUTF(); require(sha.matches(Regex("[a-f0-9]{64}")))
            val count = input.readInt(); require(count in 1..VideoProjectBackup.MAX_ASSETS); var total = 0L
            val entries = (0 until count).map { index -> val mime = input.readUTF(); val bytes = input.readLong(); val digest = input.readUTF(); require(mime.length in 3..100 && mime.none { it.isISOControl() } && bytes in 1..VideoProjectBackup.MAX_MEDIA && bytes <= VideoProjectBackup.MAX_MEDIA - total && digest.matches(Regex("[a-f0-9]{64}"))); total += bytes; VideoProjectBackup.Entry("media/" + index.toString().padStart(3, '0') + ".bin", mime, bytes, digest) }
            require(input.available() == 0); return Receipt(duration, sha, entries)
        }
    }
    fun verify(id: String, current: () -> Unit): Document {
        current(); val document = load(id); val directory = directory(id); val receipt = synchronized(lock) { receipt(directory) }
        receipt.entries.forEachIndexed { index, entry -> current(); require(VideoProjectBackup.fileSha(asset(directory, index), current) == entry.sha256) { "An imported media checksum changed" } }
        current(); return document
    }
    private fun syncDirectory(directory: File) {
        directorySync(directory)
    }
    fun load(id: String): Document = synchronized(lock) {
        val directory = directory(id); require(directory.isDirectory)
        val receipt = receipt(directory); val bytes = small(file(directory, "draft.bin"), VideoProjectBackup.MAX_PROJECT); require(VideoProjectBackup.sha(bytes) == receipt.draftSha) { "Imported project metadata changed" }
        val draft = requireNotNull(VideoDraftCodec.decode(bytes, receipt.duration)) { "Imported project is unreadable" }
        val expected = receipt.entries.indices.map { mediaUri(id, it).toString() }
        require(GuestVideoProjectMedia.originals(expected.first(), draft).toSet() == expected.toSet()) { "Imported project references changed" }
        receipt.entries.forEachIndexed { index, entry -> require(asset(directory, index).isFile && asset(directory, index).length() == entry.bytes) { "An imported media copy is missing or changed" } }
        Document(id, expected.first(), draft, receipt.entries.sumOf { it.bytes })
    }
    fun commit(staged: Staged, current: () -> Unit): Document = synchronized(lock) {
        require(!staged.consumed && staged.id in active && staged.directory == File(root(), ".pending-${staged.id}"))
        current(); val target = directory(staged.id); require(!target.exists()); var moved = false
        try {
            require(file(staged.directory, "reserve.bin").delete()); current(); syncDirectory(staged.directory); require(staged.directory.renameTo(target)) { "Could not keep imported copy" }; moved = true; current()
            syncDirectory(root()); val document = load(staged.id); current(); staged.consumed = true; active -= staged.id; document
        } catch (error: Throwable) { active -= staged.id; deleteFiles(staged.directory); if (moved && target.exists()) deleteFiles(target); staged.consumed = true; throw error }
    }
    fun abandon(staged: Staged) = synchronized(lock) { if (!staged.consumed) { require(validId(staged.id) && staged.directory == File(root(), ".pending-${staged.id}")); active -= staged.id; deleteFiles(staged.directory); staged.consumed = true } }
    fun discardNew(document: Document) = synchronized(lock) { deleteFiles(directory(document.id)) }
    fun list(): List<Row> = synchronized(lock) { ownedDirectories().filter { validId(it.name) }.map { directory -> runCatching { Row(directory.name, load(directory.name), false) }.getOrElse { Row(directory.name, null, true) } } }
    fun delete(id: String) = synchronized(lock) { require(validId(id) && id !in active); deleteFiles(directory(id)) }
    fun clearInterrupted() = synchronized(lock) { ownedDirectories().filter { it.name.startsWith(".pending-") && it.name.removePrefix(".pending-") !in active }.forEach(::deleteFiles) }
    private fun deleteFiles(directory: File) {
        require(directory.parentFile?.canonicalFile == root() && directory.canonicalFile == File(root(), directory.name))
        val children = directory.listFiles().orEmpty(); children.forEach { child -> require(child.isFile && child.canonicalFile == File(directory.canonicalFile, child.name)) }
        children.forEach { child -> require(child.delete()) { "Could not delete an imported private file" } }
        if (directory.exists()) require(directory.delete()) { "Could not delete imported directory" }
    }
    fun owned(value: Uri): Boolean = runCatching { resolve(value); true }.getOrDefault(false)
    fun resolve(value: Uri): Pair<File, VideoProjectBackup.Entry> = synchronized(lock) {
        require(value.scheme == "content" && value.authority == context.packageName + ".video-project-media" && value.query == null && value.fragment == null && value.pathSegments.size == 2)
        val id = value.pathSegments[0]; val index = value.pathSegments[1].toIntOrNull() ?: error("Invalid imported asset")
        require(validId(id) && index in 0 until VideoProjectBackup.MAX_ASSETS && value == mediaUri(id, index))
        val receipt = receipt(directory(id)); require(index in receipt.entries.indices); val file = asset(directory(id), index); require(file.isFile && file.length() == receipt.entries[index].bytes); file to receipt.entries[index]
    }
    fun matches(id: String, primary: String, bytes: ByteArray): Boolean = runCatching {
        val stored = load(id); require(stored.primary == primary && bytes.contentEquals(VideoDraftCodec.encode(stored.draft))); true
    }.getOrDefault(false)
}
