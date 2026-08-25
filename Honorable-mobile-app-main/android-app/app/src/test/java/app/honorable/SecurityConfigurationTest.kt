package app.honorable

import app.honorable.search.ModelAssetIntegrity
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigurationTest {
    @Test fun bundledModelAndTokenizerMatchPinnedDigests() {
        ModelAssetIntegrity.requireModel(File("src/main/assets/tinyclip/model_int8.onnx").readBytes())
        ModelAssetIntegrity.requireTokenizer(File("src/main/assets/tinyclip/tokenizer.json").readBytes())
    }

    @Test fun manifestRestrictsBackupsCleartextAndExportedComponents() {
        val manifest=File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android:exported=\"true\""))
        assertFalse(manifest.contains("android:debuggable=\"true\""))
    }
}
