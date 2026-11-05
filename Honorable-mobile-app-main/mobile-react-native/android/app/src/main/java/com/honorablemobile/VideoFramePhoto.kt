package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/** Local nearest source-frame decode. No video grade/crop/captions, network or media writes. */
internal object VideoFramePhoto {
    fun extract(context:Context,source:Uri,sourceDurationMs:Long,plan:VideoFramePhotoPlan,requireCurrent:()->Unit):File {
        require(source.scheme=="content")
        requireCurrent()
        val retriever=MediaMetadataRetriever()
        var decoded:Bitmap?=null
        var scaled:Bitmap?=null
        try {
            retriever.setDataSource(context,source);requireCurrent()
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)=="yes"){"The source has no readable video"}
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()==sourceDurationMs){"The original video changed"}
            require(plan.clip.endMs<=sourceDurationMs)
            val frame=if(Build.VERSION.SDK_INT>=27){
                retriever.getScaledFrameAtTime(plan.sourceUs,MediaMetadataRetriever.OPTION_CLOSEST,VideoFramePhotoPlan.MAX_EDGE,VideoFramePhotoPlan.MAX_EDGE)
            }else{
                val width=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:0
                val height=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0
                require(VideoFramePhotoPlan.legacyDecodeAllowed(width,height)){"This older Android version supports frame photos from videos up to 8.3 megapixels"}
                retriever.getFrameAtTime(plan.sourceUs,MediaMetadataRetriever.OPTION_CLOSEST)
            }?:error("No frame could be decoded at this moment")
            decoded=frame;requireCurrent()
            val size=VideoFramePhotoPlan.size(frame.width,frame.height)
            val output=if(frame.width==size.first&&frame.height==size.second)frame else Bitmap.createScaledBitmap(frame,size.first,size.second,true).also{scaled=it}
            return VideoFramePhotoFiles.write(context.cacheDir,requireCurrent){file->
                FileOutputStream(file).use{stream->check(output.compress(Bitmap.CompressFormat.PNG,100,stream)){"Frame encoding failed"};stream.fd.sync()}
            }
        }finally{
            if(scaled!==decoded)scaled?.recycle()
            decoded?.recycle();runCatching{retriever.release()}
        }
    }
}
