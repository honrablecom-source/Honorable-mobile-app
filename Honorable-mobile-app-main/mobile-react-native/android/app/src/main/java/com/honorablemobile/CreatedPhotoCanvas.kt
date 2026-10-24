package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal enum class PhotoCanvasFormat(val id:String,val width:Int,val height:Int) {
    SQUARE("square",1080,1080), PORTRAIT("portrait",1080,1920), LANDSCAPE("landscape",1920,1080);
    companion object {
        fun parse(id:String):PhotoCanvasFormat = entries.firstOrNull{it.id==id}
            ?: throw IllegalArgumentException("Choose square, portrait or landscape")
    }
}

/** A new local source, never a write to an imported photo or account project. */
internal object CreatedPhotoCanvas {
    fun create(context:Context,formatId:String):Uri {
        val format=PhotoCanvasFormat.parse(formatId)
        val directory=File(context.cacheDir,"created-images")
        check(directory.isDirectory||directory.mkdirs()) { "Image creation storage is unavailable" }
        val file=File.createTempFile("honorable-canvas-",".png",directory)
        var bitmap:Bitmap?=null
        try {
            val canvas=Bitmap.createBitmap(format.width,format.height,Bitmap.Config.ARGB_8888)
            bitmap=canvas;canvas.eraseColor(Color.WHITE)
            file.outputStream().use { check(canvas.compress(Bitmap.CompressFormat.PNG,100,it)) { "Could not create the image" } }
            return FileProvider.getUriForFile(context,context.packageName+".creation",file)
        } catch(error:OutOfMemoryError) {
            file.delete();throw IllegalStateException("Not enough memory to create an image. Try a square canvas.",error)
        } catch(error:Exception) {
            file.delete();throw error
        } finally { bitmap?.recycle() }
    }
}
