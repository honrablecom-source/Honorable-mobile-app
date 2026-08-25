package app.honorable.search

import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlin.math.sqrt

data class MediaRecord(
    val id: Long,
    val kind: MediaKind,
    val capturedAtEpochMs: Long,
    val location: String? = null,
    val ocr: String = "",
    val labels: Set<String> = emptySet(),
    val embedding: FloatArray? = null,
    val videoFrames: List<VideoFrame> = emptyList(),
    val metadataTerms: Set<String> = emptySet(),
    val dominantColors: Set<String> = emptySet(),
    val isScreenshot: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val uri: String = "",
    val displayName: String = "",
    val durationMs: Long? = null,
    val visionUnderstanding: VisionUnderstanding? = null
)

data class VisionUnderstanding(
    val caption:String="",val objects:Set<String> = emptySet(),val activities:Set<String> = emptySet(),
    val scenes:Set<String> = emptySet(),val colors:Set<String> = emptySet(),val clothing:Set<String> = emptySet(),
    val environment:Set<String> = emptySet(),val textualContext:Set<String> = emptySet(),val attributes:Set<String> = emptySet(),
    val modelId:String,val analysisVersion:Int=1,val analyzedAtEpochMs:Long=0,val analysisTimeMs:Long=0
) { fun terms()=objects+activities+scenes+colors+clothing+environment+textualContext+attributes }

enum class MediaKind { IMAGE, VIDEO }
enum class MediaSubtype { SCREENSHOT }
enum class TimeOfDay { MORNING, EVENING, NIGHT }
enum class MatchConfidence { STRONG, POSSIBLE, WEAK }

data class VideoFrame(
    val timestampMs: Long,
    val ocr: String,
    val labels: Set<String>,
    val embedding: FloatArray?,
    val dominantColors: Set<String> = emptySet(),
    val sceneFingerprint: Long? = null,
    val visionUnderstanding: VisionUnderstanding? = null
)

data class SearchQuery(
    val terms: List<String>,
    val mediaKind: MediaKind? = null,
    val afterEpochMs: Long? = null,
    val beforeEpochMs: Long? = null,
    val location: String? = null,
    val raw: String = terms.joinToString(" "),
    val mediaSubtype: MediaSubtype? = null,
    val semanticConcepts: List<String> = emptyList(),
    val ocrTerms: List<String> = emptyList(),
    val colors: Set<String> = emptySet(),
    val activities: Set<String> = emptySet(),
    val scenes: Set<String> = emptySet(),
    val objects: Set<String> = emptySet(),
    val peopleTerms: Set<String> = emptySet(),
    val negativeTerms: Set<String> = emptySet(),
    val timeOfDay: TimeOfDay? = null,
    val unresolvedTemporalPhrase: String? = null
)

data class ScoreBreakdown(
    val fullSemantic: Double = 0.0,
    val conceptCoverage: Double = 0.0,
    val ocr: Double = 0.0,
    val metadata: Double = 0.0,
    val labels: Double = 0.0,
    val colors: Double = 0.0,
    val videoFrames: Double = 0.0,
    val negativePenalty: Double = 0.0,
    val vlmCaption: Double = 0.0,
    val vlmObjects: Double = 0.0,
    val vlmActivities: Double = 0.0,
    val vlmScenes: Double = 0.0
)

data class SearchMatch(
    val media: MediaRecord,
    val score: Double,
    val explanations: List<String>,
    val bestTimestampMs: Long? = null,
    val confidence: MatchConfidence = MatchConfidence.WEAK,
    val breakdown: ScoreBreakdown = ScoreBreakdown()
)

data class ConfidenceDecision(val confident:Boolean,val semantic:Double,val margin:Double,val reason:String)
fun confidenceDecision(matches:List<SearchMatch>,minConfidence:Double=.30,minMargin:Double=.03):ConfidenceDecision {
    val best=matches.firstOrNull()?.breakdown?.fullSemantic?:0.0
    val second=matches.getOrNull(1)?.breakdown?.fullSemantic
    val margin=if(second==null&&matches.isNotEmpty())1.0 else (best-(second?:best)).coerceAtLeast(0.0)
    val reason=when {
        matches.isEmpty()->"no candidates"
        best<minConfidence->"semantic confidence below ${"%.3f".format(minConfidence)}"
        margin<minMargin->"top candidates are too close"
        else->"absolute confidence and top-1 margin passed"
    }
    return ConfidenceDecision(matches.isNotEmpty()&&best>=minConfidence&&margin>=minMargin,best,margin,reason)
}

