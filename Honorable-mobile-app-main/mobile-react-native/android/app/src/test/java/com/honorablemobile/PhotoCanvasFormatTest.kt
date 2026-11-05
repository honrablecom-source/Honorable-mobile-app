package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
class PhotoCanvasFormatTest {
 @Test fun formatsStayBoundedAndKeepExpectedOrientation(){assertEquals(12,PhotoCanvasFormat.entries.size);PhotoCanvasFormat.entries.forEach{assertTrue(it.width*it.height<=2764800);assertEquals(it,PhotoCanvasFormat.parse(it.id))};assertTrue(PhotoCanvasFormat.PORTRAIT.height>PhotoCanvasFormat.PORTRAIT.width);assertTrue(PhotoCanvasFormat.LANDSCAPE.width>PhotoCanvasFormat.LANDSCAPE.height)}
 @Test fun unknownFormatsAreRefused(){listOf("", "../file", "SQUARE").forEach{assertTrue(runCatching{PhotoCanvasFormat.parse(it)}.isFailure)}}
}
