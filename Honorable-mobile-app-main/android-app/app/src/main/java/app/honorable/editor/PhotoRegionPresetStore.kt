package app.honorable.editor

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException

/** Separate no-backup guest/account namespaces; failed reads never become empty writable shelves. */
class PhotoRegionPresetStore(directory:File,namespace:String){
    init{require(PhotoPresetNamespace.valid(namespace))}
    private val file=AtomicFile(File(directory,"$namespace.bin"))
    fun load():PhotoRegionPresetCatalog=synchronized(lock){read()}
    fun add(value:NamedPhotoRegions,requireCurrent:()->Unit)=synchronized(lock){write(read().add(value),requireCurrent)}
    fun remove(name:String,requireCurrent:()->Unit)=synchronized(lock){write(read().remove(name),requireCurrent)}
    private fun read():PhotoRegionPresetCatalog{
        val input=try{file.openRead()}catch(missing:FileNotFoundException){if(file.baseFile.exists()||File(file.baseFile.path+".bak").exists())throw missing;return PhotoRegionPresetCatalog()}
        input.use{val bytes=ByteArrayOutputStream();val buffer=ByteArray(1024);while(true){val remaining=PhotoRegionPresetCodec.MAX_BYTES+1-bytes.size();require(remaining>0);val count=it.read(buffer,0,minOf(remaining,buffer.size));if(count<0)break;bytes.write(buffer,0,count);require(bytes.size()<=PhotoRegionPresetCodec.MAX_BYTES)};return PhotoRegionPresetCodec.decode(bytes.toByteArray())}
    }
    private fun write(catalog:PhotoRegionPresetCatalog,requireCurrent:()->Unit){
        val bytes=PhotoRegionPresetCodec.encode(catalog);requireCurrent();val folder=requireNotNull(file.baseFile.parentFile);check(folder.isDirectory||folder.mkdirs()){"Region recipe storage unavailable"}
        val output=file.startWrite();try{output.write(bytes);requireCurrent();file.finishWrite(output);check(read()==catalog){"Saved recipe could not be verified"}}catch(failure:Throwable){file.failWrite(output);throw failure}
    }
    companion object{private val lock=Any()}
}
