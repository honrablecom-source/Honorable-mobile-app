package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class VideoMarianModelTest {
    @Test fun cacheReorderDuplicatesSelectedParentsWithoutMutatingNativeCopy(){
        val raw=floatArrayOf(10f,11f,12f,20f,21f,22f);val old=raw.copyOf()
        assertArrayEquals(floatArrayOf(20f,21f,22f,20f,21f,22f,10f,11f,12f),VideoMarianModel.reorder(raw,2,intArrayOf(1,1,0)),0f)
        assertArrayEquals(old,raw,0f)
        try{VideoMarianModel.reorder(raw,2,intArrayOf(2));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun stickyStopRefusesBeforeAnyFileOrNativeSession(){
        val stop=VideoMarianModel.Stop();stop.check();stop.cancel();stop.cancel()
        try{VideoMarianModel.verify(File("/nonexistent-noble-generated-translation-model"),VideoMarianModel.artifacts.first(),stop);fail()}catch(_:CancellationException){}
    }
    @Test fun incompleteArtifactIsNotAcceptedAsAnInstalledTranslationModel(){
        val file=File.createTempFile("noble-synthetic-model-",".tmp")
        try{file.writeBytes(byteArrayOf(1,2));try{VideoMarianModel.verify(file,VideoMarianModel.artifacts.first(),VideoMarianModel.Stop());fail()}catch(_:IllegalArgumentException){}}
        finally{file.delete()}
    }
}
