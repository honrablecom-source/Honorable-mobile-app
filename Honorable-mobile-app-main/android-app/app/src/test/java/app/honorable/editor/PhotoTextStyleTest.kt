package app.honorable.editor

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.math.cos
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class PhotoTextStyleTest {
    private val scope=PhotoSessionCodec.scope("owner","https://example.test","content://synthetic/photo")
    private val text=PhotoObject("text",PhotoObjectType.TEXT,text="Editable typography")
    @Test fun arcPlansHaveSymmetricFiniteGeometryAndScaleWithTheCanvas(){
        for(angle in listOf(1f,30f,90f,180f)){
            val up=PhotoTextArcPlan.create(200f,angle);val down=PhotoTextArcPlan.create(200f,-angle)
            assertEquals(up.radius,down.radius,0f);assertEquals(up.centerY,-down.centerY,0f);assertEquals(up.length,down.length,0f)
            val left=up.radius*cos(Math.toRadians(up.startDegrees.toDouble()));val right=up.radius*cos(Math.toRadians((up.startDegrees+up.sweepDegrees).toDouble()))
            assertEquals(-100.0,left,.002);assertEquals(100.0,right,.002);assertTrue(up.length>=200f)
            val large=PhotoTextArcPlan.create(800f,angle);assertEquals(up.radius*4,large.radius,.001f);assertEquals(up.length*4,large.length,.001f)
        }
    }
    @Test fun shortWordsUseTheChosenCurveAcrossTheirActualAdvance(){
        val fitted=PhotoTextArcPlan.fit(300f,120f,70f)
        assertEquals(70f,fitted.length,.001f)
        val capped=PhotoTextArcPlan.fit(300f,120f,10000f)
        assertEquals(PhotoTextArcPlan.create(300f,120f),capped)
        assertTrue(runCatching{PhotoTextArcPlan.fit(300f,120f,Float.NaN)}.isFailure)
    }
    @Test fun tinyNonfiniteAndOutOfRangeTypographyIsRejected(){
        for(block in listOf<()->Unit>({text.copy(curve=Float.NaN)},{text.copy(curve=.01f)},{text.copy(curve=181f)},{PhotoTextStyle(family=3)},{PhotoTextStyle(outline=.04f)},{PhotoTextStyle(shadow=.05f)},{PhotoTextStyle(tracking=Float.NaN)},{PhotoTextArcPlan.create(0f,90f)},{PhotoTextArcPlan.create(200f,0f)},{PhotoObject("shape",PhotoObjectType.RECTANGLE,curve=90f)}))assertTrue(runCatching(block).isFailure)
    }
    @Test fun versionNineKeepsCurvesFontAndAppearanceAcrossAllProjectCodecs(){
        val style=PhotoTextStyle(family=1,bold=true,italic=true,alignment=2,tracking=.12f,outline=.008f,outlineColor=0x99ffffff.toInt(),shadow=.015f,shadowX=-.01f,shadowY=.02f)
        val edit=PhotoEdit(layers=PhotoLayers(listOf(text.copy(curve=-120f,textStyle=style))))
        val state=PhotoSessionSnapshot(scope,edit,listOf(PhotoEdit()),pendingExport=edit)
        val bytes=PhotoSessionCodec.encode(state);DataInputStream(ByteArrayInputStream(bytes)).use{it.readInt();assertEquals(9,it.readInt())}
        assertEquals(state,PhotoSessionCodec.decode(bytes,scope))
        val fingerprint="a".repeat(64);assertEquals(edit,PhotoProjectCodec.decode(PhotoProjectCodec.encode(PhotoPresetNamespace.GUEST,"content://synthetic/photo",fingerprint,edit),PhotoPresetNamespace.GUEST,"content://synthetic/photo",fingerprint))
        val canvas=CanvasProject("12345678-1234-1234-1234-123456789abc","Styled image",fingerprint,edit);assertEquals(canvas,CanvasProjectCodec.decode(CanvasProjectCodec.encode(canvas),canvas.id))
    }
    @Test fun legacyObjectSessionsRemainVersionEightWithDefaultTypography(){
        val state=PhotoSessionSnapshot(scope,PhotoEdit(layers=PhotoLayers(listOf(text))))
        val bytes=PhotoSessionCodec.encode(state);DataInputStream(ByteArrayInputStream(bytes)).use{it.readInt();assertEquals(8,it.readInt())}
        assertEquals(state,PhotoSessionCodec.decode(bytes,scope))
        val negativeZero=state.copy(edit=state.edit.copy(layers=PhotoLayers(listOf(text.copy(curve=-0.0f)))))
        assertEquals(negativeZero,PhotoSessionCodec.decode(PhotoSessionCodec.encode(negativeZero),scope))
    }
    @Test fun richStyledRecoveryStillKeepsCurrentAndFrozenExportWithinTheBudget(){
        val rich=PhotoEdit(layers=PhotoLayers((0 until 32).map{text.copy(id="text$it",text="界".repeat(200),curve=90f,textStyle=PhotoTextStyle(bold=true,outline=.01f,shadow=.02f))}))
        val state=PhotoSessionSnapshot(scope,rich,List(12){rich},List(12){rich},pendingExport=rich)
        val bytes=PhotoSessionCodec.encodeForRecovery(state);assertTrue(bytes.size<=PhotoSessionCodec.MAX_BYTES)
        val recovered=PhotoSessionCodec.decode(bytes,scope);assertEquals(rich,recovered.edit);assertEquals(rich,recovered.pendingExport);assertTrue(recovered.undo.size+recovered.redo.size<24)
    }
}