interface EmbeddingService { val modelId: String; val dimension: Int; fun image(bytes: ByteArray): FloatArray?; fun text(query: String): FloatArray? }
interface OCRService { fun recognize(bytes: ByteArray): String }
interface VideoAnalysisService { fun representativeFrames(uri: String, cancellation: () -> Boolean): Sequence<VideoFrame> }
interface MediaIndexer { fun synchronize(cancellation: () -> Boolean, paused: () -> Boolean): IndexStats }
interface VectorIndex { fun upsert(id: Long, vector: FloatArray); fun nearest(vector: FloatArray, limit: Int): List<Pair<Long, Double>> }
data class IndexStats(val added: Int, val updated: Int, val deleted: Int)
data class IndexCompatibility(val schemaVersion: Int = 5, val modelId: String = TinyClipEmbeddingService.MODEL_ID, val embeddingDimension: Int = 512, val preprocessingVersion: String = "tinyclip-clip-v1") {
    fun compatibleWith(other: IndexCompatibility?) = other != null && schemaVersion == other.schemaVersion && modelId == other.modelId && embeddingDimension == other.embeddingDimension && preprocessingVersion == other.preprocessingVersion
}

class QueryParser(private val clock: Clock = Clock.systemDefaultZone()) {
    fun parse(raw: String): SearchQuery {
        val normalized = OcrNormalizer.normalize(raw)
        val tokens = normalized.split(' ').filter(String::isNotBlank)
        val kind = when {
            tokens.any { it in VIDEO_WORDS } -> MediaKind.VIDEO
            tokens.any { it in IMAGE_WORDS } || "screenshot" in tokens -> MediaKind.IMAGE
            else -> null
        }
        val subtype = if ("screenshot" in tokens) MediaSubtype.SCREENSHOT else null
        val colors = tokens.filterTo(mutableSetOf()) { it in COLORS }
        val activities = tokens.mapNotNullTo(mutableSetOf()) { ACTIVITY_ALIASES[it] }
        val scenes = tokens.mapNotNullTo(mutableSetOf()) { SCENE_ALIASES[it] }
        val objects = tokens.filterTo(mutableSetOf()) { it in OBJECTS }
        val people = tokens.filterTo(mutableSetOf()) { it in PEOPLE }
        val negative = parseNegative(tokens)
        val temporal = parseTemporal(normalized)
        val location = Regex("(?:in|at|near|by) ([a-z]+(?: [a-z]+){0,2})").find(normalized)?.groupValues?.get(1)?.takeUnless { candidate ->
            candidate.split(' ').any { it in LOCATION_STOP }
        }
        val useful = tokens.filter { it !in STOP_WORDS && it !in VIDEO_WORDS && it !in IMAGE_WORDS && it !in TEMPORAL_WORDS && it !in negative }
        val ocrTerms = extractOcrTerms(raw, subtype, useful)
        val concepts = buildConcepts(useful, colors, activities, scenes, objects, people)
        return SearchQuery(
            terms = useful.distinct(), mediaKind = kind, afterEpochMs = temporal.start, beforeEpochMs = temporal.end,
            location = location, raw = raw, mediaSubtype = subtype, semanticConcepts = concepts, ocrTerms = ocrTerms,
            colors = colors, activities = activities, scenes = scenes, objects = objects, peopleTerms = people,
            negativeTerms = negative, timeOfDay = temporal.timeOfDay, unresolvedTemporalPhrase = temporal.unresolved
        )
    }

    private fun parseNegative(tokens: List<String>): Set<String> {
        val result = mutableSetOf<String>()
        tokens.forEachIndexed { index, token ->
            if (token in NEGATION_WORDS) tokens.drop(index + 1).takeWhile { it !in setOf("with", "from", "at", "in", "by") }.take(2).filterTo(result) { it !in STOP_WORDS }
        }
        return result
    }

    private fun extractOcrTerms(raw: String, subtype: MediaSubtype?, terms: List<String>): List<String> {
        val quoted = Regex("[\"“]([^\"”]+)[\"”]").findAll(raw).map { OcrNormalizer.normalize(it.groupValues[1]) }.toList()
        if (quoted.isNotEmpty()) return quoted
        val properPhrase = Regex("""\b(?:[A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)\b""").findAll(raw).map { OcrNormalizer.normalize(it.value) }.toList()
        return (properPhrase + if (subtype == MediaSubtype.SCREENSHOT) terms.chunked(3).map { it.joinToString(" ") } else emptyList()).distinct()
    }

