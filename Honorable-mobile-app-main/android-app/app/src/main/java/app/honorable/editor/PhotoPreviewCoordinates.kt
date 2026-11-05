package app.honorable.editor

/** After inverse ImageView matrix and padding/bounds-origin removal, convert drawable to bitmap pixels.
 * Does not undo photo composition (frames, repeats, perspective or reflection). */
object PhotoPreviewCoordinates {
 fun bitmapPoint(width:Int,height:Int,drawableWidth:Int,drawableHeight:Int,x:Float,y:Float):PhotoPoint? {
  require(width>0&&height>0&&drawableWidth>0&&drawableHeight>0)
  if(!x.isFinite()||!y.isFinite()||x<0||y<0||x>drawableWidth||y>drawableHeight)return null
  return PhotoPoint((x.toDouble()*width/drawableWidth).toFloat(),(y.toDouble()*height/drawableHeight).toFloat())
 }
}
