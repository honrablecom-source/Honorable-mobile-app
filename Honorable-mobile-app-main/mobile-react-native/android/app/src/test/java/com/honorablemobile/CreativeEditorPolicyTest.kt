package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, manifest = Config.NONE)
class CreativeEditorPolicyTest {
    @Test fun onlyPrivateExpectedOriginAndSafeAssetPaths() {
        assertEquals("creative-editors/image/index.html", CreativeEditorPolicy.assetPath("image", "https", CreativeEditorPolicy.HOST, -1, "/image/index.html"))
        for (path in listOf("/video/index.html", "/image/../secret", "/image//index.html", "/image/%2e%2e/file", "/image/")) {
            assertNull(CreativeEditorPolicy.assetPath("image", "https", CreativeEditorPolicy.HOST, -1, path))
        }
        assertNull(CreativeEditorPolicy.assetPath("image", "https", "example.com", -1, "/image/index.html"))
        assertNull(CreativeEditorPolicy.assetPath("image", "http", CreativeEditorPolicy.HOST, -1, "/image/index.html"))
        assertNull(CreativeEditorPolicy.assetPath("image", "https", CreativeEditorPolicy.HOST, 8080, "/image/index.html"))
        assertNull(CreativeEditorPolicy.kind("model"))
    }
    @Test fun packagedFullEditorBundlesPassActualStreamingVerification() {
        val folder = "Honorable-mobile-app-main/mobile-react-native/android/app/src/main/assets"
        val root = generateSequence(java.io.File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
            .map { java.io.File(it, folder) }.firstOrNull { java.io.File(it, "creative-editors/video/bundle.json").isFile }
            ?: java.io.File("src/main/assets")
        for(kind in listOf("image", "video")) assertTrue("The real packaged full editor must match its byte receipt", CreativeEditorPolicy.verified(kind) { java.io.File(root, it).inputStream() })
    }
    @Test fun compressedRuntimeStreamsMatchTheOriginalCompilerOutputs() {
        val folder = "Honorable-mobile-app-main/mobile-react-native/android/app/src/main/assets"
        val root = generateSequence(java.io.File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
            .map { java.io.File(it, folder) }.firstOrNull { java.io.File(it, "creative-editors/video/bundle.json").isFile }
            ?: java.io.File("src/main/assets")
        for(kind in listOf("image", "video")) {
            val prefix = "creative-editors/$kind/"
            assertFalse(java.io.File(root, prefix + "editor_bg.wasm").exists())
            val expected = JSONObject(java.io.File(root, prefix + "provenance.json").readText()).getJSONObject("wasm")
            val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
            CreativeEditorPolicy.openRuntimeAsset(prefix + "editor_bg.wasm") { java.io.File(root, it).inputStream() }.use { input ->
                val buffer = ByteArray(32768)
                while(true) { val n=input.read(buffer); if(n<0)break; if(n==0)continue;count+=n;digest.update(buffer,0,n) }
            }
            assertEquals(expected.getLong("bytes"), count)
            assertEquals(expected.getString("sha256"), digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        }
    }
    @Test fun everyBundledByteMustMatchTheReceipt() {
        val data = linkedMapOf("index.html" to "editor".toByteArray(), "host-bridge.js" to "bridge".toByteArray(), "editor.wasm" to byteArrayOf(0,97,115,109))
        val files = JSONObject()
        for ((name, bytes) in data) files.put(name, JSONObject().put("bytes", bytes.size).put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }))
        val receipt = JSONObject().put("version",1).put("kind","image").put("sourceRevision","a".repeat(40)).put("files",files).toString().toByteArray()
        fun valid(): Boolean = CreativeEditorPolicy.verified("image") { path -> if(path.endsWith("bundle.json")) receipt.inputStream() else (data[path.substringAfterLast('/')] ?: error("Missing asset")).inputStream() }
        assertTrue(valid())
        data["editor.wasm"] = byteArrayOf(0,97,115,108)
        assertFalse(valid())
        data.remove("editor.wasm")
        assertFalse(valid())
        assertFalse(CreativeEditorPolicy.verified("image") { "{}".byteInputStream() })
    }
}
