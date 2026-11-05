package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

/** Host-only state regression: bypass Android constructors and stop preview at its null-bitmap guard.
 * Executes production change(), but establishes no UI, lifecycle, ownership or rendering acceptance. */
class PhotoEditorHistoryTest{
 private fun field(name:String)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}
 private data class Fixture(val activity:PhotoEditorActivity,val undo:MutableList<PhotoEdit>,val redo:MutableList<PhotoEdit>)
 private fun fixture(initial:PhotoEdit,undo:MutableList<PhotoEdit> = mutableListOf(),redo:MutableList<PhotoEdit> = mutableListOf()):Fixture{
  val type=Class.forName("sun.misc.Unsafe")
  val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
  val activity=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,PhotoEditorActivity::class.java) as PhotoEditorActivity
  for((name,value)in listOf("edit" to initial,"undo" to undo,"redo" to redo,"sliders" to mutableListOf<Any>(),"before" to true,"placingLight" to PhotoRadialLight(exposure=.5f),"placingOn" to initial))field(name).set(activity,value)
  return Fixture(activity,undo,redo)
 }
 private fun apply(f:Fixture,next:PhotoEdit){PhotoEditorActivity::class.java.getDeclaredMethod("change",PhotoEdit::class.java).apply{isAccessible=true}.invoke(f.activity,next)}
 private val original=PhotoEdit(layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Preserve"))))
 @Test fun identicalApplyPreservesBothHistoriesAndFinishesPlacementAndComparison(){
  val future=original.copy(exposure=1f);val f=fixture(original,redo=mutableListOf(future))
  repeat(20){apply(f,original.copy(layers=PhotoLayers(original.layers.objects.toList())))}
  assertTrue("Identical Apply must not create empty Undo steps",f.undo.isEmpty())
  assertEquals(listOf(future),f.redo);assertSame(original,field("edit").get(f.activity))
  assertNull(field("placingLight").get(f.activity));assertNull(field("placingOn").get(f.activity));assertEquals(false,field("before").get(f.activity))
 }
 @Test fun genuinelyChangedApplyAddsOneStepAndDiscardsAbandonedRedo(){
  val f=fixture(original,redo=mutableListOf(original.copy(exposure=1f)));val changed=original.copy(exposure=.5f)
  apply(f,changed)
  assertEquals(listOf(original),f.undo);assertTrue(f.redo.isEmpty());assertSame(changed,field("edit").get(f.activity))
  assertNull(field("placingLight").get(f.activity));assertEquals(false,field("before").get(f.activity))
 }
 @Test fun genuineChangesRetainFiftyStepCapAndNearestSnapshots(){
  val history=MutableList(50){PhotoEdit(brightness=it.toFloat())};val nearestOldest=history[1]
  val f=fixture(original,undo=history);apply(f,original.copy(exposure=.5f))
  assertEquals(50,f.undo.size);assertSame(nearestOldest,f.undo.first());assertSame(original,f.undo.last())
 }
 private fun navigate(f:Fixture,forward:Boolean){PhotoEditorActivity::class.java.getDeclaredMethod("navigateHistory",java.lang.Boolean.TYPE).apply{isAccessible=true}.invoke(f.activity,forward)}
 @Test fun undoAndRedoFinishComparisonAndPlacementWhileRestoringExactSnapshots(){
  val changed=original.copy(exposure=.5f);val f=fixture(changed,undo=mutableListOf(original))
  navigate(f,false)
  assertSame(original,field("edit").get(f.activity));assertEquals(listOf(changed),f.redo);assertTrue(f.undo.isEmpty())
  assertEquals(false,field("before").get(f.activity));assertNull(field("placingLight").get(f.activity));assertNull(field("placingOn").get(f.activity))
  field("before").set(f.activity,true);field("placingLight").set(f.activity,PhotoRadialLight());field("placingOn").set(f.activity,original)
  navigate(f,true)
  assertSame(changed,field("edit").get(f.activity));assertEquals(listOf(original),f.undo);assertTrue(f.redo.isEmpty())
  assertEquals(false,field("before").get(f.activity));assertNull(field("placingLight").get(f.activity));assertNull(field("placingOn").get(f.activity))
 }
 @Test fun emptyAndBusyHistoryNavigationDoNotChangeSnapshotOrTransientModes(){
  for(forward in listOf(false,true)){
   val empty=fixture(original);navigate(empty,forward)
   assertSame(original,field("edit").get(empty.activity));assertEquals(true,field("before").get(empty.activity));assertNotNull(field("placingLight").get(empty.activity))
   val earlier=original.copy(exposure=.25f);val later=original.copy(exposure=.75f)
   val busy=fixture(original,mutableListOf(earlier),mutableListOf(later));field("photoProjectBusy").set(busy.activity,true)
   navigate(busy,forward)
   assertSame(original,field("edit").get(busy.activity));assertEquals(listOf(earlier),busy.undo);assertEquals(listOf(later),busy.redo)
   assertEquals(true,field("before").get(busy.activity));assertNotNull(field("placingLight").get(busy.activity));assertSame(original,field("placingOn").get(busy.activity))
  }
 }
 @Test fun repeatedUndoRedoRoundTripKeepsEveryReachableStateInOrder(){
  val states=listOf(original,original.copy(exposure=.25f),original.copy(exposure=.5f),original.copy(exposure=.75f))
  val f=fixture(states.last(),undo=states.dropLast(1).toMutableList())
  for(index in 2 downTo 0){navigate(f,false);assertSame(states[index],field("edit").get(f.activity))}
  assertTrue(f.undo.isEmpty())
  for(index in 1..3){navigate(f,true);assertSame(states[index],field("edit").get(f.activity))}
  assertTrue(f.redo.isEmpty());assertEquals(states.dropLast(1),f.undo)
 }
 @Test fun acceptedMaximumHistoryRemainsRecoverableAfterEitherNavigationDirection(){
  val scope=PhotoSessionCodec.scope("history-owner","https://example.test","content://fixture/photo")
  val undo=List(50){PhotoEdit(brightness=it.toFloat())}
  val redo=List(50){PhotoEdit(exposure=it/25f-1f)}
  val saved=PhotoSessionSnapshot(scope,original,undo,redo,pendingExport=original)
  val restored=PhotoSessionCodec.decode(PhotoSessionCodec.encode(saved),scope)
  assertEquals(50,restored.undo.size);assertEquals(50,restored.redo.size)
  for(forward in listOf(false,true)){
   val f=fixture(restored.edit,restored.undo.toMutableList(),restored.redo.toMutableList())
   val expectedOldest=(if(forward)f.undo else f.redo)[1]
   val expectedCurrent=(if(forward)f.redo else f.undo).last()
   navigate(f,forward)
   val current=field("edit").get(f.activity) as PhotoEdit
   val next=PhotoSessionSnapshot(scope,current,f.undo.toList(),f.redo.toList(),pendingExport=restored.pendingExport)
   val bytes=PhotoSessionCodec.encodeForRecovery(next)
   assertEquals(next,PhotoSessionCodec.decode(bytes,scope))
   val destination=if(forward)f.undo else f.redo
   assertEquals(50,destination.size);assertSame(expectedOldest,destination.first());assertSame(restored.edit,destination.last())
   assertSame(expectedCurrent,current);assertEquals(original,next.pendingExport)
   assertEquals(49,(if(forward)f.redo else f.undo).size)
  }
 }
}
