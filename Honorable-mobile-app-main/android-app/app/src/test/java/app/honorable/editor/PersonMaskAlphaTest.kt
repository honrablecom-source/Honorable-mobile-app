package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PersonMaskAlphaTest {
    @Test fun confidenceKeepsSoftEdgesAndTransparentBackground() {
        val pixels = PersonMaskAlpha.pixels(4, 1, floatArrayOf(0f, .5f, 1f, .25f))
        assertArrayEquals(intArrayOf(0x00ffffff, 0x80ffffff.toInt(), -1, 0x40ffffff), pixels)
    }
    @Test fun finiteOutsideRangeValuesAreClamped() {
        assertArrayEquals(intArrayOf(0x00ffffff, -1), PersonMaskAlpha.pixels(2,1,floatArrayOf(-.01f,1.01f)))
    }
    @Test fun missingOrAmbiguousPeopleDoNotProduceAFakeCutout() {
        for (values in listOf(FloatArray(4), FloatArray(4){1f}, FloatArray(4){.5f})) {
            try { PersonMaskAlpha.pixels(2,2,values); fail("Unclear matte accepted") }
            catch (_: PersonMaskAlpha.NoClearPerson) { }
        }
    }
    @Test fun malformedDimensionsAndNonFiniteConfidenceAreRejected() {
        for ((w,h,v) in listOf(Triple(0,1,floatArrayOf()),Triple(Int.MAX_VALUE,2,floatArrayOf()),
            Triple(2,2,floatArrayOf(0f,1f)),Triple(2,1,floatArrayOf(Float.NaN,1f)),
            Triple(2,1,floatArrayOf(0f,Float.POSITIVE_INFINITY)))) {
            try { PersonMaskAlpha.pixels(w,h,v); fail("Invalid mask accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun conversionDoesNotMutateModelConfidence() {
        val original = floatArrayOf(0f,.2f,.8f,1f)
        val before = original.copyOf()
        PersonMaskAlpha.pixels(2,2,original)
        assertArrayEquals(before, original, 0f)
    }
}
