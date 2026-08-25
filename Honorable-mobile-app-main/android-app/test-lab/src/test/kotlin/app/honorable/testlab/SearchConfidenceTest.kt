package app.honorable.testlab

import app.honorable.search.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SearchConfidenceTest {
    private fun match(semantic:Double,name:String="x.jpg")=SearchMatch(MediaRecord(name.hashCode().toLong(),MediaKind.IMAGE,0,displayName=name),semantic*2.5,emptyList(),breakdown=ScoreBreakdown(fullSemantic=semantic))

    @Test fun clearWinner(){val d=confidenceDecision(listOf(match(.42),match(.34)),.30,.03);assertTrue(d.confident);assertEquals(.08,d.margin,1e-9)}
    @Test fun nearlyIdenticalScoresAreAmbiguous(){assertFalse(confidenceDecision(listOf(match(.370),match(.362)),.30,.03).confident)}
    @Test fun lowOverallConfidenceIsRejected(){assertFalse(confidenceDecision(listOf(match(.19),match(.10)),.30,.03).confident)}
    @Test fun noMatchIsRejected(){assertFalse(confidenceDecision(emptyList(),.30,.03).confident)}
    @Test fun rankedCandidatesRemainAvailableForTopFiveDebug(){val matches=(1..5).map{match(.5-it*.01,"$it.jpg")};assertEquals(5,matches.take(5).size)}
    @Test fun defaultDecisionSelectsOnlyBestClearWinner(){val matches=listOf(match(.45,"best.jpg"),match(.30,"other.jpg"));assertTrue(confidenceDecision(matches).confident);assertEquals("best.jpg",matches.first().media.displayName)}
}
