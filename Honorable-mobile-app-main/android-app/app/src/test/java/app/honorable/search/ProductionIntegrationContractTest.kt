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
            source("app/honorable/search/LocalMediaDatabase.kt")
        ).joinToString("\n")
        listOf("OkHttpClient","HttpURLConnection","java.net.","retrofit2.","ktor.client","https://","http://").forEach {
            assertFalse("Network dependency found: $it",runtime.contains(it))
        }
    }
}
