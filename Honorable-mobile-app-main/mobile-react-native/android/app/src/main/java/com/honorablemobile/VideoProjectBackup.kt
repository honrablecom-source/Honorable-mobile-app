package com.honorablemobile

import java.io.*
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Portable edit state and original media; every reference URI is neutralized, not user text/media metadata. */
internal object VideoProjectBackup {
    const val MAX_ASSETS = 65
    const val MAX_MEDIA = 256L * 1024 * 1024
    const val MAX_ZIP = 257L * 1024 * 1024
    const val MAX_PROJECT = 256_000
    const val MAX_MANIFEST = 32_768
    private const val MAGIC = 0x4e564231 // NVB1
    private const val PREFIX = "content://noble-video-backup/asset/"
    data class Entry(val name: String, val mime: String, val bytes: Long, val sha256: String)
    data class Plan(val primary: String, val original: VideoDraft, val originals: List<String>, val portable: VideoDraft)
    class Verified(val file: File, val draft: VideoDraft, entries: List<Entry>, val sha256: String, val bytes: Long) {
        val entries: List<Entry> = frozen(entries)
    }
    private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())
    private fun freeze(value: VideoDraft) = value.copy(segments = frozen(value.segments.map { it.copy(captions = frozen(it.captions)) }), sources = frozen(value.sources))
    fun placeholder(index: Int): String { require(index in 0 until MAX_ASSETS); return PREFIX + index.toString().padStart(3, '0') }
    private fun reference(value: String): String {
        require(value.startsWith("content://") && value.none { it.isISOControl() } && value.toByteArray(Charsets.UTF_8).size <= 2048 && value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == value) { "Invalid media reference" }; return value
    }
    private fun references(primary: String, draft: VideoDraft): List<String> = linkedSetOf(primary).apply {
        addAll(draft.sources.map { it.uri }); draft.soundtrack?.let { add(it.uri) }; addAll(draft.segments.mapNotNull { it.captionSourceUri })
    }.toList()
    private fun map(draft: VideoDraft, transform: (String) -> String): VideoDraft = freeze(draft.copy(
        sources = draft.sources.map { it.copy(uri = transform(it.uri)) },
        soundtrack = draft.soundtrack?.let { it.copy(uri = transform(it.uri)) },
        segments = draft.segments.map { it.copy(captionSourceUri = it.captionSourceUri?.let(transform)) }))
    fun plan(primary: String, draft: VideoDraft): Plan {
        reference(primary); val original = freeze(draft); VideoDraftCodec.encode(original)
        require(original.sources.none { it.uri == primary }) { "Conflicting primary/source references" }
        val originals = references(primary, original); require(originals.size in 1..MAX_ASSETS)
        originals.forEach(::reference)
        val indices = originals.withIndex().associate { it.value to it.index }
        val portable = map(original) { placeholder(indices.getValue(it)) }
        VideoDraftCodec.encode(portable)
        return Plan(primary, original, frozen(originals), portable)
    }
    fun resolve(value: Verified, mediaUris: List<String>): VideoDraft {
        require(mediaUris.size == value.entries.size)
        return relocate(value.draft, mediaUris)
    }
    fun relocate(draft: VideoDraft, mediaUris: List<String>): VideoDraft {
        require(mediaUris.size in 1..MAX_ASSETS && mediaUris.distinct().size == mediaUris.size)
        mediaUris.forEach { reference(it); require(!it.startsWith(PREFIX)) }
        validateReferences(draft, mediaUris.size)
        return map(draft) { mediaUris[referenceIndex(it, mediaUris.size)] }.also { VideoDraftCodec.encode(it) }
    }
    fun description(draft: VideoDraft, entries: List<Entry>): String = buildString {
        append("Editable snapshot: ${draft.segments.size} clips; ${entries.size} media copies. Primary duration ${VideoCaptions.formatTime(draft.sourceDurationMs)}.\nSelected clip ${draft.selectedClip + 1}, source playhead ${VideoCaptions.formatTime(draft.previewPositionMs)}, source trim ${VideoCaptions.formatTime(draft.trimStartMs)}–${VideoCaptions.formatTime(draft.trimEndMs)}.\n\nOriginal byte copies (including embedded metadata):\n")
        entries.forEachIndexed { index, entry -> append("Asset ${index + 1}: ${entry.mime}; ${entry.bytes} bytes; SHA-256 ${entry.sha256}\n") }
        append("\nSource pool: primary → asset1\n")
        draft.sources.forEach { append("${it.id} → asset${referenceIndex(it.uri, entries.size) + 1}; duration ${VideoCaptions.formatTime(it.durationMs)}\n") }
        draft.soundtrack?.let { append("\nSoundtrack asset${referenceIndex(it.uri, entries.size) + 1}; duration ${VideoCaptions.formatTime(it.durationMs)}, excerpt ${VideoCaptions.formatTime(it.startMs)}–${VideoCaptions.formatTime(it.endMs)}, gain ${it.volume}, ${if (it.loop) "loop" else "play once"}.\n") }
        append("\nComplete clip and caption text list:\n")
        draft.segments.forEachIndexed { index, clip ->
            append("\n${index + 1}: source ${clip.sourceId}, ${VideoCaptions.formatTime(clip.startMs)}–${VideoCaptions.formatTime(clip.endMs)}, speed ${clip.speed}x, gain ${clip.volume}; video fade ${clip.fadeEdges}, audio fade ${clip.audioFade}.\nTitle: ${clip.title.ifEmpty { "(none)" }}\n")
            clip.captionSourceUri?.let { append("Subtitle document asset${referenceIndex(it, entries.size) + 1}\n") }
            clip.captions.forEach { append("${VideoCaptions.formatTime(it.startMs)} → ${VideoCaptions.formatTime(it.endMs)}\n${it.text}\n") }
        }
        append("\nAll grades, layout, title styles, source-motion anchors and other editable settings are included in the checked project payload. This review does not render their appearance. Original bytes and embedded metadata are preserved, including any private information. Only media-reference URI fields are replaced with neutral asset references; user-entered title/caption text and original media metadata remain. Checksums verify bytes, not who created a backup. Opening restores a separate local guest copy and transfers no account access, credits or privileges. Existing source files, current edits and saves are unchanged.")
    }.also { require(it.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "Backup review is too large; simplify this project" } }
    fun referenceIndex(value: String, count: Int): Int {
        require(count in 1..MAX_ASSETS)
        val index = (0 until count).firstOrNull { placeholder(it) == value }
        return requireNotNull(index) { "Backup contains an external or unknown reference URI" }
    }
    private fun validateReferences(draft: VideoDraft, count: Int) {
        val used = references(placeholder(0), draft).map { referenceIndex(it, count) }.toSet()
        require(used == (0 until count).toSet()) { "Unused/missing media in backup" }
        require(draft.sources.none { it.uri == placeholder(0) }) { "Conflicting primary/source references" }
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    fun fileSha(file: File, current: () -> Unit = {}): String {
        current(); require(file.isFile && file.length() in 1..MAX_ZIP) { "Backup missing or oversized" }
        val digest = MessageDigest.getInstance("SHA-256"); var bytes = 0L
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { current(); val n = input.read(buffer); if (n < 0) break; check(n > 0 && n <= MAX_ZIP - bytes); digest.update(buffer, 0, n); bytes += n } }
        current(); return digest.digest().hex()
    }
    private fun manifest(draft: VideoDraft, project: ByteArray, entries: List<Entry>): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC); data.writeInt(1); data.writeLong(draft.sourceDurationMs); data.writeInt(project.size); data.writeUTF(sha(project)); data.writeInt(entries.size)
            entries.forEach { data.writeUTF(it.name); data.writeUTF(it.mime); data.writeLong(it.bytes); data.writeUTF(it.sha256) }
        }
        return output.toByteArray().also { require(it.size <= MAX_MANIFEST) }
    }
    private fun decodeManifest(bytes: ByteArray): Triple<Long, Pair<Int, String>, List<Entry>> {
        require(bytes.size in 1..MAX_MANIFEST)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == 1) { "Unsupported Noble video backup" }
            val duration = input.readLong(); require(duration in 1..Int.MAX_VALUE.toLong())
            val projectSize = input.readInt(); require(projectSize in 1..MAX_PROJECT); val projectSha = input.readUTF(); require(projectSha.matches(Regex("[a-f0-9]{64}")))
            val count = input.readInt(); require(count in 1..MAX_ASSETS); var total = 0L
            val entries = (0 until count).map { index ->
                val entry = Entry(input.readUTF(), input.readUTF(), input.readLong(), input.readUTF())
                require(entry.name == "media/" + index.toString().padStart(3, '0') + ".bin" && validMime(entry.mime)) { "Noncanonical media entry" }
                require(entry.bytes in 1..MAX_MEDIA && entry.bytes <= MAX_MEDIA - total && entry.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid media size or checksum" }; total += entry.bytes; entry
            }
            require(input.available() == 0); return Triple(duration, projectSize to projectSha, frozen(entries))
        }
    }
    private fun validMime(value: String) = value.length in 3..100 && value.matches(Regex("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+"))
    class Budget(private val output: OutputStream, private val current: () -> Unit, private val maximum: Long = MAX_ZIP): OutputStream() {
        private var written = 0L; private var failed = false
        init { require(maximum in 1..MAX_ZIP) }
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            check(!failed)
            try { current(); require(offset >= 0 && length >= 0 && offset <= bytes.size - length); check(length <= maximum - written) { "Backup exceeds its size limit" }; output.write(bytes, offset, length); written += length }
            catch (error: Throwable) { failed = true; throw error }
        }
    }
    fun create(file: File, plan: Plan, types: List<String?>, read: (Int, (InputStream) -> Unit) -> Unit, current: () -> Unit = {}): Verified {
        require(file.isFile && file.length() == 0L && file.canonicalFile == file.absoluteFile) { "Create a fresh private temporary output" }
        try {
            require(types.size == plan.originals.size && plan.originals.size in 1..MAX_ASSETS)
            val canonical = VideoProjectBackup.plan(plan.primary, plan.original); require(canonical == plan) { "Project changed" }
            val project = VideoDraftCodec.encode(plan.portable); var total = 0L; val entries = mutableListOf<Entry>()
            FileOutputStream(file).use { output -> ZipOutputStream(Budget(output, current)).use { zip ->
                zip.setLevel(0)
                plan.originals.indices.forEach { index ->
                    current(); val name = "media/" + index.toString().padStart(3, '0') + ".bin"; var size = 0L; var visited = false; val digest = MessageDigest.getInstance("SHA-256")
                    zip.putNextEntry(ZipEntry(name).apply { time = 0 })
                    read(index) { source ->
                        check(!visited) { "Media supplied twice" }; visited = true
                        val buffer = ByteArray(65536)
                        while (true) { current(); val n = source.read(buffer); if (n < 0) break; check(n > 0) { "Media stream made no progress" }; check(n <= MAX_MEDIA - total) { "Project media exceeds256MiB" }; zip.write(buffer, 0, n); digest.update(buffer, 0, n); total += n; size += n }
                    }
                    check(visited && size > 0) { "Media empty or unavailable" }; zip.closeEntry()
                    entries += Entry(name, types[index]?.takeIf(::validMime) ?: "application/octet-stream", size, digest.digest().hex())
                }
                zip.putNextEntry(ZipEntry("project.bin").apply { time = 0 }); zip.write(project); zip.closeEntry()
                zip.putNextEntry(ZipEntry("manifest.bin").apply { time = 0 }); zip.write(manifest(plan.portable, project, entries)); zip.closeEntry(); zip.finish(); zip.flush(); output.fd.sync()
            } }
            return inspect(file, current)
        } catch (error: Throwable) { file.delete(); throw error }
    }
    /** Bound the central directory before ZipFile allocates or reads any untrusted names. No comments/ZIP64/spanning. */
    private fun envelope(file: File): Int {
        require(file.isFile && file.length() in 22..MAX_ZIP)
        RandomAccessFile(file, "r").use { input ->
            input.seek(file.length() - 22); val bytes = ByteArray(22); input.readFully(bytes)
            fun u16(at: Int) = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
            fun u32(at: Int) = (0 until 4).sumOf { (bytes[at + it].toLong() and 255) shl (8 * it) }
            require(bytes.take(4) == listOf<Byte>(80, 75, 5, 6) && u16(4) == 0 && u16(6) == 0 && u16(20) == 0) { "Unsupported ZIP envelope" }
            val count = u16(10); require(count in 3..MAX_ASSETS + 2 && u16(8) == count)
            val size = u32(12); val offset = u32(16); require(size in 1..32768 && offset + size == file.length() - 22) { "Invalid ZIP directory" }; return count
        }
    }
    private fun copy(zip: ZipFile, entry: ZipEntry, expected: Long, output: OutputStream, current: () -> Unit): String {
        require(entry.size == expected && entry.method in listOf(ZipEntry.STORED, ZipEntry.DEFLATED) && expected in 1..MAX_MEDIA)
        val digest = MessageDigest.getInstance("SHA-256"); val crc = CRC32(); var total = 0L
        zip.getInputStream(entry).use { input -> val buffer = ByteArray(65536); while (true) { current(); val n = input.read(buffer); if (n < 0) break; require(n > 0 && n <= expected - total) { "Entry expands beyond its declared limit" }; output.write(buffer, 0, n); digest.update(buffer, 0, n); crc.update(buffer, 0, n); total += n } }
        require(total == expected && crc.value == entry.crc) { "Entry truncated or CRC changed" }; current(); return digest.digest().hex()
    }
    private fun small(zip: ZipFile, name: String, maximum: Int, current: () -> Unit): ByteArray {
        val entry = requireNotNull(zip.getEntry(name)); require(entry.size in 1..maximum.toLong())
        val output = ByteArrayOutputStream(); copy(zip, entry, entry.size, output, current); return output.toByteArray()
    }
    fun inspect(file: File, current: () -> Unit = {}, expectedSha: String? = null): Verified {
        current(); val count = envelope(file); val digest = fileSha(file, current); expectedSha?.let { require(it == digest) { "Reviewed backup changed" } }
        ZipFile(file).use { zip ->
            val header = decodeManifest(small(zip, "manifest.bin", MAX_MANIFEST, current)); val entries = header.third
            val expectedNames = entries.map { it.name } + listOf("project.bin", "manifest.bin")
            val actual = zip.entries().toList().map { it.name }; require(actual == expectedNames && actual.size == count && actual.distinct().size == actual.size) { "Backup entry set/order differs" }
            val project = small(zip, "project.bin", MAX_PROJECT, current); require(project.size == header.second.first && sha(project) == header.second.second) { "Project checksum changed" }
            val draft = requireNotNull(VideoDraftCodec.decode(project, header.first)) { "Project unsupported or damaged" }; validateReferences(draft, entries.size)
            entries.forEach { entry -> current(); require(copy(zip, requireNotNull(zip.getEntry(entry.name)), entry.bytes, object : OutputStream() { override fun write(value: Int) {} ; override fun write(bytes: ByteArray, offset: Int, length: Int) {} }, current) == entry.sha256) { "Media checksum changed" } }
            current(); return Verified(file, freeze(draft), entries, digest, file.length())
        }
    }
    fun extract(value: Verified, write: (Int, (OutputStream) -> Unit) -> Unit, current: () -> Unit = {}) {
        current(); require(fileSha(value.file, current) == value.sha256) { "Backup changed before extraction" }; envelope(value.file)
        ZipFile(value.file).use { zip -> value.entries.forEachIndexed { index, entry ->
            var visited = false
            write(index) { output -> check(!visited); visited = true; require(copy(zip, requireNotNull(zip.getEntry(entry.name)), entry.bytes, output, current) == entry.sha256) { "Media changed during extraction" } }
            require(visited) { "Destination unavailable" }; current()
        } }
    }
}
