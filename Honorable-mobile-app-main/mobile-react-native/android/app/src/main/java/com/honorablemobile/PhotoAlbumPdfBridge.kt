package com.honorablemobile

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.ImportedPhotoOriginals
import app.honorable.search.MediaRecord
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.LifecycleState
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

internal class PhotoAlbumPdfPreview(
    val owner: String,
    val originals: List<LibraryCollectionItem>,
    val file: File,
    val digest: String,
    val pages: List<PhotoPixelDifference.Plan>,
) {
    val token = UUID.randomUUID().toString()
    fun dispose() { file.delete() }
    fun uri(context: ReactApplicationContext): Uri = FileProvider.getUriForFile(context, context.packageName + ".creation", file)
    fun map(): WritableMap = Arguments.createMap().apply {
        putString("token", token); putDouble("bytes", file.length().toDouble())
        putArray("pages", Arguments.createArray().apply {
            pages.forEach { p -> pushMap(Arguments.createMap().apply {
                putInt("width", p.size.width); putInt("height", p.size.height)
                putInt("sourceWidth", p.sourceWidth); putInt("sourceHeight", p.sourceHeight)
                putBoolean("sampled", p.sampled)
            }) }
        })
    }
}

internal object PhotoAlbumPdfBridge {
    fun requireCurrent(context: ReactApplicationContext, owner: String, originals: List<LibraryCollectionItem>, records: () -> List<MediaRecord>) {
        val a = context.currentActivity
        check(a != null && !a.isFinishing && !a.isDestroyed && context.lifecycleState == LifecycleState.RESUMED) { "Open Library again before preparing a PDF" }
        check(VideoPrivateOwner.namespace(context, EditorAccessMode.ACCOUNT) == owner) { "Account changed. Prepare the PDF again" }
        val current = records().associateBy { it.id.toString() }
        check(originals.all { e -> current[e.id]?.let { it.uri == e.uri && it.kind.name == e.kind } == true }) { "A selected original changed. Select the photos again" }
    }

