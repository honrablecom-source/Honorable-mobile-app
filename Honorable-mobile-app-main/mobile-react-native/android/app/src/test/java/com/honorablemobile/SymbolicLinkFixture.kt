package com.honorablemobile

import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assume.assumeNoException

internal fun createSymbolicLinkFixture(link: Path, target: Path) {
    try {
        Files.createSymbolicLink(link, target)
    } catch (error: FileSystemException) {
        val windows = System.getProperty("os.name").startsWith("Windows")
        val missingPrivilege = error.reason?.contains("A required privilege is not held", ignoreCase = true) == true
        if (!windows || !missingPrivilege) throw error
        assumeNoException("This Windows account cannot create a symbolic-link fixture", error)
    }
}
