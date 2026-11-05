package app.honorable.editor

import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/** Root is the app-private files/canvas-projects directory, excluded from backup by the app. */
class CanvasProjectStore(private val root:File) {
    data class Summary(val id:String,val name:String,val readable:Boolean)
    private fun directory(id:String):File {require(CanvasProjectCodec.validId(id));return File(root,id)}
    fun sourceFile(id:String)=File(directory(id),"source.png")
    private fun metadata(id:String)=AtomicFile(File(directory(id),"project.bin"))
    private fun read(id:String):CanvasProject {
        metadata(id).openRead().use{input->
            val bytes=ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true){val remaining=CanvasProjectCodec.MAX_BYTES+1-bytes.size();require(remaining>0){"Canvas project is too large"};val count=input.read(buffer,0,minOf(buffer.size,remaining));if(count<0)break;bytes.write(buffer,0,count);require(bytes.size()<=CanvasProjectCodec.MAX_BYTES)}
            return CanvasProjectCodec.decode(bytes.toByteArray(),id)
        }
    }
    fun list():List<Summary> = synchronized(lock){
        if(!root.exists())return@synchronized emptyList()
        requireNotNull(root.listFiles()){"Canvas project storage cannot be read"}.filter{it.isDirectory&&CanvasProjectCodec.validId(it.name)}.map{folder->
            runCatching{read(folder.name)}.fold(onSuccess={Summary(it.id,it.name,true)},onFailure={Summary(folder.name,"Unreadable image project",false)})
        }.sortedBy{it.name.lowercase()}
    }
    fun load(id:String,requireCurrent:()->Unit = {}):CanvasProject = synchronized(lock){
        requireCurrent();val project=read(id)
        val digest=FileInputStream(sourceFile(id)).use{PhotoProjectCodec.fingerprint(it,requireCurrent)}
        require(digest==project.sourceDigest){"The saved project source changed or is damaged"}
        requireCurrent();project
    }
    private fun write(file:AtomicFile,project:CanvasProject,requireCurrent:()->Unit){
        val bytes=CanvasProjectCodec.encode(project);val output=file.startWrite()
        try{output.write(bytes);requireCurrent();file.finishWrite(output)}catch(failure:Throwable){file.failWrite(output);throw failure}
        check(file.openRead().use{input->CanvasProjectCodec.decode(input.readBytes(),project.id)}==project){"Canvas project could not be verified after saving"}
    }
    fun create(name:String,input:InputStream,edit:PhotoEdit,requireCurrent:()->Unit):CanvasProject = create(name,input,edit,emptyList(),requireCurrent)
    fun create(name:String,input:InputStream,edit:PhotoEdit,protectedOriginals:List<String>,requireCurrent:()->Unit):CanvasProject = synchronized(lock){
        val originals=ImportedPhotoOriginals.validate(protectedOriginals).toList()
        val id=UUID.randomUUID().toString()
        CanvasProject(id,name,"0".repeat(64),edit,originals) // Validate labels/state before copying a source.
        requireCurrent();check(root.isDirectory||root.mkdirs()){"Canvas project storage is unavailable"}
        val existing=list();require(existing.size<20){"Up to 20 image projects can be saved. Delete an older project first."}
        require(existing.none{it.readable&&it.name.equals(name,true)}){"A project with that name already exists"}
        val staging=File(root,".pending-$id");check(!staging.exists()&&staging.mkdir()){"Canvas project staging is unavailable"}
        try {
            val image=File(staging,"source.png")
            val digest=FileOutputStream(image).use{output->
                val tee=object:InputStream(){override fun read():Int{val value=input.read();if(value>=0)output.write(value);return value};override fun read(bytes:ByteArray,offset:Int,length:Int):Int{val count=input.read(bytes,offset,length);if(count>0)output.write(bytes,offset,count);return count}}
                PhotoProjectCodec.fingerprint(tee,requireCurrent).also{output.fd.sync()}
            }
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(image.path,bounds)
            require(bounds.outMimeType=="image/png"&&bounds.outWidth in 1..4096&&bounds.outHeight in 1..4096&&bounds.outWidth.toLong()*bounds.outHeight<=4_194_304){"Use a Noble-created PNG within the canvas pixel budget"}
            val project=CanvasProject(id,name,digest,edit,originals)
            write(AtomicFile(File(staging,"project.bin")),project,requireCurrent)
            requireCurrent();check(!directory(id).exists()&&staging.renameTo(directory(id))){"Canvas project could not be committed"}
            project
        } finally {if(staging.exists())staging.deleteRecursively()}
    }
    fun update(id:String,name:String,edit:PhotoEdit,requireCurrent:()->Unit):CanvasProject=update(id,name,edit,emptyList(),requireCurrent)
    fun update(id:String,name:String,edit:PhotoEdit,protectedOriginals:List<String>,requireCurrent:()->Unit):CanvasProject=synchronized(lock){
        val supplied=ImportedPhotoOriginals.validate(protectedOriginals)
        val original=load(id,requireCurrent)
        val originals=ImportedPhotoOriginals.validate((original.protectedOriginals+supplied).distinct())
        require(list().none{it.id!=id&&it.readable&&it.name.equals(name,true)}){"A project with that name already exists"}
        val next=original.copy(name=name,edit=edit,protectedOriginals=originals);write(metadata(id),next,requireCurrent);next
    }
    fun delete(id:String,requireCurrent:()->Unit)=synchronized(lock){
        requireCurrent();val folder=directory(id)
        check(!folder.exists()||folder.deleteRecursively()){"Canvas project could not be completely deleted"}
    }
    companion object{private val lock=Any()}
}
