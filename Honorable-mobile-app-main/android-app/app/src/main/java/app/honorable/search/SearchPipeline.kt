package app.honorable.search

import java.util.LinkedHashMap
import kotlin.math.abs

data class QueryEmbeddings(
    val fullQuery: FloatArray?,
    val concepts: Map<String, FloatArray>,
    /** Local CLIP prompt variants used only for candidate recall; ranking still uses the raw query. */
    val retrievalVectors: List<FloatArray> = listOfNotNull(fullQuery)
)

/** Text inference happens once per distinct query/sub-concept; indexed media is never re-inferred at search time. */
class SemanticQueryEncoder(private val embeddings: EmbeddingService, cacheSize: Int = 64) {
    private val cache = object : LinkedHashMap<String, FloatArray?>(cacheSize, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray?>?) = size > cacheSize
    }
    @Synchronized private fun embed(text: String): FloatArray? = if (cache.containsKey(text)) cache[text] else embeddings.text(text)?.takeIf { it.size == embeddings.dimension }?.also { cache[text] = it } ?: run { cache[text] = null; null }
    fun encode(query: SearchQuery): QueryEmbeddings {
        val raw=embed(query.raw)
        val retrieval=LocalPromptPlanner.variants(query).mapNotNull(::embed).distinctBy { it.contentHashCode() }
        return QueryEmbeddings(raw,query.semanticConcepts.associateWithNotNull(::embed),retrieval.ifEmpty{listOfNotNull(raw)})
    }
    private inline fun <K,V:Any> Iterable<K>.associateWithNotNull(transform:(K)->V?):Map<K,V>{val result=linkedMapOf<K,V>();for(item in this)transform(item)?.let{result[item]=it};return result}
}

/** Deterministic, offline prompt planning improves CLIP recall for terse visual queries. */
object LocalPromptPlanner {
    fun variants(query: SearchQuery): List<String> {
        val raw=query.raw.trim();if(raw.isEmpty())return emptyList()
        if(query.terms.size !in 1..3||query.ocrTerms.isNotEmpty()||query.mediaSubtype==MediaSubtype.SCREENSHOT)return listOf(raw)
        val visual=query.terms.joinToString(" ")
        val prefix=if(query.mediaKind==MediaKind.VIDEO)"a video frame of" else "a photo of"
        return listOf(raw,"$prefix $visual").distinct()
    }
}

/** Two-stage retrieval: ANN candidate lookup followed by the richer hybrid ranker. */
class HybridSearchEngine(private val vectorIndex: VectorIndex, private val ranker: SearchRanker = SearchRanker(), private val candidateLimit: Int = 500) {
    fun search(query: SearchQuery, recordsById: Map<Long, MediaRecord>, embeddings: QueryEmbeddings): List<SearchMatch> {
        val candidateIds = embeddings.retrievalVectors.flatMap { vectorIndex.nearest(it,candidateLimit) }.sortedByDescending { it.second }.mapTo(linkedSetOf()) { it.first }
        val candidates = if (candidateIds.isNullOrEmpty()) recordsById.values.toList() else candidateIds.mapNotNull(recordsById::get)
        return ranker.rank(query, candidates, embeddings.fullQuery, embeddings.concepts)
    }
}

data class SearchDiagnostics(
    val catalogSize: Int,
    val resultCount: Int,
    val queryEncodingMs: Long,
    val retrievalAndRankingMs: Long,
    val totalMs: Long,
    val queryParsingMs:Long=0,
    val candidateRetrievalMs:Long=0,
    val ocrRetrievalMs:Long=0,
    val metadataFilteringMs:Long=0,
    val vectorSearchMs:Long=0,
    val scoreNormalizationMs:Long=0,
    val fusionMs:Long=0,
    val rankingMs:Long=retrievalAndRankingMs,
    val materializationMs:Long=0,
    val plan:QueryPath=QueryPath.HYBRID
)

data class SearchRun(
    val query: SearchQuery,
    val matches: List<SearchMatch>,
    val confidence: ConfidenceDecision,
    val diagnostics: SearchDiagnostics
)

/**
 * Owns an immutable in-memory search snapshot. Index construction happens after synchronization,
 * not on every query, and all inference remains behind the local [EmbeddingService].
 */
