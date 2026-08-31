package app.honorable.search

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.ByteArrayOutputStream

enum class IndexingStage { DISCOVERING, READING_MEDIA, OCR, LABELING, EMBEDDING, VIDEO_FRAMES, PERSISTING, CLEANUP }
enum class IndexFailureKind { MEDIA_READ, PROCESSING, DATABASE }
data class IndexIssue(val mediaId:Long,val mediaKind:MediaKind,val stage:IndexingStage,val kind:IndexFailureKind)
data class IndexProgress(
    val processed:Int=0,
    val total:Int=0,
    val failed:Int=0,
    val skipped:Int=0,
    val stage:IndexingStage=IndexingStage.DISCOVERING,
    val recentIssue:IndexIssue?=null
)
sealed interface MemorySearchState {
    data object PermissionRequired:MemorySearchState
    data class Indexing(val progress:IndexProgress):MemorySearchState
    data class Ready(val count:Int):MemorySearchState
    data class Results(val query:String,val matches:List<SearchMatch>):MemorySearchState
    data class Failed(val message:String):MemorySearchState
}

data class DiscoveredMedia(val id:Long,val uri:String,val kind:MediaKind,val capturedAt:Long,val modifiedAt:Long,val name:String,val durationMs:Long?)

/** Namespaces platform IDs so an image and video with the same MediaStore ID remain distinct. */
internal fun stableMediaId(kind:MediaKind,sourceId:Long):Long {
    require(sourceId>=0) { "MediaStore ID must be non-negative" }
    require(sourceId<=Long.MAX_VALUE/2) { "MediaStore ID is too large" }
    return sourceId*2 + if(kind==MediaKind.VIDEO) 1 else 0
}