    private fun buildConcepts(terms: List<String>, colors: Set<String>, activities: Set<String>, scenes: Set<String>, objects: Set<String>, people: Set<String>): List<String> {
        val concepts = linkedSetOf<String>()
        colors.forEach { color -> objects.firstOrNull()?.let { concepts += "$color $it" } ?: run { concepts += color } }
        activities.forEach { concepts += it }; scenes.forEach { concepts += it }; objects.forEach { concepts += it }; people.forEach { concepts += it }
        terms.windowed(2).filter { pair -> pair.any { it in colors || it in OBJECTS || it in ACTIVITY_ALIASES } }.forEach { concepts += it.joinToString(" ") }
        return concepts.take(MAX_CONCEPTS)
    }

    private fun parseTemporal(text: String): TemporalParse {
        val zone = clock.zone
        val now = ZonedDateTime.now(clock)
        fun bounds(start: LocalDate, endExclusive: LocalDate) = TemporalParse(start.atStartOfDay(zone).toInstant().toEpochMilli(), endExclusive.atStartOfDay(zone).toInstant().toEpochMilli() - 1)
        val timeOfDay = when { "morning" in text -> TimeOfDay.MORNING; "evening" in text || "sunset" in text -> TimeOfDay.EVENING; "night" in text -> TimeOfDay.NIGHT; else -> null }
        val parsed = when {
            "today" in text -> bounds(now.toLocalDate(), now.toLocalDate().plusDays(1))
            "yesterday" in text -> bounds(now.toLocalDate().minusDays(1), now.toLocalDate())
            "last week" in text -> { val thisWeek = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); bounds(thisWeek.minusWeeks(1), thisWeek) }
            "last month" in text -> { val thisMonth = now.toLocalDate().withDayOfMonth(1); bounds(thisMonth.minusMonths(1), thisMonth) }
            "last year" in text -> bounds(LocalDate.of(now.year - 1, 1, 1), LocalDate.of(now.year, 1, 1))
            "two years ago" in text -> bounds(LocalDate.of(now.year - 2, 1, 1), LocalDate.of(now.year - 1, 1, 1))
            "last summer" in text -> bounds(LocalDate.of(now.year - 1, 6, 1), LocalDate.of(now.year - 1, 9, 1))
            "last christmas" in text -> bounds(LocalDate.of(now.year - 1, 12, 20), LocalDate.of(now.year - 1, 12, 28))
            Regex("around june(?: (20\\d{2}))?").containsMatchIn(text) -> {
                val year = Regex("around june (20\\d{2})").find(text)?.groupValues?.get(1)?.toIntOrNull()
                if (year == null) TemporalParse(unresolved = "around June") else bounds(LocalDate.of(year, 5, 25), LocalDate.of(year, 7, 8))
            }
            else -> TemporalParse()
        }
        return parsed.copy(timeOfDay = timeOfDay)
    }

    private data class TemporalParse(val start: Long? = null, val end: Long? = null, val timeOfDay: TimeOfDay? = null, val unresolved: String? = null)
    companion object {
        val COLORS = setOf("red","blue","green","black","white","yellow","orange","purple","pink","brown","gray","grey")
        private val VIDEO_WORDS = setOf("video","videos","clip","movie")
        private val IMAGE_WORDS = setOf("photo","photos","picture","pictures","image","images")
        private val NEGATION_WORDS = setOf("without","not","exclude","excluding","no")
        private val PEOPLE = setOf("person","people","someone","son","daughter","child","children","man","woman","family")
        private val OBJECTS = setOf("car","vehicle","cake","shirt","dress","shoe","shoes","ball","burger","fries","dog","cat","airplane","plane","passport","meal","food","document")
        private val ACTIVITY_ALIASES = mapOf("playing" to "playing", "serves" to "serving", "serve" to "serving", "running" to "running", "dancing" to "dancing", "walking" to "walking", "laughing" to "laughing", "holding" to "holding", "parked" to "parked")
        private val SCENE_ALIASES = mapOf("outside" to "outdoor", "outdoors" to "outdoor", "indoors" to "indoor", "inside" to "indoor", "snow" to "snow", "water" to "water", "beach" to "beach", "restaurant" to "restaurant", "grass" to "grass", "sunset" to "sunset", "night" to "night")
        private val STOP_WORDS = setOf("find","show","the","a","an","where","my","was","is","are","of","with","that","this","only","taken","showing","from","around","in","at","by","on","near","wearing") + NEGATION_WORDS
        private val TEMPORAL_WORDS = setOf("today","yesterday","last","week","month","summer","christmas","two","years","ago","morning","night","evening")
        private val LOCATION_STOP = setOf("snow","night","summer","christmas","water","sunset","grass","airplane","restaurant")
        private const val MAX_CONCEPTS = 6
    }
}

