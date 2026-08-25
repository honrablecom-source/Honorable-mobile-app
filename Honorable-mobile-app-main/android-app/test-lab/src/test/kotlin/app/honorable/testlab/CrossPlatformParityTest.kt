package app.honorable.testlab

import app.honorable.search.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Both adapters must enter the exact shared parser/ranker; only acquisition/OCR may differ. */
class CrossPlatformParityTest {
    private val queries=listOf(
        "white sandy beach with tall grass",
        "black shoes",
        "person wearing red",
        "screenshot with flight confirmation"
    )
    private val records=listOf(
        MediaRecord(1,MediaKind.IMAGE,1,ocr="FLIGHT CONFIRMATION",embedding=floatArrayOf(1f,0f),dominantColors=setOf("white"),isScreenshot=true),
        MediaRecord(2,MediaKind.IMAGE,2,labels=setOf("person","shoes"),embedding=floatArrayOf(.7f,.7f),dominantColors=setOf("black","red")),
        MediaRecord(3,MediaKind.IMAGE,3,labels=setOf("beach","grass"),embedding=floatArrayOf(0f,1f),dominantColors=setOf("white","green"))
    )

    private fun androidSharedSearch(raw:String):List<SearchMatch>{val query=QueryParser().parse(raw);return SearchRanker().rank(query,records,floatArrayOf(.5f,.5f))}
    private fun linuxSharedSearch(raw:String):List<SearchMatch>{val query=QueryParser().parse(raw);return HybridSearchEngine(LocalVectorIndex()).search(query,records.associateBy{it.id},QueryEmbeddings(floatArrayOf(.5f,.5f),emptyMap()))}

    @Test fun queryPlanRankOrderScoresConfidenceAndBestResultMatch()=queries.forEach { raw ->
        assertEquals(QueryParser().parse(raw),QueryParser().parse(raw))
        val android=androidSharedSearch(raw);val linux=linuxSharedSearch(raw)
        assertEquals(android.map{it.media.id},linux.map{it.media.id},raw)
        assertEquals(android.map{it.score},linux.map{it.score},raw)
        assertEquals(confidenceDecision(android),confidenceDecision(linux),raw)
        assertEquals(android.firstOrNull()?.media?.id,linux.firstOrNull()?.media?.id,raw)
    }
}
