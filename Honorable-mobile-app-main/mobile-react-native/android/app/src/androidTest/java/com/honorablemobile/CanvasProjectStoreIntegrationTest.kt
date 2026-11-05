package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CanvasProjectStoreIntegrationTest {
 private fun png():ByteArray {val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.TRANSPARENT)};return ByteArrayOutputStream().use{out->check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));bitmap.recycle();out.toByteArray()}}
 private fun directory()=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"canvas-projects/.test-${java.util.UUID.randomUUID()}")
 private fun edit()=PhotoEdit(layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Persistent text"),PhotoObject("shape",PhotoObjectType.RECTANGLE))))
 @Test fun sourceAndEditableStateSurviveStoreRecreationAndProviderAccess(){
  val root=directory();val original=png()
  try {val saved=CanvasProjectStore(root).create("Poster",ByteArrayInputStream(original),edit()){}
   assertArrayEquals(original,CanvasProjectStore(root).sourceFile(saved.id).readBytes())
   assertEquals(saved,CanvasProjectStore(root).load(saved.id))
   val changed=CanvasProjectStore(root).update(saved.id,"Renamed",saved.edit.copy(exposure=.5f)){}
   assertEquals(changed,CanvasProjectStore(root).load(saved.id));assertArrayEquals(original,CanvasProjectStore(root).sourceFile(saved.id).readBytes())
   val context=InstrumentationRegistry.getInstrumentation().targetContext
   val uri=FileProvider.getUriForFile(context,context.packageName+".creation",CanvasProjectStore(root).sourceFile(saved.id))
   assertEquals("image/png",context.contentResolver.getType(uri));context.contentResolver.openInputStream(uri).use{assertArrayEquals(original,requireNotNull(it).readBytes())}
  }finally{root.deleteRecursively()}
 }
 @Test fun interruptedCreationRemovesOnlyItsStagingAndSourceDamageDoesNotResetProjects(){
  val root=directory();val bytes=png()
  try {val store=CanvasProjectStore(root);var checks=0
   assertTrue(runCatching{store.create("Cancelled",ByteArrayInputStream(bytes),edit()){if(++checks==2)error("Editor closed")}}.isFailure)
   assertTrue(store.list().isEmpty());assertTrue(root.listFiles()?.none{it.name.startsWith(".pending-")}==true)
   val saved=store.create("Keep",ByteArrayInputStream(bytes),edit()){}
   store.sourceFile(saved.id).appendBytes(byteArrayOf(1))
   assertTrue(runCatching{store.load(saved.id)}.isFailure);assertEquals(1,store.list().size)
   assertTrue(runCatching{store.sourceFile("../outside")}.isFailure)
  }finally{root.deleteRecursively()}
 }
 @Test fun duplicateNamesAndCancelledUpdatesPreservePriorSourceAndUnrelatedFiles(){
  val root=directory();val bytes=png()
  try {val store=CanvasProjectStore(root);val saved=store.create("Original",ByteArrayInputStream(bytes),edit()){}
   assertTrue(runCatching{store.create("original",ByteArrayInputStream(bytes),edit()){}}.isFailure)
   assertTrue(runCatching{store.update(saved.id,"Changed",PhotoEdit()){error("Editor closed")}}.isFailure)
   assertEquals(saved,store.load(saved.id));assertArrayEquals(bytes,store.sourceFile(saved.id).readBytes())
   val unrelated=File(root,"unrelated.bin").apply{writeText("keep")};store.delete(saved.id){}
   assertEquals("keep",unrelated.readText());assertTrue(store.list().isEmpty())
  }finally{root.deleteRecursively()}
 }
 @Test fun importedOriginalProtectionSurvivesStoreReloadAndRefusesAliasedExport(){
  val root=directory();check(root.mkdirs());val context=InstrumentationRegistry.getInstrumentation().targetContext
  val original=File(root,"imported-original.png");val alias=File(root,"alias-output.png")
  val bytes=png();original.writeBytes(bytes)
  val originalUri=FileProvider.getUriForFile(context,context.packageName+".creation",original)
  try{
   val store=CanvasProjectStore(root)
   val saved=store.create("Derived image",ByteArrayInputStream(bytes),edit(),listOf(originalUri.toString())){}
   val loaded=CanvasProjectStore(root).load(saved.id)
   assertEquals(listOf(originalUri.toString()),loaded.protectedOriginals)
   val changed=store.update(saved.id,"Renamed",loaded.edit.copy(exposure=.25f)){}
   assertEquals(loaded.protectedOriginals,changed.protectedOriginals)
   android.system.Os.link(original.path,alias.path)
   val destination=FileProvider.getUriForFile(context,context.packageName+".creation",alias)
   val source=FileProvider.getUriForFile(context,context.packageName+".creation",store.sourceFile(saved.id))
   val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
   try{assertTrue(runCatching{SafePhotoCopy.save(context,source,destination,bitmap,protectedOriginals=changed.protectedOriginals.map(android.net.Uri::parse))}.isFailure)}finally{bitmap.recycle()}
   assertArrayEquals(bytes,original.readBytes());assertArrayEquals(bytes,alias.readBytes());assertArrayEquals(bytes,store.sourceFile(saved.id).readBytes())
  }finally{root.deleteRecursively()}
 }
 @Test fun provenanceUpdatesAreMonotonicBoundedAndInterruptedWritesPreserveMetadata(){
  val root=directory();val bytes=png()
  try{
   val store=CanvasProjectStore(root)
   val saved=store.create("Protected",ByteArrayInputStream(bytes),edit(),listOf("content://fixture/first","content://fixture/second")){}
   val added=store.update(saved.id,"Protected update",saved.edit,listOf("content://fixture/second","content://fixture/third")){}
   assertEquals(listOf("content://fixture/first","content://fixture/second","content://fixture/third"),added.protectedOriginals)
   assertEquals(added.protectedOriginals,store.update(saved.id,added.name,added.edit,emptyList()){}.protectedOriginals)
   val file=File(store.sourceFile(saved.id).parentFile,"project.bin");val before=file.readBytes()
   assertTrue(runCatching{store.update(saved.id,"Overflow",saved.edit,List(16){"content://fixture/new-$it"}){}}.isFailure)
   assertTrue(runCatching{store.update(saved.id,"Interrupted",PhotoEdit()){
    check(!(File(file.path+".new").length()>0||File(file.path+".bak").exists())){"Editor left during atomic write"}
   }}.isFailure)
   assertArrayEquals(before,file.readBytes());assertEquals(added,store.load(saved.id));assertArrayEquals(bytes,store.sourceFile(saved.id).readBytes())
  }finally{root.deleteRecursively()}
 }
}
