package app.honorable.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream

/** Encode privately, refuse aliased destinations before truncation, then verify the saved PNG. */
object SafePhotoCopy {
    enum class Encoding(val mime:String,val extension:String){PNG("image/png","png"),JPEG("image/jpeg","jpg")}
    fun save(context: Context, source: Uri, destination: Uri, bitmap: Bitmap,encoding:Encoding=Encoding.PNG,quality:Int=95,protectedOriginals:List<Uri> = emptyList(),requireCurrent:()->Unit={}) {
        requireCurrent()
        require(quality in 1..100)
        ImportedPhotoOriginals.validate(protectedOriginals.map { it.toString() })
        val originals=(listOf(source)+protectedOriginals).distinct()
        require(destination !in originals) { "Choose a new copy destination" }
        val originalHandles=mutableListOf<Pair<Uri,ParcelFileDescriptor>>()
        val temp = File.createTempFile("honorable-photo-", ".${encoding.extension}", context.cacheDir)
        try {
            requireCurrent()
            FileOutputStream(temp).use { output ->
                var flat:Bitmap?=null
                try {
                    val encoded=if(encoding==Encoding.JPEG){Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also{flat=it;val c=android.graphics.Canvas(it);c.drawColor(android.graphics.Color.WHITE);c.drawBitmap(bitmap,0f,0f,null)}}else bitmap
                    check(encoded.compress(if(encoding==Encoding.PNG)Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,if(encoding==Encoding.PNG)100 else quality,output)){"Photo encoding failed"}
                }finally{flat?.recycle()}
                requireCurrent();output.fd.sync();requireCurrent()
            }
            val expected = temp.inputStream().use{PhotoExportStreams.digest(it,requireCurrent)}
            // Keep every original open until the destination has been checked and written.
            // This includes images flattened by another creation workspace before this editor opened.
            originals.forEach { uri -> requireCurrent();originalHandles.add(uri to requireNotNull(context.contentResolver.openFileDescriptor(uri,"r")) { "Reopen the project's original photos before exporting" }) }
                requireCurrent()
                requireNotNull(context.contentResolver.openFileDescriptor(destination, "rw")).use { copy ->
                    requireCurrent()
                    val to = Os.fstat(copy.fileDescriptor)
                    originalHandles.forEach { (uri,original) ->
                        requireCurrent();val from=Os.fstat(original.fileDescriptor)
                        PhotoDestinationSafety.requireSeparate(uri.toString(), destination.toString(), from.st_dev, from.st_ino, to.st_dev, to.st_ino)
                    }
                    requireCurrent();Os.lseek(copy.fileDescriptor, 0, OsConstants.SEEK_SET)
                    requireCurrent();Os.ftruncate(copy.fileDescriptor, 0)
                    ParcelFileDescriptor.AutoCloseOutputStream(copy).use { output ->
                        temp.inputStream().use { PhotoExportStreams.copy(it,output,requireCurrent) }
                        requireCurrent();output.flush();requireCurrent()
                    }
                }
            requireCurrent()
            val actual = requireNotNull(context.contentResolver.openInputStream(destination)).use{PhotoExportStreams.digest(it,requireCurrent)}
            check(expected.contentEquals(actual)) { if(encoding==Encoding.PNG)"Saved PNG differs from rendered copy" else "Saved JPEG differs from encoded copy" }
            requireCurrent()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(destination).use { BitmapFactory.decodeStream(it, null, bounds) }
            check(bounds.outWidth == bitmap.width && bounds.outHeight == bitmap.height && bounds.outMimeType == encoding.mime) {
                if(encoding==Encoding.PNG)"Saved PNG could not be validated" else "Saved JPEG could not be validated"
            }
            requireCurrent()
        } finally { originalHandles.forEach { runCatching { it.second.close() } };temp.delete() }
    }

}
