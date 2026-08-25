package app.honorable.search

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.ByteArrayOutputStream

data class IndexProgress(val processed:Int=0,val total:Int=0)
sealed interface MemorySearchState {
    data object PermissionRequired:MemorySearchState
    data class Indexing(val progress:IndexProgress):MemorySearchState
    data class Ready(val count:Int):MemorySearchState
    data class Results(val query:String,val matches:List<SearchMatch>):MemorySearchState
    data class Failed(val message:String):MemorySearchState
}

data class DiscoveredMedia(val id:Long,val uri:String,val kind:MediaKind,val capturedAt:Long,val modifiedAt:Long,val name:String,val durationMs:Long?)

class AndroidMediaIndexer(private val context:Context,private val database:LocalMediaDatabase,private val embeddings:EmbeddingService) {
    private val resolver=context.contentResolver
    suspend fun synchronize(progress:(IndexProgress)->Unit):IndexStats=withContext(Dispatchers.IO) {
        val discovered=discover();val known=database.modifiedTimes();val cached=database.records().associateBy{it.uri};var added=0;var updated=0;var processed=0
        discovered.forEach { item ->
            if(known[item.uri]!=item.modifiedAt) {
                val bitmap=thumbnail(item)
                val screenshot=item.name.contains("screenshot",true)
                val labels=bitmap?.let{label(it)}?:emptySet()
                val colors=bitmap?.let{sampleColors(it)}?:emptySet()
                val ocr=bitmap?.let{recognize(it)}?:""
                val vector=bitmap?.let{embeddings.image(it.jpeg())}
                val frames=if(item.kind==MediaKind.VIDEO)analyzeVideo(item)else emptyList()
                database.upsert(MediaRecord(item.id,item.kind,item.capturedAt,ocr=ocr,labels=labels,embedding=vector,videoFrames=frames,metadataTerms=setOf(if(item.kind==MediaKind.VIDEO)"video" else "photo"),dominantColors=colors,isScreenshot=screenshot,uri=item.uri,displayName=item.name,durationMs=item.durationMs,visionUnderstanding=cached[item.uri]?.visionUnderstanding),item.modifiedAt)
                if(item.uri in known)updated++ else added++
            }
            progress(IndexProgress(++processed,discovered.size))
        }
        val uris=discovered.mapTo(mutableSetOf()){it.uri};val deleted=known.keys.count{it !in uris};database.removeDeleted(uris)
        IndexStats(added,updated,deleted)
    }
    private fun discover():List<DiscoveredMedia> {
        val result=mutableListOf<DiscoveredMedia>()
        fun query(collection:Uri,kind:MediaKind) {
            val projection=arrayOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DATE_TAKEN,MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns.DISPLAY_NAME,if(kind==MediaKind.VIDEO)MediaStore.Video.VideoColumns.DURATION else MediaStore.MediaColumns.SIZE)
            resolver.query(collection,projection,null,null,"${MediaStore.MediaColumns.DATE_TAKEN} DESC")?.use { c ->
                val id=c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);val taken=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);val modified=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);val name=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);val last=c.getColumnIndexOrThrow(projection.last())
                while(c.moveToNext()){val mediaId=c.getLong(id);result+=DiscoveredMedia(mediaId,ContentUris.withAppendedId(collection,mediaId).toString(),kind,c.getLong(taken).takeIf{it>0}?:c.getLong(modified)*1000,c.getLong(modified)*1000,c.getString(name).orEmpty(),if(kind==MediaKind.VIDEO)c.getLong(last) else null)}
            }
        }
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,MediaKind.IMAGE);query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,MediaKind.VIDEO)
        return result
    }
    private fun thumbnail(item:DiscoveredMedia):Bitmap?=runCatching { resolver.loadThumbnail(Uri.parse(item.uri),Size(384,384),null) }.getOrNull()
    private suspend fun analyzeVideo(item:DiscoveredMedia):List<VideoFrame> { val duration=item.durationMs?:return emptyList();val candidates=listOf(0L,duration/4,duration/2,duration*3/4,(duration-1).coerceAtLeast(0)).distinct().mapNotNull { at->videoBitmap(item.uri,at)?.let{FrameCandidate(at,fingerprint(it))} };return RepresentativeFrameSelector.select(candidates).mapNotNull { selected->videoBitmap(item.uri,selected.timestampMs)?.let{bitmap->VideoFrame(selected.timestampMs,recognize(bitmap),label(bitmap),embeddings.image(bitmap.jpeg()),sampleColors(bitmap),selected.sceneFingerprint)} } }
    private fun videoBitmap(uri:String,atMs:Long):Bitmap?=runCatching { MediaMetadataRetriever().let { retriever->try{retriever.setDataSource(context,Uri.parse(uri));retriever.getFrameAtTime(atMs*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)}finally{retriever.release()} } }.getOrNull()
    private fun fingerprint(bitmap:Bitmap):Long { var bits=0L;var sum=0L;val values=IntArray(64){i->bitmap.getPixel(i%8*bitmap.width/8,i/8*bitmap.height/8).let{p->((p shr 16 and 255)+(p shr 8 and 255)+(p and 255))/3}.also{sum+=it}};values.forEachIndexed{i,v->if(v>=sum/64)bits=bits or(1L shl i)};return bits }
    private fun Bitmap.jpeg():ByteArray=ByteArrayOutputStream().use{out->compress(Bitmap.CompressFormat.JPEG,92,out);out.toByteArray()}
    private suspend fun recognize(bitmap:Bitmap):String=suspendCancellableCoroutine { continuation ->
        val client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);client.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{continuation.resume(it.text)}.addOnFailureListener{continuation.resume("")}.addOnCompleteListener{client.close()}
    }
    private suspend fun label(bitmap:Bitmap):Set<String> = suspendCancellableCoroutine { continuation ->
        val client=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);client.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{labels->continuation.resume(labels.filter{it.confidence>=.55f}.mapTo(linkedSetOf()){it.text.lowercase()})}.addOnFailureListener{continuation.resume(emptySet())}.addOnCompleteListener{client.close()}
    }
    private fun sampleColors(bitmap:Bitmap):Set<String>{val w=bitmap.width;val h=bitmap.height;if(w==0||h==0)return emptySet();val pixels=IntArray(256){i->bitmap.getPixel((i%16*w/16).coerceAtMost(w-1),(i/16*h/16).coerceAtMost(h-1))};return ColorEvidenceAnalyzer.dominantColors(pixels)}
}

