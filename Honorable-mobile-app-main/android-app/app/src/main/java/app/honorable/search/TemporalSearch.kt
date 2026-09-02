package app.honorable.search

import kotlin.math.sqrt

data class TemporalIndexVersions(val temporalSchemaVersion:Int=1,val temporalSamplingVersion:String="adaptive-scenes-v1",val momentRankingVersion:String="window-ranker-v1",val activityVersion:String="cached-labels-v1",val vlmEnrichmentVersion:String="optional-candidate-v1")
data class TemporalWindow(val startMs:Long,val endMs:Long,val centerMs:Long,val supportingFrames:List<VideoFrame>,val embedding:FloatArray?,val ocr:String,val labels:Set<String>,val colors:Set<String>)
enum class MomentResultState { VIDEO_MATCH, EXACT_MOMENT, TIMESTAMP_LOW_CONFIDENCE }
data class MomentConfidence(val score:Double,val margin:Double,val semanticAgreement:Double,val activityAgreement:Double,val neighboringConsistency:Double,val querySpecificity:Double,val confident:Boolean)
data class MomentResult(val state:MomentResultState,val bestTimestampMs:Long?,val windowStartMs:Long?,val windowEndMs:Long?,val confidence:MomentConfidence?,val supportingFrames:List<Long>,val secondBestTimestampMs:Long?,val negativePenalty:Double,val vlmUsed:Boolean=false)
data class V3VideoMatch(val base:SearchMatch,val moment:MomentResult,val deepScore:Double)

object V3TemporalSamplingPolicy {
    /** Approximately one sample per 15 seconds, bounded for battery/storage safety. */
    fun targetFrameCount(durationMs:Long)=when {durationMs<=0->0;else->((durationMs/15_000L)+2).toInt().coerceIn(4,36)}
    fun sampleTimestamps(durationMs:Long):List<Long>{val count=targetFrameCount(durationMs);if(count==0)return emptyList();if(count==1)return listOf(0);val end=(durationMs-1).coerceAtLeast(0);return (0 until count).map{index->end*index/(count-1)}.distinct()}
}

object TemporalWindowBuilder {
    fun build(frames:List<VideoFrame>,durationMs:Long?):List<TemporalWindow>{if(frames.isEmpty())return emptyList();val ordered=frames.sortedBy(VideoFrame::timestampMs);val gaps=ordered.zipWithNext{a,b->b.timestampMs-a.timestampMs}.filter{it>0}.sorted();val groupingGap=(gaps.getOrNull((gaps.size-1).coerceAtLeast(0)/2)?:3_000L).coerceIn(3_000L,7_000L);val groups=ordered.fold(mutableListOf<MutableList<VideoFrame>>()){result,frame->val current=result.lastOrNull();if(current==null||frame.timestampMs-current.last().timestampMs>groupingGap)result+=mutableListOf(frame)else current+=frame;result};return groups.map{support->val center=support[support.size/2].timestampMs;TemporalWindow((support.first().timestampMs-groupingGap).coerceAtLeast(0),(support.last().timestampMs+groupingGap).coerceAtMost(durationMs?:Long.MAX_VALUE),center,support,average(support.mapNotNull(VideoFrame::embedding)),support.joinToString(" "){it.ocr},support.flatMapTo(linkedSetOf()){it.labels+it.visionUnderstanding?.activities.orEmpty()+it.visionUnderstanding?.scenes.orEmpty()},support.flatMapTo(linkedSetOf()){it.dominantColors})}}
    private fun average(vectors:List<FloatArray>):FloatArray?{if(vectors.isEmpty())return null;val size=vectors.first().size;if(vectors.any{it.size!=size})return null;return FloatArray(size){i->vectors.sumOf{it[i].toDouble()}.div(vectors.size).toFloat()}}
}

data class V3QueryInterpretation(val momentIntent:Boolean,val boundedSemanticQueries:List<String>,val negativeConcepts:Set<String>,val concepts:List<String>)
enum class V3SearchDepth { QUICK, SMART, DEEP }
data class V3DepthPlan(val useV2CachedRetrieval:Boolean,val decomposeQuery:Boolean,val temporalWindows:Boolean,val deepRerank:Boolean,val optionalCandidateVlm:Boolean)
object V3DepthPolicy { fun plan(depth:V3SearchDepth)=when(depth){V3SearchDepth.QUICK->V3DepthPlan(true,false,false,false,false);V3SearchDepth.SMART->V3DepthPlan(true,true,true,false,false);V3SearchDepth.DEEP->V3DepthPlan(true,true,true,true,true)} }
object V3QueryPlanner {
    private val moment=Regex("(?i)\\b(when|the part where|the moment when)\\b")
    fun interpret(query:SearchQuery):V3QueryInterpretation{val concepts=query.semanticConcepts.ifEmpty{query.terms.chunked(3).map{it.joinToString(" ")}}.filter(String::isNotBlank).distinct().take(3);return V3QueryInterpretation(moment.containsMatchIn(query.raw),concepts.ifEmpty{listOf(query.raw)}.take(3),query.negativeTerms,concepts)}
}

