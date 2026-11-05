package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal enum class PhotoCanvasFormat(val id:String,val width:Int,val height:Int) {
    SQUARE("square",1080,1080), PORTRAIT("portrait",1080,1920), LANDSCAPE("landscape",1920,1080),
    FEED_PORTRAIT("feed_portrait",1080,1350), STORY("story",1080,1920), REELS("reels",1080,1920),
    ULTRAWIDE("ultrawide",2560,1080), POSTCARD("postcard",1200,1500), BANNER("banner",1500,500),
    POSTER("poster",1080,1620), COVER("cover",1600,900), WALLPAPER("wallpaper",1920,1200);
    companion object {
        fun parse(id:String):PhotoCanvasFormat = entries.firstOrNull{it.id==id}
            ?: throw IllegalArgumentException("Choose a supported canvas format")
    }
}

/** A new local source, never a write to an imported photo or account project. */
internal object CreatedPhotoCanvas {
    fun create(context:Context,formatId:String):Uri {
        val format=PhotoCanvasFormat.parse(formatId)
        return createSized(context,format.width,format.height)
    }

    fun createSized(context:Context,width:Int,height:Int,background:String="white"):Uri {
        CanvasDimensions.validate(width,height)
        val backgroundColor=when(background){"white"->Color.WHITE;"black"->Color.BLACK;"transparent"->Color.TRANSPARENT;else->throw IllegalArgumentException("Choose a supported background")}
        val directory=File(context.cacheDir,"created-images")
        check(directory.isDirectory||directory.mkdirs()) { "Image creation storage is unavailable" }
        val file=File.createTempFile("honorable-canvas-",".png",directory)
        var bitmap:Bitmap?=null
        try {
            val canvas=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            bitmap=canvas;canvas.eraseColor(backgroundColor)
            file.outputStream().use { check(canvas.compress(Bitmap.CompressFormat.PNG,100,it)) { "Could not create the image" } }
            return FileProvider.getUriForFile(context,context.packageName+".creation",file)
        } catch(error:OutOfMemoryError) {
            file.delete();throw IllegalStateException("Not enough memory to create an image. Try a square canvas.",error)
        } catch(error:Exception) {
            file.delete();throw error
        } finally { bitmap?.recycle() }
    }
}
