package app.honorable.search

enum class IndexJobState { QUEUED, RUNNING, COMPLETED, PARTIAL_FAILURE, FAILED, CANCELLED }

enum class EvidenceProcessor { METADATA, OCR, LABELS, COLORS, TINY_CLIP, CAPTION_VLM, VIDEO_SAMPLING, VIDEO_EMBEDDING }

/** Every evidence family has its own compatibility key; changing one never invalidates another. */
data class ProcessorVersions(
    val metadata:String="metadata-1", val ocr:String="mlkit-latin-1", val labels:String="mlkit-labels-1",
    val colors:String="rgb-prototypes-1", val tinyClipModel:String="tinyclip-1", val tinyClipPreprocessing:String="jpeg384-v1",
    val captionVlm:String="disabled-1", val videoSampling:String="representative-frames-1", val videoEmbedding:String="tinyclip-video-1"
) {
    fun key(processor:EvidenceProcessor):String=when(processor) {
        EvidenceProcessor.METADATA->metadata;EvidenceProcessor.OCR->ocr;EvidenceProcessor.LABELS->labels;EvidenceProcessor.COLORS->colors
        EvidenceProcessor.TINY_CLIP->"$tinyClipModel@$tinyClipPreprocessing";EvidenceProcessor.CAPTION_VLM->captionVlm
        EvidenceProcessor.VIDEO_SAMPLING->videoSampling;EvidenceProcessor.VIDEO_EMBEDDING->videoEmbedding
    }
    init { EvidenceProcessor.entries.forEach { require(key(it).isNotBlank()){"Processor versions must not be blank"} } }
}

object EvidenceInvalidationPlanner {
    fun stale(stored:Map<EvidenceProcessor,String>, current:ProcessorVersions, isVideo:Boolean):Set<EvidenceProcessor> =
        EvidenceProcessor.entries.filterTo(linkedSetOf()) { processor ->
            (isVideo || processor !in setOf(EvidenceProcessor.VIDEO_SAMPLING,EvidenceProcessor.VIDEO_EMBEDDING)) && stored[processor]!=current.key(processor)
        }
}

enum class EngineFailureKind { PERMANENT_MEDIA, TRANSIENT_IO, DATABASE_CONTENTION, MODEL_INITIALIZATION, TEMPORARY_RESOURCE, PERMISSION, CANCELLED, UNKNOWN }
data class EngineFailure(val kind:EngineFailureKind,val processor:EvidenceProcessor?=null,val retryable:Boolean=kind in RETRYABLE) {
    companion object { val RETRYABLE=setOf(EngineFailureKind.TRANSIENT_IO,EngineFailureKind.DATABASE_CONTENTION,EngineFailureKind.MODEL_INITIALIZATION,EngineFailureKind.TEMPORARY_RESOURCE) }
}

data class RetryPolicy(val maxAttempts:Int=3,val initialDelayMs:Long=1_000,val maximumDelayMs:Long=30_000) {
    init { require(maxAttempts in 1..10);require(initialDelayMs>0);require(maximumDelayMs>=initialDelayMs) }
    fun delayMs(attempt:Int)=(initialDelayMs*(1L shl (attempt-1).coerceIn(0,20))).coerceAtMost(maximumDelayMs)
}

class ProcessorCircuitBreaker(private val threshold:Int=5,private val coolDownMs:Long=15*60_000,private val now:()->Long=System::currentTimeMillis) {
    private data class Entry(var failures:Int=0,var openUntil:Long=0)
    private val entries=mutableMapOf<EvidenceProcessor,Entry>()
    @Synchronized fun allow(processor:EvidenceProcessor)=entries[processor]?.openUntil?.let{it<=now()}?:true
    @Synchronized fun success(processor:EvidenceProcessor){entries.remove(processor)}
    @Synchronized fun failure(processor:EvidenceProcessor){val e=entries.getOrPut(processor){Entry()};e.failures++;if(e.failures>=threshold)e.openUntil=now()+coolDownMs}
    @Synchronized fun openProcessors()=entries.filterValues{it.openUntil>now()}.keys
}

enum class EngineEventType { JOB_QUEUED, JOB_STARTED, STAGE_CHANGED, ITEM_COMPLETED, ITEM_FAILED, PROCESSOR_CIRCUIT_OPEN, JOB_COMPLETED, JOB_FAILED, JOB_CANCELLED }
data class EngineEvent(val type:EngineEventType,val jobId:String,val stage:String?=null,val processed:Int=0,val total:Int=0,val failed:Int=0,val atEpochMs:Long=System.currentTimeMillis())

enum class WorkloadCost { LIGHT, MEDIUM, HEAVY }
data class ResourceLimits(val bitmapDecodes:Int,val ocr:Int,val tinyClip:Int,val vlm:Int,val videoDecode:Int,val databaseWrites:Int) {
    init { require(listOf(bitmapDecodes,ocr,tinyClip,vlm,videoDecode,databaseWrites).all{it in 1..8}) }
}

