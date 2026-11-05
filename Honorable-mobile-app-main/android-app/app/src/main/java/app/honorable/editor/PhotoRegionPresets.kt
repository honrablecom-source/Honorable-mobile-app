package app.honorable.editor

import java.io.*
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

/** Reusable local region geometry/effects only: no source photo, URI, account identity or pixels. */
data class NamedPhotoRegions(val name:String,val regions:PhotoRegions){
    init{require(name==name.trim()&&name.isNotEmpty()&&name.length<=40&&name.none{it.isISOControl()});val bytes=name.toByteArray(Charsets.UTF_8);require(bytes.size<=120&&bytes.toString(Charsets.UTF_8)==name);require(regions.values.isNotEmpty()){ "Add an effect region before saving a recipe" }}
    internal val key get()=name.lowercase(Locale.ROOT)
    /** Replace the region stack; all unrelated adjustments and objects belong to the target. */
    fun applyTo(target:PhotoEdit)=target.copy(regions=regions)
}
class PhotoRegionPresetCatalog(entries:List<NamedPhotoRegions> = emptyList()){
    val entries:List<NamedPhotoRegions> = Collections.unmodifiableList(entries.toList())
    init{require(this.entries.size<=20);require(this.entries.map{it.key}.distinct().size==this.entries.size)}
    fun add(value:NamedPhotoRegions):PhotoRegionPresetCatalog{require(entries.none{it.key==value.key}){"Choose an unused recipe name"};return PhotoRegionPresetCatalog(entries+value)}
    fun remove(name:String):PhotoRegionPresetCatalog{val key=name.lowercase(Locale.ROOT);require(entries.any{it.key==key});return PhotoRegionPresetCatalog(entries.filterNot{it.key==key})}
    override fun equals(other:Any?)=other is PhotoRegionPresetCatalog&&entries==other.entries
    override fun hashCode()=entries.hashCode()
}
object PhotoRegionPresetCodec{
    const val MAX_BYTES=32_768
    fun encode(catalog:PhotoRegionPresetCatalog):ByteArray{
        val payload=ByteArrayOutputStream().use{bytes->DataOutputStream(bytes).use{out->out.writeInt(catalog.entries.size);catalog.entries.forEach{out.writeUTF(it.name);it.regions.write(out)}};bytes.toByteArray()}
        return ByteArrayOutputStream().use{bytes->DataOutputStream(bytes).use{out->out.writeInt(0x4E525043);out.writeInt(1);out.writeInt(payload.size);out.write(MessageDigest.getInstance("SHA-256").digest(payload));out.write(payload)};bytes.toByteArray().also{require(it.size<=MAX_BYTES)}}
    }
    fun decode(bytes:ByteArray):PhotoRegionPresetCatalog{
        require(bytes.size in 48..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use{input->require(input.readInt()==0x4E525043&&input.readInt()==1);val size=input.readInt();require(size==input.available()-32);val digest=ByteArray(32);input.readFully(digest);val payload=ByteArray(size);input.readFully(payload);require(MessageDigest.isEqual(digest,MessageDigest.getInstance("SHA-256").digest(payload))){"Region recipes are damaged"}
            DataInputStream(ByteArrayInputStream(payload)).use{data->val count=data.readInt();require(count in 0..20);val values=List(count){NamedPhotoRegions(data.readUTF(),PhotoRegions.read(data))};require(data.available()==0);return PhotoRegionPresetCatalog(values)}
        }
    }
}
