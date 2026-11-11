package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/** Execute production envelope parsing with Android Base64/JSON and the actual current draft codec. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoAccountDraftRecordTest {
    private val draft=VideoDraft(8000,listOf(VideoSegment(1000,6000,title="Saved words 😀")),0,2000,1000,6000)
    private fun record(bytes:ByteArray=VideoDraftCodec.encode(draft))=JSONObject().put("draft",Base64.encodeToString(bytes,Base64.NO_WRAP))
    private fun reject(value:JSONObject,duration:Long=8000) {
        val before=value.toString()
        try { VideoAccountDraftRecord.decode(value,duration);fail("Invalid record looked absent or valid") }
        catch(error:VideoDraftRecordException) { assertTrue(error.message!!.contains("Saved data and current edits were kept")) }
        assertEquals(before,value.toString())
    }
    @Test fun onlyAbsentRecordReturnsAbsent() { assertNull(VideoAccountDraftRecord.decode(null,8000));reject(JSONObject()) }
    @Test fun validRecordRoundTripsWithoutChangingEnvelope() {
        val value=record().put("futureMetadata","keep")
        val before=value.toString()
        assertEquals(draft,VideoAccountDraftRecord.decode(value,8000));assertEquals(before,value.toString())
    }
    @Test fun invalidTypesEmptyAndOversizedValuesFailWithoutMutation() {
        for(value in listOf<Any>(JSONObject.NULL,12,true,JSONObject(),""," ","A".repeat(350001))) reject(JSONObject().put("draft",value))
    }
    @Test fun invalidBase64AndDamagedBinaryAreNotMissingDrafts() {
        for(value in listOf("!invalid!","====","A"))reject(JSONObject().put("draft",value))
        val bytes=VideoDraftCodec.encode(draft)
        reject(record(bytes.copyOf(bytes.size-1)))
        reject(record(bytes+byteArrayOf(1)))
        reject(record(bytes.copyOf().also { it[0]=0 }))
    }
    @Test fun futureVersionAndChangedOriginalRemainUnavailableWithoutReplacingProject() {
        val bytes=VideoDraftCodec.encode(draft)
        reject(record(bytes.copyOf().also { ByteBuffer.wrap(it).putInt(4,Int.MAX_VALUE) }))
        val project=VideoEditProject(8000);project.split(0,4000);val before=project.segments.toList()
        reject(record(),8001)
        assertEquals(before,project.segments);assertTrue(project.undo());assertEquals(listOf(VideoSegment(0,8000)),project.segments)
    }
    @Test fun lineWrappedHistoricalBase64RemainsReadable() {
        val encoded=Base64.encodeToString(VideoDraftCodec.encode(draft),Base64.DEFAULT)
        assertEquals(draft,VideoAccountDraftRecord.decode(JSONObject().put("draft",encoded),8000))
    }
}
