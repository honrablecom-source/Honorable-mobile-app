package com.honorablemobile
import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=android.app.Application::class)
class DrawingGifShareTest {
    private fun fixture()=Base64.decode("R0lGODlhEAAQAPAAAAAA/wAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQECgAAACwAAAAAEAAQAAAIHQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFgQEBACH5BAQKAAAALAAAAAAQABAAgP8AAAAAAAgdAAEIHEiwoMGDCBMqXMiwocOHECNKnEixosWBAQEAIfkEBAoAAAAsAAAAABAAEACAAAD/AAAACB0AAQgcSLCgwYMIEypcyLChw4cQI0qcSLGixYEBAQAh+QQECgAAACwAAAAAEAAQAID/AAAAAAAIHQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFgQEBACH5BAQKAAAALAAAAAAQABAAgAAA/wAAAAgdAAEIHEiwoMGDCBMqXMiwocOHECNKnEixosWBAQEAIfkEBAoAAAAsAAAAABAAEACA/wAAAAAACB0AAQgcSLCgwYMIEypcyLChw4cQI0qcSLGixYEBAQAh+QQECgAAACwAAAAAEAAQAIAAAP8AAAAIHQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFgQEBACH5BAQKAAAALAAAAAAQABAAgP8AAAAAAAgdAAEIHEiwoMGDCBMqXMiwocOHECNKnEixosWBAQEAIfkEBAoAAAAsAAAAABAAEACAAAD/AAAACB0AAQgcSLCgwYMIEypcyLChw4cQI0qcSLGixYEBAQAh+QQECgAAACwAAAAAEAAQAID/AAAAAAAIHQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFgQEBADs=",Base64.NO_WRAP)
    private fun rejects(action:()->Unit){assertTrue(runCatching(action).isFailure)}
    @Test fun actualGifencOutputHasTenBoundedFramesAndTransfersExactly(){val data=fixture();assertEquals(10,DrawingGifShare.validate(data));val upload=DrawingGifUpload(4,data.size);upload.append(Base64.encodeToString(data,Base64.NO_WRAP));assertArrayEquals(data,upload.finish());rejects{upload.finish()};rejects{upload.append("AAAA")}}
    @Test fun malformedHeadersDimensionsTrailerAndImageBlocksRefuse(){val original=fixture();for(offset in listOf(0,6,8,original.lastIndex)){val data=original.copyOf();data[offset]=0;rejects{DrawingGifShare.validate(data)}};rejects{DrawingGifShare.validate(original.copyOf(original.size-1))};rejects{DrawingGifShare.validate(ByteArray(DrawingGifShare.MAX_BYTES+1))}}
    @Test fun invalidRequestsIncompleteAndNoncanonicalChunksCannotProduceFiles(){for(id in listOf(0,-1,1_000_000_001))rejects{DrawingGifUpload(id,100)};for(size in listOf(0,DrawingGifShare.MAX_BYTES+1))rejects{DrawingGifUpload(1,size)};val upload=DrawingGifUpload(1,fixture().size);for(chunk in listOf("","AB==","!!!!","A".repeat(32772)))rejects{upload.append(chunk)};rejects{upload.finish()};upload.append(Base64.encodeToString(fixture(),Base64.NO_WRAP));assertArrayEquals(fixture(),upload.finish())}
    @Test fun completeFileIsByteExactAndCancellationRemovesPartialOutput(){val directory=Files.createTempDirectory("noble-gif-test").toFile();try{val file=DrawingGifShare.prepare(directory,fixture()){};assertTrue(file.name.endsWith(".gif"));assertArrayEquals(fixture(),file.readBytes());file.delete();var calls=0;rejects{DrawingGifShare.prepare(directory,fixture()){check(++calls<3)}};assertEquals(0,directory.listFiles()!!.size)}finally{directory.deleteRecursively()}}
}
