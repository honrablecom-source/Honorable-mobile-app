package com.honorablemobile

import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Stream bounded chunks to a private temporary copy; never write to an imported URI. */
internal class CreativeEditorShare(private val directory: File, val id: Int, private val length: Int, val title: String) {
    companion object {
        private val extensions = setOf("png","jpg","jpeg","webp","gif","psd","psb","pcraft","svg","tif","tiff","exr","hdr","bmp","tga","ico","qoi","pnm","mp4","mov","mxf","avi","webm","wav","mp3","flac","aiff","fcproj","otio","xml","edl","srt","vtt","zip","omf","aaf","json","csv","txt")
        fun title(value: String): String {
            require(value.length in 1..120 && value.none { it.code < 32 || it == '/' || it == '\\' })
            require(value.substringAfterLast('.', "").lowercase() in extensions)
            return value
        }
        fun contentType(title: String): String = when (title.substringAfterLast('.').lowercase()) {
            "mp4" -> "video/mp4"; "mov" -> "video/quicktime"; "webm" -> "video/webm"; "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "psd" -> "image/vnd.adobe.photoshop"
            "png","jpg","jpeg","webp","gif","svg" -> CreativeEditorPolicy.mime(title)
            "srt","vtt","csv","txt","edl" -> "text/plain"; "json","fcproj","otio" -> "application/json"
            "zip","pcraft" -> "application/zip"; else -> "application/octet-stream"
        }
    }
    private val root = directory.canonicalFile
    private val partial: File
    private var stream: FileOutputStream? = null
    private var count = 0
    private var done = false
    init {
        require(id in 1..1_000_000_000 && length in 1..CreativeEditorPolicy.MAX_EXPORT)
        title(title)
        require(root.isDirectory || root.mkdirs())
        partial = File.createTempFile("noble-creative-", ".part", root)
        require(partial.canonicalFile.parentFile == root)
        stream = FileOutputStream(partial)
    }
    @Synchronized fun append(value: String): Boolean = runCatching {
        check(!done && stream != null)
        require(value.isNotEmpty() && value.length <= 43692 && value.length % 4 == 0 && Regex("[A-Za-z0-9+/]+={0,2}").matches(value))
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.isNotEmpty() && bytes.size <= 32768 && Base64.encodeToString(bytes, Base64.NO_WRAP) == value)
        require(count.toLong() + bytes.size <= length)
        stream?.write(bytes) ?: error("Export stream is closed")
        count += bytes.size; true
    }.getOrElse { cancel(); false }
    @Synchronized fun finish(): File {
        check(!done && stream != null && count == length)
        stream?.flush(); stream?.close(); stream = null
        check(partial.length() == length.toLong())
        val extension = title.substringAfterLast('.').lowercase()
        val result = File(root, "Noble-editor-${UUID.randomUUID()}.$extension")
        check(result.canonicalFile.parentFile == root && !result.exists() && partial.renameTo(result))
        check(result.length() == length.toLong()); done = true
        return result
    }
    @Synchronized fun cancel() {
        runCatching { stream?.close() }; stream = null
        if (!done) partial.delete()
        done = true
    }
}
