package app.honorable.testlab

import app.honorable.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EntitlementMatrixTest {
    @Test fun `canonical storage and video allowances are exact`() {
        val gib=1024L*1024L*1024L
        assertEquals(listOf(15L,100L,350L,700L,1024L),Plan.entries.map{EntitlementMatrix.policy(it).storageBytes/gib})
        assertEquals(listOf(5,20,45,120,600),Plan.entries.map{EntitlementMatrix.policy(it).videoMinutesPerWindow})
        assertTrue(Plan.entries.all{EntitlementMatrix.policy(it).videoCountPerWindow==4})
    }
    @Test fun `plans inherit features and premium remains verified`() {
        val ordered=Plan.entries.map{EntitlementMatrix.policy(it).features}
        ordered.zipWithNext().forEach{(lower,higher)->assertTrue(higher.containsAll(lower))}
        assertFalse(EntitlementMatrix.policy(Plan.PLUS).allows(HonorableFeature.EXACT_VIDEO_MOMENT))
        assertTrue(EntitlementMatrix.policy(Plan.PRO).allows(HonorableFeature.EXACT_VIDEO_MOMENT))
        assertEquals(Plan.FREE,TrustedEntitlementState(Plan.ULTIMATE,false).effectivePolicy().plan)
    }
    @Test fun `video window tracks videos and duration without implicit reset`() {
        val start=VideoQuotaWindow("server-window-1",Plan.PLUS,0,0)
        val used=start.consume(600).consume(599)
        assertEquals(2,used.videosConsumed);assertEquals(1,used.durationRemainingSeconds)
        assertFalse(used.canConsume(2));assertEquals("server-window-1",used.windowId)
    }
}
