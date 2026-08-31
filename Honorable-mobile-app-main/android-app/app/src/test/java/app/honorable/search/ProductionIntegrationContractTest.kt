package app.honorable.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** JVM CI contract checks for Android integration wiring; physical MediaStore behavior remains a device test. */
class ProductionIntegrationContractTest {
    private fun source(path:String)=File("src/main/java/$path").readText()

    @Test fun `production index is backed by MediaStore and local SQLite`() {
        val production=source("app/honorable/search/ProductionMemorySearch.kt")
        val database=source("app/honorable/search/LocalMediaDatabase.kt")
        assertTrue(production.contains("MediaStore.Images.Media.EXTERNAL_CONTENT_URI"))
        assertTrue(production.contains("MediaStore.Video.Media.EXTERNAL_CONTENT_URI"))
        assertTrue(production.contains("database.upsert"))
        assertTrue(database.contains("CREATE TABLE media_index"))
    }

    @Test fun `permission denied and indexing states are wired to production UI`() {
        val production=source("app/honorable/search/ProductionMemorySearch.kt")
        val ui=source("app/honorable/MainActivity.kt")
        assertTrue(production.contains("READ_MEDIA_IMAGES"))
        assertTrue(production.contains("READ_MEDIA_VIDEO"))
        assertTrue(production.contains("READ_EXTERNAL_STORAGE"))
        assertTrue(ui.contains("MemorySearchState.PermissionRequired->PermissionState"))
        assertTrue(ui.contains("MemorySearchState.Indexing->IndexingState"))
    }

    @Test fun `production results consume ranked indexed matches not sample items`() {
        val ui=source("app/honorable/MainActivity.kt")
        val results=ui.substringAfter("@Composable private fun SearchResults").substringBefore("@Composable private fun RealMemory")
        assertTrue(results.contains("matches:List<SearchMatch>"))
        assertTrue(results.contains("BestMatchCard(ranked.first()"))
        assertTrue(results.contains("items(ranked.drop(1)"))
        assertFalse(results.contains("memoryItems"))
    }

    @Test fun `local search runtime has no network client dependency`() {
        val runtime=listOf(
            source("app/honorable/search/ProductionMemorySearch.kt"),
            source("app/honorable/search/SearchCore.kt"),
            source("app/honorable/search/SearchPipeline.kt"),
            source("app/honorable/search/LocalMediaDatabase.kt"),
            source("app/honorable/search/AndroidTinyClipEmbeddingService.kt"),
            source("app/honorable/search/VisionEnrichment.kt"),
            source("app/honorable/search/AndroidEngineRuntime.kt")
        ).joinToString("\n")
        listOf("OkHttpClient","HttpURLConnection","java.net.","retrofit2.","ktor.client","https://","http://").forEach {
            assertFalse("Network dependency found: $it",runtime.contains(it))
        }
    }

    @Test fun `application does not request internet permission`() {
        val manifest=File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android.permission.INTERNET"))
    }

    @Test fun `media store IDs are namespaced by kind`() {
        assertTrue(stableMediaId(MediaKind.IMAGE,42)!=stableMediaId(MediaKind.VIDEO,42))
        assertTrue(stableMediaId(MediaKind.IMAGE,42)>=0)
    }

    @Test fun `partial library and resource budgets are centralized`() {
        val runtime=source("app/honorable/search/AndroidEngineRuntime.kt")
        assertTrue(runtime.contains("READ_MEDIA_VISUAL_USER_SELECTED"))
        assertTrue(runtime.contains("MODEL_INFERENCE to 1"))
        assertTrue(runtime.contains("VIDEO_DECODE to 1"))
        assertTrue(runtime.contains("class EngineDoctor"))
        assertFalse(runtime.contains("displayName"))
    }

    @Test fun `durable indexing is unique observable cancellable and not viewmodel owned`() {
        val work=source("app/honorable/search/DurableIndexingWork.kt")
        val production=source("app/honorable/search/ProductionMemorySearch.kt")
        assertTrue(work.contains("enqueueUniqueWork"));assertTrue(work.contains("ExistingWorkPolicy.KEEP"))
        assertTrue(work.contains("getWorkInfosForUniqueWorkFlow"));assertTrue(work.contains("cancelUniqueWork"))
        assertTrue(work.contains("Result.retry()"));assertTrue(work.contains("MAX_ATTEMPTS=3"))
        assertFalse(production.substringAfter("class MemoriesViewModel").contains("indexer.synchronize"))
    }

    @Test fun `evidence versions and generation invalidation are persisted independently`() {
        val db=source("app/honorable/search/LocalMediaDatabase.kt");val runtime=source("app/honorable/search/IndexingRuntime.kt")
        assertTrue(db.contains("CREATE TABLE IF NOT EXISTS evidence_version"));assertTrue(db.contains("index_generation"))
        assertTrue(runtime.contains("enum class EvidenceProcessor"));assertTrue(runtime.contains("EvidenceInvalidationPlanner"))
    }

    @Test fun `diagnostics report excludes private media fields`() {
        val runtime=source("app/honorable/search/AndroidEngineRuntime.kt")
        val report=runtime.substringAfter("fun privacySafeReport")
        listOf("displayName","vision_caption","ocr TEXT","media.uri","path=").forEach{assertFalse(report.contains(it))}
        assertTrue(report.contains("schema="));assertTrue(report.contains("generation="));assertTrue(report.contains("limits="))
    }
}
