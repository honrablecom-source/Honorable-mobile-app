package app.honorable.testlab

import app.honorable.search.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SeranModelProfileTest {
    @Test fun `V1 and V2 have real distinct native plans with safe persistence policy`() {
        assertEquals(listOf(SeranModelProfile.SERAN_V1,SeranModelProfile.SERAN_V2),SeranModelPolicy.selectableModels())
        assertEquals(SeranModelProfile.SERAN_V2,SeranModelPolicy.resolvePersisted("SERAN_V2"))
        assertEquals(SeranModelProfile.SERAN_V1,SeranModelPolicy.resolvePersisted("SERAN_V3"))
        assertThrows(IllegalArgumentException::class.java){SeranModelPolicy.authorize("SERAN_ULTRA")}
        assertEquals(200,SeranModelPolicy.v1.batchLimit);assertEquals(300,SeranModelPolicy.v2.batchLimit)
        val v1Photo=SeranModelPolicy.v1.searchPlan(QueryParser().parse("red car"));assertTrue(v1Photo.processors.containsAll(setOf(EvidenceProcessor.TINY_CLIP,EvidenceProcessor.OCR,EvidenceProcessor.COLORS,EvidenceProcessor.LABELS)))
        val v1Video=SeranModelPolicy.v1.searchPlan(QueryParser().parse("tennis serve video"));assertFalse(v1Video.semanticVideo);assertEquals(setOf(EvidenceProcessor.METADATA),v1Video.processors)
        val v2Video=SeranModelPolicy.v2.searchPlan(QueryParser().parse("tennis serve video"));assertTrue(v2Video.semanticVideo);assertTrue(v2Video.processors.containsAll(setOf(EvidenceProcessor.VIDEO_SAMPLING,EvidenceProcessor.VIDEO_EMBEDDING,EvidenceProcessor.TINY_CLIP,EvidenceProcessor.OCR,EvidenceProcessor.COLORS)))
        assertEquals(SeranModelPolicy.v1.photoProcessors,SeranModelPolicy.v2.photoProcessors)
        assertTrue(SeranModelPolicy.v2.videoProcessors.containsAll(SeranModelPolicy.v1.videoProcessors))
    }
}
