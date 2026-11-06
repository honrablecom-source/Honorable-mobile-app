package app.honorable.editor

import android.net.Uri

/** Capability is a current precheck, never proof an individual selected original is readable. */
internal object PhotoProjectReadAccess {
    private val volume=Regex("[A-Za-z0-9_-]{1,64}")
    private val item=Regex("[1-9][0-9]{0,18}")
    fun hasExistingRead(source:Uri,persisted:Boolean,canReadImages:()->Boolean):Boolean {
        if(source.scheme!="content")return false
        if(persisted)return true
        if(source.authority!="media")return false
        val parts=source.pathSegments
        if(parts.size!=4||!volume.matches(parts[0])||parts[1]!="images"||parts[2]!="media"||!item.matches(parts[3])||(parts[3].toLongOrNull()?:0)<=0)return false
        return canReadImages()
    }
}