    fun prepare(context: ReactApplicationContext, items: ReadableArray, request: LibraryDuplicateRequest, records: () -> List<MediaRecord>): PhotoAlbumPdfPreview {
        PhotoAlbumPdf.requireCount(items.size())
        val originals = (0 until items.size()).map { i ->
            val item = requireNotNull(items.getMap(i))
            LibraryCollectionItem(requireNotNull(item.getString("id")), requireNotNull(item.getString("uri")), requireNotNull(item.getString("kind")))
        }
        require(originals.all { it.kind == "IMAGE" } && originals.map { it.id }.distinct().size == originals.size && originals.map { it.uri }.distinct().size == originals.size) { "Choose different photo originals" }
        ImportedPhotoOriginals.validate(originals.map { it.uri })
        val owner = requireNotNull(VideoPrivateOwner.namespace(context, EditorAccessMode.ACCOUNT)) { "Sign in to prepare Library photos" }
        val started = System.nanoTime()
        fun active() {
            request.requireActive()
            check(System.nanoTime() - started < 60_000_000_000L) { "PDF preparation timed out. Try fewer or smaller photos" }
        }
        fun current() { active(); requireCurrent(context, owner, originals, records) }
        current()
        val runtime = Runtime.getRuntime()
        check(runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) >= 96L * 1024 * 1024) { "Not enough free memory for a PDF. Close another workspace" }
        val budget = PhotoDifferenceReadBudget(current = ::active)
        fun <T> read(original: LibraryCollectionItem, block: (InputStream) -> T): T {
            current()
            val asset = context.contentResolver.openAssetFileDescriptor(Uri.parse(original.uri), "r", request.signal) ?: error("A selected photo is unavailable")
            try {
                val input = asset.createInputStream()
                try {
                    request.bind(input)
                    val limited = budget.wrap(input)
                    val result = block(limited); limited.finish(); current(); return result
                } finally { request.detach(input); runCatching { input.close() } }
            } finally { asset.close() }
        }
        var file: File? = null
        try {
            val metadata = originals.map { original ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
                read(original) { BitmapFactory.decodeStream(it, null, options) }
                // Validate before opening another stream or allocating pixels.
                PhotoPixelDifference.Original(options.outWidth, options.outHeight, 1)
                val orientation = try { read(original) { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } }
                    catch (_: java.io.IOException) { active(); 1 }
                    catch (_: IllegalArgumentException) { active(); 1 }
                PhotoPixelDifference.Original(options.outWidth, options.outHeight, if (orientation == 0) 1 else orientation)
            }
            val plans = metadata.map { PhotoPixelDifference.plan(it, it) }
            val directory = File(context.cacheDir, "created-images")
            check(directory.canonicalFile == File(context.cacheDir.canonicalFile, "created-images"))
            check(directory.isDirectory || directory.mkdirs())
            // Only this workflow's old, explicitly handed-off cache copies are eligible.
            directory.listFiles()?.filter { it.name.startsWith("noble-photo-album-") && it.extension == "pdf" && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
            file = File.createTempFile("noble-photo-album-", ".pdf", directory)
            val document = PdfDocument()
            var unfinished: PdfDocument.Page? = null
            var outputDigest: String? = null
            try {
                originals.forEachIndexed { index, original ->
                    current()
                    var decoded: Bitmap? = null
                    var normalized: Bitmap? = null
                    try {
                        val plan = plans[index]
                        decoded = read(original) { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = plan.sample; inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }) } ?: error("A photo could not be decoded")
                        current(); PhotoPixelDifference.Size(decoded.width, decoded.height)
                        val raw = IntArray(decoded.width * decoded.height)
                        decoded.getPixels(raw, 0, decoded.width, 0, 0, decoded.width, decoded.height)
                        val pixels = PhotoPixelDifference.normalize(raw, decoded.width, decoded.height, metadata[index].orientation, plan.size, ::active)
                        normalized = Bitmap.createBitmap(pixels, plan.size.width, plan.size.height, Bitmap.Config.ARGB_8888)
                        current()
                        val layout = PhotoAlbumPdf.page(plan.sourceWidth, plan.sourceHeight)
                        val page = document.startPage(PdfDocument.PageInfo.Builder(layout.width, layout.height, index + 1).create())
                        unfinished = page
                        page.canvas.drawColor(Color.WHITE)
                        page.canvas.drawBitmap(normalized, null, RectF(layout.left, layout.top, layout.right, layout.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
                        document.finishPage(page); unfinished = null; current()
                    } finally { normalized?.recycle(); decoded?.recycle() }
                }
                FileOutputStream(file).use { stream ->
                    val bounded = PhotoAlbumPdf.WriteBudget(stream, current = ::active)
                    document.writeTo(bounded); current(); stream.fd.sync(); outputDigest = bounded.finish()
                }
            } finally {
                unfinished?.let { runCatching { document.finishPage(it) } }
                document.close()
            }
            current()
            check(file.isFile && file.length() in 1..PhotoAlbumPdf.MAX_OUTPUT_BYTES) { "PDF preparation did not complete" }
            return PhotoAlbumPdfPreview(owner, originals.toList(), file, requireNotNull(outputDigest), plans)
        } catch (error: OutOfMemoryError) {
            file?.delete(); throw IllegalStateException("Not enough memory for this PDF. Try fewer photos", error)
        } catch (error: Throwable) { file?.delete(); throw error }
    }

    fun open(context: ReactApplicationContext, preview: PhotoAlbumPdfPreview, request: LibraryDuplicateRequest, records: () -> List<MediaRecord>) {
        request.requireActive(); requireCurrent(context, preview.owner, preview.originals, records)
        check(preview.file.isFile && preview.file.length() in 1..PhotoAlbumPdf.MAX_OUTPUT_BYTES) { "PDF expired. Prepare another copy" }
        val digest = MessageDigest.getInstance("SHA-256")
        preview.file.inputStream().use { input ->
            request.bind(input)
            try {
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    request.requireActive(); val n = input.read(buffer); if (n < 0) break
                    total += n; check(total <= PhotoAlbumPdf.MAX_OUTPUT_BYTES) { "Prepared PDF changed" }; digest.update(buffer, 0, n)
                }
            } finally { request.detach(input) }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == preview.digest) { "Prepared PDF changed. Prepare another copy" }
        request.requireActive(); requireCurrent(context, preview.owner, preview.originals, records)
        val uri = preview.uri(context)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Noble photo PDF", uri)
        }
        val chooser = Intent.createChooser(intent, "Open photo PDF").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        request.requireActive(); context.startActivity(chooser)
    }
}
