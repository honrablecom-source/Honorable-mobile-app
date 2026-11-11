package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoTextReadGateTest {
    @Test fun onePendingReadRetainsOwnershipUntilItsOwnSettlement() {
        val gate = PhotoTextReadGate(); assertNull(gate.begin(1, false)); assertFalse(gate.busy)
        val first = requireNotNull(gate.begin(1, true)); assertTrue(gate.busy)
        assertNull(gate.begin(1, true)); assertNull(gate.begin(2, true))
        assertTrue(gate.settle(first)); assertFalse(gate.busy)
        assertTrue(gate.isCurrent(first, 1, true)) // Copy/redact dialog remains current after result notification.
        assertFalse(gate.settle(first)); assertFalse(gate.busy)
    }
    @Test fun pauseAndResumeEpochCannotReviveOldReadEvenWithSamePhotoAndOwner() {
        val gate = PhotoTextReadGate(); val first = requireNotNull(gate.begin(7, true))
        assertFalse(gate.isCurrent(first, 8, false)); assertFalse(gate.isCurrent(first, 9, true))
        assertTrue(gate.busy); assertNull(gate.begin(9, true))
        gate.settle(first); val second = requireNotNull(gate.begin(9, true))
        assertFalse(gate.isCurrent(first, 9, true)); assertTrue(gate.isCurrent(second, 9, true))
        assertFalse(gate.settle(first)); assertTrue(gate.busy)
    }
    @Test fun oldResultCallbacksNeverReleaseANewerReadOrReactivateAfterReplacement() {
        val gate = PhotoTextReadGate(); var previous: PhotoTextReadGate.Request? = null
        for (epoch in 1L..100L) {
            val current = requireNotNull(gate.begin(epoch, true)); assertTrue(gate.busy)
            previous?.let { old -> assertFalse(gate.settle(old)); assertTrue(gate.busy); assertFalse(gate.isCurrent(old, epoch, true)) }
            assertTrue(gate.isCurrent(current, epoch, true)); gate.settle(current); assertFalse(gate.busy)
            previous?.let { assertFalse(gate.isCurrent(it, epoch, true)) }; previous = current
        }
    }
}
