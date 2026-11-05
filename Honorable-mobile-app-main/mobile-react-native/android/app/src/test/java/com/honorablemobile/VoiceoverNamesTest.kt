package com.honorablemobile

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VoiceoverNamesTest {
    private fun saved(files:VoiceoverFiles):File=files.createPending().also{it.writeBytes(byteArrayOf(1,2,3))}.let(files::promote)
    @Test fun renameKeepsStableAudioIdentityAndBytesAcrossReplacement(){
        val root=Files.createTempDirectory("voice-name").toFile()
        try{
            val files=VoiceoverFiles(root,"guest-basic");val audio=saved(files);val id=audio.name.removeSuffix(".m4a")
            assertNull(files.readName(id));files.renameSaved(id,"  Opening narration  "){}
            assertEquals("Opening narration",files.readName(id));files.renameSaved(id,"Final narration"){}
            assertEquals("Final narration",files.readName(id));assertEquals(listOf(audio),files.saved())
            assertArrayEquals(byteArrayOf(1,2,3),audio.readBytes());assertEquals(audio,files.savedFile(id))
        }finally{root.deleteRecursively()}
    }
    @Test fun ownerCancellationAtCommitKeepsExistingLabelAndOtherAudio(){
        val root=Files.createTempDirectory("voice-name-cancel").toFile()
        try{
            val files=VoiceoverFiles(root,"guest-basic");val audio=saved(files);val id=audio.name.removeSuffix(".m4a")
            files.renameSaved(id,"Keep"){};val other=saved(files);var calls=0
            assertTrue(runCatching{files.renameSaved(id,"Cancelled"){check(++calls<2)}}.isFailure)
            assertEquals("Keep",files.readName(id));assertTrue(other.isFile);assertTrue(audio.isFile)
            assertFalse(audio.parentFile.listFiles()!!.any{it.name.startsWith(".voiceover-label-")})
        }finally{root.deleteRecursively()}
    }
    @Test fun unicodeBoundsInvalidLabelsAndAbsentAudioFailWithoutMutating(){
        assertEquals("😀".repeat(80),VoiceoverFiles.checkedName("😀".repeat(80)))
        for(value in listOf(" ","x".repeat(81),"hello\nthere","bad\u0000name","\uD800"))assertTrue(runCatching{VoiceoverFiles.checkedName(value)}.isFailure)
        val root=Files.createTempDirectory("voice-name-invalid").toFile()
        try{
            val files=VoiceoverFiles(root,"guest-basic");val missing="12345678-1234-1234-1234-123456789abc"
            assertTrue(runCatching{files.renameSaved(missing,"Missing") {}}.isFailure)
            assertNull(files.readName(missing));assertTrue(files.saved().isEmpty())
        }finally{root.deleteRecursively()}
    }
    @Test fun malformedLabelFallsBackAndDeletingTakeCleansOnlyItsLabel(){
        val root=Files.createTempDirectory("voice-name-damage").toFile()
        try{
            val files=VoiceoverFiles(root,"guest-basic");val audio=saved(files);val id=audio.name.removeSuffix(".m4a")
            val label=File(audio.parentFile,"$id.label").apply{writeBytes(byteArrayOf(0xC3.toByte()))}
            assertNull(files.readName(id));assertArrayEquals(byteArrayOf(0xC3.toByte()),label.readBytes())
            files.renameSaved(id,"Recovered label"){}
            val other=saved(files);val otherId=other.name.removeSuffix(".m4a");files.renameSaved(otherId,"Keep other"){}
            files.deleteSaved(id);assertFalse(audio.exists());assertFalse(label.exists())
            assertTrue(other.isFile);assertEquals("Keep other",files.readName(otherId))
        }finally{root.deleteRecursively()}
    }
    @Test fun ownerNamespacesAndSymlinkLabelCannotCrossOrModifyOutsideFiles(){
        val root=Files.createTempDirectory("voice-name-owner").toFile();val outside=Files.createTempFile("voice-name-outside",".txt").toFile()
        try{
            val guest=VoiceoverFiles(root,"guest-basic");val account=VoiceoverFiles(root,"account-"+"a".repeat(64))
            val audio=saved(guest);val id=audio.name.removeSuffix(".m4a");guest.renameSaved(id,"Guest name"){}
            assertNull(account.readName(id));assertTrue(runCatching{account.renameSaved(id,"Not owned") {}}.isFailure)
            val label=File(audio.parentFile,"$id.label");assertTrue(label.delete());outside.writeText("outside")
            Files.createSymbolicLink(label.toPath(),outside.toPath())
            assertNull(guest.readName(id));assertTrue(runCatching{guest.renameSaved(id,"Do not overwrite") {}}.isFailure)
            assertEquals("outside",outside.readText());label.delete()
        }finally{root.deleteRecursively();outside.delete()}
    }
}
