package com.honorablemobile

import app.honorable.auth.SecureAccountStore
import android.util.AtomicFile
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Actual storage reader and real AtomicFile with synthetic bytes; no Android keystore or credentials. */
class SecureAccountStoreReadTest {
    private fun store(file:File):SecureAccountStore {
        val field=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}
        val instance=(field.get(null)as Unsafe).allocateInstance(SecureAccountStore::class.java)as SecureAccountStore
        instance.javaClass.getDeclaredField("file").apply{isAccessible=true}.set(instance,AtomicFile(file))
        return instance
    }
    private fun folder(block:(File)->Unit){val dir=Files.createTempDirectory("synthetic-draft-read").toFile();try{block(dir)}finally{dir.deleteRecursively()}}
    @Test fun missingPreservedRecordIsAbsentWithoutCreatingAFile()=folder { dir ->
        val file=File(dir,"missing.enc");assertNull(store(file).readPreserving());assertFalse(file.exists())
    }
    @Test fun malformedOuterEnvelopeIsPreservedAcrossRepeatedReads()=folder { dir ->
        val file=File(dir,"draft.enc");val bytes="not a JSON envelope".toByteArray();file.writeBytes(bytes)
        repeat(2){try{store(file).readPreserving();fail("Unreadable file looked absent")}catch(_:IOException){};assertArrayEquals(bytes,file.readBytes())}
    }
    @Test fun oversizedFileIsBoundedAndPreserved()=folder { dir ->
        val file=File(dir,"large.enc");val bytes=ByteArray(1_048_577){65};file.writeBytes(bytes)
        try{store(file).readPreserving();fail("Oversized file looked absent")}catch(_:IOException){}
        assertArrayEquals(bytes,file.readBytes())
    }
    @Test fun legacyCredentialReadStillClearsInvalidData()=folder { dir ->
        val file=File(dir,"credential-fixture.enc");file.writeText("not JSON")
        assertNull(store(file).read());assertFalse(file.exists())
    }
}
