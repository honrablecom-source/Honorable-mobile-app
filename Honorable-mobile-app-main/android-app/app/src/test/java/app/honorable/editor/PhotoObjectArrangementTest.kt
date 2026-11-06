package app.honorable.editor

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class PhotoObjectArrangementTest {
    private fun objectAt(id:String,x:Float,y:Float=.5f)=PhotoObject(id,PhotoObjectType.TEXT,x=x,y=y,width=.4f,height=.3f,rotation=45f,text="Keep $id",curve=30f)
    private fun edit(objects:List<PhotoObject>)=PhotoEdit(exposure=.25f,rotation=90,finish=PhotoFinish(caption=PhotoCaption("Keep caption")),layers=PhotoLayers(objects))
    private val a=objectAt("a",.1f,.2f);private val b=objectAt("b",.6f,.5f);private val c=objectAt("c",.9f,.8f)
    private fun reject(block:()->Unit){assertTrue(runCatching(block).isFailure)}
    @Test fun columnAlignsCentersToSelectionMidpointAndKeepsOtherGeometry(){
        val base=edit(listOf(a,b,c));val plan=PhotoObjectArrangement.prepare(base,listOf("a","c"),PhotoObjectArrangement.Action.COLUMN)
        assertEquals(base.copy(layers=PhotoLayers(listOf(a.copy(x=.5f),b,c.copy(x=.5f)))),plan.edit);assertEquals(2,plan.changes.size);assertSame(base,plan.base)
    }
    @Test fun rowUsesVerticalMidpointWithoutChangingHorizontalPositions(){
        val base=edit(listOf(a,b,c));val plan=PhotoObjectArrangement.prepare(base,listOf("a","c"),PhotoObjectArrangement.Action.ROW)
        assertEquals(listOf(a.copy(y=.5f),b,c.copy(y=.5f)),plan.edit.layers.objects);assertSame(b,plan.edit.layers.objects[1])
    }
    @Test fun acrossUsesSpatialOrderRatherThanLayerOrSelectionOrder(){
        val base=edit(listOf(c,a,b));val plan=PhotoObjectArrangement.prepare(base,listOf("b","a","c"),PhotoObjectArrangement.Action.ACROSS)
        assertEquals(listOf(c,a,b.copy(x=.5f)),plan.edit.layers.objects);assertSame(c,plan.edit.layers.objects[0]);assertSame(a,plan.edit.layers.objects[1])
    }
    @Test fun downPreservesOuterCentersAndTheOrthogonalCoordinate(){
        val base=edit(listOf(c,a,b.copy(y=.7f)));val plan=PhotoObjectArrangement.prepare(base,listOf("a","b","c"),PhotoObjectArrangement.Action.DOWN)
        assertEquals(listOf(c,a,b.copy(y=.5f)),plan.edit.layers.objects);assertEquals(1,plan.changes.size)
    }
    @Test fun coincidentCentersUseOriginalLayerOrderDeterministically(){
        val objects=listOf(a,objectAt("b",.1f),c,objectAt("d",.9f));val base=edit(objects)
        val first=PhotoObjectArrangement.prepare(base,listOf("d","c","b","a"),PhotoObjectArrangement.Action.ACROSS)
        val second=PhotoObjectArrangement.prepare(base,listOf("a","b","c","d"),PhotoObjectArrangement.Action.ACROSS)
        assertEquals(first.edit,second.edit);assertEquals(listOf("a","b","c","d"),first.edit.layers.objects.map{it.id});assertEquals(.36666667f,first.edit.layers.objects[1].x,.000001f);assertEquals(.6333333f,first.edit.layers.objects[2].x,.000001f)
    }
    @Test fun unknownDuplicateLockedHiddenAndTransparentSelectionsReject(){
        val base=edit(listOf(a,b.copy(locked=true),c.copy(visible=false),objectAt("d",.5f).copy(opacity=0f)))
        for(ids in listOf(listOf("a","missing"),listOf("a","a"),listOf("a","b"),listOf("a","c"),listOf("a","d")))reject{PhotoObjectArrangement.prepare(base,ids,PhotoObjectArrangement.Action.COLUMN)}
    }
    @Test fun alignmentNeedsTwoAndSpacingNeedsThreeUniqueObjects(){
        val base=edit(listOf(a,b,c));for(ids in listOf(emptyList(),listOf("a"),List(33){"item$it"}))reject{PhotoObjectArrangement.prepare(base,ids,PhotoObjectArrangement.Action.COLUMN)}
        reject{PhotoObjectArrangement.prepare(base,listOf("a","b"),PhotoObjectArrangement.Action.ACROSS)};reject{PhotoObjectArrangement.prepare(base,listOf("a","b"),PhotoObjectArrangement.Action.DOWN)}
    }
    @Test fun noOpPreservesModelIdentityAndPlansExposeReadOnlyLists(){
        val base=edit(listOf(a,a.copy(id="b")));val unchanged=PhotoObjectArrangement.prepare(base,listOf("a","b"),PhotoObjectArrangement.Action.COLUMN);assertFalse(unchanged.changed);assertSame(base,unchanged.edit)
        val plan=PhotoObjectArrangement.prepare(edit(listOf(a,c)),listOf("a","c"),PhotoObjectArrangement.Action.COLUMN)
        try{(plan.changes as MutableList).clear();fail("Mutable changes")}catch(_:UnsupportedOperationException){}
        try{(plan.selected as MutableList).clear();fail("Mutable selection")}catch(_:UnsupportedOperationException){}
    }
    @Test fun hiddenLockedUnselectedObjectsKeepIdentityAndStacking(){
        val objects=listOf(a,b.copy(locked=true),c,objectAt("hidden",.3f).copy(visible=false),objectAt("unselected",.7f));val base=edit(objects)
        val plan=PhotoObjectArrangement.prepare(base,listOf("a","c"),PhotoObjectArrangement.Action.COLUMN)
        for(index in listOf(1,3,4))assertSame(objects[index],plan.edit.layers.objects[index]);assertEquals(objects.map{it.id},plan.edit.layers.objects.map{it.id})
    }
    @Test fun maximumSelectionRemainsBoundedFiniteAndEvenlySpaced(){
        val objects=List(32){objectAt("item$it",it*it/961f,it/31f)};val base=edit(objects);val ids=objects.map{it.id}.reversed()
        val plan=PhotoObjectArrangement.prepare(base,ids,PhotoObjectArrangement.Action.ACROSS)
        for(index in 0..31){val value=plan.edit.layers.objects[index];assertEquals(index/31f,value.x,.000001f);assertTrue(value.x.isFinite()&&value.x in 0f..1f);assertEquals(objects[index].y,value.y)}
        assertSame(objects.first(),plan.edit.layers.objects.first());assertSame(objects.last(),plan.edit.layers.objects.last())
    }
    @Test fun arrangedObjectsRetainNativeProjectSerializationAndOriginalBinding(){
        val base=edit(listOf(a,b,c));val plan=PhotoObjectArrangement.prepare(base,listOf("a","b","c"),PhotoObjectArrangement.Action.COLUMN)
        val source="content://synthetic/photo";val fingerprint=PhotoProjectCodec.fingerprint(ByteArrayInputStream("synthetic".toByteArray()));val bytes=PhotoProjectCodec.encode(PhotoPresetNamespace.GUEST,source,fingerprint,plan.edit)
        assertEquals(plan.edit,PhotoProjectCodec.decode(bytes,PhotoPresetNamespace.GUEST,source,fingerprint));assertEquals(base.finish,plan.edit.finish)
    }
    @Test fun largeRotatedObjectsAreNotResizedClampedOrReordered(){
        val objects=listOf(a.copy(x=0f,width=1f,height=1f,rotation=-180f),b,c.copy(x=1f,rotation=180f));val base=edit(objects);val plan=PhotoObjectArrangement.prepare(base,listOf("a","b","c"),PhotoObjectArrangement.Action.ROW)
        for((old,next)in objects.zip(plan.edit.layers.objects))assertEquals(old.copy(y=.5f),next)
        assertEquals(base.copy(layers=plan.edit.layers),plan.edit)
    }
}
