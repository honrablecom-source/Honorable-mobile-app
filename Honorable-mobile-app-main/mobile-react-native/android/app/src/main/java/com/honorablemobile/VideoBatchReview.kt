package com.honorablemobile

import java.util.Collections

/** Single current UI stage and immutable one-use proposal; no Android I/O or algorithm changes. */
internal class VideoBatchReview(clips:List<VideoSegment>,val music:VideoSoundtrack?){
    val base:List<VideoSegment> = freeze(clips)
    private var stage:Any?=null
    private var proposed:List<VideoSegment>?=null
    var isClosed=false
        private set
    fun open(next:Any):Boolean {
        if(isClosed)return false
        stage=next;proposed=null;return true
    }
    fun owns(candidate:Any)=!isClosed&&stage===candidate
    fun dismiss(candidate:Any){if(owns(candidate))close()}
    fun close(){isClosed=true;stage=null;proposed=null}
    fun propose(candidate:Any,clips:List<VideoSegment>):Boolean {
        if(!owns(candidate)||proposed!=null)return false
        require(clips.size in 1..1000)
        proposed=freeze(clips);return true
    }
    fun apply(candidate:Any,project:VideoEditProject,requireCurrent:()->Unit):Boolean {
        if(!owns(candidate))return false
        val next=proposed?:return false
        close() // A failed/stale approval is also consumed; Undo cannot revive it.
        requireCurrent()
        require(project.segments==base&&project.soundtrack==music){"Project changed. Prepare this edit again."}
        require(next!=base){"These settings leave the project unchanged."}
        project.applyDraft(next,soundtrack=music);return true
    }
    private fun freeze(clips:List<VideoSegment>)=Collections.unmodifiableList(clips.map{it.copy(captions=Collections.unmodifiableList(it.captions.toList()))})
}
