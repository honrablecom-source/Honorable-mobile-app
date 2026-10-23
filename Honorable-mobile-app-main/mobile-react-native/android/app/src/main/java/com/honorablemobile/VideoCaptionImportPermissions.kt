package com.honorablemobile

/** Commit on the editor thread, after parsing and stale-result checks have passed. */
internal object VideoCaptionImportPermissions {
    fun <T> commit(
        hasReadGrant: () -> Boolean,
        takeReadGrant: () -> Unit,
        releaseReadGrant: () -> Unit,
        applyCaptions: () -> T,
    ): T {
        val existing = hasReadGrant()
        var acquired = false
        try {
            if (!existing) {
                takeReadGrant()
                acquired = true
            }
            require(hasReadGrant()) { "This document provider cannot keep subtitle access" }
            return applyCaptions()
        } catch (error: Throwable) {
            // Never revoke access that another project, saved draft or editor already owned.
            if (acquired) runCatching(releaseReadGrant).exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }
}
