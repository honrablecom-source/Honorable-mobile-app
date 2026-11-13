package com.honorablemobile

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class VideoSoundStoreTest {
    private fun directory()=Files.createTempDirectory("noble-sound-store-test").toFile()
    @Test fun noConsentNeverConnectsOrCreatesMissingDirectory(){
        val parent=directory();val folder=File(parent,"missing")
        try{try{VideoSoundStore.obtain(folder,false,VideoSoundModel.Stop()){error("No network without consent")};fail()}catch(_:IllegalArgumentException){};assertFalse(folder.exists())}finally{parent.deleteRecursively()}
    }
    @Test fun incompleteTransferKeepsOldFileAndRemovesPendingFile(){
        val folder=directory();val old=File(folder,"yamnet.onnx").apply{writeText("old")};var disconnected=false
        try{
            try{VideoSoundStore.obtain(folder,true,VideoSoundModel.Stop()){url->object:HttpURLConnection(url){
                override fun connect(){};override fun usingProxy()=false;override fun disconnect(){disconnected=true}
                override fun getResponseCode()=200;override fun getContentLengthLong()=-1L
                override fun getInputStream()="incomplete".byteInputStream()
            }};fail()}catch(_:IllegalArgumentException){}
            assertTrue(disconnected);assertEquals("old",old.readText());assertEquals(listOf("yamnet.onnx"),folder.listFiles()!!.map{it.name})
        }finally{folder.deleteRecursively()}
    }
    @Test fun redirectCannotLeavePublicHttpsAllowlistAndStickyStopPreventsIo(){
        val folder=directory();var requests=0;var disconnected=0
        try{
            for(redirect in listOf("http://huggingface.co/model","https://private.invalid/model","https://name:password@huggingface.co/model","https://huggingface.co:444/model")){
                try{VideoSoundStore.obtain(folder,true,VideoSoundModel.Stop()){url->requests++;object:HttpURLConnection(url){
                    override fun connect(){};override fun usingProxy()=false;override fun disconnect(){disconnected++}
                    override fun getResponseCode()=302;override fun getHeaderField(name:String)=if(name=="Location")redirect else null
                }};fail()}catch(_:IllegalArgumentException){}
            }
            assertEquals(4,requests);assertEquals(4,disconnected);assertTrue(folder.listFiles()!!.isEmpty())
            val stop=VideoSoundModel.Stop();stop.cancel()
            try{VideoSoundStore.obtain(folder,true,stop){error("Stopped before network")};fail()}catch(_:CancellationException){}
        }finally{folder.deleteRecursively()}
    }
}
