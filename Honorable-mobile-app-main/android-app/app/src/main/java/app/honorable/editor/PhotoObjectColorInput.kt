package app.honorable.editor

/** RGB only. Retains the object's existing alpha; opacity remains its separate editor control. */
object PhotoObjectColorInput {
 fun parse(text:String,originalColor:Int):Int? {
  val rgb=text.trim().removePrefix("#")
  if(!rgb.matches(Regex("[A-Fa-f0-9]{6}")))return null
  return rgb.toInt(16)or(originalColor and -0x1000000)
 }
}