class QueryRefiner(private val parser: QueryParser = QueryParser()) {
    fun refine(previous: SearchQuery, followUp: String): SearchQuery {
        val next = parser.parse(followUp)
        return previous.copy(
            raw = "${previous.raw} · $followUp",
            terms = (previous.terms + next.terms).distinct(),
            mediaKind = next.mediaKind ?: previous.mediaKind,
            mediaSubtype = next.mediaSubtype ?: previous.mediaSubtype,
            afterEpochMs = next.afterEpochMs ?: previous.afterEpochMs,
            beforeEpochMs = next.beforeEpochMs ?: previous.beforeEpochMs,
            location = next.location ?: previous.location,
            semanticConcepts = (previous.semanticConcepts + next.semanticConcepts).distinct().take(6),
            ocrTerms = (previous.ocrTerms + next.ocrTerms).distinct(), colors = previous.colors + next.colors,
            activities = previous.activities + next.activities, scenes = previous.scenes + next.scenes,
            objects = previous.objects + next.objects, peopleTerms = previous.peopleTerms + next.peopleTerms,
            negativeTerms = previous.negativeTerms + next.negativeTerms, timeOfDay = next.timeOfDay ?: previous.timeOfDay,
            unresolvedTemporalPhrase = next.unresolvedTemporalPhrase ?: previous.unresolvedTemporalPhrase
        )
    }
}

data class RankingWeights(
    val fullSemantic: Double = 2.5, val conceptCoverage: Double = 3.5, val ocrToken: Double = 2.4,
    val ocrExactPhrase: Double = 5.0, val metadata: Double = 1.3, val label: Double = 1.8,
    val color: Double = 2.0, val videoFrame: Double = 2.4, val negative: Double = 3.0
)

class SearchRanker(private val weights: RankingWeights = RankingWeights(), private val zoneId: ZoneId = ZoneId.systemDefault()) {
    fun rank(query: SearchQuery, records: List<MediaRecord>, queryVector: FloatArray? = null, conceptVectors: Map<String, FloatArray> = emptyMap()): List<SearchMatch> = records.asSequence()
        .filter { query.mediaKind == null || it.kind == query.mediaKind }
        .filter { query.mediaSubtype != MediaSubtype.SCREENSHOT || it.isScreenshot }
        .filter { query.afterEpochMs == null || it.capturedAtEpochMs >= query.afterEpochMs }
        .filter { query.beforeEpochMs == null || it.capturedAtEpochMs <= query.beforeEpochMs }
        .filter { query.location == null || it.location?.contains(query.location, true) == true }
        .map { score(query, it, queryVector, conceptVectors) }
        .filter { it.score > 0 }.sortedByDescending { it.score }.toList()

