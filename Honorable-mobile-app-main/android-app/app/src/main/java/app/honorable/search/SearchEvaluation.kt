package app.honorable.search

data class EvaluationCase(
    val query: String,
    val expectedMediaIds: Set<Long>,
    val difficulty: String,
    val category: String,
    val mediaKind: MediaKind? = null
)
data class RetrievalMetrics(val recallAt1: Double, val recallAt5: Double, val recallAt10: Double, val mrr: Double, val queryCount: Int)

object SearchEvaluationHarness {
    fun evaluate(cases: List<EvaluationCase>, search: (EvaluationCase) -> List<Long>): RetrievalMetrics {
        require(cases.isNotEmpty()) { "A labeled evaluation dataset is required" }
        var r1=0;var r5=0;var r10=0;var reciprocal=0.0
        cases.forEach { case ->
            val ranked=search(case);if(ranked.take(1).any(case.expectedMediaIds::contains))r1++
            if(ranked.take(5).any(case.expectedMediaIds::contains))r5++
            if(ranked.take(10).any(case.expectedMediaIds::contains))r10++
            val rank=ranked.indexOfFirst(case.expectedMediaIds::contains);if(rank>=0)reciprocal+=1.0/(rank+1)
        }
        return RetrievalMetrics(r1.toDouble()/cases.size,r5.toDouble()/cases.size,r10.toDouble()/cases.size,reciprocal/cases.size,cases.size)
    }
}
