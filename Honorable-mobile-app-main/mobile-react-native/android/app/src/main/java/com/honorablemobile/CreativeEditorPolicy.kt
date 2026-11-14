package com.honorablemobile

import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONObject

/** Only immutable bundled editors are executable. No hosted editor or uploaded media. */
internal object CreativeEditorPolicy {
    const val HOST = "noble-editors.local"
    const val MAX_IMPORT = 128L * 1024 * 1024
    const val MAX_EXPORT = 64 * 1024 * 1024
    private val kinds = setOf("image", "video")
    private val segment = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,159}")
    fun kind(value: String?): String? = value?.takeIf { it in kinds }
    fun relativePath(value: String): Boolean = value.length in 1..240 && value.split('/').all { segment.matches(it) && it != "." && it != ".." }
    fun assetPath(kind: String, scheme: String?, host: String?, port: Int, path: String?): String? {
        if (kind !in kinds || scheme != "https" || host != HOST || port !in setOf(-1, 443) || path == null) return null
        val prefix = "/$kind/"
        if (!path.startsWith(prefix)) return null
        val relative = path.removePrefix(prefix)
        return if (relativePath(relative)) "creative-editors/$kind/$relative" else null
    }
    fun mime(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "html" -> "text/html"; "js", "mjs" -> "text/javascript"; "wasm" -> "application/wasm"
        "css" -> "text/css"; "json" -> "application/json"; "txt", "tsv" -> "text/plain"
        "woff2" -> "font/woff2"; "woff" -> "font/woff"; "ttf" -> "font/ttf"; "otf" -> "font/otf"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "svg" -> "image/svg+xml"
        "webp" -> "image/webp"; "gif" -> "image/gif"
        else -> "application/octet-stream"
    }
    const val CSP = "default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' blob:; connect-src 'self' blob:; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
    fun verified(kind: String, open: (String) -> InputStream): Boolean = runCatching {
        require(kind in kinds)
        val root = "creative-editors/$kind/"
        val receiptBytes = open(root + "bundle.json").use { input ->
            val bytes = input.readBytesBounded(128 * 1024); require(bytes.isNotEmpty()); bytes
        }
        val receipt = JSONObject(String(receiptBytes, Charsets.UTF_8))
        require(receipt.getInt("version") == 1 && receipt.getString("kind") == kind)
        require(Regex("[0-9a-f]{40}").matches(receipt.getString("sourceRevision")))
        val files = receipt.getJSONObject("files")
        require(files.length() in 3..512 && files.has("index.html") && files.has("host-bridge.js"))
        var wasm = false
        for (name in files.keys()) {
            require(relativePath(name))
            val pin = files.getJSONObject(name); val length = pin.getLong("bytes")
            require(length in 1..(64L * 1024 * 1024))
            val expected = pin.getString("sha256"); require(Regex("[0-9a-f]{64}").matches(expected))
            val digest = MessageDigest.getInstance("SHA-256"); var actual = 0L
            open(root + name).use { input ->
                val buffer = ByteArray(32768)
                while (true) { val n = input.read(buffer); if (n < 0) break; if (n == 0) continue
                    actual += n; require(actual <= length); digest.update(buffer, 0, n)
                }
            }
            require(actual == length && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == expected)
            if (name.endsWith(".wasm")) wasm = true
        }
        require(wasm); true
    }.getOrDefault(false)
    private fun InputStream.readBytesBounded(max: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
        while (true) { val n = read(buffer); if (n < 0) break; if (n == 0) continue
            require(output.size() + n <= max); output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }
}
