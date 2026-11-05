package com.honorablemobile

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import app.honorable.editor.PhotoProjectCodec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID

internal class PhotoVideoProjectStore(private val context:Context) {
    private val root=File(context.filesDir,"photo-video-projects")
    data class Summary(val id:String,val name:String,val readable:Boolean)
    data class Staging(val id:String,val name:String,val format:PhotoVideoFormat,val directory:File)
    private fun directory(id:String):File {require(PhotoVideoProjectCodec.validId(id));return File(root,id)}
    fun sourceFile(id:String,index:Int)=File(directory(id),PhotoVideoProjectCodec.filename(index))
    fun sourceUri(id:String,index:Int):Uri=FileProvider.getUriForFile(context,context.packageName+".creation",sourceFile(id,index))
    private fun read(id:String):PhotoVideoProject {
        AtomicFile(File(directory(id),"project.bin")).openRead().use{input->
            val bytes=ByteArrayOutputStream();val buffer=ByteArray(1024)
            while(true){val remaining=PhotoVideoProjectCodec.MAX_BYTES+1-bytes.size();require(remaining>0);val count=input.read(buffer,0,minOf(remaining,buffer.size));if(count<0)break;bytes.write(buffer,0,count);require(bytes.size()<=PhotoVideoProjectCodec.MAX_BYTES)}
            val result=PhotoVideoProjectCodec.decode(bytes.toByteArray(),id);require(result.authority==context.packageName+".creation");return result
        }
    }
    fun list():List<Summary> = synchronized(lock){
        if(!root.exists())return@synchronized emptyList()
        requireNotNull(root.listFiles()){"Photo video storage is unavailable"}.filter{it.isDirectory&&PhotoVideoProjectCodec.validId(it.name)}.map{folder->runCatching{read(folder.name)}.fold({Summary(it.id,it.name,true)},{Summary(folder.name,"Unreadable photo video",false)})}.sortedBy{it.name.lowercase()}
    }
    fun begin(name:String,format:PhotoVideoFormat,requireCurrent:()->Unit):Staging=synchronized(lock){
        val id=UUID.randomUUID().toString();PhotoVideoProject(id,name,context.packageName+".creation",format,listOf(PhotoVideoAsset(1,"0".repeat(64))))
        requireCurrent();check(root.isDirectory||root.mkdirs()){"Photo video storage is unavailable"}
        val saved=list();require(saved.size<20){"Delete an older photo video before creating another. Limit: 20 projects."};require(saved.none{it.readable&&it.name.equals(name,true)}){"Choose an unused project name"}
        val folder=File(root,".pending-$id");check(!folder.exists()&&folder.mkdir()){"Photo video preparation is unavailable"};Staging(id,name,format,folder)
    }
    fun commit(staging:Staging,assets:List<PhotoVideoAsset>,requireCurrent:()->Unit):PhotoVideoProject=synchronized(lock){
        require(staging.directory.canonicalFile==File(root,".pending-${staging.id}").canonicalFile&&PhotoVideoProjectCodec.validId(staging.id))
        val project=PhotoVideoProject(staging.id,staging.name,context.packageName+".creation",staging.format,assets)
        for(index in assets.indices){val file=File(staging.directory,PhotoVideoProjectCodec.filename(index));val digest=FileInputStream(file).use{PhotoProjectCodec.fingerprint(it,requireCurrent)};require(digest==assets[index].digest){"Prepared video clip changed"};check(sourceUri(staging.id,index).toString()==project.source(index)){"Private video source mapping is unavailable"}}
        requireCurrent();val existing=list();require(existing.size<20){"Photo video project limit reached"};require(existing.none{it.readable&&it.name.equals(project.name,true)}){"A project with this name was saved during preparation"}
        val bytes=PhotoVideoProjectCodec.encode(project);val atomic=AtomicFile(File(staging.directory,"project.bin"));val output=atomic.startWrite()
        try{output.write(bytes);requireCurrent();atomic.finishWrite(output)}catch(failure:Throwable){atomic.failWrite(output);throw failure}
        check(atomic.openRead().use{PhotoVideoProjectCodec.decode(it.readBytes(),project.id)}==project)
        requireCurrent();check(!directory(project.id).exists()&&staging.directory.renameTo(directory(project.id))){"Photo video project could not be committed"};project
    }
    fun rename(id:String,expectedName:String,name:String,requireCurrent:()->Unit):PhotoVideoProject=synchronized(lock){
        requireCurrent()
        val current=read(id);require(current.name==expectedName){"Project name changed. Refresh the shelf before renaming."}
        val next=PhotoVideoProject(current.id,name.trim(),current.authority,current.format,current.assets)
        require(next.name!=current.name){"Choose a different project name"}
        require(list().none{it.id!=id&&it.readable&&it.name.equals(next.name,true)}){"Choose an unused project name"}
        val bytes=PhotoVideoProjectCodec.encode(next)
        val atomic=AtomicFile(File(directory(id),"project.bin"));requireCurrent();val output=atomic.startWrite()
        try{output.write(bytes);requireCurrent();atomic.finishWrite(output)}catch(failure:Throwable){atomic.failWrite(output);throw failure}
        check(read(id)==next){"Renamed project could not be verified"}
        next
    }
    fun abandon(staging:Staging){require(PhotoVideoProjectCodec.validId(staging.id)&&staging.directory.canonicalFile==File(root,".pending-${staging.id}").canonicalFile);staging.directory.deleteRecursively()}
    fun load(id:String,requireCurrent:()->Unit = {}):PhotoVideoProject=synchronized(lock){val project=read(id);for(index in project.assets.indices){requireCurrent();val digest=FileInputStream(sourceFile(id,index)).use{PhotoProjectCodec.fingerprint(it,requireCurrent)};require(digest==project.assets[index].digest){"A prepared photo video source changed or is missing"}};project}
    fun ownsReadableSource(uri:Uri):Boolean=runCatching{
        if(uri.scheme!="content"||uri.authority!=context.packageName+".creation")return@runCatching false
        val parts=uri.pathSegments;if(parts.size!=3||parts[0]!="photo_video_projects"||!PhotoVideoProjectCodec.validId(parts[1]))return@runCatching false
        val match=Regex("clip-([0-9]{2})\\.mp4").matchEntire(parts[2])?:return@runCatching false
        val index=match.groupValues[1].toInt();if(index !in 0..19)return@runCatching false
        val file=sourceFile(parts[1],index);uri==sourceUri(parts[1],index)&&file.isFile&&file.canRead()&&file.length()>0
    }.getOrDefault(false)
    fun delete(id:String,requireCurrent:()->Unit)=synchronized(lock){requireCurrent();val folder=directory(id);check(!folder.exists()||folder.deleteRecursively()){"Photo video could not be completely deleted"}}
    companion object{private val lock=Any()}
}
