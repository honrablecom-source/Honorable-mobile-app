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
    /** Bounded original-file bundle. Validate every item before reading any source. */
    fun batchSources(raw: String): List<Pair<String, String>> {
        require(raw.length in 1..170000) { "Choose up to 20 originals" }
        val parser = org.json.JSONTokener(raw)
        val values = parser.nextValue() as? org.json.JSONArray ?: throw IllegalArgumentException("Invalid selected media")
        require(parser.nextClean() == 0.toChar()) { "Invalid selected media" }
        require(values.length() in 1..20) { "Choose 1 to 20 originals" }
        val result = (0 until values.length()).map { index ->
            val item = values.getJSONObject(index)
            require(item.keys().asSequence().toSet() == setOf("mediaUri", "mediaType")) { "Invalid selected media" }
            require(item.opt("mediaUri") is String && item.opt("mediaType") is String) { "Invalid selected media" }
            val uri = item.getString("mediaUri"); val kind = item.getString("mediaType")
            source(uri, kind)
            uri to kind
        }
        require(result.map { it.first }.distinct().size == result.size) { "Choose each original only once" }
        return result
    }

    fun prepareBatch(context: Context, raw: String): Intent = prepareBatch(batchSources(raw),
        { context.contentResolver.getType(it) }, { uri ->
            context.contentResolver.openFileDescriptor(uri, "r").use { requireNotNull(it) { "A selected original could not be read" } }
        })

    fun prepareBatch(items: List<Pair<String, String>>, mimeOf: (Uri) -> String?, read: (Uri) -> Unit): Intent {
        require(items.size in 1..20) { "Choose 1 to 20 originals" }
        require(items.map { it.first }.distinct().size == items.size) { "Choose each original only once" }
        val validated = items.map { (raw, kind) ->
            val uri = source(raw, kind)
            val mime = mimeOf(uri) ?: error("A selected original's type is unavailable")
            send(uri, kind, mime) // Same MIME/type policy as single sharing.
            uri to mime
        }
        validated.forEach { read(it.first) }
        val types = validated.map { it.second }.distinct()
        val mime = when {
            types.size == 1 -> types.first()
            types.all { it.startsWith("image/") } -> "image/*"
            types.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
        val uris = ArrayList(validated.map { it.first })
        val clips = ClipData.newRawUri("Selected originals", uris.first())
        uris.drop(1).forEach { clips.addItem(ClipData.Item(it)) }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = clips
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share ${uris.size} original ${if (uris.size == 1) "file" else "files"}").apply {
            clipData = clips
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

}
