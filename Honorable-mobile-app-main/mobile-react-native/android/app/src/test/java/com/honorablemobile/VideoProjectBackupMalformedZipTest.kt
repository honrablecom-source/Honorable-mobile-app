package com.honorablemobile

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Tiny synthetic archives exercise malformed ZIP metadata without large decompression allocations. */
class VideoProjectBackupMalformedZipTest {
    private fun backup(): VideoProjectBackup.Verified {
        val file = Files.createTempFile("noble-malformed-backup-", ".zip").toFile()
        val draft = VideoDraft(5000, listOf(VideoSegment(0, 5000)), 0, 0, 0, 5000)
        return VideoProjectBackup.create(file, VideoProjectBackup.plan("content://synthetic/original", draft), listOf("video/mp4"), { _, use -> ByteArray(257) { it.toByte() }.inputStream().use(use) })
    }
    private fun central(bytes: ByteArray): List<Int> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = buffer.getInt(bytes.size - 22 + 16)
        val entries = buffer.getShort(bytes.size - 22 + 10).toInt() and 65535
        return List(entries) {
            val start = offset; assertEquals(0x02014b50, buffer.getInt(start))
            offset += 46 + (buffer.getShort(start + 28).toInt() and 65535) + (buffer.getShort(start + 30).toInt() and 65535) + (buffer.getShort(start + 32).toInt() and 65535)
            start
        }.also { assertEquals(bytes.size - 22, offset) }
    }
    @Test fun duplicateCentralDirectoryNamesCannotHideAnotherEntry() {
        val value = backup()
        try {
            // project.bin and media/000.bin differ in length, so use a second canonical media entry.
            val draft = value.draft.copy(sources = listOf(VideoSource("other", "content://synthetic/other", 5000)))
            value.file.delete(); value.file.createNewFile()
            VideoProjectBackup.create(value.file, VideoProjectBackup.plan("content://synthetic/original", draft), listOf("video/mp4", "video/mp4"), { _, use -> byteArrayOf(1, 2, 3).inputStream().use(use) })
            val bytes = value.file.readBytes(); val offsets = central(bytes)
            assertEquals(13, "media/000.bin".length)
            "media/000.bin".toByteArray().copyInto(bytes, offsets[1] + 46)
            value.file.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(value.file) }
        } finally { value.file.delete() }
    }
    @Test fun incorrectCentralCrcIsRejectedEvenWhenPayloadAndManifestShaAreUnchanged() {
        val value = backup()
        try {
            val bytes = value.file.readBytes(); val offset = central(bytes).first()
            bytes[offset + 16] = (bytes[offset + 16].toInt() xor 1).toByte(); value.file.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(value.file) }
        } finally { value.file.delete() }
    }
    @Test fun forgedSmallerManifestAndZipSizesCannotAuthorizeLongerInflatedMedia() {
        val value = backup()
        try {
            val contents = ZipFile(value.file).use { zip -> zip.entries().toList().associateTo(linkedMapOf()) { it.name to zip.getInputStream(it).use { input -> input.readBytes() } } }
            val project = contents.getValue("project.bin"); val entry = value.entries.single(); val header = ByteArrayOutputStream()
            DataOutputStream(header).use { out ->
                out.writeInt(0x4e564231); out.writeInt(1); out.writeLong(5000); out.writeInt(project.size); out.writeUTF(VideoProjectBackup.sha(project)); out.writeInt(1)
                out.writeUTF(entry.name); out.writeUTF(entry.mime); out.writeLong(entry.bytes - 1); out.writeUTF(entry.sha256)
            }
            contents["manifest.bin"] = header.toByteArray()
            ZipOutputStream(value.file.outputStream()).use { zip -> contents.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
            val bytes = value.file.readBytes(); val offset = central(bytes).first()
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset + 24, (entry.bytes - 1).toInt()); value.file.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { VideoProjectBackup.inspect(value.file) }
        } finally { value.file.delete() }
    }
}
