package app.honorable.editor

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhotoEditTransferTest {
    private val scope=PhotoSessionCodec.scope("noble-photo-settings","portable-edit-v1","no-original")
    private val edit=PhotoEdit(rotation=90,crop=.8f,exposure=.25f,finish=PhotoFinish(caption=PhotoCaption("Caption 😀")),
        layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Hidden text",visible=false),PhotoObject("shape",PhotoObjectType.ELLIPSE))),
        regions=PhotoRegions(listOf(PhotoRegion("region",PhotoRegionEffect.BLUR))),alphaGradient=PhotoAlphaGradient(enabled=true))
    private fun envelope(bytes:ByteArray,data:String=Base64.encodeToString(bytes,Base64.NO_WRAP)):String {
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        return """{"format":"noble.photo-settings","version":1,"data":"$data","sha256":"$digest"}"""
    }
    private fun reject(value:CharSequence){assertTrue(runCatching{PhotoEditTransfer.decode(value)}.isFailure)}
    @Test fun completeRichEditRoundTripsIncludingHiddenTextAndNormalizedRegions(){
        assertEquals(edit,PhotoEditTransfer.decode(PhotoEditTransfer.encode(edit)))
        assertTrue(PhotoEditTransfer.encode(edit).length<=PhotoEditTransfer.MAX_TEXT_BYTES)
    }
    @Test fun payloadContainsOnlyEditAndPublicConstantScopeNotHistoryOrPendingExport(){
        val json=JSONObject(PhotoEditTransfer.encode(edit));assertEquals(setOf("format","version","data","sha256"),json.keys().asSequence().toSet())
        val saved=PhotoSessionCodec.decode(Base64.decode(json.getString("data"),Base64.NO_WRAP),scope)
        assertEquals(edit,saved.edit);assertTrue(saved.undo.isEmpty());assertTrue(saved.redo.isEmpty());assertFalse(saved.compareOriginal);assertEquals(-1,saved.activeTool);assertNull(saved.pendingExport)
    }
    @Test fun malformedFutureDuplicateExtraAndNoncanonicalEnvelopesFailClosed(){
        val valid=PhotoEditTransfer.encode(edit)
        for(value in listOf(valid.dropLast(1),valid.replace("\"version\":1","\"version\":2"),valid.replace("\"version\":1","\"version\":\"1\""),valid.replace("\"version\":1","\"version\":1.0"),
            valid.replaceFirst("{","{ "),valid.replaceFirst("{","{\"extra\":0,"),valid.replaceFirst("{","{\"version\":1,"),valid.replace("noble.photo-settings","other.photo-settings")))reject(value)
    }
    @Test fun integrityAndCanonicalBase64AreRequiredBeforeDecoding(){
        val valid=PhotoEditTransfer.encode(edit);val json=JSONObject(valid);val data=json.getString("data")
        val changed=(if(data[0]=='A')"B"else"A")+data.drop(1);reject(valid.replace(data,changed))
        reject(envelope(byteArrayOf(1),"AQ"));reject(envelope(byteArrayOf(1),"A Q=="));reject(envelope(byteArrayOf(1),"AQ==="))
        reject(valid.replace(json.getString("sha256"),"0".repeat(64)))
    }
    @Test fun historiesComparisonToolPendingExportAndForeignScopeCannotBeSmuggled(){
        val initial=PhotoSessionSnapshot(scope,edit)
        for(snapshot in listOf(initial.copy(undo=listOf(PhotoEdit())),initial.copy(redo=listOf(PhotoEdit())),initial.copy(compareOriginal=true),initial.copy(activeTool=0),initial.copy(pendingExport=PhotoEdit()),initial.copy(scope="a".repeat(64))))reject(envelope(PhotoSessionCodec.encode(snapshot)))
    }
    @Test fun emptyOversizedUtf8AndNestedInputAreRejectedWithoutRecursiveParsing(){
        reject("");reject("z".repeat(PhotoEditTransfer.MAX_TEXT_BYTES+1));reject("界".repeat(40_000));reject("[".repeat(30_000));reject(envelope(ByteArray(PhotoSessionCodec.MAX_BYTES+1)))
        val lazy=object:CharSequence{override val length=PhotoEditTransfer.MAX_TEXT_BYTES+1;override fun get(index:Int):Char=error("Must not read");override fun subSequence(startIndex:Int,endIndex:Int):CharSequence=error("Must not slice");override fun toString():String=error("Must reject length before materializing")}
        assertTrue(runCatching{PhotoEditTransfer.decode(lazy)}.exceptionOrNull()is IllegalArgumentException)
    }
    @Test fun supportedLegacyEditPayloadsUseTheSameSourceIndependentContract(){
        val bytes=ByteArrayOutputStream();DataOutputStream(bytes).use{out->
            out.writeInt(0x48504831);out.writeInt(1);out.writeUTF(scope);out.writeInt(0);out.writeFloat(1f);out.writeFloat(0f);out.writeFloat(1f);out.writeFloat(1f);out.writeFloat(0f);out.writeBoolean(false)
            out.writeInt(0);out.writeInt(0);out.writeBoolean(false);out.writeInt(-1);out.writeBoolean(false)
        }
        assertEquals(PhotoEdit(),PhotoEditTransfer.decode(envelope(bytes.toByteArray())))
    }
    @Test fun encodingIsDeterministicAndOuterClipboardWhitespaceDoesNotChangeSettings(){
        val encoded=PhotoEditTransfer.encode(edit)
        assertEquals(encoded,PhotoEditTransfer.encode(edit.copy()));assertEquals(edit,PhotoEditTransfer.decode("\n "+encoded+" \n"))
    }
}
