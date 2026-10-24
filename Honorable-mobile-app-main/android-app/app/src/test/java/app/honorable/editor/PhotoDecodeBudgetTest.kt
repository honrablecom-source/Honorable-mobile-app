package app.honorable.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDecodeBudgetTest {
    @Test fun `small originals keep their resolution`() {
        assertEquals(1, PhotoDecodeBudget.sampleSize(1200, 800, 8192, 6_000_000))
    }
    @Test fun `square images are constrained by pixels as well as dimension`() {
        assertEquals(4, PhotoDecodeBudget.sampleSize(8192, 8192, 8192, 6_000_000))
    }
    @Test fun `panoramas respect the long edge cap`() {
        assertEquals(16, PhotoDecodeBudget.sampleSize(100_000, 100, 8192, 6_000_000))
    }
    @Test fun `odd sizes and extreme dimensions do not overflow`() {
        listOf(8193 to 8193, Int.MAX_VALUE to Int.MAX_VALUE, 11 to 9).forEach { (w,h) ->
            val sample = PhotoDecodeBudget.sampleSize(w, h, 8192, 6_000_000)
            val sampledW = (w.toLong() + sample - 1) / sample
            val sampledH = (h.toLong() + sample - 1) / sample
            assertTrue(sample and (sample - 1) == 0)
            assertTrue(sampledW <= 8192 && sampledH <= 8192)
            assertTrue(sampledW * sampledH <= 6_000_000)
        }
    }
    @Test(expected = IllegalArgumentException::class) fun `invalid dimensions are rejected`() {
        PhotoDecodeBudget.sampleSize(0, 100, 8192, 6_000_000)
    }
}
