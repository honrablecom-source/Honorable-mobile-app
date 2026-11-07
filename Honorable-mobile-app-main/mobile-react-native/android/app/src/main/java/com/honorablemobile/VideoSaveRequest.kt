package com.honorablemobile

import android.net.Uri
import app.honorable.editor.EditorAccessMode
import java.util.Collections

/** One destination picker and one immutable render request. No provider or media I/O. */
internal class VideoSaveRequest(val requestCode:Int,val state:VideoCloseState,val mode:EditorAccessMode,val namespace:String,
    clips:List<VideoSegment>,val music:VideoSoundtrack?,sources:Map<String,Uri>,durations:Map<String,Long>,catalog:Map<String,VideoSource>){
    val clips:List<VideoSegment> = Collections.unmodifiableList(clips.map{it.copy(captions=Collections.unmodifiableList(it.captions.toList()))})
    val sources:Map<String,Uri> = Collections.unmodifiableMap(sources.toMap())
    val durations:Map<String,Long> = Collections.unmodifiableMap(durations.toMap())
    private val catalog:Map<String,VideoSource> = Collections.unmodifiableMap(catalog.toMap())
    @Volatile private var phase=0 // picker, returned metadata, writing, closed
    private var destination:Uri?=null
    init{require(ownsCode(requestCode));require(clips.size in 1..1000&&sources.isNotEmpty())}
    val isPending:Boolean get()=phase==0
    val isReady:Boolean get()=phase==1
    val isWriting:Boolean get()=phase==2
    val isClosed:Boolean get()=phase==3
    @Synchronized fun accept(code:Int,ok:Boolean,uri:Uri?):Boolean {
        if(code!=requestCode||phase!=0)return false
        if(!ok||uri==null||uri.scheme!="content"||uri.authority.isNullOrBlank()||uri in sources.values||uri.toString()==music?.uri){phase=3;return true}
        destination=uri;phase=1;return true
    }
    @Synchronized fun beginWriting():Uri? {if(phase!=1)return null;phase=2;return destination}
    @Synchronized fun close(){phase=3;destination=null}
    fun matches(current:VideoCloseState,mode:EditorAccessMode,namespace:String?,sources:Map<String,Uri>,durations:Map<String,Long>,catalog:Map<String,VideoSource>):Boolean =
        !isClosed&&this.mode==mode&&this.namespace==namespace&&state.stillMatches(current)&&this.sources==sources&&this.durations==durations&&this.catalog==catalog
    companion object {const val FIRST_CODE=1000;const val LAST_CODE=65535;fun ownsCode(code:Int)=code in FIRST_CODE..LAST_CODE}
}
