package app.honorable.editor

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

class PhotoEditClipboardTest {
    private val edit=PhotoEdit(exposure=.25f,finish=PhotoFinish(caption=PhotoCaption("Keep caption")))
    private fun textItem(item:ClipData.Item)=ClipData(ClipDescription("Synthetic",arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)),item)
    @Test fun actualClipMetadataHasOneTextItemPublicMimeAndSensitivePreviewFlag(){
        val clip=PhotoEditClipboard.create(edit)
        assertEquals(1,clip.itemCount);assertEquals("Noble photo settings",clip.description.label)
        assertTrue(clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN));assertTrue(clip.description.hasMimeType(PhotoEditClipboard.MIME))
        assertTrue(clip.description.extras!!.getBoolean(PhotoEditClipboard.SENSITIVE));assertNull(clip.getItemAt(0).uri);assertNull(clip.getItemAt(0).intent)
        assertEquals(edit,PhotoEditClipboard.read(clip))
    }
    @Test fun plainMimeNeverCoercesUriIntentOrHtmlIntoMetadata(){
        val text=PhotoEditTransfer.encode(edit)
        for(item in listOf(ClipData.Item(Uri.parse("content://synthetic/do-not-resolve")),ClipData.Item(Intent("synthetic.DO_NOT_LAUNCH")),ClipData.Item(text,"<b>synthetic</b>")))assertTrue(runCatching{PhotoEditClipboard.read(textItem(item))}.isFailure)
    }
    @Test fun missingMultipleAndWrongMimeClipboardItemsFailClosed(){
        assertTrue(runCatching{PhotoEditClipboard.read(null)}.isFailure)
        val multiple=PhotoEditClipboard.create(edit);multiple.addItem(ClipData.Item(PhotoEditTransfer.encode(edit)));assertTrue(runCatching{PhotoEditClipboard.read(multiple)}.isFailure)
        val wrong=ClipData(ClipDescription("Synthetic",arrayOf("image/png")),ClipData.Item(PhotoEditTransfer.encode(edit)));assertTrue(runCatching{PhotoEditClipboard.read(wrong)}.isFailure)
    }
    @Test fun oversizedCharSequenceIsRejectedBeforeMaterializingClipboardText(){
        val lazy=object:CharSequence{override val length=PhotoEditTransfer.MAX_TEXT_BYTES+1;override fun get(index:Int):Char=error("Must not read");override fun subSequence(startIndex:Int,endIndex:Int):CharSequence=error("Must not slice");override fun toString():String=error("Must not materialize")}
        assertTrue(runCatching{PhotoEditClipboard.read(textItem(ClipData.Item(lazy)))}.exceptionOrNull()is IllegalArgumentException)
    }
    @Test fun preparedSettingsDetachFromLaterClipboardTextMutation(){
        val text=java.lang.StringBuilder(PhotoEditTransfer.encode(edit));val clip=ClipData.newPlainText("Synthetic",text)
        val prepared=PhotoEditClipboard.read(clip);text.setLength(0);text.append("unrelated")
        assertEquals(edit,prepared);assertTrue(runCatching{PhotoEditClipboard.read(clip)}.isFailure)
    }
    @Test fun unrelatedClipboardTextIsNotEchoedInCodecErrors(){
        val privateText="synthetic-unrelated-value";val error=runCatching{PhotoEditClipboard.read(ClipData.newPlainText("Synthetic",privateText))}.exceptionOrNull()
        assertNotNull(error);assertFalse(error!!.message.orEmpty().contains(privateText))
    }
}
