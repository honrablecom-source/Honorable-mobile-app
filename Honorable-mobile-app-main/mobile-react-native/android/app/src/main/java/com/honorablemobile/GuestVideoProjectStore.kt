package com.honorablemobile

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException

/** One bounded saved guest project per root video, isolated from encrypted account drafts. */
internal class GuestVideoProjectStore(private val directory: File, private val source: String) {
    private val file = AtomicFile(File(directory, GuestVideoProjectCodec.storageName(source)))
    fun load(durationMs: Long): VideoDraft? = synchronized(lock) {
        val input = try { file.openRead() } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw missing
            return@synchronized null
        }
        input.use {
            val bytes = ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) {
                val remaining = GuestVideoProjectCodec.MAX_BYTES + 1 - bytes.size()
                require(remaining > 0) { "Saved guest project exceeds the local storage limit" }
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                bytes.write(buffer, 0, count)
                require(bytes.size() <= GuestVideoProjectCodec.MAX_BYTES)
            }
            GuestVideoProjectCodec.decode(bytes.toByteArray(), source, durationMs)
        }
    }
    fun save(draft: VideoDraft, requireCurrent: () -> Unit) = synchronized(lock) {
        val bytes = GuestVideoProjectCodec.encode(source, draft)
        requireCurrent()
        check(directory.isDirectory || directory.mkdirs()) { "Guest project storage is unavailable" }
        val names = directory.listFiles()?.mapNotNull { entry ->
            Regex("(guest-video-project-[0-9a-f]{64}\\.bin)(?:\\.(?:bak|new))?").matchEntire(entry.name)?.groupValues?.get(1)
        }?.toSet() ?: error("Guest project storage cannot be read")
        require(file.baseFile.name in names || names.size < MAX_PROJECTS) { "Up to 20 guest projects are saved. Reopen an older original and delete its saved project first." }
        val output = file.startWrite()
        try {
            output.write(bytes); requireCurrent(); file.finishWrite(output)
            // AtomicFile may log a failed sync/rename rather than throw. Never report a stale save as successful.
            check(load(draft.sourceDurationMs) == draft) { "Guest project could not be verified after saving" }
        }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }
    fun delete(requireCurrent: () -> Unit) = synchronized(lock) {
        requireCurrent(); file.delete()
        check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists() && !File(file.baseFile.path + ".new").exists()) { "Saved guest project could not be deleted" }
    }
    companion object {
        const val MAX_PROJECTS = 20
        private val lock = Any()
        fun deleteAll(directory: File, requireCurrent: () -> Unit) = synchronized(lock) {
            requireCurrent()
            if (!directory.exists()) return@synchronized
            val names = requireNotNull(directory.listFiles()) { "Guest project storage cannot be read" }.mapNotNull {
                Regex("(guest-video-project-[0-9a-f]{64}\\.bin)(?:\\.(?:bak|new))?").matchEntire(it.name)?.groupValues?.get(1)
            }.toSet()
            names.forEach { name -> requireCurrent(); AtomicFile(File(directory, name)).delete() }
            check(directory.listFiles()?.none { it.name.matches(Regex("guest-video-project-[0-9a-f]{64}\\.bin(?:\\.(?:bak|new))?")) } == true) { "Some guest saves could not be deleted" }
        }
    }
}
