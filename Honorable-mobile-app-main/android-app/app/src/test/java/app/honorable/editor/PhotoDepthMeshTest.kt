package app.honorable.editor

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PhotoDepthMeshTest {
    private val png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII=")
    private fun fails(action:()->Unit){try{action();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    @Test fun normalizedGridPreservesCornersOrientationAndOwnership(){
        val input=FloatArray(14*14){(it%14).toFloat()}
        val grid=PhotoDepthMesh.create(input,14,14,400,200,3,2)
        assertArrayEquals(floatArrayOf(0f,.5f,1f,0f,.5f,1f),grid.depths(),.00001f);assertEquals(2.0,grid.aspect,0.0)
        input.fill(99f);grid.depths().fill(88f);assertEquals(0f,grid.depths()[0],0f)
        val flat=PhotoDepthMesh.create(FloatArray(196){3f},14,14,1,1,2,2);assertTrue(flat.depths().all{it==.5f})
    }
    @Test fun glbHasEmbeddedTextureBoundedTopologyFrontNormalsAndMatchingUvs(){
        val grid=PhotoDepthMesh.create(FloatArray(196){3f},14,14,2,1,2,2);val bytes=PhotoDepthMesh.encode(grid,png)
        val header=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546c67,header.int);assertEquals(2,header.int);assertEquals(bytes.size,header.int)
        val jsonLength=header.int;assertEquals(0x4e4f534a,header.int)
        val json=ByteArray(jsonLength).also{header.get(it)}.toString(Charsets.UTF_8)
        assertTrue(json.contains("\"relativeDepth\":true"));assertFalse(json.contains("\"uri\":"));assertTrue(json.contains("\"componentType\":5123"))
        val binaryLength=header.int;assertEquals(0x004e4942,header.int);assertEquals(binaryLength,header.remaining())
        val positions=FloatArray(12){header.float};assertArrayEquals(floatArrayOf(-2f,1f,0f,2f,1f,0f,-2f,-1f,0f,2f,-1f,0f),positions,0f)
        repeat(4){assertEquals(0f,header.float,0f);assertEquals(0f,header.float,0f);assertEquals(1f,header.float,0f)}
        assertArrayEquals(floatArrayOf(0f,0f,1f,0f,0f,1f,1f,1f),FloatArray(8){header.float},0f)
        assertArrayEquals(intArrayOf(0,2,1,1,2,3),IntArray(6){header.short.toInt() and 65535})
        assertArrayEquals(png,ByteArray(png.size).also{header.get(it)});assertTrue(bytes.size<PhotoDepthMesh.MAX_PAYLOAD_BYTES)
    }
    @Test fun invalidGeometryTextureAndCancellationRefuseBeforeExport(){
        fails{PhotoDepthMesh.create(FloatArray(196){Float.NaN},14,14,1,1)}
        fails{PhotoDepthMesh.create(FloatArray(196),14,14,1,100)}
        fails{PhotoDepthMesh.create(FloatArray(196),14,14,1,1,97,2)}
        val grid=PhotoDepthMesh.create(FloatArray(196),14,14,1,1)
        fails{PhotoDepthMesh.encode(grid,ByteArray(64))}
        fails{PhotoDepthMesh.encode(grid,png.copyOf().apply{this[16]=127})}
        try{PhotoDepthMesh.create(FloatArray(196),14,14,1,1){throw java.util.concurrent.CancellationException()};fail()}catch(_:java.util.concurrent.CancellationException){}
    }
}
