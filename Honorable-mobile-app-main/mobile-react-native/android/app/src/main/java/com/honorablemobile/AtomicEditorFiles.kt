package com.honorablemobile

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Promote a synced sibling without removing the old file before the replacement is ready. */
internal fun replaceEditorFile(pending: File, target: File, failureMessage: String) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Os.rename(pending.absolutePath, target.absolutePath)
        }
    } catch (error: IOException) {
        throw IllegalStateException(failureMessage, error)
    } catch (error: ErrnoException) {
        throw IllegalStateException(failureMessage, error)
    }
}
