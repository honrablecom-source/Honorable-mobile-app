package app.honorable.editor

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class PhotoComparisonTest {
 @get:Rule val temporary=TemporaryFolder()
 @Test fun sideBySideHasLabelledSeparatedPanelsInsideBoundedCanvas(){
  val p=PhotoComparisonPlan.create(1600,900,900,1600,PhotoComparisonLayout.SIDE_BY_SIDE)
  assertEquals(1928,p.width);assertEquals(1024,p.height)
  assertTrue(p.original.image.right<p.edited.image.left)
  for(panel in listOf(p.original,p.edited)){assertTrue(panel.labelBaseline<panel.image.top);assertTrue(panel.image.bottom<=p.height-24)}
 }
 @Test fun stackedHasSeparatedPanelsAndPreservesBothAspectRatios(){
  val p=PhotoComparisonPlan.create(1600,900,900,1600,PhotoComparisonLayout.STACKED)
  assertEquals(976,p.width);assertEquals(2024,p.height)
  assertTrue(p.original.image.bottom<p.edited.labelBaseline)
  assertEquals(1600.0/900,(p.original.image.width/p.original.image.height).toDouble(),.00001)
  assertEquals(900.0/1600,(p.edited.image.width/p.edited.image.height).toDouble(),.00001)
 }
 @Test fun extremeInputShapesFitWithoutCroppingOrEscapingCanvas(){
  for((w,h)in listOf(1 to 8192,8192 to 1,1 to 1,2400 to 2400,100 to 300))for(layout in PhotoComparisonLayout.entries){
   val p=PhotoComparisonPlan.create(w,h,h,w,layout)
   assertTrue(p.width<=2048&&p.height<=2048&&p.width.toLong()*p.height<=4_194_304)
   for(panel in listOf(p.original,p.edited)){val r=panel.image;assertTrue(r.left>=0&&r.top>=0&&r.right<=p.width&&r.bottom<=p.height);assertTrue(r.width>0&&r.height>0)}
   assertEquals(w.toDouble()/h,p.original.image.width.toDouble()/p.original.image.height,.002)
  }
 }
 @Test fun invalidInputBoundsAreRefused(){
  for((w,h)in listOf(0 to 1,1 to 0,-1 to 1,8193 to 1,8192 to 8192)){
   try{PhotoComparisonPlan.create(w,h,100,100,PhotoComparisonLayout.STACKED);fail("Invalid input accepted")}catch(expected:IllegalArgumentException){}
  }
 }
 @Test fun provenanceKeepsAllSourcesDeduplicatesAndRefusesUnsafeOrOverflowingLists(){
  val source="content://fixture/current";val inherited=listOf("content://fixture/old",source)
  assertEquals(inherited,PhotoComparisonFiles.originals(source,inherited))
  assertEquals(inherited,PhotoComparisonFiles.originals(source,listOf(inherited.first())))
  for(bad in listOf("file:///tmp/photo","content://fixture/a#fragment","content://fixture/a\n"))try{PhotoComparisonFiles.originals(bad,emptyList());fail("Unsafe URI accepted")}catch(expected:IllegalArgumentException){}
  try{PhotoComparisonFiles.originals(source,List(16){"content://fixture/$it"});fail("Provenance silently dropped")}catch(expected:IllegalArgumentException){}
 }
 @Test fun privateWritesAreUniqueAndLeaveExistingFilesAlone(){
  val cache=temporary.newFolder();val original=File(cache,"original.png").apply{writeText("original fixture")}
  val a=PhotoComparisonFiles.write(cache,{}){it.writeBytes(byteArrayOf(1,2,3))}
  val b=PhotoComparisonFiles.write(cache,{}){it.writeBytes(byteArrayOf(4,5,6))}
  assertNotEquals(a,b);assertEquals(File(cache,"created-images").canonicalFile,a.parentFile.canonicalFile)
  assertArrayEquals(byteArrayOf(1,2,3),a.readBytes());assertEquals("original fixture",original.readText())
 }
 @Test fun encodeFailureCleansOnlyItsNewPartialFile(){
  val cache=temporary.newFolder();val directory=File(cache,"created-images").apply{mkdirs()};val existing=File(directory,"keep.png").apply{writeText("keep")}
  try{PhotoComparisonFiles.write(cache,{}){it.writeText("partial");error("synthetic encode failure")};fail("Failure swallowed")}catch(expected:IllegalStateException){}
  assertEquals(listOf("keep.png"),directory.listFiles()!!.map{it.name});assertEquals("keep",existing.readText())
 }
 @Test fun emptyAndOverLimitOutputsAreRemoved(){
  for(length in listOf(0L,PhotoComparisonFiles.MAX_BYTES+1)){
   val cache=temporary.newFolder()
   try{PhotoComparisonFiles.write(cache,{}){RandomAccessFile(it,"rw").use{file->file.setLength(length)}};fail("Invalid output accepted")}catch(expected:IllegalArgumentException){}
   assertTrue(File(cache,"created-images").listFiles()!!.isEmpty())
  }
 }
 @Test fun sourceSessionCancellationBeforeOrAfterEncodingPublishesNothing(){
  val before=temporary.newFolder();try{PhotoComparisonFiles.write(before,{error("cancelled")}){fail("Encoded after cancellation")};fail("Cancellation ignored")}catch(expected:IllegalStateException){}
  assertFalse(File(before,"created-images").exists())
  val after=temporary.newFolder();var active=true
  try{PhotoComparisonFiles.write(after,{check(active)}){it.writeText("synthetic PNG bytes");active=false};fail("Late cancellation ignored")}catch(expected:IllegalStateException){}
  assertTrue(File(after,"created-images").listFiles()!!.isEmpty())
 }
 @Test fun cacheSymlinkAndFileCollisionNeverReachEncoder(){
  val cache=temporary.newFolder();val outside=temporary.newFolder();Files.createSymbolicLink(File(cache,"created-images").toPath(),outside.toPath())
  try{PhotoComparisonFiles.write(cache,{}){fail("Followed cache symlink")};fail("Symlink accepted")}catch(expected:IllegalStateException){}
  assertTrue(outside.listFiles()!!.isEmpty())
  val blocked=temporary.newFolder();val blocker=File(blocked,"created-images").apply{writeText("keep")}
  try{PhotoComparisonFiles.write(blocked,{}){fail("Encoded into file collision")};fail("File collision accepted")}catch(expected:IllegalStateException){}
  assertEquals("keep",blocker.readText())
 }
}
