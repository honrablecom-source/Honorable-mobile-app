package app.honorable.editor

import java.util.Locale

/** Existing effects use opaque RGB endpoints. Accept the HEX form copied by the local palette. */
object PhotoEffectColors{
 fun parse(value:String):Int{
  val text=value.trim().removePrefix("#")
  require(text.matches(Regex("[0-9a-fA-F]{6}"))){"Use six HEX digits, with or without #"}
  return (0xff000000L or text.toLong(16)).toInt()
 }
 fun format(color:Int)=String.format(Locale.ROOT,"#%06X",color and 0xffffff)
}
