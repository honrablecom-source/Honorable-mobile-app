package com.honorablemobile

import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class VideoSoundModelTest {
    private fun csv()="index,mid,display_name\r\n"+(0..520).joinToString("\r\n"){i->"$i,/m/id_$i,"+if(i==10)"\"Sound, with \"\"quotes\"\"\""else "Sound $i"}+"\r\n"
    private fun fails(action:()->Unit){try{action();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    @Test fun completeCsvSupportsQuotedCommasEscapedQuotesAndAllOfficialMidFamilies(){
        val parsed=VideoSoundModel.parseLabels(csv().replace("10,/m/id_10,","10,/t/id_10,").replace("11,/m/id_11,","11,/g/id_11,"))
        assertEquals(521,parsed.size);assertEquals("Sound, with \"quotes\"",parsed[10]);assertEquals("Sound 520",parsed.last())
    }
    @Test fun malformedMissingReorderedDuplicateAndUnclosedRowsRefuseAllLabels(){
        fails{VideoSoundModel.parseLabels(csv().substringBeforeLast("520,"))}
        fails{VideoSoundModel.parseLabels(csv().replace("10,/m/id_10,","11,/m/id_10,"))}
        fails{VideoSoundModel.parseLabels(csv().replace("/m/id_10","/m/id_9"))}
        fails{VideoSoundModel.parseLabels(csv().replace("Sound 20","bad,unquoted"))}
        fails{VideoSoundModel.parseLabels(csv().replace("Sound 20","\"unclosed"))}
    }
    @Test fun stickyStopAndInvalidAudioRefuseBeforeAnyModelNativeAccess(){
        val file=File("/nonexistent-synthetic-sound-model");val stop=VideoSoundModel.Stop();stop.cancel()
        try{VideoSoundModel.verify(file,VideoSoundModel.BYTES,VideoSoundModel.SHA256,stop);fail()}catch(_:CancellationException){}
        fails{VideoSoundModel.recognize(file,FloatArray(8000){Float.NaN},0,List(521){"Sound"},VideoSoundModel.Stop())}
        fails{VideoSoundModel.verify(file,VideoSoundModel.BYTES,VideoSoundModel.SHA256,VideoSoundModel.Stop())}
    }
}
