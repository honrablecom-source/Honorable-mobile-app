package app.honorable.search

import org.junit.Assert.*
import org.junit.Test

class SeranModelPolicyTest {
    @Test fun `V1 and V2 are selectable while future models stay locked`() {
        assertEquals(listOf(SeranModelProfile.SERAN_V1,SeranModelProfile.SERAN_V2),SeranModelPolicy.selectableModels())
        assertFails { SeranModelPolicy.authorize("SERAN_V3") };assertFails { SeranModelPolicy.authorize("SERAN_ULTRA") }
    }
    @Test fun `invalid persisted selections safely fall back to V1`() {
        assertEquals(SeranModelProfile.SERAN_V2,SeranModelPolicy.resolvePersisted("SERAN_V2"));assertEquals(SeranModelProfile.SERAN_V1,SeranModelPolicy.resolvePersisted("SERAN_V3"));assertEquals(SeranModelProfile.SERAN_V1,SeranModelPolicy.resolvePersisted("JS_FAKE"))
    }
    @Test fun `batch limits are bounded independently from worker concurrency`() { assertEquals(200,SeranModelPolicy.v1.batchLimit);assertEquals(300,SeranModelPolicy.v2.batchLimit) }
    @Test fun `V1 photo plan uses real lightweight evidence`() {
        val plan=SeranModelPolicy.v1.searchPlan(QueryParser().parse("red car"));assertFalse(plan.semanticVideo);assertTrue(plan.processors.containsAll(setOf(EvidenceProcessor.TINY_CLIP,EvidenceProcessor.OCR,EvidenceProcessor.COLORS,EvidenceProcessor.LABELS,EvidenceProcessor.METADATA)))
    }
    @Test fun `V1 does not expose semantic video intelligence`() { val plan=SeranModelPolicy.v1.searchPlan(QueryParser().parse("tennis serve video"));assertEquals(MediaKind.VIDEO,plan.mediaKind);assertFalse(plan.semanticVideo);assertEquals(setOf(EvidenceProcessor.METADATA),plan.processors) }
    @Test fun `V2 exposes representative frame video evidence`() {
        val plan=SeranModelPolicy.v2.searchPlan(QueryParser().parse("tennis serve video"));assertTrue(plan.semanticVideo);assertTrue(plan.processors.containsAll(setOf(EvidenceProcessor.VIDEO_SAMPLING,EvidenceProcessor.VIDEO_EMBEDDING,EvidenceProcessor.TINY_CLIP,EvidenceProcessor.OCR,EvidenceProcessor.COLORS)))
    }
    @Test fun `V1 and V2 share compatible photo evidence versions`() { assertEquals(SeranModelPolicy.v1.photoProcessors,SeranModelPolicy.v2.photoProcessors) }
    @Test fun `downgrading hides but does not invalidate cached V2 processors`() { assertTrue(SeranModelPolicy.v2.videoProcessors.containsAll(SeranModelPolicy.v1.videoProcessors));assertFalse(EvidenceProcessor.VIDEO_EMBEDDING in SeranModelPolicy.v1.videoProcessors) }
    private fun assertFails(block:()->Unit){try{block();fail("Expected native authorization failure")}catch(_:IllegalArgumentException){}}
}
