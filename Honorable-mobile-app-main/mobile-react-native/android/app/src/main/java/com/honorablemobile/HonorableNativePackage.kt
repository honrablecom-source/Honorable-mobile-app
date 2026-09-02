package com.honorablemobile

import app.honorable.HonorableFeature
import app.honorable.Plan
import app.honorable.TrustedEntitlementState
import app.honorable.search.*
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager
import kotlinx.coroutines.runBlocking
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class HonorableNativePackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext) = listOf(HonorableSearchModule(context))
    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

/** Thin transport only. Parsing, retrieval, ordering, confidence and indexing stay in the shared Kotlin engine. */
class HonorableSearchModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    private val work = Executors.newFixedThreadPool(2)
    private val cancelled = AtomicBoolean(false)
    private val databaseDelegate=lazy { LocalMediaDatabase(context) }
    private val embeddingsDelegate=lazy { AndroidTinyClipEmbeddingService(context) }
    private val database by databaseDelegate
    private val embeddings by embeddingsDelegate
    private val coordinator by lazy { LocalSearchCoordinator(SemanticQueryEncoder(embeddings)) }
    @Volatile private var catalogLoaded=false
    private val enrichmentRunning=AtomicBoolean(false)
    @Volatile private var enrichmentStatus="IDLE"
    @Volatile private var enrichmentProcessed=0
    @Volatile private var enrichmentTotal=0
    override fun getName() = "HonorableSearchModule"

    @ReactMethod fun getStatus(promise: Promise) = work.execute { guarded(promise) {
        val records=database.records();val job=database.latestJob()
        val status=when{!hasMediaPermission()->"PERMISSION_REQUIRED";records.isEmpty()->"NOT_INDEXED";job?.state==IndexJobState.COMPLETED->"READY";else->job?.state?.name?:"READY"}
        Arguments.createMap().apply { putString("engine", "REAL");putBoolean("permissionGranted",hasMediaPermission());putInt("indexedCount",records.size);putString("status",status);job?.let{putInt("processed",it.processed);putInt("total",it.total);putInt("failed",it.failedItems);it.finishedAtEpochMs?.let{at->putDouble("lastCompletedAt",at.toDouble())}} }
    } }

    @ReactMethod fun getLibrary(limit:Int,offset:Int,kind:String?,promise:Promise)=work.execute{guarded(promise){
        val requested=kind?.takeIf{it.isNotBlank()&&it!="ALL"}?.let{MediaKind.valueOf(it)}
        val all=database.records().asSequence().filter{requested==null||it.kind==requested}.sortedByDescending{it.capturedAtEpochMs}.toList()
        val page=all.drop(offset.coerceAtLeast(0)).take(limit.coerceIn(1,200))
        Arguments.createMap().apply{putInt("total",all.size);putArray("items",Arguments.createArray().apply{page.forEach{pushMap(libraryMap(it))}})}
    }}

    @ReactMethod fun refreshIndex(promise: Promise) = work.execute { guarded(promise) {
        if(!hasMediaPermission()) throw IllegalStateException("Media permission is required")
        val stats=runBlocking { AndroidMediaIndexer(context,database,embeddings,profile=activeSeranProfile()).synchronize(progress = {}) }
        val count=reloadCatalog()
        Arguments.createMap().apply { putInt("added",stats.added);putInt("updated",stats.updated);putInt("deleted",stats.deleted);putInt("failed",stats.failed);putInt("skipped",stats.skipped);putInt("indexedCount",count) }
    } }

    @ReactMethod fun search(raw: String, promise: Promise) = work.execute { guarded(promise) {
        require(raw.isNotBlank()) { "Query must not be blank" };rememberSearch(raw);cancelled.set(false)
        val policy=trustedEntitlement().effectivePolicy();val query=QueryParser().parse(raw);val profile=activeSeranProfile()
        if(query.mediaKind==MediaKind.VIDEO&&!profile.semanticVideo) return@guarded locked("VIDEO_INTELLIGENCE_AVAILABLE_WITH_SERAN_V2")
        if(!catalogLoaded)reloadCatalog()
        val run=coordinator.search(raw);val ordered=run.matches
        val decision=run.confidence;val visible=if(decision.confident&&!cancelled.get())ordered else emptyList()
        Arguments.createMap().apply { putBoolean("confident",decision.confident);putString("decision",decision.reason);putDouble("semantic",decision.semantic);putDouble("margin",decision.margin);putArray("results",Arguments.createArray().apply { visible.forEachIndexed { rank,match -> pushMap(resultMap(rank,match,profile.semanticVideo||policy.allows(HonorableFeature.EXACT_VIDEO_MOMENT))) } }) }
    } }

    @ReactMethod fun cancelSearch() { cancelled.set(true) }
    @ReactMethod fun getAccountConfiguration(promise:Promise)=promise.resolve(Arguments.createMap().apply{putBoolean("googleConfigured",BuildConfig.HONORABLE_GOOGLE_WEB_CLIENT_ID.isNotBlank());putString("apiUrl",BuildConfig.HONORABLE_ACCOUNT_API_URL)})

    @ReactMethod fun signInWithGoogle(promise:Promise)=work.execute{guarded(promise){
        require(BuildConfig.HONORABLE_GOOGLE_WEB_CLIENT_ID.isNotBlank()){"HONORABLE_GOOGLE_WEB_CLIENT_ID is required"}
        val activity=context.currentActivity?:error("Google Sign-In requires an active Android screen")
        val option=GetGoogleIdOption.Builder().setServerClientId(BuildConfig.HONORABLE_GOOGLE_WEB_CLIENT_ID).setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
        val result=runBlocking{CredentialManager.create(context).getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build())}
        val credential=result.credential
        require(credential is CustomCredential&&credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL){"Google did not return an ID token"}
        val google=GoogleIdTokenCredential.createFrom(credential.data)
        Arguments.createMap().apply{putString("idToken",google.idToken)}
    }}
    @ReactMethod fun signOutGoogle(promise:Promise)=work.execute{guarded(promise){runBlocking{CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())};Arguments.createMap().apply{putBoolean("signedOut",true)}}}

    @ReactMethod fun getSearchHistory(promise:Promise)=work.execute{guarded(promise){Arguments.createMap().apply{putArray("queries",Arguments.fromList(searchHistory()))}}}
    @ReactMethod fun clearSearchHistory(promise:Promise)=work.execute{guarded(promise){context.getSharedPreferences("honorable-ui",0).edit().remove("search-history").apply();Arguments.createMap().apply{putBoolean("cleared",true)}}}
    @ReactMethod fun openMedia(uri:String,kind:String,timestampMs:Double?,promise:Promise)=work.execute{guarded(promise){context.startActivity(Intent(context,HonorableMediaViewerActivity::class.java).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION);putExtra("uri",uri);putExtra("kind",kind);timestampMs?.let{putExtra("timestampMs",it.toLong())}});Arguments.createMap().apply{putBoolean("opened",true)}}}

    @ReactMethod fun getEntitlementState(promise: Promise) = work.execute { guarded(promise) {
        // Until Play Billing/server verification is configured, native authority is deliberately unverified Free.
        val state=trustedEntitlement();val policy=state.effectivePolicy()
        Arguments.createMap().apply { putString("tier",policy.plan.name);putBoolean("verified",state.verified);putDouble("storageLimitBytes",policy.storageBytes.toDouble());putInt("videoMinutesPerWindow",policy.videoMinutesPerWindow);putInt("videosPerWindow",policy.videoCountPerWindow);putInt("familyMembersTotal",policy.familyMembersTotal);putArray("features",Arguments.fromList(policy.features.map{it.name})) }
    } }

    @ReactMethod fun getSeranModelState(promise:Promise)=work.execute{guarded(promise){ensureV2Enrichment();seranModelState()}}
    @ReactMethod fun selectSeranModel(model:String,promise:Promise)=work.execute{guarded(promise){
        require(model in availableSeranModels()){"Seran model is not available for the trusted entitlement"}
        val previous=selectedSeranModel();context.getSharedPreferences("honorable-models",0).edit().putString("selected",model).apply();if(model=="SERAN_V2"&&previous!=model)enrichmentStatus="IDLE";catalogLoaded=false;reloadCatalog();ensureV2Enrichment();seranModelState()
    }}
    @ReactMethod fun getImprovementProgramState(promise:Promise)=work.execute{guarded(promise){improvementState()}}
    @ReactMethod fun setImprovementProgramConsent(enabled:Boolean,consentVersion:String,policyVersion:String,promise:Promise)=work.execute{guarded(promise){
        val preferences=context.getSharedPreferences("seran-improvement-consent",0);val now=System.currentTimeMillis()
        preferences.edit().putBoolean("enabled",enabled).putString("consentVersion",consentVersion).putString("policyVersion",policyVersion).putLong(if(enabled)"acceptedAt" else "revokedAt",now).apply()
        improvementState()
    }}

    private fun trustedEntitlement()=TrustedEntitlementState(Plan.FREE,false)
    private fun availableSeranModels():List<String>{trustedEntitlement().effectivePolicy();return SeranModelPolicy.selectableModels().map{it.name}}
    private fun selectedSeranModel():String=SeranModelPolicy.resolvePersisted(context.getSharedPreferences("honorable-models",0).getString("selected",null)).name
    private fun activeSeranProfile()=SeranModelPolicy.profile(SeranModelProfile.valueOf(selectedSeranModel()))
    private fun seranModelState():WritableMap{val available=availableSeranModels();val selected=selectedSeranModel();return Arguments.createMap().apply{putString("selected",selected);putArray("available",Arguments.fromList(available));putString("authority","NATIVE_ENTITLEMENT");putInt("batchLimit",activeSeranProfile().batchLimit);putBoolean("semanticVideo",activeSeranProfile().semanticVideo);putString("enrichmentStatus",enrichmentStatus);putInt("enrichmentProcessed",enrichmentProcessed);putInt("enrichmentTotal",enrichmentTotal)}}
    private fun ensureV2Enrichment(){if(selectedSeranModel()!="SERAN_V2"||enrichmentStatus=="READY"||!hasMediaPermission()||!enrichmentRunning.compareAndSet(false,true))return;enrichmentStatus="PREPARING_VIDEO_INTELLIGENCE";work.execute{try{var completed=0;var batches=0;var batchTotal:Int;do{batchTotal=0;runBlocking{AndroidMediaIndexer(context,database,embeddings,profile=SeranModelPolicy.v2).synchronize({progress->batchTotal=progress.total;enrichmentProcessed=completed+progress.processed;enrichmentTotal=completed+progress.total})};completed+=batchTotal;batches++}while(selectedSeranModel()=="SERAN_V2"&&batchTotal==SeranModelPolicy.v2.batchLimit&&batches<20);catalogLoaded=false;reloadCatalog();enrichmentStatus=if(selectedSeranModel()=="SERAN_V2")"READY" else "IDLE"}catch(error:Throwable){enrichmentStatus="FAILED"}finally{enrichmentRunning.set(false)}}}
    private fun improvementState():WritableMap{val preferences=context.getSharedPreferences("seran-improvement-consent",0);return Arguments.createMap().apply{putBoolean("enabled",preferences.getBoolean("enabled",false));putString("backendStatus","BACKEND_NOT_CONFIGURED");putBoolean("uploadActive",false);preferences.getString("consentVersion",null)?.let{putString("consentVersion",it)};preferences.getString("policyVersion",null)?.let{putString("policyVersion",it)};preferences.getLong("acceptedAt",0).takeIf{it>0}?.let{putDouble("acceptedAt",it.toDouble())};preferences.getLong("revokedAt",0).takeIf{it>0}?.let{putDouble("revokedAt",it.toDouble())}}}
    private fun reloadCatalog():Int { val profile=activeSeranProfile();val records=database.records().filter{it.kind!=MediaKind.VIDEO||profile.semanticVideo};coordinator.replaceRecords(records);catalogLoaded=true;return records.size }
    private fun locked(reason:String)=Arguments.createMap().apply{putBoolean("confident",false);putString("decision",reason);putDouble("semantic",0.0);putDouble("margin",0.0);putArray("results",Arguments.createArray())}
    private fun resultMap(rank:Int, match:SearchMatch, exactMoment:Boolean)=Arguments.createMap().apply { putInt("rank",rank+1);putDouble("mediaId",match.media.id.toDouble());putString("mediaUri",match.media.uri);putString("mediaType",match.media.kind.name);putString("displayName",match.media.displayName);putDouble("score",match.score);putDouble("semantic",match.breakdown.fullSemantic);if(exactMoment)match.bestTimestampMs?.let{putDouble("bestTimestampMs",it.toDouble())};putString("confidence",match.confidence.name);putArray("evidence",Arguments.fromList(match.explanations)) }
    private fun libraryMap(media:MediaRecord)=Arguments.createMap().apply{putDouble("mediaId",media.id.toDouble());putString("mediaUri",media.uri);putString("mediaType",media.kind.name);putString("displayName",media.displayName);putDouble("capturedAt",media.capturedAtEpochMs.toDouble());media.durationMs?.let{putDouble("durationMs",it.toDouble())};videoThumbnail(media)?.let{putString("thumbnailUri",it)}}
    private fun videoThumbnail(media:MediaRecord):String?{if(media.kind!=MediaKind.VIDEO||Build.VERSION.SDK_INT<29)return null;return runCatching{val dir=File(context.cacheDir,"media-thumbnails").apply{mkdirs()};val file=File(dir,"${media.id}.jpg");if(!file.exists()){val bitmap=context.contentResolver.loadThumbnail(Uri.parse(media.uri),Size(640,640),null);FileOutputStream(file).use{bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,it)};bitmap.recycle()};Uri.fromFile(file).toString()}.getOrNull()}
    private fun searchHistory(): List<String> = context.getSharedPreferences("honorable-ui",0).getString("search-history","").orEmpty().lineSequence().filter(String::isNotBlank).toList()
    private fun rememberSearch(raw:String){val clean=raw.trim().replace('\n',' ');val history=(listOf(clean)+searchHistory().filterNot{it.equals(clean,true)}).take(12);context.getSharedPreferences("honorable-ui",0).edit().putString("search-history",history.joinToString("\n")).apply()}
    private fun hasMediaPermission()=MediaCapabilityManager(context).current().canReadAny
    private fun guarded(promise:Promise, block:()->WritableMap) { try { promise.resolve(block()) } catch(error:Throwable) { promise.reject("HONORABLE_NATIVE_ERROR",error.message,error) } }
    override fun invalidate() { work.shutdownNow();if(embeddingsDelegate.isInitialized())embeddings.close();if(databaseDelegate.isInitialized())database.close();super.invalidate() }
}
