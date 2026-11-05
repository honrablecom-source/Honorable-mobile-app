package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import app.honorable.editor.PhotoSourceDecoder
import java.io.ByteArrayOutputStream

/** Only reads an explicitly selected content URI; output is an in-memory fitted copy without source metadata. */
internal object DrawingPhotoCopy {
    fun prepare(context:Context,source:Uri,cancelled:()->Boolean):String {
        require(DrawingPhotoPolicy.acceptsSource(source.scheme)){"Choose a local content photo"}
        fun active(){check(!cancelled()&&!Thread.currentThread().isInterrupted){"Photo preparation cancelled"}}
        active()
        var bitmap:Bitmap?=null
        try {
            bitmap=PhotoSourceDecoder.decode(context,source,DrawingPhotoPolicy.MAX_SIDE)
            active()
            val alpha=bitmap.hasAlpha();val mime=if(alpha)"image/png"else"image/jpeg"
            val format=if(alpha)Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            var side=minOf(DrawingPhotoPolicy.MAX_SIDE,maxOf(bitmap.width,bitmap.height))
            while(true){
                active()
                val current=requireNotNull(bitmap);val(width,height)=DrawingPhotoPolicy.fitted(current.width,current.height,side)
                if(width!=current.width||height!=current.height){val scaled=Bitmap.createScaledBitmap(current,width,height,true);bitmap=scaled;if(scaled!==current)current.recycle()}
                val bytes=ByteArrayOutputStream().use{output->check(requireNotNull(bitmap).compress(format,86,output));output.toByteArray()}
                active()
                if(bytes.size<=DrawingPhotoPolicy.MAX_BYTES)return "data:$mime;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)
                side=DrawingPhotoPolicy.smaller(side)?:error("Photo copy exceeds its budget")
            }
        } finally {bitmap?.recycle()}
    }
}
