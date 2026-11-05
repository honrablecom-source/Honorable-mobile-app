package app.honorable.editor

import java.io.File
import kotlin.math.min

enum class PhotoComparisonLayout { SIDE_BY_SIDE, STACKED }
data class PhotoComparisonRect(val left:Float,val top:Float,val right:Float,val bottom:Float){
 val width:Float get()=right-left
 val height:Float get()=bottom-top
}
data class PhotoComparisonPanel(val image:PhotoComparisonRect,val labelX:Float,val labelBaseline:Float)
data class PhotoComparisonPlan(val width:Int,val height:Int,val original:PhotoComparisonPanel,val edited:PhotoComparisonPanel){
 companion object {
  const val CELL=928;const val MARGIN=24;const val LABEL=48;const val GAP=24
  fun create(originalWidth:Int,originalHeight:Int,editedWidth:Int,editedHeight:Int,layout:PhotoComparisonLayout):PhotoComparisonPlan {
   for((w,h)in listOf(originalWidth to originalHeight,editedWidth to editedHeight))require(w in 1..8192&&h in 1..8192&&w.toLong()*h<=6_000_000)
   fun panel(w:Int,h:Int,left:Int,top:Int):PhotoComparisonPanel {
    val scale=min(CELL.toDouble()/w,CELL.toDouble()/h)
    val width=(w*scale).toFloat();val height=(h*scale).toFloat()
    val x=left+(CELL-width)/2;val y=top+LABEL+(CELL-height)/2
    return PhotoComparisonPanel(PhotoComparisonRect(x,y,x+width,y+height),left.toFloat(),top+32f)
   }
   val stacked=layout==PhotoComparisonLayout.STACKED
   val secondX=if(stacked)MARGIN else MARGIN+CELL+GAP
   val secondY=if(stacked)MARGIN+LABEL+CELL+GAP else MARGIN
   return PhotoComparisonPlan(if(stacked)CELL+2*MARGIN else 2*CELL+2*MARGIN+GAP,
    if(stacked)2*(LABEL+CELL)+2*MARGIN+GAP else LABEL+CELL+2*MARGIN,
    panel(originalWidth,originalHeight,MARGIN,MARGIN),panel(editedWidth,editedHeight,secondX,secondY))
  }
 }
}

/** Private temporary PNG publication; never writes/deletes an imported source. */
object PhotoComparisonFiles {
 const val MAX_BYTES=8L*1024*1024
 fun originals(source:String,inherited:List<String>)=ImportedPhotoOriginals.validate((inherited+source).distinct())
 fun write(cache:File,requireCurrent:()->Unit,encode:(File)->Unit):File {
  requireCurrent()
  val directory=File(cache,"created-images")
  check(directory.canonicalFile==File(cache.canonicalFile,"created-images")){"Invalid image cache"}
  check(directory.isDirectory||directory.mkdirs()){"Image cache is unavailable"}
  val target=File.createTempFile("noble-comparison-",".png",directory)
  try{
   requireCurrent();encode(target);requireCurrent()
   require(target.isFile&&target.length() in 1..MAX_BYTES){"Comparison image exceeds the private image limit"}
   return target
  }catch(failure:Throwable){target.delete();throw failure}
 }
}
