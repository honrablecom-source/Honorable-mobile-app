package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
class CanvasDimensionsTest {
 @Test fun boundsAllowUsefulSizes(){listOf(64 to 64,4096 to 1024,2048 to 2048,800 to 600).forEach{CanvasDimensions.validate(it.first,it.second)}}
 @Test fun excessiveAndInvalidAllocationsFailBeforeCreation(){listOf(0 to 100,63 to 100,4097 to 64,4096 to 4096,Int.MAX_VALUE to Int.MAX_VALUE,-1 to 100).forEach{assertTrue(runCatching{CanvasDimensions.validate(it.first,it.second)}.isFailure)}}
}
