package app.honorable.editor

import android.graphics.Bitmap

/** Uses the same source matte and geometry for both preview and saved PNG. */
object PhotoBackgroundRenderer {
    fun apply(output:Bitmap,mask:Bitmap,edit:PhotoEdit,inputWidth:Int,inputHeight:Int) {
        val settings=edit.background?:return
        val geometry=PhotoToneGeometry(inputWidth,inputHeight,edit)
        val maskPixels=IntArray(mask.width*mask.height)
        mask.getPixels(maskPixels,0,mask.width,0,0,mask.width,mask.height)
        fun alpha(x:Float,y:Float):Int = PhotoBackgroundMath.sample(maskPixels,mask.width,mask.height,geometry.sourceX(x,y)/inputWidth*mask.width,geometry.sourceY(x,y)/inputHeight*mask.height) ushr 24
        var blur:IntArray?=null;var proxyWidth=1;var proxyHeight=1
        if(settings.mode==PhotoBackgroundMode.BLUR) {
            val scale=minOf(1f,384f/maxOf(output.width,output.height))
            proxyWidth=(output.width*scale).toInt().coerceAtLeast(1);proxyHeight=(output.height*scale).toInt().coerceAtLeast(1)
            val proxy=Bitmap.createScaledBitmap(output,proxyWidth,proxyHeight,true)
            val pixels=IntArray(proxyWidth*proxyHeight)
            try {proxy.getPixels(pixels,0,proxyWidth,0,0,proxyWidth,proxyHeight)} finally {if(proxy!==output)proxy.recycle()}
            val matte=IntArray(pixels.size){i->alpha((i%proxyWidth+.5f)*output.width/proxyWidth,(i/proxyWidth+.5f)*output.height/proxyHeight)}
            blur=PhotoBackgroundMath.blur(pixels,matte,proxyWidth,proxyHeight,(settings.strength*24/100).coerceAtLeast(1))
        }
        val row=IntArray(output.width)
        for(y in 0 until output.height) {
            output.getPixels(row,0,output.width,0,y,output.width,1)
            for(x in row.indices) {
                val background=if(blur==null)settings.color else PhotoBackgroundMath.sample(blur,proxyWidth,proxyHeight,(x+.5f)*proxyWidth/output.width,(y+.5f)*proxyHeight/output.height)
                row[x]=PhotoBackgroundMath.composite(row[x],background,alpha(x+.5f,y+.5f))
            }
            output.setPixels(row,0,output.width,0,y,output.width,1)
        }
    }
}
