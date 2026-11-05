package com.honorablemobile

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import java.io.File

/** Real local Media3 image encoding, not generation. Only writes the supplied owned temporary file. */
@UnstableApi
internal class PhotoVideoEncoder(private val context:Context) {
    private var transformer:Transformer?=null
    private var finished=false
    fun start(image:File,output:File,format:PhotoVideoFormat,durationMs:Long,complete:(Result<Unit>)->Unit){
        check(Looper.myLooper()==Looper.getMainLooper()&&!finished&&transformer==null)
        fun finish(result:Result<Unit>){if(finished)return;finished=true;transformer=null;complete(result)}
        try {
            require(durationMs in 1000..10000&&image.isFile&&!output.exists())
            val item=MediaItem.Builder().setUri(Uri.fromFile(image)).setImageDurationMs(durationMs).build()
            val edited=EditedMediaItem.Builder(item).setFrameRate(30).setRemoveAudio(true)
                .setEffects(Effects(emptyList(),listOf(Presentation.createForWidthAndHeight(format.width,format.height,Presentation.LAYOUT_SCALE_TO_FIT)))).build()
            transformer=Transformer.Builder(context.applicationContext).setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object:Transformer.Listener{
                    override fun onCompleted(composition:Composition,result:ExportResult){finish(Result.success(Unit))}
                    override fun onError(composition:Composition,result:ExportResult,exception:ExportException){finish(Result.failure(exception))}
                }).build()
            transformer!!.start(edited,output.absolutePath)
        }catch(failure:Exception){finish(Result.failure(failure))}
    }
    fun cancel(){check(Looper.myLooper()==Looper.getMainLooper());finished=true;transformer?.cancel();transformer=null}
}
