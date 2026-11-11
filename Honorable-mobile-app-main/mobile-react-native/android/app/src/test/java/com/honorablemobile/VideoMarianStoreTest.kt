package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CancellationException

class VideoMarianStoreTest {
    private class Reply(val code:Int=200,val redirect:String?=null):HttpURLConnection(URL("https://huggingface.co/")){
        var disconnected=false
        override fun connect(){}
        override fun usingProxy()=false
        override fun disconnect(){disconnected=true}
        override fun getResponseCode()=code
        override fun getHeaderField(key:String?)=if(key=="Location")redirect else null
        override fun getContentLengthLong()=-1L
        override fun getInputStream()=ByteArrayInputStream(byteArrayOf(1,2,3))
    }
    @Test fun noConsentNeverCreatesStorageOrOpensConnection(){
        val root=Files.createTempDirectory("noble-synthetic-translation-").toFile();val folder=File(root,"absent");var requests=0
        try{try{VideoMarianStore.obtain(folder,false,VideoMarianModel.Stop()){requests++;Reply()};fail()}catch(_:IllegalArgumentException){}
            assertFalse(folder.exists());assertEquals(0,requests)}finally{root.deleteRecursively()}
    }
    @Test fun malformedDownloadPreservesPriorFileAndRemovesPart(){
        val root=Files.createTempDirectory("noble-synthetic-translation-").toFile();val old=File(root,VideoMarianModel.artifacts.first().name);old.writeBytes(byteArrayOf(9));val reply=Reply()
        try{try{VideoMarianStore.obtain(root,true,VideoMarianModel.Stop()){reply};fail()}catch(_:IllegalArgumentException){}
            assertArrayEquals(byteArrayOf(9),old.readBytes());assertEquals(listOf(old.name),root.list()!!.toList());assertTrue(reply.disconnected);assertEquals("GET",reply.requestMethod)}finally{root.deleteRecursively()}
    }
    @Test fun unsafeRedirectAndStopCannotContinueSetup(){
        val root=Files.createTempDirectory("noble-synthetic-translation-").toFile();var requests=0
        try{try{VideoMarianStore.obtain(root,true,VideoMarianModel.Stop()){requests++;Reply(302,"http://example.com/model")};fail()}catch(_:IllegalArgumentException){}
            assertEquals(1,requests);assertEquals(0,root.list()!!.size)
            val stop=VideoMarianModel.Stop().apply{cancel()}
            try{VideoMarianStore.obtain(root,true,stop){requests++;Reply()};fail()}catch(_:CancellationException){}
            assertEquals(1,requests)}finally{root.deleteRecursively()}
    }
}
