package app.honorable.editor

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PhotoAlphaTrimTest {
 private fun box(width:Int,height:Int,pixels:IntArray)=PhotoAlphaTrim.bounds(width,height){y,row->pixels.copyInto(row,0,y*width,(y+1)*width)}
 @Test fun findsAsymmetricVisibleBoundsWithoutCenterCropping(){
  val pixels=IntArray(8*6);pixels[2*8+1]=0xff112233.toInt();pixels[4*8+5]=0xff445566.toInt()
  assertEquals(PhotoAlphaBounds(1,2,5,3),box(8,6,pixels))
 }
 @Test fun keepsEvenOneLevelOfAlphaAndSinglePixelArtwork(){
  val pixels=IntArray(7*5){0x00ffffff};pixels[3*7+6]=0x01000000
  assertEquals(PhotoAlphaBounds(6,3,1,1),box(7,5,pixels))
 }
 @Test fun transparentHiddenRgbDoesNotCreateVisibleBounds(){assertNull(box(3,2,IntArray(6){0x00ffffff}))}
 @Test fun opaqueAndEdgeTouchingImagesKeepTheirFullBounds(){
  assertEquals(PhotoAlphaBounds(0,0,3,2),box(3,2,IntArray(6){-1}))
  val pixels=IntArray(15);pixels[0]=-1;pixels[14]=0x02000000
  assertEquals(PhotoAlphaBounds(0,0,5,3),box(5,3,pixels))
 }
 @Test fun scansEachRowOnceUsingOneReusableBuffer(){
  val rows=mutableListOf<Int>();var buffer:IntArray?=null
  val result=PhotoAlphaTrim.bounds(4,3){y,row->rows+=y;if(buffer==null)buffer=row else assertSame(buffer,row);row.fill(0);if(y==1)row[2]=-1}
  assertEquals(listOf(0,1,2),rows);assertEquals(PhotoAlphaBounds(2,1,1,1),result)
 }
 @Test fun invalidAllocationRefusesBeforePixelRead(){
  for((width,height)in listOf(0 to 1,-1 to 2,4097 to 1,4096 to 4096,Int.MAX_VALUE to Int.MAX_VALUE)){
   var reads=0
   try{PhotoAlphaTrim.bounds(width,height){_,_->reads++};fail("Expected invalid size")}catch(_:IllegalArgumentException){}
   assertEquals(0,reads)
  }
 }
 @Test fun readerCancellationStopsTheScan(){
  var reads=0
  try{PhotoAlphaTrim.bounds(4,8){y,row->reads++;if(y==3)error("Synthetic cancellation");row.fill(-1)};fail("Expected cancellation")}catch(e:IllegalStateException){assertEquals("Synthetic cancellation",e.message)}
  assertEquals(4,reads)
 }
 @Test fun randomBoundsContainEveryVisiblePixelAndHaveNoEmptyOuterEdge(){
  val random=Random(71)
  repeat(200){
   val w=random.nextInt(1,25);val h=random.nextInt(1,25);val pixels=IntArray(w*h){if(random.nextInt(5)==0)random.nextInt(1,256) shl 24 else 0x00ffffff}
   val result=box(w,h,pixels)
   val visible=pixels.indices.filter{pixels[it] ushr 24 !=0}
   if(visible.isEmpty())assertNull(result)else{
    val b=requireNotNull(result)
    assertTrue(visible.all{it%w in b.x until b.x+b.width && it/w in b.y until b.y+b.height})
    assertTrue(visible.any{it%w==b.x});assertTrue(visible.any{it%w==b.x+b.width-1})
    assertTrue(visible.any{it/w==b.y});assertTrue(visible.any{it/w==b.y+b.height-1})
   }
  }
 }
 @Test fun pausedActivityRejectsTrimBeforeAccessingAbsentUi(){
  val type=Class.forName("sun.misc.Unsafe")
  val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
  fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
  val activity=uncreatedPhotoActivity() as PhotoEditorActivity
  PhotoEditorActivity::class.java.getDeclaredField("accessMode").apply{isAccessible=true}.set(activity,EditorAccessMode.GUEST_BASIC)
  PhotoEditorActivity::class.java.getDeclaredField("bitmap").apply{isAccessible=true}.set(activity,allocate(android.graphics.Bitmap::class.java))
  assertEquals(true,PhotoEditorActivity::class.java.getDeclaredMethod("canFinish").apply{isAccessible=true}.invoke(activity))
  PhotoEditorActivity::class.java.getDeclaredMethod("createTrimmedCopy").apply{isAccessible=true}.invoke(activity)
 }
}
