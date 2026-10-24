package app.honorable.editor

/** Bounds all decoded rasters, including very large panoramas. No Android dependencies. */
object PhotoDecodeBudget {
    fun sampleSize(width: Int, height: Int, maxDimension: Int, maxPixels: Long): Int {
        require(width > 0 && height > 0 && maxDimension > 0 && maxPixels > 0)
        var sample = 1
        while (true) {
            // Round up: BitmapFactory can return an extra pixel for odd dimensions.
            val w = (width.toLong() + sample - 1) / sample
            val h = (height.toLong() + sample - 1) / sample
            if (w <= maxDimension && h <= maxDimension && w * h <= maxPixels) return sample
            check(sample <= Int.MAX_VALUE / 2) { "Image dimensions exceed decoder limits" }
            sample *= 2
        }
    }
}
