package com.honorablemobile

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Existing synthetic test asset only; isolated cache and no personal video inputs. */
class VideoStoryboardSheetIntegrationTest {
    private fun fixture(action:(Context,Uri,File,Long)->Unit){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val real=instrumentation.targetContext
        val parent=File(real.cacheDir,"created-images");check(parent.isDirectory||parent.mkdirs())
        val directory=File(parent,"storyboard-acceptance-"+java.util.UUID.randomUUID());check(directory.mkdir())
        val isolated=object:ContextWrapper(real){override fun getCacheDir():File=directory}
        try{
            val source=File(directory,"synthetic-source.mp4")
            instrumentation.context.assets.open("portrait-audio.mp4").use{input->source.outputStream().use{input.copyTo(it)}}
            val uri=FileProvider.getUriForFile(real,real.packageName+".creation",source)
            val metadata=MediaMetadataRetriever()
            val duration=try{metadata.setDataSource(real,uri);requireNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()}finally{metadata.release()}
            require(duration>=2000)
            action(isolated,uri,source,duration)
        }finally{directory.deleteRecursively()}
    }
    private fun digest(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes())
    private fun nonWhite(bitmap:Bitmap,left:Int,top:Int,right:Int,bottom:Int):Int{
        var count=0
        for(y in top until bottom step 2)for(x in left until right step 2)if(bitmap.getPixel(x,y)!=Color.WHITE)count++
        return count
    }
    @Test fun pngContainsPhotoAndLabelCellsKeepsSourceAndReopensThroughPrivateProvider()=fixture{context,uri,source,duration->
        val before=digest(source)
        val plan=VideoStoryboardPlan.create(listOf(VideoSegment(0,1000,title="Opening"),VideoSegment(1000,2000,title="Closing")))
        val output=VideoStoryboardSheet.create(context,plan,mapOf("primary" to uri),mapOf("primary" to duration),{}, {_,_->})
        try{
            val image=requireNotNull(BitmapFactory.decodeFile(output.path))
            try{
                assertEquals(plan.width,image.width);assertEquals(plan.height,image.height)
                assertEquals(Color.WHITE,image.getPixel(0,0))
                plan.shots.indices.forEach{index->val cell=plan.cell(index)
                    assertTrue(nonWhite(image,cell.first+16,cell.second+16,cell.first+464,cell.second+252)>30)
                    assertTrue(nonWhite(image,cell.first+10,cell.second+268,cell.first+470,cell.second+344)>10)
                }
            }finally{image.recycle()}
            val pngUri=FileProvider.getUriForFile(context,context.packageName+".creation",output)
            assertEquals("image/png",context.contentResolver.getType(pngUri))
            context.contentResolver.openInputStream(pngUri).use{input->assertArrayEquals(output.readBytes(),requireNotNull(input).readBytes())}
            assertArrayEquals(before,digest(source))
            assertEquals(listOf(output.name),File(context.cacheDir,"created-images").list()!!.toList())
        }finally{output.delete()}
    }
    @Test fun cancellationAfterFirstFrameRemovesOnlyOwnedIntermediatesAndKeepsSource()=fixture{context,uri,source,duration->
        val before=digest(source);var current=true
        val plan=VideoStoryboardPlan.create(listOf(VideoSegment(0,1000),VideoSegment(1000,2000)))
        assertTrue(runCatching{
            VideoStoryboardSheet.create(context,plan,mapOf("primary" to uri),mapOf("primary" to duration),{check(current)}){index,_->if(index==2)current=false}
        }.isFailure)
        assertArrayEquals(before,digest(source))
        assertTrue(File(context.cacheDir,"created-images").listFiles()!!.isEmpty())
    }
}
