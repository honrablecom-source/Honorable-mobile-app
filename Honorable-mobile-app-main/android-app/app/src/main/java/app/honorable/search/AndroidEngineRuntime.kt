package app.honorable.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.ActivityManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class IndexResource { MEDIA_DECODE, OCR, LOCAL_VISION, MODEL_INFERENCE, VIDEO_DECODE, DATABASE_WRITE }

class IndexResourceScheduler internal constructor(val limits:Map<IndexResource,Int>) {
    private val permits=IndexResource.entries.associateWith { resource -> Semaphore(limits[resource]?:1) }
    suspend fun <T> withResource(resource:IndexResource,block:suspend()->T):T=permits.getValue(resource).withPermit{block()}
}

/** Process-wide mobile resource budgets shared by UI and bridge indexing entry points. */
object HonorableResourceScheduler {
    val instance=IndexResourceScheduler(mapOf(
        IndexResource.MEDIA_DECODE to 2,
        IndexResource.OCR to 1,
        IndexResource.LOCAL_VISION to 1,
        IndexResource.MODEL_INFERENCE to 1,
        IndexResource.VIDEO_DECODE to 1,
        IndexResource.DATABASE_WRITE to 1
    ))
}

data class ResourceGovernorSnapshot(val device:DeviceCapability,val thermalStatus:Int?,val limits:ResourceLimits,val degraded:Boolean)
class AndroidResourceGovernor(context:Context) {
    private val app=context.applicationContext
    val device:DeviceCapability by lazy {
        val activity=app.getSystemService(ActivityManager::class.java)
        DeviceCapability(activity.memoryClass,Runtime.getRuntime().availableProcessors(),activity.isLowRamDevice,true,if(Build.VERSION.SDK_INT>=28)activity.isBackgroundRestricted else false)
    }
    val limits:ResourceLimits by lazy(device::defaultLimits)
    val scheduler:IndexResourceScheduler by lazy { IndexResourceScheduler(mapOf(IndexResource.MEDIA_DECODE to limits.bitmapDecodes,IndexResource.OCR to limits.ocr,IndexResource.LOCAL_VISION to 1,IndexResource.MODEL_INFERENCE to limits.tinyClip,IndexResource.VIDEO_DECODE to limits.videoDecode,IndexResource.DATABASE_WRITE to limits.databaseWrites)) }
    fun snapshot():ResourceGovernorSnapshot { val thermal=if(Build.VERSION.SDK_INT>=29)app.getSystemService(PowerManager::class.java).currentThermalStatus else null;return ResourceGovernorSnapshot(device,thermal,limits,device.lowRam||(thermal?:0)>=PowerManager.THERMAL_STATUS_SEVERE) }
}

enum class MediaLibraryAccess { NONE, PARTIAL, FULL }
data class MediaCapabilities(val access:MediaLibraryAccess,val canReadImages:Boolean,val canReadVideos:Boolean) { val canReadAny get()=canReadImages||canReadVideos }

/** Single authority for Android media permission and partial-library capability checks. */
class MediaCapabilityManager(private val context:Context) {
    fun current():MediaCapabilities {
        if(Build.VERSION.SDK_INT<33) {
            val legacy=granted(Manifest.permission.READ_EXTERNAL_STORAGE)
            return MediaCapabilities(if(legacy)MediaLibraryAccess.FULL else MediaLibraryAccess.NONE,legacy,legacy)
        }
        val images=granted(Manifest.permission.READ_MEDIA_IMAGES);val videos=granted(Manifest.permission.READ_MEDIA_VIDEO)
        val selected=Build.VERSION.SDK_INT>=34&&granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val access=when { images&&videos->MediaLibraryAccess.FULL;images||videos||selected->MediaLibraryAccess.PARTIAL;else->MediaLibraryAccess.NONE }
        return MediaCapabilities(access,images||selected,videos||selected)
    }

    fun requestedPermissions():Array<String> = when {
        Build.VERSION.SDK_INT>=34->arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT>=33->arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)
        else->arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(permission:String)=ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED
}

enum class LocalModelState { UNLOADED, READY, CLOSED }
data class DatabaseDiagnostics(val healthy:Boolean,val schemaVersion:Int,val indexedItems:Int,val indexedVideoFrames:Int,val indexGeneration:Long=0,val staleItems:Int=0,val recentFailureCount:Int=0)
data class EngineDoctorSnapshot(
    val mediaCapabilities:MediaCapabilities,
    val tinyClipState:LocalModelState,
    val database:DatabaseDiagnostics,
    val activeIndexJob:IndexJobSnapshot?,
    val processorVersions:ProcessorVersions,
    val embeddingDimension:Int,
    val ocrReady:Boolean,
    val vlmReady:Boolean,
    val videoReady:Boolean,
    val governor:ResourceGovernorSnapshot
)

/** On-demand, local-only diagnostics. It reports health/counts and never media content. */
class EngineDoctor(
    private val capabilities:MediaCapabilityManager,
    private val database:LocalMediaDatabase,
    private val tinyClip:AndroidTinyClipEmbeddingService,
    private val governor:AndroidResourceGovernor=AndroidResourceGovernor(database.context())
) {
    private val versions=ProcessorVersions(tinyClipModel=tinyClip.modelId)
    fun inspect()=EngineDoctorSnapshot(capabilities.current(),tinyClip.lifecycleState(),database.diagnostics(versions),database.latestJob(),versions,tinyClip.dimension,true,false,true,governor.snapshot())
    fun privacySafeReport():String=inspect().let { s -> "Honorable Engine Health\npermission=${s.mediaCapabilities.access}\nindexed=${s.database.indexedItems}\nstale=${s.database.staleItems}\nfailed=${s.database.recentFailureCount}\nschema=${s.database.schemaVersion}\ngeneration=${s.database.indexGeneration}\ntinyclip=${s.tinyClipState}:${s.processorVersions.tinyClipModel}:${s.embeddingDimension}\nocr=${s.ocrReady}\nvlm=${s.vlmReady}\nvideo=${s.videoReady}\nlimits=${s.governor.limits}\nthermal=${s.governor.thermalStatus}\njob=${s.activeIndexJob?.state}" }
}
