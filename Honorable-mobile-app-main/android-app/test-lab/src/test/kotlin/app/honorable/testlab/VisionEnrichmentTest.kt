package app.honorable.testlab

import app.honorable.search.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VisionEnrichmentTest {
    private fun match(id:Long,semantic:Double,vision:VisionUnderstanding?=null)=SearchMatch(
        MediaRecord(id,MediaKind.IMAGE,0,visionUnderstanding=vision),semantic,emptyList(),breakdown=ScoreBreakdown(fullSemantic=semantic))

    @Test fun `priority queue favors recent frequently retrieved media`() {
        val now=1_000_000_000L;val q=VisionEnrichmentQueue{now}
        q.offer(1,EnrichmentSignals(now-100*86_400_000L))
        q.offer(2,EnrichmentSignals(now,retrievalCount=4,candidateSetCount=10))
        assertEquals(2,q.poll()!!.mediaId);assertEquals(1,q.size())
    }

    @Test fun `queue deduplicates jobs`() { val q=VisionEnrichmentQueue{100};assertTrue(q.offer(1,EnrichmentSignals(0)));assertFalse(q.offer(1,EnrichmentSignals(0))) }

    @Test fun `gate skips confident fast result`() {
        val d=ConfidenceGatedVision.decide(listOf(match(1,.50),match(2,.30)),GatedVisionConfig(enabled=true,maxCandidates=3))
        assertTrue(d.analyzeCandidateIds.isEmpty())
    }

    @Test fun `gate caps uncached ambiguous candidates`() {
        val cached=VisionUnderstanding(modelId="local",analysisVersion=1)
        val d=ConfidenceGatedVision.decide(listOf(match(1,.31,cached),match(2,.30),match(3,.29),match(4,.28)),GatedVisionConfig(enabled=true,maxCandidates=2))
        assertEquals(listOf(2L,3L),d.analyzeCandidateIds)
    }

    @Test fun `query complexity separates simple moderate and complex`() {
        val parser=QueryParser()
        assertEquals(QueryComplexity.SIMPLE,QueryComplexityClassifier.classify(parser.parse("ocean")))
        assertEquals(QueryComplexity.MODERATE,QueryComplexityClassifier.classify(parser.parse("black shoes")))
        assertEquals(QueryComplexity.COMPLEX,QueryComplexityClassifier.classify(parser.parse("person wearing red shirt playing tennis outside")))
    }

    @Test fun `complex uncached query triggers refinement despite adequate margin`() {
        val q=QueryParser().parse("person wearing red shirt playing tennis outside")
        assertTrue(SmartVisionTrigger.decide(q,listOf(match(1,.50),match(2,.30)),GatedVisionConfig(enabled=true,maxCandidates=3)).shouldRefine)
    }
}