object V3MomentRanker {
    fun rank(query:SearchQuery,windows:List<TemporalWindow>,queryVector:FloatArray?,conceptVectors:Map<String,FloatArray>):MomentResult{if(windows.isEmpty())return MomentResult(MomentResultState.VIDEO_MATCH,null,null,null,null,emptyList(),null,0.0);val interpretation=V3QueryPlanner.interpret(query);data class Scored(val window:TemporalWindow,val score:Double,val semantic:Double,val activity:Double,val consistency:Double,val negative:Double);val scored=windows.map{window->val semantic=cosine(queryVector,window.embedding).coerceAtLeast(0.0);val coverage=if(conceptVectors.isEmpty()||window.embedding==null)0.0 else conceptVectors.values.count{cosine(it,window.embedding)>=.24}.toDouble()/conceptVectors.size;val text=(window.ocr+" "+window.labels.joinToString(" ")).lowercase();val activity=query.activities.count{text.contains(it)}.toDouble();val lexical=query.terms.count{text.contains(it)}.toDouble()/query.terms.size.coerceAtLeast(1);val consistency=window.supportingFrames.count{frame->query.terms.any{term->frame.ocr.contains(term,true)||frame.labels.any{label->label.contains(term,true)}}}.toDouble()/window.supportingFrames.size.coerceAtLeast(1);val negative=interpretation.negativeConcepts.count{text.contains(it)}*.8;Scored(window,semantic*2.4+coverage*2.0+activity*1.2+lexical+consistency*.8-negative,semantic,activity,consistency,negative)}.sortedByDescending(Scored::score);val best=scored.first();val second=scored.getOrNull(1);val margin=best.score-(second?.score?:0.0);val specificity=(query.terms.size+query.activities.size+query.colors.size).coerceAtMost(8)/8.0;val confident=interpretation.momentIntent&&best.score>=1.25&&margin>=.12&&(best.semantic>=.20||best.activity>0||best.consistency>=.5);val confidence=MomentConfidence(best.score,margin,best.semantic,best.activity,best.consistency,specificity,confident);val usedCachedVlm=best.window.supportingFrames.any{it.visionUnderstanding!=null};return MomentResult(if(confident)MomentResultState.EXACT_MOMENT else MomentResultState.TIMESTAMP_LOW_CONFIDENCE,if(confident)best.window.centerMs else null,best.window.startMs,best.window.endMs,confidence,best.window.supportingFrames.map(VideoFrame::timestampMs),second?.window?.centerMs,best.negative,usedCachedVlm)}
    private fun cosine(a:FloatArray?,b:FloatArray?):Double{if(a==null||b==null||a.size!=b.size||a.isEmpty())return 0.0;var dot=0.0;var an=0.0;var bn=0.0;for(i in a.indices){dot+=a[i]*b[i];an+=a[i]*a[i];bn+=b[i]*b[i]};return if(an==0.0||bn==0.0)0.0 else dot/(sqrt(an)*sqrt(bn))}
}

object V3DeepReranker {
    /** Runs only after bounded V2 candidate retrieval; it never scans or invokes VLM across the library. */
    fun rerank(query:SearchQuery,candidates:List<SearchMatch>,queryVector:FloatArray?,conceptVectors:Map<String,FloatArray>,limit:Int=24):List<V3VideoMatch> = candidates.asSequence().filter{it.media.kind==MediaKind.VIDEO}.take(limit.coerceIn(1,64)).map{base->val moment=V3MomentRanker.rank(query,TemporalWindowBuilder.build(base.media.videoFrames,base.media.durationMs),queryVector,conceptVectors);V3VideoMatch(base,moment,base.score+(moment.confidence?.score?:0.0)*.55-moment.negativePenalty)}.sortedByDescending(V3VideoMatch::deepScore).toList()
}

data class V3LabDiagnostics(val interpretation:V3QueryInterpretation,val candidateVideoIds:List<Long>,val windowsByVideo:Map<Long,List<TemporalWindow>>,val resultsByVideo:Map<Long,MomentResult>,val vlmUsed:Boolean=false)
