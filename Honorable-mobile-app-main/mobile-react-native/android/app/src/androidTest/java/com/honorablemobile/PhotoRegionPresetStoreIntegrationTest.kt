package com.honorablemobile

import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PhotoRegionPresetStoreIntegrationTest{
 private fun root()=File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,"region-recipes-test-${java.util.UUID.randomUUID()}")
 private fun recipe(name:String)=NamedPhotoRegions(name,PhotoRegions(listOf(PhotoRegion("local",PhotoRegionEffect.BLUR))))
 @Test fun shelvesAreIsolatedAndCancelledWritesPreserveExistingRecipes(){
  val directory=root();try{
   val guest=PhotoRegionPresetStore(directory,PhotoPresetNamespace.GUEST);val account=PhotoRegionPresetStore(directory,PhotoPresetNamespace.account("synthetic","https://example.invalid"))
   guest.add(recipe("Guest")){};assertEquals(1,guest.load().entries.size);assertTrue(account.load().entries.isEmpty())
   account.add(recipe("Account")){};assertEquals("Guest",guest.load().entries.single().name)
   var checks=0;assertTrue(runCatching{guest.add(recipe("Cancelled")){if(++checks==2)error("Session changed")}}.isFailure)
   assertEquals(listOf("Guest"),PhotoRegionPresetStore(directory,PhotoPresetNamespace.GUEST).load().entries.map{it.name})
   guest.remove("Guest"){};assertTrue(guest.load().entries.isEmpty());assertEquals(1,account.load().entries.size)
  }finally{directory.deleteRecursively()}
 }
 @Test fun damagedShelvesDoNotBecomeEmptyWritableCatalogs(){
  val directory=root();try{
   val store=PhotoRegionPresetStore(directory,PhotoPresetNamespace.GUEST);store.add(recipe("Keep")){}
   val file=File(directory,"${PhotoPresetNamespace.GUEST}.bin");file.writeBytes(byteArrayOf(1,2,3));val damaged=file.readBytes()
   assertTrue(runCatching{store.load()}.isFailure);assertTrue(runCatching{store.add(recipe("Overwrite")){}}.isFailure);assertArrayEquals(damaged,file.readBytes())
   assertTrue(runCatching{PhotoRegionPresetStore(directory,"../other")}.isFailure)
  }finally{directory.deleteRecursively()}
 }
}
