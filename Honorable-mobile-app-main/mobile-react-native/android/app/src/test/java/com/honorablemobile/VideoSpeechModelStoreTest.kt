package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CancellationException

class VideoSpeechModelStoreTest {
    private class Reply(val code:Int=200,val location:String?=null,val content:InputStream=ByteArrayInputStream(byteArrayOf(1,2,3))):HttpURLConnection(URL(VideoSpeechModel.URL)) {
        var disconnected=false
        override fun connect(){}
        override fun disconnect(){disconnected=true}
        override fun usingProxy()=false
        override fun getResponseCode()=code
        override fun getHeaderField(key:String?)=if(key=="Location")location else null
        override fun getInputStream()=content
        override fun getContentLengthLong()=-1L
    }
    private fun directory()=Files.createTempDirectory("noble-synthetic-speech-").toFile()
    @Test fun absentConsentNeverOpensConnectionOrCreatesCache() {
        val root=directory();val cache=File(root,"not-created");var requests=0
        try{
            try{VideoSpeechModelStore.obtain(cache,false,VideoSpeechModel.Stop(),connect={requests++;Reply()});fail()}catch(_:IllegalArgumentException){}
            assertEquals(0,requests);assertFalse(cache.exists())
        }finally{root.deleteRecursively()}
    }
    @Test fun partialDownloadKeepsOldModelAndRemovesTemporaryFile() {
        val root=directory();val target=VideoSpeechModelStore.model(root);target.writeBytes(byteArrayOf(9,8));val reply=Reply()
        try{
            try{VideoSpeechModelStore.obtain(root,true,VideoSpeechModel.Stop(),connect={url->assertEquals(VideoSpeechModel.URL,url.toString());reply});fail()}catch(_:IllegalArgumentException){}
            assertArrayEquals(byteArrayOf(9,8),target.readBytes());assertEquals(listOf(target.name),root.list()!!.toList());assertTrue(reply.disconnected)
            assertEquals("GET",reply.requestMethod);assertFalse(reply.instanceFollowRedirects)
        }finally{root.deleteRecursively()}
    }
    @Test fun redirectsCannotDowngradeOrLeavePublicModelHosts() {
        for(location in listOf("http://huggingface.co/model","https://example.com/model","https://name:password@huggingface.co/model")) {
            val root=directory();var requests=0;val reply=Reply(302,location)
            try{
                try{VideoSpeechModelStore.obtain(root,true,VideoSpeechModel.Stop(),connect={requests++;reply});fail()}catch(_:IllegalArgumentException){}
                assertEquals(1,requests);assertEquals(0,root.list()!!.size);assertTrue(reply.disconnected)
            }finally{root.deleteRecursively()}
        }
    }
    @Test fun cancelledReadDeletesPartialButKeepsModelAndReleasesLease() {
        val root=directory();val target=VideoSpeechModelStore.model(root);target.writeBytes(byteArrayOf(7));val stop=VideoSpeechModel.Stop()
        val reply=Reply(content=object:InputStream(){override fun read():Int{stop.cancel();return 1};override fun read(bytes:ByteArray,off:Int,len:Int):Int{stop.cancel();bytes[off]=1;return 1}})
        try{
            try{VideoSpeechModelStore.obtain(root,true,stop,connect={reply});fail()}catch(_:CancellationException){}
            assertArrayEquals(byteArrayOf(7),target.readBytes());assertEquals(1,root.list()!!.size);assertTrue(reply.disconnected)
            try{VideoSpeechModelStore.obtain(root,false,VideoSpeechModel.Stop());fail()}catch(_:IllegalArgumentException){}
        }finally{root.deleteRecursively()}
    }
    @Test fun correctLengthWrongDigestCannotBeUsedAsARealModel() {
        val root=directory();val file=VideoSpeechModelStore.model(root)
        try{
            RandomAccessFile(file,"rw").use{it.setLength(VideoSpeechModel.BYTES)}
            try{VideoSpeechModelStore.obtain(root,false,VideoSpeechModel.Stop());fail("Same size is not model integrity")}catch(error:IllegalArgumentException){assertTrue(error.message!!.contains("integrity"))}
        }finally{root.deleteRecursively()}
    }
}
