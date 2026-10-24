package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoCaptionImportPermissionsTest {
    private class Grants(var granted: Boolean = false) {
        var takes = 0
        var releases = 0
        fun <T> commit(apply: () -> T): T = VideoCaptionImportPermissions.commit(
            hasReadGrant = { granted },
            takeReadGrant = { takes++; granted = true },
            releaseReadGrant = { releases++; granted = false },
            applyCaptions = apply,
        )
    }

    @Test fun successfulImportPersistsAccessBeforeApplyingAndKeepsIt() {
        val grants = Grants()
        val project = VideoEditProject(5000)
        grants.commit {
            assertTrue(grants.granted)
            project.setCaptions(0,listOf(VideoCaption(0,1000,"Caption")),"content://subtitles/one")
        }
        assertEquals(1,grants.takes);assertEquals(0,grants.releases)
        assertEquals(setOf("content://subtitles/one"),project.captionSourceUris())
        assertTrue(project.undo());assertEquals(setOf("content://subtitles/one"),project.captionSourceUris())
        assertTrue(grants.granted)
    }

    @Test fun successfulImportReusesExistingAccess() {
        val grants = Grants(true)
        assertEquals("applied",grants.commit { "applied" })
        assertEquals(0,grants.takes);assertEquals(0,grants.releases);assertTrue(grants.granted)
    }

    @Test fun rejectedProjectEditReleasesOnlyNewAccessAndLeavesHistoryUnchanged() {
        val grants = Grants()
        val project = VideoEditProject(5000)
        val fullClip = VideoSegment(0,5000,captions=List(200) { VideoCaption(it*20L,it*20L+10,"x") })
        project.restore(List(5) { fullClip } + VideoSegment(0,5000))
        val before = project.segments
        assertTrue(runCatching {
            grants.commit { project.setCaptions(5,listOf(VideoCaption(0,1000,"Caption")),"content://subtitles/new") }
        }.isFailure)
        assertEquals(before,project.segments);assertFalse(project.canUndo)
        assertEquals(1,grants.takes);assertEquals(1,grants.releases);assertFalse(grants.granted)
    }

    @Test fun rejectedEditPreservesAccessOwnedBeforeThisImport() {
        val grants = Grants(true)
        val failure = IllegalArgumentException("Project limit")
        val result = runCatching { grants.commit<Unit> { throw failure } }
        assertSame(failure,result.exceptionOrNull())
        assertEquals(0,grants.takes);assertEquals(0,grants.releases);assertTrue(grants.granted)
    }

    @Test fun providerWithoutDurableReadAccessNeverAppliesCaptions() {
        var applied = false
        var releases = 0
        val result = runCatching {
            VideoCaptionImportPermissions.commit(
                hasReadGrant = { false },
                takeReadGrant = {},
                releaseReadGrant = { releases++ },
                applyCaptions = { applied = true },
            )
        }
        assertTrue(result.isFailure);assertFalse(applied);assertEquals(1,releases)
    }

    @Test fun acquisitionFailureDoesNotApplyOrReleaseAnUnacquiredGrant() {
        var applied = false
        var releases = 0
        val failure = SecurityException("Provider refused durable access")
        val result = runCatching {
            VideoCaptionImportPermissions.commit(
                hasReadGrant = { false },
                takeReadGrant = { throw failure },
                releaseReadGrant = { releases++ },
                applyCaptions = { applied = true },
            )
        }
        assertSame(failure,result.exceptionOrNull());assertFalse(applied);assertEquals(0,releases)
    }

    @Test fun cleanupFailureKeepsTheProjectFailureAsThePrimaryCause() {
        val failure = IllegalArgumentException("Project limit")
        val cleanup = SecurityException("Provider refused release")
        var granted = false
        val result = runCatching {
            VideoCaptionImportPermissions.commit(
                hasReadGrant = { granted },
                takeReadGrant = { granted = true },
                releaseReadGrant = { throw cleanup },
                applyCaptions = { throw failure },
            )
        }
        assertSame(failure,result.exceptionOrNull());assertArrayEquals(arrayOf(cleanup),failure.suppressed)
    }
}
