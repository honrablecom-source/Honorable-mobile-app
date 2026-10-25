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
import java.io.InputStream
import java.security.MessageDigest

/** Encode privately, refuse aliased destinations before truncation, then verify the saved PNG. */
object SafePhotoCopy {
    enum class Encoding(val mime:String,val extension:String){PNG("image/png","png"),JPEG("image/jpeg","jpg")}
    fun save(context: Context, source: Uri, destination: Uri, bitmap: Bitmap,encoding:Encoding=Encoding.PNG,quality:Int=95,protectedOriginals:List<Uri> = emptyList()) {
        require(quality in 1..100)
        ImportedPhotoOriginals.validate(protectedOriginals.map { it.toString() })
        val originals=(listOf(source)+protectedOriginals).distinct()
        require(destination !in originals) { "Choose a new copy destination" }
        val originalHandles=mutableListOf<Pair<Uri,ParcelFileDescriptor>>()
        val temp = File.createTempFile("honorable-photo-", ".${encoding.extension}", context.cacheDir)
        try {
            FileOutputStream(temp).use { output ->
                var flat:Bitmap?=null
                try {
                    val encoded=if(encoding==Encoding.JPEG){Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also{flat=it;val c=android.graphics.Canvas(it);c.drawColor(android.graphics.Color.WHITE);c.drawBitmap(bitmap,0f,0f,null)}}else bitmap
                    check(encoded.compress(if(encoding==Encoding.PNG)Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,if(encoding==Encoding.PNG)100 else quality,output)){"Photo encoding failed"}
                }finally{flat?.recycle()}
                output.fd.sync()
            }
            val expected = temp.inputStream().use(::digest)
            // Keep every original open until the destination has been checked and written.
            // This includes images flattened by another creation workspace before this editor opened.
            originals.forEach { uri -> originalHandles.add(uri to requireNotNull(context.contentResolver.openFileDescriptor(uri,"r")) { "Reopen the project's original photos before exporting" }) }
                requireNotNull(context.contentResolver.openFileDescriptor(destination, "rw")).use { copy ->
                    val to = Os.fstat(copy.fileDescriptor)
                    originalHandles.forEach { (uri,original) ->
                        val from=Os.fstat(original.fileDescriptor)
                        PhotoDestinationSafety.requireSeparate(uri.toString(), destination.toString(), from.st_dev, from.st_ino, to.st_dev, to.st_ino)
                    }
                    Os.lseek(copy.fileDescriptor, 0, OsConstants.SEEK_SET)
                    Os.ftruncate(copy.fileDescriptor, 0)
                    ParcelFileDescriptor.AutoCloseOutputStream(copy).use { output ->
                        temp.inputStream().use { it.copyTo(output) }
                        output.flush()
                    }
                }
            val actual = requireNotNull(context.contentResolver.openInputStream(destination)).use(::digest)
            check(expected.contentEquals(actual)) { if(encoding==Encoding.PNG)"Saved PNG differs from rendered copy" else "Saved JPEG differs from encoded copy" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(destination).use { BitmapFactory.decodeStream(it, null, bounds) }
            check(bounds.outWidth == bitmap.width && bounds.outHeight == bitmap.height && bounds.outMimeType == encoding.mime) {
                if(encoding==Encoding.PNG)"Saved PNG could not be validated" else "Saved JPEG could not be validated"
            }
        } finally { originalHandles.forEach { runCatching { it.second.close() } };temp.delete() }
    }

    private fun digest(input: InputStream): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(32 * 1024)
        while (true) {
            val read = input.read(bytes)
            if (read < 0) break
            if (read > 0) hash.update(bytes, 0, read)
        }
        return hash.digest()
    }
}
