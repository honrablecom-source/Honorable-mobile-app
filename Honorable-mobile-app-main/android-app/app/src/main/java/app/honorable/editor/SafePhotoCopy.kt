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
    fun save(context: Context, source: Uri, destination: Uri, bitmap: Bitmap) {
        require(source != destination) { "Choose a new copy destination" }
        val temp = File.createTempFile("honorable-photo-", ".png", context.cacheDir)
        try {
            FileOutputStream(temp).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encoding failed" }
                output.fd.sync()
            }
            val expected = temp.inputStream().use(::digest)
            requireNotNull(context.contentResolver.openFileDescriptor(source, "r")).use { original ->
                requireNotNull(context.contentResolver.openFileDescriptor(destination, "rw")).use { copy ->
                    val from = Os.fstat(original.fileDescriptor)
                    val to = Os.fstat(copy.fileDescriptor)
                    PhotoDestinationSafety.requireSeparate(source.toString(), destination.toString(), from.st_dev, from.st_ino, to.st_dev, to.st_ino)
                    Os.lseek(copy.fileDescriptor, 0, OsConstants.SEEK_SET)
                    Os.ftruncate(copy.fileDescriptor, 0)
                    ParcelFileDescriptor.AutoCloseOutputStream(copy).use { output ->
                        temp.inputStream().use { it.copyTo(output) }
                        output.flush()
                    }
                }
            }
            val actual = requireNotNull(context.contentResolver.openInputStream(destination)).use(::digest)
            check(expected.contentEquals(actual)) { "Saved PNG differs from rendered copy" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(destination).use { BitmapFactory.decodeStream(it, null, bounds) }
            check(bounds.outWidth == bitmap.width && bounds.outHeight == bitmap.height && bounds.outMimeType == "image/png") {
                "Saved PNG could not be validated"
            }
        } finally { temp.delete() }
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
