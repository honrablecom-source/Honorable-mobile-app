package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one immutable image until the OS print session ends. All PDF work stays off the UI thread. */
internal class DrawingPrintAdapter(context:Context,private val image:Bitmap,private val done:(String?)->Unit):PrintDocumentAdapter() {
    private val context=context.applicationContext
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val closed=AtomicBoolean(false)
    @Volatile private var attributes:PrintAttributes?=null
    @Volatile private var layoutVersion=0L
    override fun onLayout(oldAttributes:PrintAttributes?,newAttributes:PrintAttributes,cancellationSignal:CancellationSignal?,callback:LayoutResultCallback,extras:Bundle?) {
        if(closed.get()||cancellationSignal?.isCanceled==true){callback.onLayoutCancelled();return}
        layoutVersion++;attributes=newAttributes
        callback.onLayoutFinished(PrintDocumentInfo.Builder("Noble artwork.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO).setPageCount(1).build(),oldAttributes!=newAttributes)
    }
    override fun onWrite(pages:Array<out PageRange>,destination:ParcelFileDescriptor,cancellationSignal:CancellationSignal?,callback:WriteResultCallback) {
        val layout=attributes;val version=layoutVersion
        fun cancelled()=version!=layoutVersion||closed.get()||cancellationSignal?.isCanceled==true||Thread.currentThread().isInterrupted
        if(cancelled()){runCatching{destination.close()};callback.onWriteCancelled();return}
        if(layout==null){runCatching{destination.close()};callback.onWriteFailed("Choose page settings first.");return}
        if(pages.none{it.start==0}){runCatching{destination.close()};callback.onWriteFinished(emptyArray());return}
        try{worker.execute{
            val result=runCatching{
                if(cancelled())throw DrawingPrintCancelled()
                val document=PrintedPdfDocument(context,layout)
                var page:android.graphics.pdf.PdfDocument.Page?=null
                var monochrome:Bitmap?=null
                try{
                    require(document.pageWidth in 1..14400&&document.pageHeight in 1..14400){"Choose a smaller page size"}
                    page=document.startPage(0)
                    val bounds=document.pageContentRect
                    // PdfDocument records a content-sized canvas; the framework applies page margins on output.
                    val fit=DrawingPrintPolicy.fit(image.width,image.height,0,0,bounds.width(),bounds.height())
                    page.canvas.drawColor(Color.WHITE)
                    if(cancelled())throw DrawingPrintCancelled()
                    // PDF canvases do not support Paint color filters. Materialize an optional gray copy first.
                    val printable=if(layout.colorMode==PrintAttributes.COLOR_MODE_MONOCHROME){
                        Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888).also{copy->
                            monochrome=copy
                            Canvas(copy).apply{drawColor(Color.WHITE);drawBitmap(image,0f,0f,Paint(Paint.FILTER_BITMAP_FLAG).apply{colorFilter=ColorMatrixColorFilter(ColorMatrix().apply{setSaturation(0f)})})}
                        }
                    }else image
                    if(cancelled())throw DrawingPrintCancelled()
                    page.canvas.drawBitmap(printable,null,RectF(fit.left,fit.top,fit.right,fit.bottom),Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
                    document.finishPage(page);page=null
                    if(cancelled())throw DrawingPrintCancelled()
                    FileOutputStream(destination.fileDescriptor).use{out->document.writeTo(DrawingPrintOutput(out,{!cancelled()}));out.flush()}
                    if(cancelled())throw DrawingPrintCancelled()
                }finally{
                    page?.let{runCatching{document.finishPage(it)}}
                    runCatching{document.close()};monochrome?.recycle();runCatching{destination.close()}
                }
            }
            runCatching{destination.close()}
            main.post{if(version!=layoutVersion||closed.get()||cancellationSignal?.isCanceled==true){callback.onWriteCancelled();return@post};result.onSuccess{callback.onWriteFinished(arrayOf(PageRange(0,0)))}.onFailure{
                if(it is DrawingPrintCancelled||cancelled())callback.onWriteCancelled()
                else callback.onWriteFailed(if(it is OutOfMemoryError)"Not enough memory. Try smaller artwork."else "Artwork could not be printed. Try again with a smaller image or page.")
            }}
        }}catch(_:java.util.concurrent.RejectedExecutionException){runCatching{destination.close()};callback.onWriteCancelled()}
    }
    override fun onFinish(){close(null)}
    fun close(error:String?){
        if(!closed.compareAndSet(false,true))return
        // Queue cleanup after any active writer, so it cannot race a recycled bitmap.
        worker.execute{try{if(!image.isRecycled)image.recycle()}finally{worker.shutdown();main.post{done(error)}}}
    }
}
