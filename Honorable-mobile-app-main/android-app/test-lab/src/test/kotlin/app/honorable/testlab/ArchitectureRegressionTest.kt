package app.honorable.testlab

import app.honorable.search.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArchitectureRegressionTest {
    @Test fun `multiple vectors for one video use the closest representative frame`() {
        val index=LocalVectorIndex()
        index.upsert(1,floatArrayOf(1f,0f))
        index.add(1,floatArrayOf(0f,1f))
        index.upsert(2,floatArrayOf(.8f,.2f))
        assertEquals(1L,index.nearest(floatArrayOf(0f,1f),1).single().first)
    }

    @Test fun `short visual query receives only local deterministic variants`() {
        val query=QueryParser().parse("birthday chocolate cake")
        assertEquals(listOf("birthday chocolate cake","a photo of birthday chocolate cake"),LocalPromptPlanner.variants(query))
    }

    @Test fun `coordinator searches a reusable catalog with video frame evidence`() {
        val coordinator=LocalSearchCoordinator(SemanticQueryEncoder(TestEmbeddings()))
        coordinator.replaceRecords(
            listOf(MediaRecord(7,MediaKind.VIDEO,0,videoFrames=listOf(VideoFrame(1_000,"",emptySet(),floatArrayOf(0f,1f)))))
        )
        val run=coordinator.search("video tennis")
        assertEquals(1,run.diagnostics.catalogSize)
        assertEquals(7L,run.matches.single().media.id)
    }

    @Test fun `index job lifecycle reports partial completion and rejects regression`() {
        var now=10L;val job=IndexJobController{now++};job.start();job.progress(2,5,0)
        assertThrows(IllegalStateException::class.java){job.progress(1,5,0)}
        job.progress(5,5,1);job.complete(IndexStats(3,1,0,failed=1,skipped=1))
        assertEquals(IndexJobState.PARTIAL_FAILURE,job.snapshot().state)
        assertEquals(1,job.snapshot().failedItems)
    }

    @Test fun `caption model update invalidates captions only`() {
        val before=ProcessorVersions(captionVlm="caption-1")
        val stored=EvidenceProcessor.entries.associateWith(before::key)
        val stale=EvidenceInvalidationPlanner.stale(stored,before.copy(captionVlm="caption-2"),true)
        assertEquals(setOf(EvidenceProcessor.CAPTION_VLM),stale)
        assertFalse(EvidenceProcessor.OCR in stale);assertFalse(EvidenceProcessor.TINY_CLIP in stale)
    }

    @Test fun `tinyclip preprocessing update does not invalidate OCR`() {
        val before=ProcessorVersions(tinyClipPreprocessing="pre-1")
        val stale=EvidenceInvalidationPlanner.stale(EvidenceProcessor.entries.associateWith(before::key),before.copy(tinyClipPreprocessing="pre-2"),false)
        assertEquals(setOf(EvidenceProcessor.TINY_CLIP),stale)
    }

    @Test fun `retry policy is bounded and exponential`() {
        val policy=RetryPolicy(maxAttempts=3,initialDelayMs=100,maximumDelayMs=350)
        assertEquals(listOf(100L,200L,350L),(1..3).map(policy::delayMs))
        assertThrows(IllegalArgumentException::class.java){RetryPolicy(maxAttempts=0)}
    }

    @Test fun `processor circuit opens and later recovers`() {
        var now=0L;val circuit=ProcessorCircuitBreaker(threshold=2,coolDownMs=100){now}
        circuit.failure(EvidenceProcessor.CAPTION_VLM);assertTrue(circuit.allow(EvidenceProcessor.CAPTION_VLM))
        circuit.failure(EvidenceProcessor.CAPTION_VLM);assertFalse(circuit.allow(EvidenceProcessor.CAPTION_VLM))
        now=101;assertTrue(circuit.allow(EvidenceProcessor.CAPTION_VLM))
    }

    @Test fun `resource defaults constrain heavy processors`() {
        val low=DeviceCapability(128,8,true,true,false).defaultLimits();assertEquals(1,low.bitmapDecodes);assertEquals(1,low.tinyClip);assertEquals(1,low.videoDecode)
        val normal=DeviceCapability(512,8,false,true,false).defaultLimits();assertTrue(normal.bitmapDecodes<=2);assertEquals(1,normal.databaseWrites)
    }

    @Test fun `catalog generation prevents redundant rebuild and accepts evidence change`() {
        val coordinator=LocalSearchCoordinator(SemanticQueryEncoder(TestEmbeddings()));coordinator.replaceRecords(emptyList(),7);coordinator.replaceRecords(emptyList(),7);assertEquals(7,coordinator.generation());coordinator.replaceRecords(emptyList(),8);assertEquals(8,coordinator.generation())
    }

    @Test fun `query planner chooses cheap deterministic paths`() {
        assertEquals(QueryPath.METADATA,QueryExecutionPlanner.plan("IMG_3821").path)
        assertEquals(QueryPath.DATE,QueryExecutionPlanner.plan("photos from last July").path)
        assertEquals(QueryPath.OCR,QueryExecutionPlanner.plan("receipt containing Walmart").path)
        assertEquals(QueryPath.SEMANTIC,QueryExecutionPlanner.plan("blue car at night").path)
    }

    private class TestEmbeddings:EmbeddingService {
        override val modelId="test"
        override val dimension=2
        override fun image(bytes:ByteArray):FloatArray?=null
        override fun text(query:String)=if("tennis" in query)floatArrayOf(0f,1f)else floatArrayOf(1f,0f)
    }
}
