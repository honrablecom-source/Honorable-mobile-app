package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.io.FileOutputStream

/** One owned temporary decoded frame at a time; no source or project edits. */
internal object VideoStoryboardSheet {
    fun create(context:Context,plan:VideoStoryboardPlan.Plan,sources:Map<String,Uri>,durations:Map<String,Long>,requireCurrent:()->Unit,progress:(Int,Int)->Unit):File {
        require(plan.shots.size in 1..VideoStoryboardPlan.MAX_SHOTS&&plan.width in 1..1920&&plan.height in 1..1200)
        requireCurrent()
        val sheet=Bitmap.createBitmap(plan.width,plan.height,Bitmap.Config.ARGB_8888)
        try{
            val canvas=Canvas(sheet);canvas.drawColor(Color.WHITE)
            val paint=TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply{color=Color.BLACK;textSize=22f}
            canvas.drawText("Video storyboard · ${plan.shots.size} of ${plan.totalClips} clips",16f,30f,paint)
            paint.textSize=14f
            canvas.drawText(if(plan.representative)"Representative clips including first and last · original source frames"else "Every timeline clip · original source frames",16f,55f,paint)
            plan.shots.forEachIndexed{index,shot->
                requireCurrent();progress(index+1,plan.shots.size)
                val sourceId=shot.request.clip.sourceId
                val file=VideoFramePhoto.extract(context,requireNotNull(sources[sourceId]),requireNotNull(durations[sourceId]),shot.request,requireCurrent)
                var frame:Bitmap?=null
                try{
                    requireCurrent()
                    val decoded=requireNotNull(BitmapFactory.decodeFile(file.absolutePath)){"The prepared frame could not be read"};frame=decoded
                    val cell=plan.cell(index);val x=cell.first.toFloat();val y=cell.second.toFloat()
                    val state=canvas.save();canvas.clipRect(x,y,x+VideoStoryboardPlan.CELL_WIDTH,y+VideoStoryboardPlan.CELL_HEIGHT)
                    try{
                        val scale=minOf(464f/decoded.width,252f/decoded.height)
                        val width=decoded.width*scale;val height=decoded.height*scale
                        val destination=RectF(x+8+(464-width)/2,y+8+(252-height)/2,x+8+(464+width)/2,y+8+(252+height)/2)
                        canvas.drawBitmap(decoded,null,destination,paint)
                        paint.color=Color.BLACK;paint.textSize=18f
                        val title="Clip ${shot.clipIndex+1}"+(if(shot.title.isBlank())""else " · "+shot.title)
                        canvas.drawText(TextUtils.ellipsize(title,paint,460f,TextUtils.TruncateAt.END).toString(),x+10,y+286,paint)
                        paint.textSize=14f
                        canvas.drawText("Source ${VideoCaptions.formatTime(shot.request.sourceMs)} · nearest frame",x+10,y+310,paint)
                        canvas.drawText("Playback ${VideoCaptions.formatTime(shot.outputUs/1000)}",x+10,y+334,paint)
                        paint.color=Color.LTGRAY;paint.style=Paint.Style.STROKE;paint.strokeWidth=1f
                        canvas.drawRect(x+1,y+1,x+479,y+359,paint);paint.style=Paint.Style.FILL;paint.color=Color.BLACK
                    }finally{canvas.restoreToCount(state)}
                    requireCurrent()
                }finally{frame?.recycle();file.delete()}
            }
            paint.textSize=12f
            canvas.drawText(TextUtils.ellipsize("Nearest original frames; video effects excluded. Local photo copy for review and annotation.",paint,(plan.width-24).toFloat(),TextUtils.TruncateAt.END).toString(),12f,plan.height-17f,paint)
            return VideoFramePhotoFiles.write(context.cacheDir,requireCurrent){file->
                FileOutputStream(file).use{stream->check(sheet.compress(Bitmap.CompressFormat.PNG,100,stream)){"Storyboard image could not be encoded"};stream.fd.sync()}
            }
        }finally{sheet.recycle()}
    }
}
