package app.honorable.editor

import java.io.*
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

class PhotoColorPalette(val name:String,colors:List<Int>) {
 val colors:List<Int> = Collections.unmodifiableList(ArrayList(colors))
 init {
  require(name==name.trim() && name.isNotBlank() && name.codePointCount(0,name.length)<=40){"Use a palette name of 1–40 characters"}
  require(name.codePoints().allMatch{!Character.isISOControl(it) && it !in 0xd800..0xdfff}){"Use plain text for the palette name"}
  require(this.colors.size in 1..8 && this.colors.distinct().size==this.colors.size && this.colors.all{it ushr 24==255}){"Use 1–8 distinct opaque RGB colors"}
 }
 fun hex(index:Int)=String.format(Locale.ROOT,"#%06X",colors[index] and 0xffffff)
 fun hexText()=colors.indices.joinToString("\n"){hex(it)}
 override fun equals(other:Any?)=other is PhotoColorPalette&&name==other.name&&colors==other.colors
 override fun hashCode()=31*name.hashCode()+colors.hashCode()
}
class PhotoColorPalettes(values:List<PhotoColorPalette> = emptyList()) {
 val values:List<PhotoColorPalette> = Collections.unmodifiableList(ArrayList(values))
 init {require(this.values.size<=20 && this.values.map{it.name}.distinct().size==this.values.size){"Keep up to 20 uniquely named palettes"}}
 fun add(palette:PhotoColorPalette):PhotoColorPalettes {
  require(values.none{it.name==palette.name}){"That palette name is already saved. Choose another name."}
  return PhotoColorPalettes(values+palette)
 }
 fun remove(palette:PhotoColorPalette):PhotoColorPalettes {
  require(values.any{it==palette}){"Saved palettes changed. Reopen the shelf."}
  return PhotoColorPalettes(values.filterNot{it.name==palette.name})
 }
 override fun equals(other:Any?)=other is PhotoColorPalettes&&values==other.values
 override fun hashCode()=values.hashCode()
}
object PhotoColorPaletteCodec {
 const val MAX_BYTES=16384
 fun encode(catalog:PhotoColorPalettes):ByteArray {
  val bytes=ByteArrayOutputStream()
  DataOutputStream(bytes).use{out->out.writeInt(0x4e504c31);out.writeInt(1);out.writeInt(catalog.values.size);for(p in catalog.values){out.writeUTF(p.name);out.writeInt(p.colors.size);p.colors.forEach(out::writeInt)}}
  val payload=bytes.toByteArray();val result=payload+MessageDigest.getInstance("SHA-256").digest(payload)
  require(result.size<=MAX_BYTES){"Saved palettes exceed the storage limit"};return result
 }
 fun decode(bytes:ByteArray):PhotoColorPalettes {
  require(bytes.size in 44..MAX_BYTES){"Saved palette data is incomplete or too large"}
  val payload=bytes.copyOfRange(0,bytes.size-32)
  require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(payload),bytes.copyOfRange(bytes.size-32,bytes.size))){"Saved palette data could not be verified"}
  return DataInputStream(ByteArrayInputStream(payload)).use{input->
   require(input.readInt()==0x4e504c31&&input.readInt()==1){"Unsupported saved palette format"}
   val count=input.readInt();require(count in 0..20)
   val values=(0 until count).map{val name=input.readUTF();val size=input.readInt();require(size in 1..8);PhotoColorPalette(name,(0 until size).map{input.readInt()})}
   require(input.available()==0){"Unexpected palette data"};PhotoColorPalettes(values)
  }
 }
}
