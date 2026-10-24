package com.honorablemobile

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import android.system.Os
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** AAC in an audio-only MP4 container, copied only after all imported originals are protected. */
@UnstableApi
internal class VideoAudioExporter(context: Context) {
    private val context=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val cancelled=AtomicBoolean(false)
    private var transformer:Transformer?=null
    private var temporary:File?=null
    private var started=false
    private var finished=false
    private var completing=false
    private var workerBusy=false
    private var copied=false
    private var destination:Uri?=null
    private var originals=emptyMap<String,Uri>()
    private var callback:((Result<Unit>)->Unit)?=null

    fun export(sources:Map<String,Uri>, destination:Uri, clips:List<VideoSegment>, sourceDurations:Map<String,Long>, soundtrack:VideoSoundtrack?, done:(Result<Unit>)->Unit) {
        check(Looper.myLooper()==Looper.getMainLooper())
        check(!started) { "Start a new audio exporter for each copy" }
        started=true;callback=done;this.destination=destination
        originals=sources.toMap()+(soundtrack?.let { mapOf("__soundtrack__" to Uri.parse(it.uri)) }?:emptyMap())
        val snapshot=clips.toList();val durations=sourceDurations.toMap()
        workerBusy=true
        Thread {
            val prepared=runCatching {
                checkCancelled()
                require(originals.isNotEmpty()&&destination !in originals.values) { "An audio copy cannot replace an imported original" }
                require(snapshot.isNotEmpty()&&snapshot.all { it.sourceId in originals }) { "Missing clip source" }
                val audioSources=snapshot.map { it.sourceId }.distinct().filter { hasAudio(requireNotNull(originals[it])) }.toSet()
                soundtrack?.let { require(hasAudio(Uri.parse(it.uri))) { "The soundtrack has no readable audio track" } }
                VideoAudioPlan.requireAudio(snapshot,audioSources,soundtrack)
                checkCancelled()
                audioSources
            }
            main.post {
                workerBusy=false
                if(finished||completing)return@post
                if(cancelled.get()){fail(OperationCanceledException());return@post}
                prepared.onSuccess { audioSources ->
                    runCatching {
                        val file=File.createTempFile("honorable-audio-",".m4a",context.cacheDir)
                        temporary=file;require(file.delete()) { "Cannot prepare temporary audio" }
                        val composition=VideoCompositionExporter.audioComposition(originals,snapshot,durations,soundtrack,audioSources)
                        transformer=Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_AAC).setUsePlatformDiagnostics(false)
                            .addListener(object:Transformer.Listener {
                                override fun onCompleted(composition:Composition,result:ExportResult) {
                                    transformer=null
                                    if(cancelled.get()){fail(OperationCanceledException());return}
                                    workerBusy=true
                                    Thread {
                                        val resultCopy=runCatching {
                                            VideoCompositionExporter.copyRenderedAudioToDocument(context,originals,destination,file,snapshot,cancelled)
                                        }
                                        main.post {
                                            workerBusy=false;copied=resultCopy.isSuccess
                                            if(cancelled.get())fail(resultCopy.exceptionOrNull()?:OperationCanceledException())
                                            else resultCopy.fold({complete(Result.success(Unit))},{fail(it)})
                                        }
                                    }.start()
                                }
                                override fun onError(composition:Composition,result:ExportResult,error:ExportException) {
                                    transformer=null;fail(error)
                                }
                            }).build()
                        transformer!!.start(composition,file.absolutePath)
                    }.onFailure { fail(it) }
                }.onFailure { fail(it) }
            }
        }.start()
    }

    private fun checkCancelled() { if(cancelled.get()||Thread.currentThread().isInterrupted)throw OperationCanceledException() }
    private fun hasAudio(uri:Uri):Boolean {
        checkCancelled()
        val extractor=MediaExtractor()
        return try {
            extractor.setDataSource(context,uri,null)
            (0 until extractor.trackCount).any { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/") }
        } finally { extractor.release() }
    }

    private fun complete(result:Result<Unit>) {
        if(finished)return
        finished=true;transformer=null
        temporary?.delete();temporary=null
        callback?.also { callback=null }?.invoke(result)
    }

    private fun fail(error:Throwable) {
        if(finished||completing)return
        completing=true
        transformer?.cancel();transformer=null
        // No background reader still owns the private render when this method is called.
        temporary?.delete();temporary=null
        Thread {
            val failure=if(error is VideoExportFailure)error else {
                val removed=destination?.let { removeSeparateDestination(context,it,originals.values.toSet(),onlyEmpty=!copied) }?:false
                val message=if(error is OperationCanceledException)"Audio export cancelled" else "Audio export unavailable: ${error.message?:"Unsupported audio or destination"}"
                VideoExportFailure(message+(if(!removed)". Check Files for ${if(copied)"a partial"else"an empty"} copy."else""),error,!removed)
            }
            main.post { complete(Result.failure(failure)) }
        }.start()
    }

    fun cancel() {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(finished||completing)return
        cancelled.set(true);transformer?.cancel();transformer=null
        if(!workerBusy)fail(OperationCanceledException())
    }

    companion object {
        internal fun verifyOutput(context:Context,destination:Uri,clips:List<VideoSegment>) {
            val extractor=MediaExtractor();val metadata=MediaMetadataRetriever()
            try {
                extractor.setDataSource(context,destination,null)
                val audio=(0 until extractor.trackCount).filter { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/") }
                require(audio.size==1&&extractor.trackCount==1) { "Saved copy must contain one audio track and no video" }
                val format=extractor.getTrackFormat(audio.single())
                require(format.getString(MediaFormat.KEY_MIME)==MimeTypes.AUDIO_AAC) { "This device did not produce AAC audio" }
                require(format.getInteger(MediaFormat.KEY_SAMPLE_RATE)>0&&format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)>0) { "Saved audio has an invalid format" }
                extractor.selectTrack(audio.single());extractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                require(extractor.sampleTrackIndex==audio.single()&&extractor.sampleTime>=0&&extractor.readSampleData(ByteBuffer.allocate(64*1024),0)>0) { "Saved copy has no audio samples" }
                metadata.setDataSource(context,destination)
                VideoAudioPlan.validateDuration(clips,metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0)
            } finally { metadata.release();extractor.release() }
        }

        /** Only delete our separate copy; an alias or unavailable original always fails closed. */
        internal fun removeSeparateDestination(context:Context,destination:Uri,originals:Set<Uri>,onlyEmpty:Boolean):Boolean = runCatching {
            require(originals.isNotEmpty()&&destination !in originals)
            val resolver=context.contentResolver
            resolver.openFileDescriptor(destination,"rw").use { output ->
                requireNotNull(output)
                val target=Os.fstat(output.fileDescriptor)
                require(target.st_ino!=0L&&(!onlyEmpty||target.st_size==0L))
                for(original in originals)resolver.openFileDescriptor(original,"r").use { input ->
                    requireNotNull(input)
                    val source=Os.fstat(input.fileDescriptor)
                    require(source.st_ino!=0L&&(source.st_dev!=target.st_dev||source.st_ino!=target.st_ino))
                }
            }
            DocumentsContract.deleteDocument(resolver,destination)
        }.getOrDefault(false)
    }
}