class MemoriesViewModel(application:Application):AndroidViewModel(application) {
    private val db=LocalMediaDatabase(application);private val embeddings=AndroidTinyClipEmbeddingService(application);private val indexer=AndroidMediaIndexer(application,db,embeddings);private val parser=QueryParser();private val encoder=SemanticQueryEncoder(embeddings)
    private val mutableState=MutableStateFlow<MemorySearchState>(if(hasPermission(application))MemorySearchState.Indexing(IndexProgress()) else MemorySearchState.PermissionRequired)
    val state:StateFlow<MemorySearchState> = mutableState.asStateFlow()
    init { if(hasPermission(application))refresh() }
    fun permissionResult(){if(hasPermission(getApplication()))refresh()else mutableState.value=MemorySearchState.PermissionRequired}
    fun refresh(){viewModelScope.launch { mutableState.value=MemorySearchState.Indexing(IndexProgress());runCatching{indexer.synchronize{mutableState.value=MemorySearchState.Indexing(it)};db.records()}.onSuccess{mutableState.value=MemorySearchState.Ready(it.size)}.onFailure{mutableState.value=MemorySearchState.Failed(it.message?:"Unable to index media")}}}
    fun search(raw:String){viewModelScope.launch(Dispatchers.Default){val records=db.records();val query=parser.parse(raw);val vectors=LocalVectorIndex().also{index->records.forEach{record->record.embedding?.let{index.upsert(record.id,it)};record.videoFrames.forEach{frame->frame.embedding?.let{index.upsert(record.id,it)}}}};val matches=HybridSearchEngine(vectors).search(query,records.associateBy{it.id},encoder.encode(query));val credible=if(confidenceDecision(matches).confident)matches.filter{it.breakdown.fullSemantic>=.30||it.confidence!=MatchConfidence.WEAK}else emptyList();mutableState.value=MemorySearchState.Results(raw,credible)}}
    override fun onCleared(){embeddings.close();super.onCleared()}
    companion object {
        fun permissions()=if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        fun hasPermission(context:Context)=permissions().all{ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED}
    }
}
