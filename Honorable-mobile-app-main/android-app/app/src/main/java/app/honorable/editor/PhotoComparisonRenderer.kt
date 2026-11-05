package app.honorable.editor

import android.graphics.*

/** New flattened proof raster. Both inputs remain untouched; transparent areas use white. */
object PhotoComparisonRenderer {
 fun render(original:Bitmap,edited:Bitmap,layout:PhotoComparisonLayout):Bitmap {
  val plan=PhotoComparisonPlan.create(original.width,original.height,edited.width,edited.height,layout)
  val output=Bitmap.createBitmap(plan.width,plan.height,Bitmap.Config.ARGB_8888)
  try{
   val canvas=Canvas(output);canvas.drawColor(Color.WHITE)
   val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
   fun panel(input:Bitmap,panel:PhotoComparisonPanel,label:String){
    val rect=panel.image
    canvas.drawBitmap(input,null,RectF(rect.left,rect.top,rect.right,rect.bottom),paint)
    val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=26f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}
    canvas.drawText(label,panel.labelX,panel.labelBaseline,text)
   }
   panel(original,plan.original,"Original");panel(edited,plan.edited,"Edited")
   return output
  }catch(failure:Throwable){output.recycle();throw failure}
 }
}
