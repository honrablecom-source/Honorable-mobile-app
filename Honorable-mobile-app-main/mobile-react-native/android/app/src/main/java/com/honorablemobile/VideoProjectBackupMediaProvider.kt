package com.honorablemobile

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns

/** Internal same-UID, read-only imported media. No grants, public export, update or delete. */
class VideoProjectBackupMediaProvider: ContentProvider() {
    override fun onCreate() = true
    private fun store(): VideoProjectBackupStore { require(Binder.getCallingUid() == android.os.Process.myUid()) { "Private imported media" }; return VideoProjectBackupStore(requireNotNull(context)) }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor { require(mode == "r"); val file = store().resolve(uri).first; return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
    override fun getType(uri: Uri) = store().resolve(uri).second.mime
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        require(selection == null && selectionArgs == null && sortOrder == null)
        val resolved = store().resolve(uri); val columns = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        require(columns.all { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE })
        return MatrixCursor(columns.toTypedArray()).apply { addRow(columns.map<String, Any> { if (it == OpenableColumns.SIZE) resolved.first.length() else "Imported media copy" }.toTypedArray()) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Imported source copies are read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Imported source copies are read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Imported source copies are read-only")
}
