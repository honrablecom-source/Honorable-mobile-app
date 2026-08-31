package com.honorablemobile

import app.honorable.HonorableFeature
import app.honorable.Plan
import app.honorable.TrustedEntitlementState
import app.honorable.search.*
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class HonorableNativePackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext) = listOf(HonorableSearchModule(context))
    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

/** Thin transport only. Parsing, retrieval, ordering, confidence and indexing stay in the shared Kotlin engine. */
class HonorableSearchModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    private val work = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private val databaseDelegate=lazy { LocalMediaDatabase(context) }
    private val embeddingsDelegate=lazy { AndroidTinyClipEmbeddingService(context) }
    private val database by databaseDelegate
    private val embeddings by embeddingsDelegate
    private val coordinator by lazy { LocalSearchCoordinator(SemanticQueryEncoder(embeddings)) }
    @Volatile private var catalogLoaded=false
    override fun getName() = "HonorableSearchModule"

    @ReactMethod fun getStatus(promise: Promise) = work.execute { guarded(promise) {
        Arguments.createMap().apply { putString("engine", "REAL");putBoolean("permissionGranted",hasMediaPermission());putInt("indexedCount",database.records().size);putString("status",if(hasMediaPermission())"READY" else "PERMISSION_REQUIRED") }
    } }

    @ReactMethod fun refreshIndex(promise: Promise) = work.execute { guarded(promise) {
        if(!hasMediaPermission()) throw IllegalStateException("Media permission is required")
        val stats=runBlocking { AndroidMediaIndexer(context,database,embeddings).synchronize{} }
        val count=reloadCatalog()
        Arguments.createMap().apply { putInt("added",stats.added);putInt("updated",stats.updated);putInt("deleted",stats.deleted);putInt("failed",stats.failed);putInt("skipped",stats.skipped);putInt("indexedCount",count) }
    } }

    @ReactMethod fun search(raw: String, promise: Promise) = work.execute { guarded(promise) {
        require(raw.isNotBlank()) { "Query must not be blank" };cancelled.set(false)
        val policy=trustedEntitlement().effectivePolicy();val query=QueryParser().parse(raw)
        if(query.mediaKind==MediaKind.VIDEO&&!policy.allows(HonorableFeature.VIDEO_SEARCH)) return@guarded locked("VIDEO_SEARCH_REQUIRES_PLUS")
        if(!catalogLoaded)reloadCatalog()
        val run=coordinator.search(raw);val ordered=run.matches
        val decision=run.confidence;val visible=if(decision.confident&&!cancelled.get())ordered else emptyList()
        Arguments.createMap().apply { putBoolean("confident",decision.confident);putString("decision",decision.reason);putDouble("semantic",decision.semantic);putDouble("margin",decision.margin);putArray("results",Arguments.createArray().apply { visible.forEachIndexed { rank,match -> pushMap(resultMap(rank,match,policy.allows(HonorableFeature.EXACT_VIDEO_MOMENT))) } }) }
    } }

    @ReactMethod fun cancelSearch() { cancelled.set(true) }

    @ReactMethod fun getEntitlementState(promise: Promise) = work.execute { guarded(promise) {
        // Until Play Billing/server verification is configured, native authority is deliberately unverified Free.
        val state=trustedEntitlement();val policy=state.effectivePolicy()
        Arguments.createMap().apply { putString("tier",policy.plan.name);putBoolean("verified",state.verified);putDouble("storageLimitBytes",policy.storageBytes.toDouble());putInt("videoMinutesPerWindow",policy.videoMinutesPerWindow);putInt("videosPerWindow",policy.videoCountPerWindow);putInt("familyMembersTotal",policy.familyMembersTotal);putArray("features",Arguments.fromList(policy.features.map{it.name})) }
    } }

    private fun trustedEntitlement()=TrustedEntitlementState(Plan.FREE,false)
    private fun reloadCatalog():Int { val policy=trustedEntitlement().effectivePolicy();val records=database.records().filter{it.kind!=MediaKind.VIDEO||policy.allows(HonorableFeature.VIDEO_SEARCH)};coordinator.replaceRecords(records);catalogLoaded=true;return records.size }
    private fun locked(reason:String)=Arguments.createMap().apply{putBoolean("confident",false);putString("decision",reason);putDouble("semantic",0.0);putDouble("margin",0.0);putArray("results",Arguments.createArray())}
    private fun resultMap(rank:Int, match:SearchMatch, exactMoment:Boolean)=Arguments.createMap().apply { putInt("rank",rank+1);putDouble("mediaId",match.media.id.toDouble());putString("mediaUri",match.media.uri);putString("mediaType",match.media.kind.name);putString("displayName",match.media.displayName);putDouble("score",match.score);putDouble("semantic",match.breakdown.fullSemantic);if(exactMoment)match.bestTimestampMs?.let{putDouble("bestTimestampMs",it.toDouble())};putString("confidence",match.confidence.name);putArray("evidence",Arguments.fromList(match.explanations)) }
    private fun hasMediaPermission()=MediaCapabilityManager(context).current().canReadAny
    private fun guarded(promise:Promise, block:()->WritableMap) { try { promise.resolve(block()) } catch(error:Throwable) { promise.reject("HONORABLE_NATIVE_ERROR",error.message,error) } }
    override fun invalidate() { work.shutdownNow();if(embeddingsDelegate.isInitialized())embeddings.close();if(databaseDelegate.isInitialized())database.close();super.invalidate() }
}