class AndroidMediaIndexer(private val context:Context,private val database:LocalMediaDatabase,private val embeddings:EmbeddingService,private val scheduler:IndexResourceScheduler=AndroidResourceGovernor(context).scheduler,private val versions:ProcessorVersions=ProcessorVersions(tinyClipModel=embeddings.modelId),private val circuits:ProcessorCircuitBreaker=ProcessorCircuitBreaker()) {
    private val resolver=context.contentResolver
    suspend fun synchronize(progress:(IndexProgress)->Unit,job:IndexJobController=IndexJobController()):IndexStats {
      job.start();return try { withContext(Dispatchers.IO) {
        val started=System.nanoTime();progress(IndexProgress(stage=IndexingStage.DISCOVERING))
        val discovered=discover();val known=database.modifiedTimes();val cached=database.records().associateBy{it.uri};var added=0;var updated=0;var processed=0;var failed=0;var skipped=0
        discovered.forEach { item ->
            currentCoroutineContext().ensureActive()
            val previous=cached[item.uri];val mediaChanged=known[item.uri]!=item.modifiedAt||previous?.id!=item.id
            val stale=if(mediaChanged)EvidenceInvalidationPlanner.stale(emptyMap(),versions,item.kind==MediaKind.VIDEO) else EvidenceInvalidationPlanner.stale(database.evidenceVersions(item.id),versions,item.kind==MediaKind.VIDEO)
            val needsUpdate=mediaChanged||stale.isNotEmpty()
            var issue:IndexIssue?=null
            if(needsUpdate) {
                try {
                    fun report(stage:IndexingStage)=progress(IndexProgress(processed,discovered.size,failed,skipped,stage))
                    val bitmapNeeded=stale.any{it in setOf(EvidenceProcessor.OCR,EvidenceProcessor.LABELS,EvidenceProcessor.COLORS,EvidenceProcessor.TINY_CLIP)}
                    val bitmap=if(bitmapNeeded){report(IndexingStage.READING_MEDIA);scheduler.withResource(IndexResource.MEDIA_DECODE){thumbnail(item)}?:throw ItemIndexException(IndexingStage.READING_MEDIA,IndexFailureKind.MEDIA_READ)}else null
                    val record=try {
                        val labels=if(EvidenceProcessor.LABELS in stale){report(IndexingStage.LABELING);evidence(EvidenceProcessor.LABELS,previous?.labels.orEmpty()){scheduler.withResource(IndexResource.LOCAL_VISION){label(requireNotNull(bitmap))}}}else previous?.labels.orEmpty()
                        val colors=if(EvidenceProcessor.COLORS in stale)sampleColors(requireNotNull(bitmap))else previous?.dominantColors.orEmpty()
                        val ocr=if(EvidenceProcessor.OCR in stale){report(IndexingStage.OCR);evidence(EvidenceProcessor.OCR,previous?.ocr.orEmpty()){scheduler.withResource(IndexResource.OCR){recognize(requireNotNull(bitmap))}}}else previous?.ocr.orEmpty()
                        val vector=if(EvidenceProcessor.TINY_CLIP in stale){report(IndexingStage.EMBEDDING);evidence(EvidenceProcessor.TINY_CLIP,previous?.embedding){scheduler.withResource(IndexResource.MODEL_INFERENCE){embeddings.image(requireNotNull(bitmap).jpeg())}}}else previous?.embedding
                        val frames=if(item.kind==MediaKind.VIDEO&&stale.any{it==EvidenceProcessor.VIDEO_SAMPLING||it==EvidenceProcessor.VIDEO_EMBEDDING}){report(IndexingStage.VIDEO_FRAMES);scheduler.withResource(IndexResource.VIDEO_DECODE){analyzeVideo(item)}}else previous?.videoFrames.orEmpty()
                        MediaRecord(item.id,item.kind,item.capturedAt,ocr=ocr,labels=labels,embedding=vector,videoFrames=frames,metadataTerms=setOf(if(item.kind==MediaKind.VIDEO)"video" else "photo"),dominantColors=colors,isScreenshot=item.name.contains("screenshot",true),uri=item.uri,displayName=item.name,durationMs=item.durationMs,visionUnderstanding=previous?.visionUnderstanding)
                    } finally { bitmap?.takeIf{!it.isRecycled}?.recycle() }
                    report(IndexingStage.PERSISTING);scheduler.withResource(IndexResource.DATABASE_WRITE){database.upsert(record,item.modifiedAt)}
                    scheduler.withResource(IndexResource.DATABASE_WRITE){database.markEvidence(item.id,stale,versions)}
                    if(item.uri in known)updated++ else added++
                } catch(cancelled:CancellationException) { throw cancelled
                } catch(error:ItemIndexException) { failed++;issue=IndexIssue(item.id,item.kind,error.stage,error.kind)
                } catch(error:android.database.SQLException) { failed++;issue=IndexIssue(item.id,item.kind,IndexingStage.PERSISTING,IndexFailureKind.DATABASE)
                } catch(error:Exception) { failed++;issue=IndexIssue(item.id,item.kind,IndexingStage.READING_MEDIA,IndexFailureKind.PROCESSING) }
            } else {
                skipped++
            }
            progress(IndexProgress(++processed,discovered.size,failed,skipped,IndexingStage.READING_MEDIA,issue));job.progress(processed,discovered.size,failed)
        }
        progress(IndexProgress(processed,discovered.size,failed,skipped,IndexingStage.CLEANUP));val uris=discovered.mapTo(mutableSetOf()){it.uri};val deleted=known.keys.count{it !in uris};scheduler.withResource(IndexResource.DATABASE_WRITE){database.removeDeleted(uris)}
        IndexStats(added,updated,deleted,failed,skipped,(System.nanoTime()-started)/1_000_000).also(job::complete)
      } } catch(cancelled:CancellationException){job.cancel();throw cancelled}catch(error:Exception){job.fail();throw error}
    }
    private fun discover():List<DiscoveredMedia> {
        val result=mutableListOf<DiscoveredMedia>()
        fun query(collection:Uri,kind:MediaKind) {
            val projection=arrayOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DATE_TAKEN,MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns.DISPLAY_NAME,if(kind==MediaKind.VIDEO)MediaStore.Video.VideoColumns.DURATION else MediaStore.MediaColumns.SIZE)
            resolver.query(collection,projection,null,null,"${MediaStore.MediaColumns.DATE_TAKEN} DESC")?.use { c ->
                val id=c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);val taken=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);val modified=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);val name=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);val last=c.getColumnIndexOrThrow(projection.last())
                while(c.moveToNext()){val sourceId=c.getLong(id);result+=DiscoveredMedia(stableMediaId(kind,sourceId),ContentUris.withAppendedId(collection,sourceId).toString(),kind,c.getLong(taken).takeIf{it>0}?:c.getLong(modified)*1000,c.getLong(modified)*1000,c.getString(name).orEmpty(),if(kind==MediaKind.VIDEO)c.getLong(last) else null)}
            }
        }
        val capabilities=MediaCapabilityManager(context).current();if(capabilities.canReadImages)query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,MediaKind.IMAGE);if(capabilities.canReadVideos)query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,MediaKind.VIDEO)
        return result
    }
    private fun thumbnail(item:DiscoveredMedia):Bitmap?=runCatching { resolver.loadThumbnail(Uri.parse(item.uri),Size(384,384),null) }.getOrNull()
    private suspend fun analyzeVideo(item:DiscoveredMedia):List<VideoFrame> { val duration=item.durationMs?:return emptyList();val candidates=listOf(0L,duration/4,duration/2,duration/4*3,(duration-1).coerceAtLeast(0)).distinct().mapNotNull { at->videoBitmap(item.uri,at)?.let{bitmap->try{FrameCandidate(at,fingerprint(bitmap))}finally{if(!bitmap.isRecycled)bitmap.recycle()}} };return RepresentativeFrameSelector.select(candidates).mapNotNull { selected->videoBitmap(item.uri,selected.timestampMs)?.let{bitmap->try{VideoFrame(selected.timestampMs,scheduler.withResource(IndexResource.OCR){recognize(bitmap)},scheduler.withResource(IndexResource.LOCAL_VISION){label(bitmap)},scheduler.withResource(IndexResource.MODEL_INFERENCE){embeddings.image(bitmap.jpeg())},sampleColors(bitmap),selected.sceneFingerprint)}finally{if(!bitmap.isRecycled)bitmap.recycle()}} } }
    private fun videoBitmap(uri:String,atMs:Long):Bitmap?=runCatching { MediaMetadataRetriever().let { retriever->try{retriever.setDataSource(context,Uri.parse(uri));retriever.getFrameAtTime(atMs*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)}finally{retriever.release()} } }.getOrNull()
    private fun fingerprint(bitmap:Bitmap):Long { var bits=0L;var sum=0L;val values=IntArray(64){i->bitmap.getPixel(i%8*bitmap.width/8,i/8*bitmap.height/8).let{p->((p shr 16 and 255)+(p shr 8 and 255)+(p and 255))/3}.also{sum+=it}};values.forEachIndexed{i,v->if(v>=sum/64)bits=bits or(1L shl i)};return bits }
    private fun Bitmap.jpeg():ByteArray=ByteArrayOutputStream().use{out->compress(Bitmap.CompressFormat.JPEG,92,out);out.toByteArray()}
    private suspend fun <T> evidence(processor:EvidenceProcessor,fallback:T,block:suspend()->T):T { if(!circuits.allow(processor))return fallback;return try{block().also{circuits.success(processor)}}catch(cancelled:CancellationException){throw cancelled}catch(error:Throwable){circuits.failure(processor);fallback} }
    private suspend fun recognize(bitmap:Bitmap):String=suspendCancellableCoroutine { continuation ->
        val client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);client.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{continuation.resume(it.text)}.addOnFailureListener{continuation.resumeWithException(it)}.addOnCompleteListener{client.close()}
    }
    private suspend fun label(bitmap:Bitmap):Set<String> = suspendCancellableCoroutine { continuation ->
        val client=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);client.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{labels->continuation.resume(labels.filter{it.confidence>=.55f}.mapTo(linkedSetOf()){it.text.lowercase()})}.addOnFailureListener{continuation.resumeWithException(it)}.addOnCompleteListener{client.close()}
    }
    private fun sampleColors(bitmap:Bitmap):Set<String>{val w=bitmap.width;val h=bitmap.height;if(w==0||h==0)return emptySet();val pixels=IntArray(256){i->bitmap.getPixel((i%16*w/16).coerceAtMost(w-1),(i/16*h/16).coerceAtMost(h-1))};return ColorEvidenceAnalyzer.dominantColors(pixels)}
    private class ItemIndexException(val stage:IndexingStage,val kind:IndexFailureKind):Exception()
}