class LocalSearchCoordinator(
    private val encoder: SemanticQueryEncoder,
    private val parser: QueryParser = QueryParser()
) {
    @Volatile private var catalog = SearchCatalog(emptyMap(), LocalVectorIndex())
    @Volatile private var catalogGeneration:Long = -1

    fun replaceRecords(records: List<MediaRecord>,generation:Long=catalogGeneration+1) {
        if(generation==catalogGeneration)return
        val vectors = LocalVectorIndex()
        records.forEach { record ->
            var hasVector = false
            record.embedding?.let { vectors.upsert(record.id, it); hasVector = true }
            record.videoFrames.forEach { frame -> frame.embedding?.let {
                if (hasVector) vectors.add(record.id, it) else vectors.upsert(record.id, it)
                hasVector = true
            } }
        }
        catalog = SearchCatalog(records.associateBy(MediaRecord::id), vectors)
        catalogGeneration=generation
    }

    fun generation():Long=catalogGeneration

    fun search(raw: String): SearchRun {
        val started = System.nanoTime()
        val parsingStarted=System.nanoTime();val query = parser.parse(raw);val parsingMs=elapsedMs(parsingStarted);val plan=QueryExecutionPlanner.plan(raw)
        val encodingStarted = System.nanoTime()
        val embeddings = if(plan.needsEmbedding)encoder.encode(query) else QueryEmbeddings(null,emptyMap(),emptyList())
        val encodingMs = elapsedMs(encodingStarted)
        val snapshot = catalog
        val retrievalStarted = System.nanoTime()
        val matches = HybridSearchEngine(snapshot.vectors).search(query, snapshot.records, embeddings)
        val retrievalMs = elapsedMs(retrievalStarted)
        return SearchRun(
            query,
            matches,
            confidenceDecision(query, matches),
            SearchDiagnostics(snapshot.records.size, matches.size, encodingMs, retrievalMs, elapsedMs(started),queryParsingMs=parsingMs,candidateRetrievalMs=retrievalMs,vectorSearchMs=if(plan.needsEmbedding)retrievalMs else 0,rankingMs=retrievalMs,plan=plan.path)
        )
    }

    private data class SearchCatalog(val records: Map<Long, MediaRecord>, val vectors: LocalVectorIndex)
    private fun elapsedMs(started: Long) = (System.nanoTime() - started) / 1_000_000
}

/** Lightweight evidence computed while indexing downsampled pixels, never during a query. */
object ColorEvidenceAnalyzer {
    private val prototypes = mapOf(
        "red" to Triple(190,45,45), "blue" to Triple(55,95,180), "green" to Triple(55,145,70),
        "black" to Triple(25,25,25), "white" to Triple(235,235,235), "yellow" to Triple(220,195,45),
        "orange" to Triple(220,120,35), "purple" to Triple(125,65,155), "pink" to Triple(220,125,160),
        "brown" to Triple(115,75,45), "gray" to Triple(125,125,125)
    )
    fun dominantColors(argbSamples: IntArray, maxColors: Int = 3): Set<String> {
        if (argbSamples.isEmpty()) return emptySet()
        val counts = mutableMapOf<String,Int>()
        argbSamples.asSequence().filterIndexed { index, _ -> index % maxOf(1,argbSamples.size/256)==0 }.forEach { pixel ->
            val r=pixel shr 16 and 255;val g=pixel shr 8 and 255;val b=pixel and 255
            val nearest=prototypes.minBy { (_,rgb) -> val dr=r-rgb.first;val dg=g-rgb.second;val db=b-rgb.third;dr*dr+dg*dg+db*db }.key
            counts[nearest]=(counts[nearest]?:0)+1
        }
        return counts.entries.sortedByDescending{it.value}.take(maxColors).mapTo(linkedSetOf()){it.key}
    }
}

data class FrameCandidate(val timestampMs: Long, val sceneFingerprint: Long)

/** Keeps temporal coverage and scene changes while avoiding adjacent duplicate frames. */
object RepresentativeFrameSelector {
    fun select(candidates: List<FrameCandidate>, maxFrames: Int = 12, minGapMs: Long = 1_500): List<FrameCandidate> {
        if (maxFrames <= 0) return emptyList()
        val distinct = candidates.sortedBy { it.timestampMs }.fold(mutableListOf<FrameCandidate>()) { kept, candidate ->
            val previous=kept.lastOrNull();if(previous==null||candidate.timestampMs-previous.timestampMs>=minGapMs||hamming(previous.sceneFingerprint,candidate.sceneFingerprint)>=8)kept+=candidate;kept
        }
        if (distinct.size <= maxFrames) return distinct
        val step=(distinct.lastIndex).toDouble()/(maxFrames-1).coerceAtLeast(1)
        return (0 until maxFrames).map { distinct[(it*step).toInt()] }.distinctBy { it.timestampMs }
    }
    private fun hamming(a:Long,b:Long)=java.lang.Long.bitCount(a xor b)
}
