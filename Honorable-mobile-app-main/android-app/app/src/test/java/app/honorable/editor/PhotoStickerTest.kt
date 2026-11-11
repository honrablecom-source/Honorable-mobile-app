package app.honorable.editor

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PhotoStickerTest {
 private val white=-1;private val black=0xff000000.toInt()
 private fun point(pixel:Int=0xff224466.toInt())=intArrayOf(0,0,0,0,pixel,0,0,0,0)
 private fun failWith(action:()->Unit):String=try{action();fail("Expected refusal");""}catch(e:IllegalArgumentException){e.message?:""}catch(e:IllegalStateException){e.message?:""}
 @Test fun createsPaddedStickerAroundOneVisiblePixel(){
  val result=PhotoSticker.pixels(point(),3,3,2,white)
  assertEquals(9,result.width);assertEquals(9,result.height)
  assertEquals(0xff224466.toInt(),result.pixels[4*9+4])
  assertEquals(-1,result.pixels[4*9+5])
  assertEquals(128,result.pixels[4*9+6] ushr 24)
  assertEquals(0,result.pixels[4*9+7])
 }
 @Test fun preservesEveryOriginalVisiblePixelIncludingFaintAlpha(){
  val input=intArrayOf(0,0x01123456,0,0x80445566.toInt(),0xff102030.toInt(),0)
  val before=input.copyOf();val result=PhotoSticker.pixels(input,3,2,3,white)
  val pad=5
  for(y in 0..1)for(x in 0..2)if(input[y*3+x] ushr 24!=0)assertEquals(input[y*3+x],result.pixels[(y+pad)*result.width+x+pad])
  assertArrayEquals(before,input)
 }
 @Test fun discardsHiddenRgbAndTrimsOnlyEmptyOuterSpace(){
  val input=IntArray(7*5){0x00abcdef};input[3*7+5]=0xff123456.toInt()
  val result=PhotoSticker.pixels(input,7,5,2,white)
  assertEquals(9,result.width);assertEquals(9,result.height)
  assertEquals(0,result.pixels[0]);assertEquals(0xff123456.toInt(),result.pixels[4*9+4])
 }
 @Test fun roundedBorderUsesEuclideanDistanceRatherThanSquareCorners(){
  val r=PhotoSticker.pixels(point(),3,3,2,white);val c=4
  assertEquals(0,r.pixels[(c+2)*r.width+c+2])
  assertEquals(67,r.pixels[(c+1)*r.width+c+2] ushr 24)
  assertEquals(255,r.pixels[(c+1)*r.width+c+1] ushr 24)
 }
 @Test fun borderColorDoesNotRecolorTheArtwork(){
  val r=PhotoSticker.pixels(point(),3,3,2,black)
  assertEquals(black,r.pixels[4*r.width+5]);assertEquals(0xff224466.toInt(),r.pixels[4*r.width+4])
 }
 @Test fun refusesInvalidSizesBeforeReadingOrAllocatingOutput(){
  for((w,h)in listOf(0 to 2,4097 to 1,4096 to 4096,Int.MAX_VALUE to 1)){
   var checks=0;failWith{PhotoSticker.pixels(IntArray(0),w,h,2,white){checks++}};assertEquals(0,checks)
  }
  failWith{PhotoSticker.pixels(IntArray(3),2,2,2,white)}
 }
 @Test fun validatesRadiusAndOpaqueBorderColor(){
  for(radius in listOf(-1,0,1,33,Int.MAX_VALUE))failWith{PhotoSticker.pixels(point(),3,3,radius,white)}
  failWith{PhotoSticker.pixels(point(),3,3,2,0x80ffffff.toInt())}
 }
 @Test fun explainsEmptyAndOpaqueInputsWithoutChangingThem(){
  assertTrue(failWith{PhotoSticker.pixels(IntArray(9),3,3,2,white)}.contains("visible artwork"))
  val opaque=IntArray(9){-1};val copy=opaque.copyOf()
  assertTrue(failWith{PhotoSticker.pixels(opaque,3,3,2,white)}.contains("transparent PNG"));assertArrayEquals(copy,opaque)
 }
 @Test fun refusesOutputThatWouldExceedThePaddedDimensionLimit(){
  val input=IntArray(4096){-1};input[2]=0
  assertTrue(failWith{PhotoSticker.pixels(input,4096,1,32,white)}.contains("too large"))
 }
 @Test fun cancellationDuringDistancePassKeepsSourceUntouched(){
  val input=point();val copy=input.copyOf();var checks=0
  assertEquals("Cancelled",failWith{PhotoSticker.pixels(input,3,3,2,white){checks++;if(checks==4)error("Cancelled")}})
  assertArrayEquals(copy,input);assertEquals(4,checks)
 }
 @Test fun matchesIndependentBruteForceDistancesForRandomMasks(){
  val random=Random(73)
  repeat(60){
   val w=random.nextInt(2,8);val h=random.nextInt(2,8);val input=IntArray(w*h){if(random.nextBoolean())random.nextInt(1,256) shl 24 or 0x123456 else 0}
   input[0]=0;input[input.lastIndex]=0xffaabbcc.toInt()
   val visible=input.indices.filter{input[it] ushr 24!=0}
   val left=visible.minOf{it%w};val top=visible.minOf{it/w};val right=visible.maxOf{it%w};val bottom=visible.maxOf{it/w}
   val radius=random.nextInt(2,7);val pad=radius+2;val result=PhotoSticker.pixels(input,w,h,radius,white)
   assertEquals(right-left+1+pad*2,result.width);assertEquals(bottom-top+1+pad*2,result.height)
   for(y in 0 until result.height)for(x in 0 until result.width){
    val sx=x-pad+left;val sy=y-pad+top
    val original=if(sx in 0 until w && sy in 0 until h)input[sy*w+sx] else 0
    val expected=if(original ushr 24!=0)original else{
     val distance=visible.minOf{val dx=x-(it%w-left+pad);val dy=y-(it/w-top+pad);dx*dx+dy*dy}
     val alpha=((radius+.5-sqrt(distance.toDouble())).coerceIn(0.0,1.0)*255).roundToInt()
     if(alpha==0)0 else (alpha shl 24) or 0x00ffffff
    }
    assertEquals("mask $it at $x,$y",expected,result.pixels[y*result.width+x])
   }
  }
 }
 @Test fun pausedActivityRefusesStickerDialogBeforeAccessingUi(){
  val type=Class.forName("sun.misc.Unsafe");val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
  fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
  val activity=uncreatedPhotoActivity() as PhotoEditorActivity
  PhotoEditorActivity::class.java.getDeclaredField("accessMode").apply{isAccessible=true}.set(activity,EditorAccessMode.GUEST_BASIC)
  PhotoEditorActivity::class.java.getDeclaredField("bitmap").apply{isAccessible=true}.set(activity,allocate(android.graphics.Bitmap::class.java))
  assertEquals(true,PhotoEditorActivity::class.java.getDeclaredMethod("canFinish").apply{isAccessible=true}.invoke(activity))
  PhotoEditorActivity::class.java.getDeclaredMethod("createStickerCopy").apply{isAccessible=true}.invoke(activity)
 }
}
