package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer

class PhotoCompositionTest {
    private val full=PhotoComposition(.2f,0xFF123456.toInt(),.04f,-1,.7f,.4f,2,3,6,-.25f,"Made here 😀",.3f,false,0x00FF00,.2f)
    private fun sample(c:PhotoComposition,x:Int=4,y:Int=4):Int=PhotoCompositionKernel(IntArray(81){-1},9,9,c).sample(x,y)
    @Test fun identitySamplerPreservesEveryChannelAndAlpha(){val p=intArrayOf(0x00112233,0x7F445566,-1,0xFF000000.toInt());val k=PhotoCompositionKernel(p,2,2,PhotoComposition());assertEquals(p.toList(),(0..3).map{k.sample(it%2,it/2)})}
    @Test fun colorKeyRemovesMatchingColorAndKeepsUnrelatedAlpha(){val p=intArrayOf(0x7F00FF00,0x7FFF0000);val k=PhotoCompositionKernel(p,2,1,PhotoComposition(keyColor=0x00FF00));assertEquals(0,k.sample(0,0) ushr 24);assertEquals(p[1],k.sample(1,0))}
    @Test fun colorKeyFeatherProducesPartialAlpha(){val p=0xFF00D800.toInt();val out=PhotoCompositionKernel(intArrayOf(p),1,1,PhotoComposition(keyColor=0x00FF00,keyTolerance=.1f)).sample(0,0);assertTrue(out ushr 24 in 1..254);assertEquals(p and 0xFFFFFF,out and 0xFFFFFF)}
    @Test fun tilePatternRepeatsTheWholeSource(){val p=IntArray(16){0xFF000000.toInt() or it};val k=PhotoCompositionKernel(p,4,4,PhotoComposition(tiles=2));assertEquals(p[10],k.sample(1,1));assertEquals(k.sample(0,0),k.sample(2,2));assertEquals(k.sample(1,1),k.sample(3,3))}
    @Test fun kaleidoscopeIsDeterministicAndPreservesSolidAlpha(){val p=IntArray(63){0x7F234567};for(n in listOf(2,4,6,8,12)){val k=PhotoCompositionKernel(p,9,7,PhotoComposition(sectors=n));assertTrue((0 until 63).all{k.sample(it%9,it/9)==p[0]})}}
    @Test fun circleCutoutClearsCornersAndKeepsCenter(){assertEquals(0,sample(PhotoComposition(shape=1),0,0));assertEquals(-1,sample(PhotoComposition(shape=1)))}
    @Test fun roundedCornersClearOnlyTheCorners(){val k=PhotoCompositionKernel(IntArray(32*32){-1},32,32,PhotoComposition(shape=2));assertEquals(0,k.sample(0,0));assertEquals(-1,k.sample(16,0));assertEquals(-1,k.sample(16,16))}
    @Test fun diamondCutoutHasTransparentCorners(){assertEquals(0,sample(PhotoComposition(shape=3),0,0));assertEquals(-1,sample(PhotoComposition(shape=3)))}
    @Test fun heartCutoutHasAnOpaqueCenter(){assertEquals(0,sample(PhotoComposition(shape=4),0,0));assertEquals(-1,sample(PhotoComposition(shape=4)))}
    @Test fun presentationToolsCreateBoundedMarginsAndReflectionSpace(){
        val normal=PhotoCompositionPlan.create(800,600,PhotoComposition())
        assertEquals(800,normal.width);assertEquals(600,normal.height)
        for(c in listOf(PhotoComposition(padding=.2f),PhotoComposition(frame=.1f),PhotoComposition(shadow=1f))){val p=PhotoCompositionPlan.create(800,600,c);assertTrue(p.left>0 && p.top>0);assertTrue(p.width.toLong()*p.height<=480000)}
        val reflected=PhotoCompositionPlan.create(800,600,PhotoComposition(reflection=.5f));assertTrue(reflected.reflectionHeight>0);assertTrue(reflected.height.toDouble()/reflected.width>600.0/800)
    }
    @Test fun reflectionStartsAfterTheFrameAndFitsItsCanvas(){val p=PhotoCompositionPlan.create(800,600,PhotoComposition(frame=.12f,reflection=.5f));assertTrue(p.reflectionTop>p.top+600*p.scale+p.frame);assertTrue(p.reflectionTop+p.reflectionHeight<=p.height+1f)}
    @Test fun maximumCompositionRespectsDecodedPixelBudget(){for((w,h) in listOf(1 to 1,8192 to 1,1 to 8192,3000 to 2000,512 to 512)){val p=PhotoCompositionPlan.create(w,h,full.copy(padding=.4f,frame=.12f,shadow=1f,reflection=.5f));assertTrue(p.width>=1&&p.height>=1);assertTrue(p.width.toLong()*p.height<=w.toLong()*h)}}
    @Test fun pixelAndDimensionBoundsFailClosed(){assertTrue(runCatching{PhotoCompositionKernel(IntArray(3),2,2,full)}.isFailure);assertTrue(runCatching{PhotoCompositionPlan.create(3000,3000,full)}.isFailure);assertTrue(runCatching{PhotoCompositionKernel(intArrayOf(-1),1,1,full).sample(1,0)}.isFailure)}
    @Test fun invalidAndNonfiniteSettingsAreRejected(){for(x in listOf(Float.NaN,Float.POSITIVE_INFINITY,-1f,2f)){assertTrue(runCatching{full.copy(padding=x)}.isFailure);assertTrue(runCatching{full.copy(keyTolerance=x)}.isFailure)};assertTrue(runCatching{full.copy(tiles=7)}.isFailure);assertTrue(runCatching{full.copy(sectors=1)}.isFailure);assertTrue(runCatching{full.copy(keyColor=-1)}.isFailure)}
    @Test fun watermarkRejectsMalformedUnicodeControlsAndOversizeText(){for(s in listOf("\uD800","a\nb"," ","x".repeat(61),"😀".repeat(46))){assertTrue(runCatching{full.copy(watermark=s)}.isFailure)}}
    @Test fun binaryStateRoundTripsAllFields(){val b=ByteArrayOutputStream();DataOutputStream(b).use{full.write(it)};val decoded=DataInputStream(ByteArrayInputStream(b.toByteArray())).use{PhotoComposition.read(it)};assertEquals(full,decoded)}
    @Test fun versionSevenRecoversUndoRedoAndPendingExport(){val scope="a".repeat(64);val edit=PhotoEdit(composition=full);val s=PhotoSessionSnapshot(scope,edit,listOf(PhotoEdit()),listOf(edit),pendingExport=edit);val bytes=PhotoSessionCodec.encode(s);assertEquals(7,ByteBuffer.wrap(bytes).getInt(4));assertEquals(s,PhotoSessionCodec.decode(bytes,scope));assertTrue(runCatching{PhotoSessionCodec.decode(bytes.copyOf(bytes.size-1),scope)}.isFailure)}
    @Test fun richRecoveryPreservesCurrentExportAndNearestUndoWithinBudget(){
        val scope="c".repeat(64)
        val edit=PhotoEdit(composition=full.copy(watermark="界".repeat(60)),finish=PhotoFinish(caption=PhotoCaption("界".repeat(120))),radialLight=PhotoRadialLight(),background=PhotoBackground())
        val undo=List(50){edit.copy(brightness=it.toFloat())};val redo=List(50){edit.copy(contrast=it/50f)}
        val snapshot=PhotoSessionSnapshot(scope,edit,undo,redo,pendingExport=edit)
        assertTrue(runCatching{PhotoSessionCodec.encode(snapshot)}.isFailure)
        val bytes=PhotoSessionCodec.encodeForRecovery(snapshot);assertTrue(bytes.size<=PhotoSessionCodec.MAX_BYTES)
        val recovered=PhotoSessionCodec.decode(bytes,scope)
        assertEquals(edit,recovered.edit);assertEquals(edit,recovered.pendingExport);assertEquals(undo.last(),recovered.undo.last());assertEquals(redo.last(),recovered.redo.last());assertTrue(recovered.undo.size+recovered.redo.size<100)
    }
    @Test fun legacySessionsStillReadWithNoComposition(){val scope="b".repeat(64);for(e in listOf(PhotoEdit(),PhotoEdit(effects=PhotoEffects(negative=true)))){val bytes=PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,e));assertEquals(PhotoComposition(),PhotoSessionCodec.decode(bytes,scope).edit.composition)}}
    @Test fun adjustmentLooksRetainTargetCompositionAndNeverTransferWatermarks(){val source=PhotoEdit(brightness=20f,composition=full);val target=PhotoEdit(composition=PhotoComposition(shape=1));val out=PhotoAdjustmentPreset.capture(source).applyTo(target);assertEquals(target.composition,out.composition);assertEquals(20f,out.brightness,0f)}
}
