package app.honorable.editor

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PhotoEraseModelTest {
    private fun fails(action:()->Unit){try{action();fail("Expected refusal")}catch(_:IllegalArgumentException){}}
    @Test fun officialNchwRgbAndKnownMaskConventionAreExactAndDefensivelyOwned(){
        val pixels=IntArray(256){0xff123456.toInt()};pixels[0]=0;val mask=BooleanArray(256);mask[1]=true
        val input=PhotoEraseModel.prepare(pixels,16,16,mask)
        assertEquals(255,input.image[0].toInt() and 255);assertEquals(0x12,input.image[1].toInt() and 255)
        assertEquals(0x34,input.image[257].toInt() and 255);assertEquals(0x56,input.image[513].toInt() and 255)
        assertEquals(255,input.mask[0].toInt() and 255);assertEquals(0,input.mask[1].toInt() and 255)
        pixels.fill(1);mask.fill(false);assertEquals(0xff123456.toInt(),input.pixels[1]);assertTrue(input.selected[1])
    }
    @Test fun everyUnpaintedArgbPixelStaysExactEvenWhenModelChangesTheWholeImage(){
        val pixels=IntArray(256){it*1234567};val painted=BooleanArray(256){it in 100..120};val input=PhotoEraseModel.prepare(pixels,16,16,painted)
        val output=PhotoEraseModel.compose(input,ByteArray(768){200.toByte()})
        for(i in pixels.indices)assertEquals(if(painted[i])0xffc8c8c8.toInt()else pixels[i],output[i])
        assertTrue(pixels.contentEquals(input.pixels))
    }
    @Test fun missingContextBadBoundsIncompleteOutputAndCancellationRefuse(){
        fails{PhotoEraseModel.prepare(IntArray(256),16,16,BooleanArray(256))}
        fails{PhotoEraseModel.prepare(IntArray(256),16,16,BooleanArray(256){true})}
        fails{PhotoEraseModel.prepare(IntArray(256),16,16,BooleanArray(255))}
        fails{PhotoEraseModel.prepare(IntArray(256),8,32,BooleanArray(256){it==0})}
        val input=PhotoEraseModel.prepare(IntArray(256),16,16,BooleanArray(256){it==0});fails{PhotoEraseModel.compose(input,ByteArray(2))}
        val stop=PhotoEraseModel.Stop();stop.cancel();try{PhotoEraseModel.verify(File("/synthetic-missing-eraser"),stop);fail()}catch(_:java.util.concurrent.CancellationException){}
    }
}