    private fun score(q: SearchQuery, media: MediaRecord, vector: FloatArray?, conceptVectors: Map<String, FloatArray>): SearchMatch {
        val explanations = linkedSetOf<String>()
        val normalizedOcr = OcrNormalizer.normalize(media.ocr)
        val normalizedLabels = media.labels.mapTo(mutableSetOf()) { OcrNormalizer.normalize(it) }
        val normalizedMetadata = media.metadataTerms.mapTo(mutableSetOf()) { OcrNormalizer.normalize(it) }
        val ocrTokenHits = q.terms.count { OcrNormalizer.containsFuzzyToken(normalizedOcr, it) }
        val exactPhrases = q.ocrTerms.count { normalizedOcr.contains(OcrNormalizer.normalize(it)) }
        val ocrScore = ocrTokenHits * weights.ocrToken + exactPhrases * weights.ocrExactPhrase
        if (exactPhrases > 0) explanations += "Matched text: ${q.ocrTerms.first { normalizedOcr.contains(OcrNormalizer.normalize(it)) }}"
        else if (ocrTokenHits > 0) explanations += "Matched text in media"

        val labelMatches = q.terms.filter { term -> normalizedLabels.any { fuzzy(term, it) } }
        val labelScore = labelMatches.size * weights.label
        val metadataMatches = q.terms.filter { term -> normalizedMetadata.any { fuzzy(term, it) } }
        val timeMatch = q.timeOfDay?.let { wanted ->
            val hour=Instant.ofEpochMilli(media.capturedAtEpochMs).atZone(zoneId).hour
            when(wanted){TimeOfDay.MORNING->hour in 5..11;TimeOfDay.EVENING->hour in 17..21;TimeOfDay.NIGHT->hour in 20..23||hour in 0..4}
        } ?: false
        val kindMatch = q.mediaKind != null && q.mediaKind == media.kind
        val metadataScore = metadataMatches.size * weights.metadata + (if(timeMatch) weights.metadata else 0.0) + (if(kindMatch) weights.metadata else 0.0)
        if (timeMatch) explanations += "Matched: ${q.timeOfDay!!.name.lowercase()}"
        if (kindMatch) explanations += "Matched media type: ${media.kind.name.lowercase()}"
        val colorMatches = q.colors.filter { color -> color in media.dominantColors || normalizedLabels.any { it.contains(color) } }
        val colorScore = colorMatches.size * weights.color
        if (colorMatches.isNotEmpty()) explanations += "Matched color: ${colorMatches.joinToString()}"

        val semantic = if (vector != null && media.embedding != null) cosine(vector, media.embedding).coerceAtLeast(0.0) else 0.0
        val conceptSimilarities = conceptVectors.mapNotNull { (concept, conceptVector) -> media.embedding?.let { concept to cosine(conceptVector, it).coerceAtLeast(0.0) } }
        val covered = conceptSimilarities.filter { it.second >= CONCEPT_THRESHOLD }
        val coverageRatio = if (conceptVectors.isEmpty()) 0.0 else covered.size.toDouble() / conceptVectors.size
        val weakestCovered = covered.minOfOrNull { it.second } ?: 0.0
        val conceptScore = coverageRatio * weakestCovered * weights.conceptCoverage
        covered.take(3).forEach { explanations += "Matched: ${it.first}" }

        val frameEvidence = if (media.kind == MediaKind.VIDEO) scoreFrames(q, media.videoFrames, vector, conceptVectors) else FrameEvidence()
        if (frameEvidence.score > 0) explanations += "Matched video scene at ${formatTimestamp(frameEvidence.bestTimestampMs!!)}"
        val negativeHits = q.negativeTerms.count { negative -> normalizedLabels.any { fuzzy(negative, it) } || normalizedMetadata.any { fuzzy(negative, it) } }
        val negativePenalty = negativeHits * weights.negative
        val vlm = scoreVision(q, media.visionUnderstanding)
        if (vlm.total > 0) explanations += "Visual AI: ${media.visionUnderstanding!!.caption}"
        val total = semantic * weights.fullSemantic + conceptScore + ocrScore + labelScore + metadataScore + colorScore + frameEvidence.score + vlm.total - negativePenalty
        if (labelMatches.isNotEmpty()) explanations += "Matched: ${labelMatches.take(3).joinToString(" • ")}"
        if (media.kind == MediaKind.VIDEO) explanations += "Matched: video"
        val confidence = when { total >= 7.0 -> MatchConfidence.STRONG; total >= 2.5 -> MatchConfidence.POSSIBLE; else -> MatchConfidence.WEAK }
        return SearchMatch(media, total, explanations.toList(), frameEvidence.bestTimestampMs, confidence,
            ScoreBreakdown(semantic, conceptScore, ocrScore, metadataScore, labelScore, colorScore, frameEvidence.score, negativePenalty,
                vlm.caption, vlm.objects, vlm.activities, vlm.scenes))
    }

