package app.honorable.search

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The only owner of full-library synchronization. UI code may enqueue, observe, or cancel it. */
class MediaIndexWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    private val database=LocalMediaDatabase(context)
    private val jobId=id.toString()

    override suspend fun doWork():Result {
        database.createJob(jobId)
        database.updateJob(jobId,IndexJobState.RUNNING,attempt=runAttemptCount+1)
        var model:AndroidTinyClipEmbeddingService?=null
        return try {
            if(!MediaCapabilityManager(applicationContext).current().canReadAny) {
                database.updateJob(jobId,IndexJobState.FAILED,failure=EngineFailureKind.PERMISSION)
                return Result.failure(workDataOf(KEY_FAILURE to EngineFailureKind.PERMISSION.name))
            }
            model=AndroidTinyClipEmbeddingService(applicationContext)
            var priorStage:IndexingStage?=null;var stageStarted=System.nanoTime()
            val stats=AndroidMediaIndexer(applicationContext,database,requireNotNull(model)).synchronize(progress={ progress ->
                if(priorStage!=progress.stage){priorStage?.let{database.recordMetric("indexing",it.name,(System.nanoTime()-stageStarted)/1_000_000)};priorStage=progress.stage;stageStarted=System.nanoTime()}
                setProgressAsync(workDataOf(KEY_STAGE to progress.stage.name,KEY_PROCESSED to progress.processed,KEY_TOTAL to progress.total,KEY_FAILED to progress.failed))
                database.updateJob(jobId,IndexJobState.RUNNING,progress.stage.name,progress.processed,progress.total,progress.failed,runAttemptCount+1)
            })
            val terminal=if(stats.failed>0)IndexJobState.PARTIAL_FAILURE else IndexJobState.COMPLETED
            priorStage?.let{database.recordMetric("indexing",it.name,(System.nanoTime()-stageStarted)/1_000_000)}
            val handled=stats.added+stats.updated+stats.skipped+stats.failed
            database.updateJob(jobId,terminal,processed=handled,total=handled,failed=stats.failed,attempt=runAttemptCount+1)
            Result.success(workDataOf(KEY_FAILED to stats.failed))
        } catch(cancelled:CancellationException) {
            database.updateJob(jobId,IndexJobState.CANCELLED,failure=EngineFailureKind.CANCELLED);throw cancelled
        } catch(error:Throwable) {
            val failure=classify(error);database.recordError(failure.kind,null)
            if(failure.retryable&&runAttemptCount+1<MAX_ATTEMPTS) {
                database.updateJob(jobId,IndexJobState.QUEUED,attempt=runAttemptCount+1,failure=failure.kind);Result.retry()
            } else {
                database.updateJob(jobId,IndexJobState.FAILED,attempt=runAttemptCount+1,failure=failure.kind);Result.failure(workDataOf(KEY_FAILURE to failure.kind.name))
            }
        } finally { model?.close() }
    }

    private fun classify(error:Throwable)=when(error) {
        is SecurityException->EngineFailure(EngineFailureKind.PERMISSION)
        is android.database.sqlite.SQLiteDatabaseLockedException->EngineFailure(EngineFailureKind.DATABASE_CONTENTION)
        is IOException->EngineFailure(EngineFailureKind.TRANSIENT_IO)
        is OutOfMemoryError->EngineFailure(EngineFailureKind.TEMPORARY_RESOURCE)
        else->EngineFailure(EngineFailureKind.UNKNOWN)
    }

    companion object {
        const val UNIQUE_WORK="honorable-media-index";const val MAX_ATTEMPTS=3
        const val KEY_STAGE="stage";const val KEY_PROCESSED="processed";const val KEY_TOTAL="total";const val KEY_FAILED="failed";const val KEY_FAILURE="failure"
    }
}

class DurableIndexing(private val context:Context,private val manager:WorkManager=WorkManager.getInstance(context)) {
    fun enqueue():UUID {
        val request=OneTimeWorkRequestBuilder<MediaIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS).build()
        manager.enqueueUniqueWork(MediaIndexWorker.UNIQUE_WORK,ExistingWorkPolicy.KEEP,request)
        return request.id
    }
    fun cancel()=manager.cancelUniqueWork(MediaIndexWorker.UNIQUE_WORK)
    data class Observation(val state:WorkInfo.State,val progress:IndexProgress,val failure:EngineFailureKind?=null)
    fun states():Flow<Observation?> = manager.getWorkInfosForUniqueWorkFlow(MediaIndexWorker.UNIQUE_WORK).map { infos ->
        infos.maxByOrNull { it.runAttemptCount }?.let { info ->
            val data=if(info.state==WorkInfo.State.SUCCEEDED||info.state==WorkInfo.State.FAILED)info.outputData else info.progress
            Observation(info.state,IndexProgress(data.getInt(MediaIndexWorker.KEY_PROCESSED,0),data.getInt(MediaIndexWorker.KEY_TOTAL,0),data.getInt(MediaIndexWorker.KEY_FAILED,0),stage=runCatching{IndexingStage.valueOf(data.getString(MediaIndexWorker.KEY_STAGE)?:IndexingStage.DISCOVERING.name)}.getOrDefault(IndexingStage.DISCOVERING)),data.getString(MediaIndexWorker.KEY_FAILURE)?.let{runCatching{EngineFailureKind.valueOf(it)}.getOrNull()})
        }
    }
}
