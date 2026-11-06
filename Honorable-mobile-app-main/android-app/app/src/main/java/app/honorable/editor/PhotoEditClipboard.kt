package app.honorable.editor

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle

/** Explicit foreground UI calls only. Never coerce a URI/Intent/HTML into text or resolve a provider. */
internal object PhotoEditClipboard {
    const val MIME="application/vnd.noble.photo-settings+json"
    const val SENSITIVE="android.content.extra.IS_SENSITIVE"
    fun create(edit:PhotoEdit):ClipData {
        val description=ClipDescription("Noble photo settings",arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN,MIME))
        description.extras=PersistableBundle().apply{putBoolean(SENSITIVE,true)}
        return ClipData(description,ClipData.Item(PhotoEditTransfer.encode(edit)))
    }
    fun read(clip:ClipData?):PhotoEdit {
        requireNotNull(clip){"No clipboard settings"};require(clip.itemCount==1){"Use one photo settings item"}
        require(clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)||clip.description.hasMimeType(MIME)){"Use plain-text photo settings"}
        val item=clip.getItemAt(0)
        require(item.uri==null&&item.intent==null&&item.htmlText==null){"Clipboard contains external content"}
        return PhotoEditTransfer.decode(requireNotNull(item.text){"No text settings"})
    }
}
