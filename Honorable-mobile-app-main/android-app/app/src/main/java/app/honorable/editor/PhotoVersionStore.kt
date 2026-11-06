package app.honorable.editor

import android.content.Context
import android.util.Base64
import app.honorable.auth.SecureAccountStore
import org.json.JSONObject

internal interface PhotoVersionRecord{fun read():ByteArray?;fun write(bytes:ByteArray)}
internal object PhotoVersionRecordCodec {
    private const val MAX_TEXT=((PhotoVersionCodec.MAX_BYTES+2)/3)*4
    fun decode(record:JSONObject?):ByteArray? {
        if(record==null)return null
        require(record.length()==1&&record.has("photoVersions")){"Saved version record is incompatible"}
        val value=record.opt("photoVersions");require(value is String&&value.length in 1..MAX_TEXT)
        return Base64.decode(value,Base64.NO_WRAP).also{require(it.size in 1..PhotoVersionCodec.MAX_BYTES&&Base64.encodeToString(it,Base64.NO_WRAP)==value){"Saved version encoding is damaged"}}
    }
    fun encode(bytes:ByteArray):JSONObject{require(bytes.size in 1..PhotoVersionCodec.MAX_BYTES);return JSONObject().put("photoVersions",Base64.encodeToString(bytes,Base64.NO_WRAP))}
}
internal class EncryptedPhotoVersionRecord(context:Context,scope:PhotoVersionScope):PhotoVersionRecord {
    private val store=SecureAccountStore(context,scope.storageName)
    override fun read()=PhotoVersionRecordCodec.decode(store.readPreserving())
    override fun write(bytes:ByteArray){store.write(PhotoVersionRecordCodec.encode(bytes))}
}
internal class PhotoVersionStore(private val scope:PhotoVersionScope,private val record:PhotoVersionRecord){
    class Deletion internal constructor(internal val scope:String,internal val before:PhotoVersionCatalog,internal val after:PhotoVersionCatalog)
    fun load()=synchronized(lock){read()}
    fun save(entry:PhotoSavedVersion,requireCurrent:()->Unit)=synchronized(lock){requireCurrent();require(entry.scopeKey==scope.storageName);write(read().add(entry),requireCurrent)}
    fun requireEntry(entry:PhotoSavedVersion)=synchronized(lock){requireNotNull(read().entries.singleOrNull{it.id==entry.id&&it==entry}){"Saved versions changed. Reopen the shelf."}}
    fun delete(entry:PhotoSavedVersion,requireCurrent:()->Unit):Deletion=synchronized(lock){requireCurrent();val before=read();val after=before.remove(entry);write(after,requireCurrent);Deletion(scope.storageName,before,after)}
    fun undo(value:Deletion,requireCurrent:()->Unit)=synchronized(lock){requireCurrent();require(value.scope==scope.storageName&&read()==value.after){"Saved versions changed. Deletion Undo is unavailable."};write(value.before,requireCurrent)}
    private fun read()=record.read()?.let{PhotoVersionCodec.decode(scope,it)}?:PhotoVersionCatalog()
    private fun write(value:PhotoVersionCatalog,requireCurrent:()->Unit){val bytes=PhotoVersionCodec.encode(scope,value);requireCurrent();record.write(bytes);check(record.read()?.contentEquals(bytes)==true){"Version save could not be verified. Reopen versions before retrying."}}
    companion object{private val lock=Any()}
}
