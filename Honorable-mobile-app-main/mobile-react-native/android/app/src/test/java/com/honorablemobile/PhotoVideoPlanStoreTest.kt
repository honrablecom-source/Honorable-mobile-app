package com.honorablemobile

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PhotoVideoPlanStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private fun directory()=File(temp.root,"private-plans")
    private fun store()=PhotoVideoPlanStore(directory())
    private fun plan(name:String="Story",id:String=UUID.randomUUID().toString())=PhotoVideoPlan(id,name,PhotoVideoFormat.LANDSCAPE,8000,PhotoVideoSelection.of(listOf("content://local/photo"),mapOf("content://local/photo" to 1500L)))
    private fun rejects(action:()->Unit) { assertTrue(runCatching(action).isFailure) }
    @Test fun realFilesystemSaveListAndReopenAcrossStoreInstances() {
        assertTrue(store().list().isEmpty());val original=plan();val saved=store().save(original)
        val row=PhotoVideoPlanStore(directory()).list().single();assertTrue(row.readable);assertEquals("Story",row.name);assertEquals(saved.token,row.token)
        val restored=store().load(original.id);assertEquals(saved.token,restored.token)
        assertArrayEquals(PhotoVideoPlanCodec.encode(original),PhotoVideoPlanCodec.encode(restored.plan))
        assertEquals(listOf("${original.id}.bin"),directory().list()!!.toList())
    }
    @Test fun saveNeverOverwritesAnExistingNameOrIdentity() {
        val original=plan();val saved=store().save(original)
        rejects { store().save(plan("story")) };rejects { store().save(plan("Other",original.id)) }
        assertEquals(saved.token,store().load(original.id).token);assertEquals(1,store().list().size)
    }
    @Test fun twentyPlansBoundTheShelfWithoutReplacingAnyEarlierPlan() {
        val records=(1..20).map { store().save(plan("Plan $it")) }
        rejects { store().save(plan("Overflow")) };assertEquals(20,store().list().size)
        records.forEach { assertEquals(it.token,store().load(it.plan.id).token) }
    }
    @Test fun cancellationBeforeCommitRemovesOnlyItsOwnPendingFile() {
        val existing=store().save(plan("Existing"));val next=plan("New")
        rejects { store().save(next) { check(!File(directory(),".pending-${next.id}").exists()) { "Cancelled" } } }
        assertEquals(existing.token,store().load(existing.plan.id).token)
        assertFalse(File(directory(),"${next.id}.bin").exists());assertFalse(File(directory(),".pending-${next.id}").exists())
    }
    @Test fun inaccessibleRootAndOccupiedPendingFilesPreserveExistingBytes() {
        directory().writeText("not a directory");rejects { store().save(plan()) };assertEquals("not a directory",directory().readText())
        assertTrue(directory().delete());assertTrue(directory().mkdir());val next=plan()
        val pending=File(directory(),".pending-${next.id}").apply { writeText("prior interrupted save") }
        rejects { store().save(next) };assertEquals("prior interrupted save",pending.readText())
    }
    @Test fun corruptMetadataIsListedKeptAndOnlyExplicitExactDeleteRemovesIt() {
        val saved=store().save(plan());val file=File(directory(),"${saved.plan.id}.bin");val corrupt=file.readBytes().also { it[10]=(it[10].toInt() xor 1).toByte() };file.writeBytes(corrupt)
        val row=store().list().single();assertFalse(row.readable);assertNotNull(row.token)
        rejects { store().load(row.id) };assertArrayEquals(corrupt,file.readBytes())
        rejects { store().delete(row.id,saved.token) };assertTrue(file.exists())
        store().delete(row.id,row.token!!);assertFalse(file.exists())
    }
    @Test fun reviewedDeleteRejectsChangedRecordsAndCancellation() {
        val saved=store().save(plan());val target=File(directory(),"${saved.plan.id}.bin")
        rejects { store().delete(saved.plan.id,saved.token) { error("Paused") } };assertTrue(target.exists())
        val changed=plan("Changed",saved.plan.id);target.writeBytes(PhotoVideoPlanCodec.encode(changed))
        rejects { store().delete(saved.plan.id,saved.token) };assertEquals("Changed",store().load(saved.plan.id).plan.name)
        val current=store().load(saved.plan.id);store().delete(current.plan.id,current.token);assertTrue(store().list().isEmpty())
    }
    @Test fun oversizedMetadataFailsClosedWithoutUnboundedReadOrDeletion() {
        val saved=store().save(plan());val target=File(directory(),"${saved.plan.id}.bin");target.writeBytes(ByteArray(PhotoVideoPlanCodec.MAX_BYTES+1))
        val row=store().list().single();assertFalse(row.readable);assertNull(row.token)
        rejects { store().load(row.id) };rejects { store().delete(row.id,saved.token) };assertTrue(target.exists())
    }
    @Test fun traversalAndSymlinksNeverReadOrDeleteOtherFiles() {
        val outside=File(temp.root,"original-photo").apply { writeText("original untouched") }
        rejects { store().load("../original-photo") };rejects { store().delete("../original-photo","0".repeat(64)) }
        directory().mkdirs();val id=UUID.randomUUID().toString();java.nio.file.Files.createSymbolicLink(File(directory(),"$id.bin").toPath(),outside.toPath())
        rejects { store().load(id) };rejects { store().delete(id,"0".repeat(64)) };assertEquals("original untouched",outside.readText())
    }
    @Test fun concurrentStoreInstancesAdmitExactlyOneDuplicateName() {
        val start=CountDownLatch(1);val pool=Executors.newFixedThreadPool(4)
        try {
            val jobs=(1..4).map { pool.submit<Boolean> { start.await();runCatching { store().save(plan("Same name")) }.isSuccess } }
            start.countDown();assertEquals(1,jobs.count { it.get(10,TimeUnit.SECONDS) });assertEquals(1,store().list().size)
        } finally { pool.shutdownNow() }
    }
}
