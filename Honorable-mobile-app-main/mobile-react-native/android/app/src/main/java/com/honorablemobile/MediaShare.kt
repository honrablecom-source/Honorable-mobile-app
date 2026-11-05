package com.honorablemobile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Explicit user action only: delegate temporary read access, never rewrite or upload the source. */
internal object MediaShare {
    fun source(raw: String, kind: String): Uri {
        require(kind == "IMAGE" || kind == "VIDEO") { "Choose a photo or video to share" }
        require(raw.length in 1..8192) { "The selected source is unavailable" }
        val uri = Uri.parse(raw)
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank() &&
            !uri.path.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "Choose a local photo or video from your library"
        }
        return uri
    }

    fun send(uri: Uri, kind: String, mime: String): Intent {
        source(uri.toString(), kind)
        val prefix = if (kind == "IMAGE") "image/" else "video/"
        require(mime.startsWith(prefix) && mime.length > prefix.length &&
            !mime.contains('*') && !mime.any { it.isWhitespace() || it.code < 32 }) {
            "This source does not match the selected media type"
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Selected media", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun prepare(context: Context, raw: String, kind: String): Intent = prepare(
        raw, kind, { context.contentResolver.getType(it) }, { uri ->
            context.contentResolver.openFileDescriptor(uri, "r").use {
                requireNotNull(it) { "The selected file could not be read" }
            }
        }
    )

    fun prepare(raw: String, kind: String, mimeOf: (Uri) -> String?, read: (Uri) -> Unit): Intent {
        val uri = source(raw, kind)
        val mime = mimeOf(uri)
            ?: error("The selected file type is unavailable. Choose it again from your library.")
        val intent = send(uri, kind, mime)
        read(uri)
        return Intent.createChooser(intent, if (kind == "IMAGE") "Share original photo" else "Share original video").apply {
            clipData = intent.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
