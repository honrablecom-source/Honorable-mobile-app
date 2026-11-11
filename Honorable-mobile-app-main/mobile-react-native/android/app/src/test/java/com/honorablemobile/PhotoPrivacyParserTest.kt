package com.honorablemobile

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class PhotoPrivacyParserTest {
    /** Independent TIFF tag IDs and offsets, not the library's metadata writer. */
    private fun jpeg(): ByteArray {
        val owner = "Synthetic private owner\u0000".toByteArray(Charsets.US_ASCII)
        val body = "PRIVATE-BODY-001\u0000".toByteArray(Charsets.US_ASCII)
        val lens = "PRIVATE-LENS-002\u0000".toByteArray(Charsets.US_ASCII)
        val exifOffset = 38; val gpsOffset = 80; val dataOffset = 134
        val tiff = ByteBuffer.allocate(dataOffset + owner.size + body.size + lens.size + 48).order(ByteOrder.LITTLE_ENDIAN)
        tiff.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(8)
        fun entry(tag: Int, type: Int, count: Int, offset: Int) { tiff.putShort(tag.toShort()).putShort(type.toShort()).putInt(count).putInt(offset) }
        tiff.putShort(2); entry(0x8769, 4, 1, exifOffset); entry(0x8825, 4, 1, gpsOffset); tiff.putInt(0)
        assertEquals(exifOffset, tiff.position())
        tiff.putShort(3)
        entry(0xA430, 2, owner.size, dataOffset)
        entry(0xA431, 2, body.size, dataOffset + owner.size)
        entry(0xA435, 2, lens.size, dataOffset + owner.size + body.size); tiff.putInt(0)
        assertEquals(gpsOffset, tiff.position())
        val rationalOffset = dataOffset + owner.size + body.size + lens.size
        tiff.putShort(4)
        entry(1, 2, 2, 'N'.code); entry(2, 5, 3, rationalOffset)
        entry(3, 2, 2, 'W'.code); entry(4, 5, 3, rationalOffset + 24); tiff.putInt(0)
        assertEquals(dataOffset, tiff.position())
        tiff.put(owner).put(body).put(lens)
        for (value in listOf(51, 30, 0, 0, 7, 0)) tiff.putInt(value).putInt(1)
        val payload = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + tiff.array()
        val length = payload.size + 2
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe1.toByte(), (length ushr 8).toByte(), length.toByte()))
            write(payload); write(byteArrayOf(0xff.toByte(), 0xd9.toByte()))
        }.toByteArray()
    }
    private fun fields(bytes: ByteArray): List<PhotoPrivacyAudit.Field> {
        val budget = PhotoDifferenceReadBudget()
        val limited = budget.wrap(ByteArrayInputStream(bytes))
        val exif = ExifInterface(limited)
        val mapping = PhotoPrivacyBridge::class.java.getDeclaredMethod("tag", PhotoPrivacyAudit.Field::class.java).apply { isAccessible = true }
        val result = PhotoPrivacyAudit.inspect({ field -> exif.hasAttribute(mapping.invoke(PhotoPrivacyBridge, field) as String) })
        limited.finish(); return result
    }
    @Test fun realParserFindsGpsAndSupportedIdentifiersThroughProductionBoundedStreamAndTagMapping() {
        val actual = fields(jpeg())
        assertTrue("Fields found: $actual", actual.containsAll(listOf(PhotoPrivacyAudit.Field.GPS_LATITUDE, PhotoPrivacyAudit.Field.GPS_LONGITUDE,
            PhotoPrivacyAudit.Field.CAMERA_OWNER, PhotoPrivacyAudit.Field.BODY_SERIAL)))
        assertTrue(PhotoPrivacyAudit.locationFlagged(actual))
        val report = actual.joinToString { it.name }
        assertFalse(report.contains("Synthetic private owner")); assertFalse(report.contains("PRIVATE-BODY-001")); assertFalse(report.contains("PRIVATE-LENS-002"))
    }
    @Test fun metadataFreeJpegFindsNoWhitelistedFieldsInsteadOfInventingValues() {
        assertEquals(emptyList<PhotoPrivacyAudit.Field>(), fields(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())))
    }
}
