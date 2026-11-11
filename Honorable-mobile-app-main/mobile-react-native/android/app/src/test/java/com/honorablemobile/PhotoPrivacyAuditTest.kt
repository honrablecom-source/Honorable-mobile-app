package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PhotoPrivacyAuditTest {
    @Test fun noFieldsIsAnEmptyPresenceResultNotASafetyVerdict() {
        assertEquals(emptyList<PhotoPrivacyAudit.Field>(), PhotoPrivacyAudit.inspect({ false }))
    }
    @Test fun eachWhitelistedFieldIsQueriedExactlyOnceInStableOrder() {
        val queried = mutableListOf<PhotoPrivacyAudit.Field>()
        val result = PhotoPrivacyAudit.inspect({ queried += it; true })
        assertEquals(PhotoPrivacyAudit.Field.entries.toList(), queried)
        assertEquals(queried, result); assertEquals(queried.size, queried.distinct().size)
    }
    @Test fun coordinatesAreNotRequiredToFlagOtherLocationFields() {
        for (field in PhotoPrivacyAudit.Field.entries.filter { it.group == PhotoPrivacyAudit.Group.LOCATION }) {
            val result = PhotoPrivacyAudit.inspect({ it == field })
            assertEquals(listOf(field), result); assertTrue(PhotoPrivacyAudit.locationFlagged(result))
        }
    }
    @Test fun timeOrFreeTextAloneDoesNotPretendLocationWasFound() {
        val result = PhotoPrivacyAudit.inspect({ it.group != PhotoPrivacyAudit.Group.LOCATION })
        assertFalse(PhotoPrivacyAudit.locationFlagged(result)); assertEquals(9, result.size)
    }
    @Test fun randomPresenceMatchesIndependentSetMembershipWithoutInventedFields() {
        val random = Random(144)
        repeat(1000) {
            val chosen = PhotoPrivacyAudit.Field.entries.filter { random.nextBoolean() }.toSet()
            val actual = PhotoPrivacyAudit.inspect({ it in chosen })
            assertEquals(chosen, actual.toSet()); assertEquals(chosen.size, actual.size)
            assertEquals(chosen.any { it.group == PhotoPrivacyAudit.Group.LOCATION }, PhotoPrivacyAudit.locationFlagged(actual))
        }
    }
    @Test fun cancellationStopsBeforeAnotherFieldReadAndNoPartialResultReturns() {
        var calls = 0
        try {
            PhotoPrivacyAudit.inspect({ calls++; true }) { if (calls == 3) error("Synthetic cancellation") }
            fail("Returned after cancellation")
        } catch (error: IllegalStateException) { assertEquals("Synthetic cancellation", error.message) }
        assertEquals(3, calls)
    }
    @Test fun fieldPredicateFailureIsNotMisreportedAsNoMetadata() {
        try { PhotoPrivacyAudit.inspect({ error("Synthetic parser failure") }); fail("Swallowed parser failure") }
        catch (error: IllegalStateException) { assertEquals("Synthetic parser failure", error.message) }
    }
    @Test fun boundedSelectionAcceptsOneThroughTwentyAndRejectsOtherCounts() {
        for (count in 1..20) PhotoPrivacyAudit.requireCount(count)
        for (count in listOf(Int.MIN_VALUE, -1, 0, 21, Int.MAX_VALUE)) {
            try { PhotoPrivacyAudit.requireCount(count); fail("Accepted count") } catch (_: IllegalArgumentException) { }
        }
    }
}
