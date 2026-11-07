package com.honorablemobile

import android.net.Uri
import app.honorable.editor.EditorAccessMode
import java.util.Collections

/** An immutable caption-dialog approval snapshot, including imported source metadata. */
internal class VideoCaptionReviewState(val state:VideoCloseState,val mode:EditorAccessMode,val namespace:String,val generation:Long,sources:Map<String,Uri>,durations:Map<String,Long>){
    val sources:Map<String,Uri> = Collections.unmodifiableMap(sources.toMap())
    val durations:Map<String,Long> = Collections.unmodifiableMap(durations.toMap())
}
