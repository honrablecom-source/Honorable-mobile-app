package app.honorable.search

import java.util.LinkedHashMap
import kotlin.math.abs

data class QueryEmbeddings(val fullQuery: FloatArray?, val concepts: Map<String, FloatArray>)

/** Text inference happens once per distinct query/sub-concept; indexed media is never re-inferred at search time. */
class SemanticQueryEncoder(private val embeddings: EmbeddingService, cacheSize: Int = 64) {
    private val cache = object : LinkedHashMap<String, FloatArray?>(cacheSize, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray?>?) = size > cacheSize
    }
    @Synchronized private fun embed(text: String): FloatArray? = if (cache.containsKey(text)) cache[text] else embeddings.text(text)?.takeIf { it.size == embeddings.dimension }?.also { cache[text] = it } ?: run { cache[text] = null; null }
    fun encode(query: SearchQuery): QueryEmbeddings = QueryEmbeddings(embed(query.raw), query.semanticConcepts.associateWithNotNull(::embed))
    private inline fun <K,V:Any> Iterable<K>.associateWithNotNull(transform:(K)->V?):Map<K,V>{val result=linkedMapOf<K,V>();for(item in this)transform(item)?.let{result[item]=it};return result}
}

/** Two-stage retrieval: ANN candidate lookup followed by the richer hybrid ranker. */
class HybridSearchEngine(private val vectorIndex: VectorIndex, private val ranker: SearchRanker = SearchRanker(), private val candidateLimit: Int = 500) {
    fun search(query: SearchQuery, recordsById: Map<Long, MediaRecord>, embeddings: QueryEmbeddings): List<SearchMatch> {
        val candidateIds = embeddings.fullQuery?.let { vectorIndex.nearest(it, candidateLimit).mapTo(linkedSetOf()) { pair -> pair.first } }
        val candidates = if (candidateIds.isNullOrEmpty()) recordsById.values.toList() else candidateIds.mapNotNull(recordsById::get)
        return ranker.rank(query, candidates, embeddings.fullQuery, embeddings.concepts)
    }
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
