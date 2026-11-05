package app.honorable.editor

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException

/** Private no-backup caller directory, separated by validated guest/account namespace. */
class PhotoProjectStore(root:File,private val namespace:String,private val source:String) {
    init{require(PhotoPresetNamespace.valid(namespace))}
    private val directory=File(root,namespace)
    private val file=AtomicFile(File(directory,PhotoProjectCodec.storageName(namespace,source)))
    fun load(fingerprint:String):PhotoEdit?=synchronized(lock){
        val input=try{file.openRead()}catch(missing:FileNotFoundException){if(file.baseFile.exists()||File(file.baseFile.path+".bak").exists())throw missing;return@synchronized null}
        input.use{
            val bytes=ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true){val remaining=PhotoProjectCodec.MAX_BYTES+1-bytes.size();require(remaining>0){"Saved photo project is too large"};val count=input.read(buffer,0,minOf(buffer.size,remaining));if(count<0)break;bytes.write(buffer,0,count);require(bytes.size()<=PhotoProjectCodec.MAX_BYTES)}
            PhotoProjectCodec.decode(bytes.toByteArray(),namespace,source,fingerprint)
        }
    }
    fun save(fingerprint:String,edit:PhotoEdit,requireCurrent:()->Unit)=synchronized(lock){
        // Unreadable or changed-source data requires an explicit delete, never a silent overwrite.
        load(fingerprint)
        val bytes=PhotoProjectCodec.encode(namespace,source,fingerprint,edit)
        requireCurrent()
        check(directory.isDirectory||directory.mkdirs()){"Photo project storage is unavailable"}
        val names=requireNotNull(directory.listFiles()){ "Photo project storage cannot be read" }.mapNotNull{Regex("([0-9a-f]{64}\\.bin)(?:\\.(?:bak|new))?").matchEntire(it.name)?.groupValues?.get(1)}.toSet()
        require(file.baseFile.name in names || names.size<20){"Up to 20 photo originals can have saved edits. Reopen an older original and delete its saved project first."}
        val output=file.startWrite()
        try {output.write(bytes);requireCurrent();file.finishWrite(output);check(load(fingerprint)==edit){"Saved photo project could not be verified"}}
        catch(failure:Throwable){file.failWrite(output);throw failure}
    }
    fun delete(requireCurrent:()->Unit)=synchronized(lock){requireCurrent();file.delete();check(!file.baseFile.exists()&&!File(file.baseFile.path+".bak").exists()&&!File(file.baseFile.path+".new").exists()){"Saved photo project could not be deleted"}}
    companion object{private val lock=Any()}
}
