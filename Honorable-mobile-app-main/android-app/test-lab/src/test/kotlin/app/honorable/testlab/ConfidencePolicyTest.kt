package app.honorable.testlab

import app.honorable.search.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConfidencePolicyTest {
    private fun media(kind:MediaKind=MediaKind.IMAGE,ocr:String="")=MediaRecord(1,kind,0,ocr=ocr)
    private fun match(kind:MediaKind=MediaKind.IMAGE,semantic:Double=.4,marginScore:Double=4.0,breakdown:ScoreBreakdown=ScoreBreakdown(fullSemantic=semantic))=SearchMatch(media(kind),marginScore,emptyList(),breakdown=breakdown)

    @Test fun `strong semantic match is accepted`() {
        val decision=ConfidencePolicy.evaluate(QueryParser().parse("red car"),listOf(match(),match(semantic=.30,marginScore=3.0)))
        assertEquals(ConfidenceOutcome.ACCEPT,decision.decision)
    }
    @Test fun `weak nearest neighbor is rejected`() {
        assertEquals("LOW_ABSOLUTE_EVIDENCE",ConfidencePolicy.evaluate(QueryParser().parse("red car"),listOf(match(semantic=.12))).reason)
    }
    @Test fun `multi condition partial match is reduced`() {
        val q=QueryParser().parse("red car on the beach")
        val top=match(semantic=.35,breakdown=ScoreBreakdown(fullSemantic=.35,colors=2.0,conceptCoverage=.8))
        val next=match(semantic=.32,marginScore=3.0)
        assertEquals("INSUFFICIENT_SIGNAL_AGREEMENT",ConfidencePolicy.evaluate(q,listOf(top,next)).reason)
    }
    @Test fun `negative conflict is rejected`() {
        val top=match(breakdown=ScoreBreakdown(fullSemantic=.4,negativePenalty=3.0))
        assertEquals("NEGATIVE_CONDITION_CONFLICT",ConfidencePolicy.evaluate(QueryParser().parse("beach without people"),listOf(top)).reason)
    }
    @Test fun `strong OCR evidence remains accepted`() {
        val top=match(semantic=.34,breakdown=ScoreBreakdown(fullSemantic=.34,ocr=12.0))
        assertEquals(ConfidenceOutcome.ACCEPT,ConfidencePolicy.evaluate(QueryParser().parse("receipt with ID 4821"),listOf(top)).decision)
    }
    @Test fun `media intent conflict is rejected`() {
        assertEquals("MEDIA_INTENT_CONFLICT",ConfidencePolicy.evaluate(QueryParser().parse("photo of dog"),listOf(match(MediaKind.VIDEO))).reason)
        assertEquals(ConfidenceOutcome.ACCEPT,ConfidencePolicy.evaluate(QueryParser().parse("video of dog"),listOf(match(MediaKind.VIDEO))).decision)
    }
}