data class DeviceCapability(val memoryClassMb:Int,val processors:Int,val lowRam:Boolean,val hardwareAcceleration:Boolean,val backgroundRestricted:Boolean) {
    fun defaultLimits():ResourceLimits {
        val constrained=lowRam||memoryClassMb<256
        return ResourceLimits(bitmapDecodes=if(constrained)1 else minOf(2,processors.coerceAtLeast(1)),ocr=1,tinyClip=1,vlm=1,videoDecode=1,databaseWrites=1)
    }
}

data class EngineFeatureFlags(val vlmCaptions:Boolean=false,val advancedVideoSearch:Boolean=false,val rankingVersion:Int=1,val queryPlannerVersion:Int=1,val searchExplanations:Boolean=false,val adaptiveIndexing:Boolean=true) {
    init { require(rankingVersion>0&&queryPlannerVersion>0) }
}

enum class QueryPath { METADATA, DATE, OCR, SEMANTIC, HYBRID }
data class QueryExecutionPlan(val path:QueryPath,val needsEmbedding:Boolean,val needsOcr:Boolean,val needsMetadata:Boolean)
object QueryExecutionPlanner {
    private val filename=Regex("(?i)^(img|vid|dsc|pxl)[-_]?\\d+")
    private val dateWords=setOf("today","yesterday","january","february","march","april","may","june","july","august","september","october","november","december")
    private val ocrWords=setOf("containing","receipt","document","screenshot","text","says")
    fun plan(raw:String):QueryExecutionPlan { val words=raw.lowercase().split(Regex("\\s+")).toSet();return when {
        filename.containsMatchIn(raw.trim())->QueryExecutionPlan(QueryPath.METADATA,false,false,true)
        words.any(dateWords::contains)->QueryExecutionPlan(QueryPath.DATE,false,false,true)
        words.any(ocrWords::contains)->QueryExecutionPlan(QueryPath.OCR,false,true,true)
        words.size<=4->QueryExecutionPlan(QueryPath.SEMANTIC,true,false,false)
        else->QueryExecutionPlan(QueryPath.HYBRID,true,true,true)
    } }
}

class PrivacySafeTimings(private val nowNanos:()->Long=System::nanoTime) {
    data class Timing(val operation:String,val stage:String,val durationMs:Long)
    private val values=mutableListOf<Timing>()
    fun <T> measure(operation:String,stage:String,block:()->T):T { val start=nowNanos();return try{block()}finally{synchronized(values){values+=Timing(operation,stage,(nowNanos()-start)/1_000_000)}} }
    fun snapshot():List<Timing> = synchronized(values){values.toList()}
}

data class IndexJobSnapshot(
    val state:IndexJobState=IndexJobState.QUEUED,
    val createdAtEpochMs:Long,
    val startedAtEpochMs:Long?=null,
    val finishedAtEpochMs:Long?=null,
    val processed:Int=0,
    val total:Int=0,
    val failedItems:Int=0
)

/** Thread-safe lifecycle for one indexing pass. It stores counts only, never media content. */
class IndexJobController(private val now:()->Long=System::currentTimeMillis) {
    @Volatile private var value=IndexJobSnapshot(createdAtEpochMs=now())

    fun snapshot():IndexJobSnapshot=value

    @Synchronized fun start() {
        check(value.state==IndexJobState.QUEUED){"Only a queued index job can start"}
        value=value.copy(state=IndexJobState.RUNNING,startedAtEpochMs=now())
    }

    @Synchronized fun progress(processed:Int,total:Int,failedItems:Int) {
        check(value.state==IndexJobState.RUNNING){"Index progress requires a running job"}
        require(processed in 0..total&&failedItems in 0..processed)
        check(processed>=value.processed){"Index progress cannot move backwards"}
        value=value.copy(processed=processed,total=total,failedItems=failedItems)
    }

    @Synchronized fun complete(stats:IndexStats) {
        check(value.state==IndexJobState.RUNNING){"Only a running index job can complete"}
        val state=if(stats.failed>0)IndexJobState.PARTIAL_FAILURE else IndexJobState.COMPLETED
        value=value.copy(state=state,failedItems=stats.failed,finishedAtEpochMs=now())
    }

    @Synchronized fun fail()=finish(IndexJobState.FAILED)
    @Synchronized fun cancel()=finish(IndexJobState.CANCELLED)

    private fun finish(state:IndexJobState) {
        if(value.state in TERMINAL)return
        check(value.state==IndexJobState.RUNNING||state==IndexJobState.CANCELLED)
        value=value.copy(state=state,finishedAtEpochMs=now())
    }

    companion object { private val TERMINAL=setOf(IndexJobState.COMPLETED,IndexJobState.PARTIAL_FAILURE,IndexJobState.FAILED,IndexJobState.CANCELLED) }
}
