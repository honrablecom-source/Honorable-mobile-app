package com.honorablemobile

import app.honorable.editor.PhotoAutoLook
import app.honorable.editor.PhotoEdit
import app.honorable.editor.PhotoLightStats
import app.honorable.editor.PhotoLook
import kotlin.math.pow

/** Clip-owned, bounded color settings. Matrix inputs are linear RGB, matching Media3. */
internal data class VideoGrade(val exposure:Float=0f,val contrast:Float=1f,val saturation:Float=1f) {
    init {
        require(exposure.isFinite() && exposure in -2f..2f)
        require(contrast.isFinite() && contrast in 0f..2f)
        require(saturation.isFinite() && saturation in 0f..2f)
    }
    val isIdentity:Boolean get()=this==VideoGrade()
    fun matrix(hdr:Boolean=false):FloatArray {
        val luma=if(hdr)floatArrayOf(.2627f,.6780f,.0593f)else floatArrayOf(.2126f,.7152f,.0722f)
        val gain=2f.pow(exposure)*contrast
        return FloatArray(16).apply {
            // Column-major RGB transform. Contrast pivots around 18% linear gray; alpha is unchanged.
            for(column in 0..2)for(row in 0..2)this[column*4+row]=gain*((if(column==row)saturation else 0f)+(1-saturation)*luma[column])
            for(row in 0..2)this[12+row]=.18f*(1-contrast)
            this[15]=1f
        }
    }
}

/** Classifier/pixel-guided proposals, not generated footage. Reuses the existing bounded photo policy. */
internal object VideoAutoGrade {
    fun sampleTimes(startMs:Long,endMs:Long):List<Long> {
        require(startMs>=0 && endMs>startMs && endMs<=Long.MAX_VALUE/1000)
        val span=endMs-startMs
        return listOf(.15,.5,.85).map{startMs+(it*span).toLong().coerceAtMost(span-1)}.distinct()
    }
    fun suggest(samples:List<PhotoLightStats>,labels:Set<String>,look:PhotoLook):VideoGrade {
        require(samples.size in 1..3 && labels.isNotEmpty())
        samples.forEach{require(it.shadows.isFinite()&&it.median.isFinite()&&it.highlights.isFinite()&&it.shadows in 0f..it.median&&it.median<=it.highlights&&it.highlights<=1f)}
        val light=PhotoLightStats(samples.map{it.shadows}.average().toFloat(),samples.map{it.median}.average().toFloat(),samples.maxOf{it.highlights})
        val suggested=PhotoAutoLook.apply(PhotoEdit(),light,labels,look)
        // Video uses the exposed linear-RGB controls; the photo curve is intentionally not transferred.
        return VideoGrade(suggested.exposure,suggested.contrast,suggested.saturation)
    }
}
