package app.honorable.editor

import kotlin.math.ln

/** Pixel statistics and label-guided looks, not generative image synthesis. */
enum class PhotoLook { AUTO, CINEMATIC, SOFT_PORTRAIT, VIVID, MONOCHROME }
data class PhotoLightStats(val shadows:Float,val median:Float,val highlights:Float) {
    companion object {
        fun measure(pixels:IntArray):PhotoLightStats {
            require(pixels.isNotEmpty() && pixels.size<=4096)
            val histogram=IntArray(256);var count=0
            for(pixel in pixels) if((pixel ushr 24)>=128) {
                val value=((.2126*((pixel ushr 16) and 255)+.7152*((pixel ushr 8) and 255)+.0722*(pixel and 255))).toInt().coerceIn(0,255)
                histogram[value]++;count++
            }
            require(count>0) { "No visible pixels to analyze" }
            fun quantile(f:Float):Float {
                val target=(count*f).toInt().coerceAtLeast(1);var sum=0
                for(i in histogram.indices){sum+=histogram[i];if(sum>=target)return i/255f}
                return 1f
            }
            return PhotoLightStats(quantile(.1f),quantile(.5f),quantile(.95f))
        }
    }
}
object PhotoAutoLook {
    fun apply(base:PhotoEdit,light:PhotoLightStats,labels:Set<String>,look:PhotoLook):PhotoEdit {
        require(listOf(light.shadows,light.median,light.highlights).all{it.isFinite() && it in 0f..1f})
        require(light.shadows<=light.median && light.median<=light.highlights)
        val normalized=labels.map{it.lowercase()}
        val person=normalized.any{it.contains("person")||it.contains("face")||it.contains("portrait")}
        val outdoor=normalized.any{it.contains("landscape")||it.contains("beach")||it.contains("nature")||it.contains("sky")}
        val night=normalized.any{it.contains("night")||it.contains("dark")}
        var boost=(ln(.45/(light.median+.02))/ln(2.0)).toFloat().coerceIn(-.35f,.55f)
        if(light.highlights>.88f)boost=minOf(boost,.08f)
        if(night)boost=minOf(boost,.25f)
        val lift=((.32f-light.shadows)/.32f).coerceIn(0f,1f)*.07f
        val curve=when(look) {
            PhotoLook.AUTO -> PhotoToneCurve(.25f+lift,.5f+lift*.4f,if(light.highlights>.88f).72f else .77f)
            PhotoLook.CINEMATIC -> PhotoToneCurve(.22f+lift*.3f,.48f,.78f)
            PhotoLook.SOFT_PORTRAIT -> PhotoToneCurve(.29f+lift*.3f,.53f,.76f)
            PhotoLook.VIVID -> PhotoToneCurve(.23f+lift*.3f,.51f,.79f)
            PhotoLook.MONOCHROME -> PhotoToneCurve(.23f+lift,.51f,.78f)
        }
        val contrast=when(look){PhotoLook.SOFT_PORTRAIT->.98f;PhotoLook.CINEMATIC->1.06f;else->if(person)1.02f else 1.05f}
        val color=when(look){PhotoLook.MONOCHROME->0f;PhotoLook.CINEMATIC->.9f;PhotoLook.VIVID->1.16f;else->if(outdoor)1.1f else 1.01f}
        return base.copy(exposure=(base.exposure+boost).coerceIn(-2f,2f),contrast=(base.contrast*contrast).coerceIn(0f,2f),saturation=(base.saturation*color).coerceIn(0f,2f),curve=curve)
    }
}
