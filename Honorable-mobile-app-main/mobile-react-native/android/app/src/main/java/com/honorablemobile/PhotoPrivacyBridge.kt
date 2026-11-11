package com.honorablemobile

import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.ImportedPhotoOriginals
import app.honorable.search.MediaRecord
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import java.io.IOException

internal object PhotoPrivacyBridge {
    private data class Entry(val original: LibraryCollectionItem, val fields: List<PhotoPrivacyAudit.Field>?)
    private fun tag(field: PhotoPrivacyAudit.Field): String = when (field) {
        PhotoPrivacyAudit.Field.GPS_LATITUDE -> ExifInterface.TAG_GPS_LATITUDE
        PhotoPrivacyAudit.Field.GPS_LONGITUDE -> ExifInterface.TAG_GPS_LONGITUDE
        PhotoPrivacyAudit.Field.GPS_ALTITUDE -> ExifInterface.TAG_GPS_ALTITUDE
        PhotoPrivacyAudit.Field.GPS_DEST_LATITUDE -> ExifInterface.TAG_GPS_DEST_LATITUDE
        PhotoPrivacyAudit.Field.GPS_DEST_LONGITUDE -> ExifInterface.TAG_GPS_DEST_LONGITUDE
        PhotoPrivacyAudit.Field.GPS_DATE -> ExifInterface.TAG_GPS_DATESTAMP
        PhotoPrivacyAudit.Field.GPS_TIME -> ExifInterface.TAG_GPS_TIMESTAMP
        PhotoPrivacyAudit.Field.GPS_AREA -> ExifInterface.TAG_GPS_AREA_INFORMATION
        PhotoPrivacyAudit.Field.GPS_PROCESSING -> ExifInterface.TAG_GPS_PROCESSING_METHOD
        PhotoPrivacyAudit.Field.CAMERA_OWNER -> ExifInterface.TAG_CAMERA_OWNER_NAME
        PhotoPrivacyAudit.Field.BODY_SERIAL -> ExifInterface.TAG_BODY_SERIAL_NUMBER
        PhotoPrivacyAudit.Field.ARTIST -> ExifInterface.TAG_ARTIST
        PhotoPrivacyAudit.Field.COPYRIGHT -> ExifInterface.TAG_COPYRIGHT
        PhotoPrivacyAudit.Field.DESCRIPTION -> ExifInterface.TAG_IMAGE_DESCRIPTION
        PhotoPrivacyAudit.Field.USER_COMMENT -> ExifInterface.TAG_USER_COMMENT
        PhotoPrivacyAudit.Field.DATE_TIME -> ExifInterface.TAG_DATETIME
        PhotoPrivacyAudit.Field.DATE_ORIGINAL -> ExifInterface.TAG_DATETIME_ORIGINAL
        PhotoPrivacyAudit.Field.DATE_DIGITIZED -> ExifInterface.TAG_DATETIME_DIGITIZED
    }
    fun inspect(context: ReactApplicationContext, items: ReadableArray, request: LibraryDuplicateRequest, records: () -> List<MediaRecord>): WritableMap {
        PhotoPrivacyAudit.requireCount(items.size())
        val originals = (0 until items.size()).map { index ->
            val item = requireNotNull(items.getMap(index))
            LibraryCollectionItem(requireNotNull(item.getString("id")), requireNotNull(item.getString("uri")), requireNotNull(item.getString("kind")))
        }
        require(originals.all { it.kind == "IMAGE" } && originals.map { it.id }.distinct().size == originals.size && originals.map { it.uri }.distinct().size == originals.size) { "Select different photo originals" }
        ImportedPhotoOriginals.validate(originals.map { it.uri })
        val owner = requireNotNull(VideoPrivateOwner.namespace(context, EditorAccessMode.ACCOUNT)) { "Sign in to inspect Library photos" }
        val started = System.nanoTime()
        fun active() { request.requireActive(); check(System.nanoTime() - started < 60_000_000_000L) { "Metadata inspection timed out. Try fewer photos" } }
        fun current() { active(); PhotoPixelDifferenceBridge.requireCurrent(context, owner, originals, records) }
        current()
        val runtime = Runtime.getRuntime()
        check(runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) >= 96L * 1024 * 1024) { "Not enough free memory to inspect metadata. Close another workspace" }
        val budget = PhotoDifferenceReadBudget(current = ::active)
        try {
            val entries = originals.map { original ->
                current()
                val fields = try {
                    val asset = context.contentResolver.openAssetFileDescriptor(Uri.parse(original.uri), "r", request.signal) ?: throw IOException("Unavailable photo")
                    try {
                        val input = asset.createInputStream()
                        try {
                            request.bind(input)
                            val limited = budget.wrap(input)
                            val exif = ExifInterface(limited)
                            val result = PhotoPrivacyAudit.inspect({ exif.hasAttribute(tag(it)) }, ::active)
                            limited.finish(); current(); result
                        } finally { request.detach(input); runCatching { input.close() } }
                    } finally { asset.close() }
                } catch (_: IOException) { current(); null }
                  catch (_: IllegalArgumentException) { current(); null }
                  catch (_: SecurityException) { current(); null }
                Entry(original, fields)
            }
            current()
            return Arguments.createMap().apply {
                putArray("entries", Arguments.createArray().apply { entries.forEach { entry -> pushMap(Arguments.createMap().apply {
                    putString("id", entry.original.id); putString("uri", entry.original.uri)
                    putString("status", if (entry.fields == null) "UNKNOWN" else "READ")
                    putArray("fields", Arguments.createArray().apply { entry.fields?.forEach { pushString(it.name) } })
                }) } })
            }
        } catch (error: OutOfMemoryError) { throw IllegalStateException("Not enough memory to inspect metadata. Try fewer photos", error) }
    }
}
