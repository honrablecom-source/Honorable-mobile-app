package app.honorable.editor

import android.graphics.Bitmap

data class PhotoAlphaBounds(val x:Int,val y:Int,val width:Int,val height:Int)
/** Keep every nonzero-alpha pixel, including faint edges. Scan one bounded row at a time. */
object PhotoAlphaTrim {
 fun bounds(width:Int,height:Int,readRow:(Int,IntArray)->Unit):PhotoAlphaBounds? {
  require(width in 1..4096 && height in 1..4096 && width.toLong()*height<=4194304) { "Image exceeds the trimming limit" }
  var left=width;var top=height;var right=-1;var bottom=-1
  val row=IntArray(width)
  for(y in 0 until height){
   readRow(y,row)
   for(x in 0 until width)if(row[x] ushr 24 != 0){
    left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=y
   }
  }
  return if(right<0)null else PhotoAlphaBounds(left,top,right-left+1,bottom-top+1)
 }
 fun render(input:Bitmap,requireCurrent:()->Unit):Bitmap {
  val box=bounds(input.width,input.height){y,row->
   if(y%64==0)requireCurrent()
   input.getPixels(row,0,input.width,0,y,input.width,1)
  }?:error("This image is fully transparent. Add visible artwork first.")
  require(box.width<input.width || box.height<input.height) { "No fully transparent outer border was found. The current image was kept." }
  requireCurrent()
  return Bitmap.createBitmap(input,box.x,box.y,box.width,box.height)
 }
}
