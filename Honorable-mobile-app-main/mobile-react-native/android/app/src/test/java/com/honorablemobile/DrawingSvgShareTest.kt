package com.honorablemobile

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DrawingSvgShareTest {
    @get:Rule val temp=TemporaryFolder()
    private val svg="<svg xmlns=\"http://www.w3.org/2000/svg\"><text>café 🎬</text></svg>"
    private fun rejects(action:()->Unit){assertTrue(runCatching(action).isFailure)}
    @Test fun utf8VectorBytesAndSurrogateSplitUploadRoundTripExactly(){
        assertArrayEquals(svg.toByteArray(Charsets.UTF_8),DrawingSvgShare.bytes(svg))
        val upload=DrawingSvgUpload(4,svg.length);val split=svg.indexOf("🎬")+1
        upload.append(svg.substring(0,split));upload.append(svg.substring(split));assertEquals(svg,upload.finish())
    }
    @Test fun invalidRequestsChunksAndIncompleteTransfersFailClosed(){
        for(id in listOf(0,-1,1_000_000_001))rejects{DrawingSvgUpload(id,20)}
        for(size in listOf(0,DrawingSvgShare.MAX_BYTES+1))rejects{DrawingSvgUpload(1,size)}
        val upload=DrawingSvgUpload(2,svg.length);rejects{upload.finish()};rejects{upload.append("")};rejects{upload.append("x".repeat(DrawingSvgShare.CHUNK_CHARS+1))}
        upload.append(svg);rejects{upload.append("extra")};assertEquals(svg,upload.finish())
    }
    @Test fun nativeByteBudgetAndMalformedUnicodeControlsOrDeclarationsAreRefused(){
        for(value in listOf("","not SVG","<svgx></svg>","<svg>\uD800</svg>","<svg>\u0000</svg>","<svg><!DOCTYPE a></svg>","<svg><!ENTITY a b></svg>","<svg><script>x</script></svg>","<svg><foreignObject>x</foreignObject></svg>"))rejects{DrawingSvgShare.bytes(value)}
        rejects{DrawingSvgShare.bytes("<svg>"+"é".repeat(DrawingSvgShare.MAX_BYTES/2)+"</svg>")}
    }
    @Test fun verifiedPrivateFileIsAnIndependentExactCopyAndExistingArtifactsStayUntouched(){
        val directory=File(temp.root,"created-images");directory.mkdir();val existing=File(directory,"original.png").apply{writeText("original kept")}
        val first=DrawingSvgShare.prepare(directory,svg){};val second=DrawingSvgShare.prepare(directory,svg){}
        assertNotEquals(first,second);assertTrue(first.name.endsWith(".svg"));assertEquals(svg,first.readText(Charsets.UTF_8));assertEquals(svg,second.readText(Charsets.UTF_8));assertEquals("original kept",existing.readText())
        assertArrayEquals(DrawingSvgShare.bytes(svg),first.readBytes())
    }
    @Test fun cancellationDeletesOnlyItsOwnUnsharedTemporaryFile(){
        val directory=File(temp.root,"created-images");directory.mkdir();val existing=File(directory,"keep.png").apply{writeText("kept")}
        rejects{DrawingSvgShare.prepare(directory,svg){check(directory.listFiles()!!.size==1){"Paused"}}}
        assertEquals(listOf("keep.png"),directory.list()!!.toList());assertEquals("kept",existing.readText())
    }
    @Test fun malformedPayloadOrUnavailableCacheNeverOverwritesExistingBytes(){
        val directory=File(temp.root,"cache").apply{writeText("not a directory")};rejects{DrawingSvgShare.prepare(directory,svg){}};assertEquals("not a directory",directory.readText())
        val unused=File(temp.root,"unused");rejects{DrawingSvgShare.prepare(unused,"broken") {}};assertFalse(unused.exists())
    }
}
