package com.honorablemobile
import org.junit.Assert.*
import org.junit.Test
class DrawingPolicyTest {
    private fun header(width:Int=1080,height:Int=1080)=ByteArray(33).apply {
        byteArrayOf(137.toByte(),80,78,71,13,10,26,10).copyInto(this)
        java.nio.ByteBuffer.wrap(this,8,4).putInt(13);"IHDR".toByteArray().copyInto(this,12)
        java.nio.ByteBuffer.wrap(this,16,4).putInt(width);java.nio.ByteBuffer.wrap(this,20,4).putInt(height)
    }
    @Test fun onlyLocalHttpsAssetsCanLoad(){assertEquals("drawing/fonts/Assistant/a.woff2",DrawingPolicy.assetPath("https",DrawingPolicy.HOST,-1,"/drawing/fonts/Assistant/a.woff2"));for(host in listOf("evil.test",DrawingPolicy.HOST+".evil.test"))assertNull(DrawingPolicy.assetPath("https",host,-1,"/drawing/index.html"));assertNull(DrawingPolicy.assetPath("http",DrawingPolicy.HOST,-1,"/drawing/index.html"))}
    @Test fun traversalAndOtherPortsAreBlocked(){for(path in listOf("/drawing/../private","/drawing/%2e%2e/file","/drawing//file","/elsewhere/file"))assertNull(DrawingPolicy.assetPath("https",DrawingPolicy.HOST,-1,path));assertNull(DrawingPolicy.assetPath("https",DrawingPolicy.HOST,8443,"/drawing/index.html"))}
    @Test fun supportedPngSizeIsReadBeforeBitmapAllocation(){assertEquals(1080 to 1080,DrawingPolicy.validatePng(header()));assertEquals(2048 to 2048,DrawingPolicy.validatePng(header(2048,2048)))}
    @Test fun malformedAndOversizedPngHeadersAreRejected(){for(bytes in listOf(ByteArray(33),header(0,1),header(2049,1),header(1,Int.MAX_VALUE)))assertTrue(runCatching{DrawingPolicy.validatePng(bytes)}.isFailure)}
    @Test fun bridgeAcceptsOnlyBoundedPngBase64(){assertEquals("AQID",DrawingPolicy.base64Payload(DrawingPolicy.PNG_PREFIX+"AQID"));for(value in listOf("file:///private","data:image/jpeg;base64,AQID",DrawingPolicy.PNG_PREFIX+"broken!",DrawingPolicy.PNG_PREFIX+"a".repeat(12*1024*1024)))assertTrue(runCatching{DrawingPolicy.base64Payload(value)}.isFailure)}
    @Test fun truncatedOrWrongChunkIsRejected(){val bytes=header();java.nio.ByteBuffer.wrap(bytes,8,4).putInt(12);assertTrue(runCatching{DrawingPolicy.validatePng(bytes)}.isFailure);assertTrue(runCatching{DrawingPolicy.validatePng(header().copyOf(20))}.isFailure)}
}
