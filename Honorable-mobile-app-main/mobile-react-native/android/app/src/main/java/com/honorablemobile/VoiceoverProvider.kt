package com.honorablemobile

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Same-app read-only access to explicitly kept takes. No external grants or arbitrary paths. */
class VoiceoverProvider:ContentProvider() {
    override fun onCreate()=true
    private fun resolve(uri:Uri):File {
        val context=requireNotNull(context)
        require(uri.scheme=="content"&&uri.authority==context.packageName+".voiceovers"&&uri.pathSegments.size==2&&uri.query==null&&uri.fragment==null)
        val namespace=uri.pathSegments[0];VideoPrivateOwner.requireReadable(context,namespace)
        return VoiceoverFiles(File(context.noBackupFilesDir,"voiceovers"),namespace).savedFile(uri.pathSegments[1]).also{require(it.isFile)}
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor { require(mode=="r") { "Voiceovers are read-only sources" };return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY) }
    override fun getType(uri:Uri):String { resolve(uri);return "audio/mp4" }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val file=resolve(uri);val columns=projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply{addRow(columns.map{when(it){OpenableColumns.DISPLAY_NAME->(VoiceoverFiles(File(requireNotNull(context).noBackupFilesDir,"voiceovers"),uri.pathSegments[0]).readName(uri.pathSegments[1])?.let{"$it.m4a"}?:"Noble-voiceover-${file.name}");OpenableColumns.SIZE->file.length();else->null}}.toTypedArray<Any?>())}
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException("Voiceovers are read-only sources")
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException("Voiceovers are read-only sources")
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException("Use the private recording screen to delete a take")
    companion object {
        internal fun ownsReadable(context:Context,value:Uri):Boolean = runCatching {
            require(value.pathSegments.size==2)
            val namespace=value.pathSegments[0];val id=value.pathSegments[1]
            require(value==uri(context,namespace,id))
            VideoPrivateOwner.requireReadable(context,namespace)
            VoiceoverFiles(File(context.noBackupFilesDir,"voiceovers"),namespace).savedFile(id).isFile
        }.getOrDefault(false)

        internal fun uri(context:Context,namespace:String,id:String):Uri { require(VoiceoverFiles.validId(id));return Uri.Builder().scheme("content").authority(context.packageName+".voiceovers").appendPath(namespace).appendPath(id).build() }
    }
}
