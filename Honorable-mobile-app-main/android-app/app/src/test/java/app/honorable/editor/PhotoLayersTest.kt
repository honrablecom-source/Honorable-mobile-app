package app.honorable.editor

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.junit.Assert.*
import org.junit.Test

class PhotoLayersTest {
    private val box=PhotoObject("box",PhotoObjectType.RECTANGLE,x=.25f,y=.75f,width=.2f,height=.4f)
    private val text=PhotoObject("text",PhotoObjectType.TEXT,text="Editable text",opacity=.5f)
    private val scope=PhotoSessionCodec.scope("owner","https://example.test","content://photo/source")
    @Test fun immutableCollectionsProtectHistoryAndFrozenExports(){
        val source=mutableListOf(box,text);val layers=PhotoLayers(source);source.clear()
        assertEquals(2,layers.objects.size)
        assertTrue(runCatching{(layers.objects as MutableList<PhotoObject>).clear()}.isFailure)
        val edit=PhotoEdit(layers=layers);val changed=layers.update(box.copy(x=.8f))
        assertEquals(.25f,edit.layers.objects.first().x);assertEquals(.8f,changed.objects.first().x)
    }
    @Test fun boundsIdentifiersAndMalformedTextAreRejected(){
        for(action in listOf<()->Unit>(
            {box.copy(x=Float.NaN)},{box.copy(width=0f)},{box.copy(rotation=181f)},{box.copy(opacity=1.1f)},
            {text.copy(text="")},{text.copy(text="x".repeat(201))},{text.copy(text="bad\u0000text")},{text.copy(text="\uD800")},
            {PhotoLayers(listOf(box,box))},{PhotoLayers((0..32).map{box.copy(id="id$it")})}
        ))assertTrue(runCatching(action).isFailure)
    }
    @Test fun objectCoordinatesScaleConsistentlyBetweenPreviewAndExport(){
        val small=PhotoObjectPlan.create(box,400,200);val large=PhotoObjectPlan.create(box,1600,800)
        assertEquals(100f,small.centerX);assertEquals(150f,small.centerY);assertEquals(80f,small.width);assertEquals(80f,small.height)
        assertEquals(small.centerX*4,large.centerX);assertEquals(small.centerY*4,large.centerY);assertEquals(small.width*4,large.width);assertEquals(small.height*4,large.height);assertEquals(small.textSize*4,large.textSize)
    }
    @Test fun locksProtectEditsDeletionAndStackingButCanBeToggled(){
        val locked=box.copy(locked=true);val layers=PhotoLayers(listOf(locked,text))
        assertTrue(runCatching{layers.update(locked.copy(x=.6f))}.isFailure)
        assertTrue(runCatching{layers.delete(locked.id)}.isFailure);assertTrue(runCatching{layers.move(locked.id,true)}.isFailure)
        assertFalse(layers.update(locked.copy(visible=false)).objects.first().visible)
        assertFalse(layers.update(locked.copy(locked=false)).objects.first().locked)
    }
    @Test fun orderingAndDeletionAreIndependentImmutableChanges(){
        val original=PhotoLayers(listOf(box,text));assertEquals(listOf(text,box),original.move(box.id,true).objects)
        assertEquals(listOf(text,box),original.move(text.id,false).objects);assertEquals(listOf(text),original.delete(box.id).objects);assertEquals(listOf(box,text),original.objects)
    }
    @Test fun versionEightRetainsEveryObjectHistoryAndPendingExport(){
        val edit=PhotoEdit(layers=PhotoLayers(listOf(box,text.copy(visible=false,locked=true))))
        val state=PhotoSessionSnapshot(scope,edit,listOf(PhotoEdit()),listOf(edit.copy(layers=PhotoLayers(listOf(box)))),pendingExport=edit)
        val bytes=PhotoSessionCodec.encode(state)
        DataInputStream(ByteArrayInputStream(bytes)).use{it.readInt();assertEquals(8,it.readInt())}
        assertEquals(state,PhotoSessionCodec.decode(bytes,scope))
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains("content://photo/source"))
    }
    @Test fun richRecoveryKeepsCurrentAndPendingStateWhileBoundingHistory(){
        val rich=PhotoEdit(layers=PhotoLayers((0 until 32).map{text.copy(id="text$it",text="界".repeat(200))}))
        val state=PhotoSessionSnapshot(scope,rich,List(12){rich},List(12){rich},pendingExport=rich)
        val bytes=PhotoSessionCodec.encodeForRecovery(state);assertTrue(bytes.size<=PhotoSessionCodec.MAX_BYTES)
        val restored=PhotoSessionCodec.decode(bytes,scope);assertEquals(rich,restored.edit);assertEquals(rich,restored.pendingExport);assertTrue(restored.undo.size+restored.redo.size<24)
    }
    @Test fun oldSessionsDefaultToEmptyObjectsAndSavedLooksRetainObjects(){
        val original=PhotoEdit(layers=PhotoLayers(listOf(box,text)))
        val empty=PhotoSessionSnapshot(scope,PhotoEdit());assertEquals(empty,PhotoSessionCodec.decode(PhotoSessionCodec.encode(empty),scope))
        val applied=PhotoAdjustmentPreset(exposure=.5f).applyTo(original);assertEquals(original.layers,applied.layers)
    }
    @Test fun bulkVisibilityPreservesLocksAndEditProtectionAtMaximumCapacity(){
        val originals=PhotoLayers((0 until 32).map{box.copy(id="box$it",locked=it%2==0,visible=it%3==0,opacity=it/32f)})
        val snapshot=originals.objects.toList()
        val hidden=originals.withVisibility(false);val shown=hidden.withVisibility(true)
        assertEquals(32,hidden.objects.size);assertTrue(hidden.objects.none{it.visible});assertTrue(shown.objects.all{it.visible})
        for(i in originals.objects.indices){
            assertEquals(originals.objects[i].locked,hidden.objects[i].locked)
            assertEquals(PhotoObjectPlan.create(originals.objects[i],640,480),PhotoObjectPlan.create(hidden.objects[i],640,480))
            assertEquals(originals.objects[i].opacity,hidden.objects[i].opacity)
        }
        assertTrue(runCatching{hidden.delete("box0")}.isFailure)
        assertTrue(runCatching{shown.update(shown.objects.first().copy(x=.8f))}.isFailure)
        assertTrue(originals.objects.any{it.visible});assertEquals(snapshot,originals.objects)
    }
    @Test fun bulkLockingPreservesHiddenStylesAndOnlyExplicitUnlockPermitsEditing(){
        val originals=PhotoLayers(listOf(box.copy(visible=false),text.copy(visible=false,curve=90f)))
        val locked=originals.withLocking(true)
        assertTrue(locked.objects.all{it.locked&&!it.visible})
        assertEquals(originals.objects[1].textStyle,locked.objects[1].textStyle);assertEquals(90f,locked.objects[1].curve)
        assertTrue(runCatching{locked.move("text",true)}.isFailure)
        val unlocked=locked.withLocking(false);assertEquals(originals,unlocked)
        assertEquals("Updated",unlocked.update(unlocked.objects[1].copy(text="Updated")).objects[1].text)
        assertFalse(originals.objects.any{it.locked})
    }
    @Test fun bulkFlagsRoundTripWithHistoryAndPendingExportWithoutChangingSchema(){
        val before=PhotoEdit(layers=PhotoLayers(listOf(box,text)))
        val after=before.copy(layers=before.layers.withLocking(true).withVisibility(false))
        val state=PhotoSessionSnapshot(scope,after,listOf(before),pendingExport=after)
        assertEquals(state,PhotoSessionCodec.decode(PhotoSessionCodec.encode(state),scope))
        assertEquals(before.layers,before.layers.withVisibility(true).withLocking(false))
        assertEquals(PhotoLayers(),PhotoLayers().withVisibility(false).withLocking(true))
        assertEquals(after.layers,after.layers.withVisibility(false).withLocking(true))
    }
}
