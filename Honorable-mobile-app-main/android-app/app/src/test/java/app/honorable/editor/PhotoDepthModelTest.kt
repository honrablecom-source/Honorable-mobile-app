package app.honorable.editor

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PhotoDepthModelTest {
    private fun fails(action:()->Unit){try{action();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    @Test fun mobilePreprocessingKeepsAspectPatchBoundsRgbOrderAndWhiteAlphaMatte(){
        val pixels=intArrayOf(0xffff0000.toInt(),0xff00ff00.toInt(),0xff0000ff.toInt(),0x00000000)
        val before=pixels.copyOf();val input=PhotoDepthModel.prepare(pixels,2,2)
        assertEquals(518,input.width);assertEquals(518,input.height);assertArrayEquals(before,pixels)
        val plane=input.width*input.height
        assertEquals(((1-.485)/.229).toFloat(),input.values[0],.00001f)
        assertEquals(((0-.456)/.224).toFloat(),input.values[plane],.00001f)
        assertEquals(((0-.406)/.225).toFloat(),input.values[2*plane],.00001f)
        for(c in 0..2){val mean=doubleArrayOf(.485,.456,.406)[c];val std=doubleArrayOf(.229,.224,.225)[c];assertEquals(((1-mean)/std).toFloat(),input.values[(c+1)*plane-1],.00001f)}
        val wide=PhotoDepthModel.prepare(IntArray(400*100){-1},400,100);assertEquals(518,wide.width);assertEquals(126,wide.height)
        assertEquals(0,wide.width%14);assertEquals(0,wide.height%14)
    }
    @Test fun invalidSourceAndStickyStopRefuseBeforeNativeModelUse(){
        fails{PhotoDepthModel.prepare(IntArray(196),14,13)};fails{PhotoDepthModel.prepare(IntArray(1000),100,10)}
        val stop=PhotoDepthModel.Stop();stop.cancel()
        try{PhotoDepthModel.verify(File("/nonexistent-synthetic-depth-model"),stop);fail()}catch(_:java.util.concurrent.CancellationException){}
        fails{PhotoDepthModel.verify(File("/nonexistent-synthetic-depth-model"),PhotoDepthModel.Stop())}
    }
}
