package com.honorablemobile

import app.honorable.editor.EditorAccessMode
import java.util.Collections

/** Explains and routes existing outcomes. Preparing a guide performs no save/export or edit. */
internal object VideoFinishGuide {
    enum class Action { VIDEO_COPY, NAMED_VERSION, QUICK_SAVE }
    data class Choice(val action:Action,val title:String,val detail:String,val button:String)
    class Info(val summary:String,choices:List<Choice>){val choices:List<Choice> = Collections.unmodifiableList(choices.toList())}
    fun describe(clips:List<VideoSegment>,selected:Int,fromMs:Long,toMs:Long,mode:EditorAccessMode):Info {
        val output=VideoExportSnapshot.clips(clips,selected,fromMs,toMs);val us=VideoPlaybackTime.durationUs(output)
        val ms=us/1000+if(us%1000==0L)0 else 1
        val choices=mutableListOf(
            Choice(Action.VIDEO_COPY,"A playable video copy","Creates an MP4 through Files. The selected clip uses its current trim selection; other clips keep their stored ranges. This movie does not contain editable project history. Originals stay unchanged.","Export video copy"),
            Choice(Action.NAMED_VERSION,"A named editable version","Keeps another editable alternative on this device, bound to this original. Open saved versions to name and save another alternative. Keep source videos, music and subtitle documents available.","Open saved versions")
        )
        if(mode==EditorAccessMode.GUEST_BASIC||mode.allowsAccountDrafts){val name=if(mode==EditorAccessMode.GUEST_BASIC)"project"else"draft"
            choices.add(Choice(Action.QUICK_SAVE,"A quick editable $name","Saves editable work for this original on this device, replacing its quick save. This is not a movie file. Use a named version to keep alternatives and keep originals available.","Save $name on this device"))
        }
        return Info("${clips.size} clips · movie copy ${VideoCaptions.formatTime(ms)}\nChoose what you want to keep. Preview the video and sound before exporting.",choices)
    }
    class Route(actions:List<Action>,private val current:()->Boolean){
        private val allowed=actions.toSet();private var closed=false
        @Synchronized fun close(){closed=true}
        @Synchronized fun choose(action:Action,dismiss:()->Unit,dispatch:(Action)->Unit):Boolean {
            if(closed||action !in allowed)return false
            if(!current()){closed=true;return false}
            closed=true;dismiss();dispatch(action);return true
        }
    }
}