class MemoriesViewModel(application:Application):AndroidViewModel(application) {
    private val db=LocalMediaDatabase(application);private val embeddings=AndroidTinyClipEmbeddingService(application);private val durableIndexing=DurableIndexing(application);private val coordinator=LocalSearchCoordinator(SemanticQueryEncoder(embeddings))
    private val mutableState=MutableStateFlow<MemorySearchState>(if(MediaCapabilityManager(application).current().canReadAny)MemorySearchState.Ready(db.diagnostics().indexedItems) else MemorySearchState.PermissionRequired)
    val state:StateFlow<MemorySearchState> = mutableState.asStateFlow()
    init {
        if(hasPermission(application)){reloadCatalog();refresh()}
        viewModelScope.launch { durableIndexing.states().collectLatest { observation -> when(observation?.state) {
            androidx.work.WorkInfo.State.ENQUEUED,androidx.work.WorkInfo.State.BLOCKED,androidx.work.WorkInfo.State.RUNNING->{val count=db.diagnostics().indexedItems;if(count==0)mutableState.value=MemorySearchState.Indexing(observation.progress)else if(mutableState.value !is MemorySearchState.Results)mutableState.value=MemorySearchState.Ready(count)}
            androidx.work.WorkInfo.State.SUCCEEDED->reloadCatalog()
            androidx.work.WorkInfo.State.FAILED->mutableState.value=MemorySearchState.Failed("Indexing unavailable (${observation.failure?.name?.lowercase()?:"unknown"})")
            androidx.work.WorkInfo.State.CANCELLED->reloadCatalog()
            null->Unit
        } } }
    }
    fun permissionResult(){if(hasPermission(getApplication()))refresh()else mutableState.value=MemorySearchState.PermissionRequired}
    fun refresh(){durableIndexing.enqueue()}
    fun cancelIndexing(){durableIndexing.cancel()}
    private fun reloadCatalog(){val records=db.records();coordinator.replaceRecords(records,db.indexGeneration());mutableState.value=MemorySearchState.Ready(records.size)}
    fun search(raw:String){viewModelScope.launch(Dispatchers.Default){val run=coordinator.search(raw);mutableState.value=MemorySearchState.Results(raw,if(run.confidence.confident)run.matches else emptyList())}}
    override fun onCleared(){embeddings.close();super.onCleared()}
    companion object { fun hasPermission(context:Context)=MediaCapabilityManager(context).current().canReadAny }
}