    private fun scoreVision(q: SearchQuery, vision: VisionUnderstanding?): VisionEvidence {
        if (vision == null) return VisionEvidence()
        val caption = OcrNormalizer.normalize(vision.caption)
        fun matches(values:Set<String>, terms:Collection<String>) = terms.count { term -> values.any { fuzzy(term, it) } }.toDouble()
        val captionCoverage = if (q.terms.isEmpty()) 0.0 else q.terms.count { OcrNormalizer.containsFuzzyToken(caption, it) }.toDouble() / q.terms.size
        val objectScore = matches(vision.objects, q.objects.ifEmpty { q.terms.toSet() }) * 1.35
        val activityScore = matches(vision.activities, q.activities.ifEmpty { q.terms.toSet() }) * 1.15
        val sceneScore = matches(vision.scenes + vision.environment, q.scenes.ifEmpty { q.terms.toSet() }) * .90
        val visualColor = matches(vision.colors, q.colors) * .70
        // Coverage rewards a description satisfying the whole request; no single lexical hit can dominate TinyCLIP.
        val coverageBonus = if (q.terms.size > 1 && captionCoverage >= .99) .85 else 0.0
        return VisionEvidence(captionCoverage * 1.10 + coverageBonus, objectScore, activityScore, sceneScore + visualColor)
    }

    private fun scoreFrames(q: SearchQuery, frames: List<VideoFrame>, vector: FloatArray?, concepts: Map<String, FloatArray>): FrameEvidence {
        val distinct = frames.sortedBy { it.timestampMs }.fold(mutableListOf<VideoFrame>()) { kept, frame ->
            val redundant = kept.lastOrNull()?.let { previous -> frame.timestampMs - previous.timestampMs < MIN_FRAME_GAP_MS && frame.sceneFingerprint != null && frame.sceneFingerprint == previous.sceneFingerprint } == true
            if (!redundant) kept += frame
            kept
        }
        val scores = distinct.map { frame ->
            val text = OcrNormalizer.normalize(frame.ocr)
            val vision=scoreVision(q,frame.visionUnderstanding)
            val lexical = q.terms.count { term -> OcrNormalizer.containsFuzzyToken(text, term) || frame.labels.any { fuzzy(term, it) } } * 1.2 + vision.total
            val full = if (vector != null && frame.embedding != null) cosine(vector, frame.embedding).coerceAtLeast(0.0) else 0.0
            val coverage = if (concepts.isEmpty() || frame.embedding == null) 0.0 else concepts.values.count { cosine(it, frame.embedding) >= CONCEPT_THRESHOLD }.toDouble() / concepts.size
            frame to (lexical + full + coverage)
        }.sortedByDescending { it.second }
        if (scores.isEmpty() || scores.first().second <= 0) return FrameEvidence()
        val aggregate = scores.take(3).mapIndexed { index, pair -> pair.second * when(index){0->1.0;1->.5;else->.25} }.sum() * weights.videoFrame
        return FrameEvidence(aggregate, scores.first().first.timestampMs)
    }

    private data class FrameEvidence(val score: Double = 0.0, val bestTimestampMs: Long? = null)
    private data class VisionEvidence(val caption:Double=0.0,val objects:Double=0.0,val activities:Double=0.0,val scenes:Double=0.0){val total get()=caption+objects+activities+scenes}
    companion object {
        private const val CONCEPT_THRESHOLD = 0.20
        private const val MIN_FRAME_GAP_MS = 1_500L
        private fun fuzzy(a: String, b: String): Boolean { val left=OcrNormalizer.normalize(a);val right=OcrNormalizer.normalize(b);return left==right||right.contains(left)||(left.length>3&&OcrNormalizer.levenshtein(left,right)<=1) }
        private fun cosine(a: FloatArray, b: FloatArray): Double { if(a.size!=b.size)return 0.0;var dot=0.0;var aa=0.0;var bb=0.0;a.indices.forEach{dot+=a[it]*b[it];aa+=a[it]*a[it];bb+=b[it]*b[it]};return if(aa==0.0||bb==0.0)0.0 else dot/sqrt(aa*bb) }
        private fun formatTimestamp(ms: Long): String { val seconds=ms/1000;return "%02d:%02d".format(seconds/60,seconds%60) }
    }
}

/** Platform-neutral unavailable fallback. Android production uses [AndroidTinyClipEmbeddingService]. */
open class TinyClipEmbeddingService : EmbeddingService {
    companion object { const val MODEL_ID = "TinyCLIP-ViT-8M-16-Text-3M-YFCC15M-int8" }
    override val modelId = MODEL_ID; override val dimension = 512
    override fun image(bytes: ByteArray): FloatArray? = null // A validated Android-compatible ONNX asset is required.
    override fun text(query: String): FloatArray? = null
}

