package app.honorable.search

import java.util.PriorityQueue

/** Model-neutral contract. Mobile builds can provide an on-device implementation. */
interface VisionUnderstandingService {
    val modelId: String
    val analysisVersion: Int
    fun analyze(media: MediaRecord): VisionUnderstanding?
}

data class EnrichmentSignals(
    val createdAtEpochMs: Long,
    val retrievalCount: Int = 0,
    val candidateSetCount: Int = 0,
    val isScreenshotOrDocument: Boolean = false,
    val isRecentEvent: Boolean = false
)

data class EnrichmentJob(val mediaId: Long, val priority: Double, val enqueuedAtEpochMs: Long)

/** In-memory scheduling policy; durable stores can persist the same jobs and signals. */
class VisionEnrichmentQueue(private val now: () -> Long = System::currentTimeMillis) {
    private val queued = mutableSetOf<Long>()
    private val jobs = PriorityQueue<EnrichmentJob>(compareByDescending<EnrichmentJob> { it.priority }.thenBy { it.enqueuedAtEpochMs })

    @Synchronized fun offer(mediaId: Long, signals: EnrichmentSignals): Boolean {
        if (!queued.add(mediaId)) return false
        val ageDays = ((now() - signals.createdAtEpochMs).coerceAtLeast(0) / 86_400_000.0)
        val recency = (30.0 - ageDays).coerceAtLeast(0.0) / 30.0
        val priority = recency * 4 + signals.retrievalCount.coerceAtMost(20) * .35 +
            signals.candidateSetCount.coerceAtMost(50) * .12 +
            (if (signals.isScreenshotOrDocument) 1.5 else 0.0) + (if (signals.isRecentEvent) 2.0 else 0.0)
        jobs += EnrichmentJob(mediaId, priority, now())
        return true
    }

    @Synchronized fun poll(): EnrichmentJob? = jobs.poll()?.also { queued.remove(it.mediaId) }
    @Synchronized fun size(): Int = jobs.size
}

data class GatedVisionConfig(
    val enabled: Boolean = false,
    val maxCandidates: Int = 3,
    val minSemanticConfidence: Double = .30,
    val minTopMargin: Double = .03
) { init { require(maxCandidates in 0..5) } }

enum class QueryComplexity { SIMPLE, MODERATE, COMPLEX }

object QueryComplexityClassifier {
    fun classify(query: SearchQuery): QueryComplexity {
        val conceptCount = listOf(query.objects, query.activities, query.scenes, query.colors, query.peopleTerms).count { it.isNotEmpty() }
        return when {
            query.terms.size >= 5 || conceptCount >= 3 || (query.activities.isNotEmpty() && query.colors.isNotEmpty()) -> QueryComplexity.COMPLEX
            query.terms.size >= 2 || conceptCount >= 2 || query.activities.isNotEmpty() -> QueryComplexity.MODERATE
            else -> QueryComplexity.SIMPLE
        }
    }
}

data class SmartTriggerDecision(val shouldRefine: Boolean, val reason: String, val complexity: QueryComplexity)

object SmartVisionTrigger {
    fun decide(query: SearchQuery, matches: List<SearchMatch>, config: GatedVisionConfig): SmartTriggerDecision {
        val complexity=QueryComplexityClassifier.classify(query)
        val gate=ConfidenceGatedVision.decide(matches,config)
        if(!config.enabled) return SmartTriggerDecision(false,"disabled",complexity)
        if(matches.take(config.maxCandidates).all{it.media.visionUnderstanding!=null}) return SmartTriggerDecision(false,"all candidates cached",complexity)
        if(complexity==QueryComplexity.COMPLEX) return SmartTriggerDecision(true,"complex multi-signal query",complexity)
        if(query.activities.isNotEmpty()) return SmartTriggerDecision(true,"activity understanding",complexity)
        if(query.colors.isNotEmpty()&&query.objects.isNotEmpty()) return SmartTriggerDecision(true,"color and object disambiguation",complexity)
        return SmartTriggerDecision(gate.analyzeCandidateIds.isNotEmpty(),gate.reason,complexity)
    }
}

data class SearchRefinementConfig(val vision:GatedVisionConfig=GatedVisionConfig(),val budgetMs:Long=0) {
    init { require(budgetMs >= 0) }
}

data class ProgressiveSearchResult(
    val fast: List<SearchMatch>,
    val refined: List<SearchMatch>? = null,
    val vlmCalls: Int = 0,
    val budgetExhausted: Boolean = false
)

data class GatedVisionDecision(val analyzeCandidateIds: List<Long>, val reason: String)

object ConfidenceGatedVision {
    fun decide(matches: List<SearchMatch>, config: GatedVisionConfig): GatedVisionDecision {
        if (!config.enabled || config.maxCandidates == 0) return GatedVisionDecision(emptyList(), "disabled")
        val top = matches.firstOrNull() ?: return GatedVisionDecision(emptyList(), "no candidates")
        val second = matches.getOrNull(1)
        val semantic = top.breakdown.fullSemantic
        val margin = second?.let { (semantic - it.breakdown.fullSemantic).coerceAtLeast(0.0) } ?: 1.0
        if (semantic >= config.minSemanticConfidence && margin >= config.minTopMargin)
            return GatedVisionDecision(emptyList(), "fast evidence is confident")
        val candidates = matches.asSequence().filter { it.media.visionUnderstanding == null }
            .take(config.maxCandidates).map { it.media.id }.toList()
        return GatedVisionDecision(candidates, if (semantic < config.minSemanticConfidence) "low confidence" else "ambiguous top candidates")
    }
}

fun VisionUnderstanding.isCurrentFor(service: VisionUnderstandingService): Boolean =
    modelId == service.modelId && analysisVersion == service.analysisVersion
