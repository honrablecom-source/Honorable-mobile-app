package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Native Task owns its bitmap until completion, including Stop/timeout/error. No source writes. */
internal object VideoVisualTextReader {
    internal fun cropCopy(frame:Bitmap,crop:VideoVisualText.Crop,current:()->Unit={}):Bitmap{
        current();require(frame.width>0&&frame.height>0&&frame.width.toLong()*frame.height<=8_294_400)
        var scaled:Bitmap?=null;var copy:Bitmap?=null
        try{
            val factor=minOf(1.0,1280.0/maxOf(frame.width,frame.height))
            val image=if(factor<1.0)Bitmap.createScaledBitmap(frame,(frame.width*factor).toInt().coerceAtLeast(1),(frame.height*factor).toInt().coerceAtLeast(1),true).also{scaled=it}else frame
            val y=crop.pixels(image.height)
            val owned=Bitmap.createBitmap(image.width,y.count(),Bitmap.Config.ARGB_8888);copy=owned
            android.graphics.Canvas(owned).drawBitmap(image,android.graphics.Rect(0,y.first,image.width,y.last+1),android.graphics.Rect(0,0,image.width,y.count()),null)
            current();copy=null;return owned
        }finally{copy?.recycle();if(scaled!==frame)scaled?.recycle()}
    }
    internal fun <T> recognizeOwned(bitmap:Bitmap,process:(Bitmap)->Task<T>,text:(T)->String,current:()->Unit,timeoutMs:Long=15000):String {
        require(timeoutMs in 1..15000)
        var task:Task<T>?=null
        try {
            current();task=process(bitmap)
            val started=System.nanoTime();var abort:Throwable?=null
            while(!task.isComplete){
                if(abort==null)try{current();check(System.nanoTime()-started<timeoutMs*1_000_000){"Video OCR took too long"}}catch(failure:Throwable){abort=failure}
                // SDK has no reliable cancel operation. Retain bitmap/client/worker lease until it settles.
                try{Tasks.await(task,100,TimeUnit.MILLISECONDS)}catch(_:TimeoutException){}
                catch(_:InterruptedException){if(abort==null)abort=CancellationException("Video OCR stopped")}
                catch(_:java.util.concurrent.ExecutionException){}
                catch(_:CancellationException){}
            }
            abort?.let{throw it};current()
            check(!task.isCanceled){"Video OCR was cancelled"}
            check(task.isSuccessful){"A video frame could not be read"}
            return text(task.result)
        } finally {
            // No early recycle if Task construction succeeded but a later allocation/check failed.
            if(task==null||task.isComplete)bitmap.recycle()
            else task.addOnCompleteListener(java.util.concurrent.Executor{it.run()}){bitmap.recycle()}
        }
    }

    fun read(context:Context,uri:Uri,sourceDuration:Long,from:Long,to:Long,crop:VideoVisualText.Crop,cancelled:()->Boolean):List<VideoCaption>{
        require(uri.scheme=="content"){"Choose a local source video"}
        val windows=VideoVisualText.windows(from,to);require(to<=sourceDuration)
        val started=System.nanoTime()
        fun current(){if(cancelled()||Thread.currentThread().isInterrupted)throw CancellationException("Video OCR stopped");check(System.nanoTime()-started<60_000_000_000L){"Video OCR took too long. Try a shorter clip"}}
        current();val retriever=MediaMetadataRetriever();var recognizer:com.google.mlkit.vision.text.TextRecognizer?=null
        try{
            retriever.setDataSource(context,uri);current()
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()==sourceDuration){"Original video changed"}
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)=="yes")
            if(Build.VERSION.SDK_INT<27){val w=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:0;val h=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0
                require(VideoFramePhotoPlan.legacyDecodeAllowed(w,h)){"Older Android needs a source up to 8.3 megapixels"}}
            val client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);recognizer=client
            val observations=windows.map{window->
                current()
                val decoded=if(Build.VERSION.SDK_INT>=27)retriever.getScaledFrameAtTime(window.requestedMs*1000,MediaMetadataRetriever.OPTION_CLOSEST,1280,1280)
                    else retriever.getFrameAtTime(window.requestedMs*1000,MediaMetadataRetriever.OPTION_CLOSEST)
                val frame=requireNotNull(decoded){"No frame could be decoded"}
                try{
                    val snapshot=cropCopy(frame,crop,::current)
                    recognizeOwned(snapshot,{client.process(InputImage.fromBitmap(it,0))},{it.text},::current)
                }finally{frame.recycle()}
            }
            current();return VideoVisualText.captions(from,to,observations,::current)
        }finally{runCatching{recognizer?.close()};runCatching{retriever.release()}}
    }
}