/** Multi-probe SimHash narrows candidates before exact cosine reranking. */
class LocalVectorIndex(private val bits: Int = 12) : VectorIndex {
    private val vectors = mutableMapOf<Long, FloatArray>()
    private val buckets = mutableMapOf<Int, MutableSet<Long>>()
    override fun upsert(id: Long, vector: FloatArray) { vectors[id]?.let { buckets[signature(it)]?.remove(id) }; val value=normalized(vector);vectors[id]=value;buckets.getOrPut(signature(value)){mutableSetOf()}+=id }
    override fun nearest(vector: FloatArray, limit: Int): List<Pair<Long, Double>> {
        if(limit<=0)return emptyList();val q=normalized(vector);val sig=signature(q);val ids=linkedSetOf<Long>();buckets[sig]?.let(ids::addAll)
        for(bit in 0 until bits){buckets[sig xor (1 shl bit)]?.let(ids::addAll);if(ids.size>=limit*4)break}
        if(ids.size<limit) ids.addAll(vectors.keys)
        return ids.asSequence().mapNotNull{ id->vectors[id]?.let{id to cosine(q,it)} }.sortedByDescending{it.second}.take(limit).toList()
    }
    private fun signature(v: FloatArray): Int { var result=0;for(bit in 0 until bits){var sum=0.0;for(i in v.indices){val mixed=((i+1L)*0x9E3779B97F4A7C15UL.toLong() xor (bit+17L)*0xBF58476D1CE4E5B9UL.toLong());sum+=v[i]*(if((mixed and 1L)==0L)1 else -1)};if(sum>=0)result=result or(1 shl bit)};return result }
    private fun normalized(value:FloatArray):FloatArray{val norm=sqrt(value.sumOf{(it*it).toDouble()}).toFloat();return if(norm==0f)value.copyOf()else FloatArray(value.size){value[it]/norm}}
    private fun cosine(a:FloatArray,b:FloatArray)=a.indices.sumOf{(a[it]*b.getOrElse(it){0f}).toDouble()}
}

object OcrNormalizer {
    fun normalize(value: String): String = value.lowercase().replace('—','-').replace('–','-').replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().replace(Regex("\\s+")," ")
    fun containsFuzzyToken(normalizedText: String, rawTerm: String): Boolean { val term=normalize(rawTerm);val correctedTerm=commonMistakes(term);val compact=commonMistakes(normalizedText.replace(" ",""));return compact.contains(correctedTerm.replace(" ",""))||normalizedText.split(' ').any { token -> token==term||token.contains(term)||(term.length>3&&levenshtein(commonMistakes(token),correctedTerm)<=1) } }
    private fun commonMistakes(value:String)=value.replace('0','o').replace('1','l').replace("rn","m")
    fun levenshtein(a:String,b:String):Int{var prev=IntArray(b.length+1){it};for(i in a.indices){val cur=IntArray(b.length+1);cur[0]=i+1;for(j in b.indices)cur[j+1]=minOf(cur[j]+1,prev[j+1]+1,prev[j]+if(a[i]==b[j])0 else 1);prev=cur};return prev[b.length]}
}

/** Developer diagnostics only; no production UI references this formatter. */
object SearchDebugTool {
    fun report(query: SearchQuery, match: SearchMatch): String = buildString {
        appendLine("query=${query.raw}");appendLine("terms=${query.terms}");appendLine("concepts=${query.semanticConcepts}")
        appendLine("filters=media:${query.mediaKind}, subtype:${query.mediaSubtype}, after:${query.afterEpochMs}, before:${query.beforeEpochMs}, location:${query.location}")
        appendLine("semantic=${match.breakdown.fullSemantic}");appendLine("conceptCoverage=${match.breakdown.conceptCoverage}")
        appendLine("ocr=${match.breakdown.ocr}");appendLine("metadata=${match.breakdown.metadata}");appendLine("labels=${match.breakdown.labels}")
        appendLine("colors=${match.breakdown.colors}");appendLine("videoFrames=${match.breakdown.videoFrames}");appendLine("final=${match.score}")
        appendLine("vlmCaption=${match.breakdown.vlmCaption}");appendLine("vlmObjects=${match.breakdown.vlmObjects}")
        appendLine("vlmActivities=${match.breakdown.vlmActivities}");appendLine("vlmScenes=${match.breakdown.vlmScenes}")
    }
}
