package app.honorable.auth
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class SessionPolicyTest {
 @Test fun `recent verified cache permits offline recognition only before session expiry`(){assertTrue(SessionPolicy.canUseOffline(20000,1000,5000,true));assertFalse(SessionPolicy.canUseOffline(4000,1000,5000,true));assertFalse(SessionPolicy.canUseOffline(20000,1000,5000,false))}
 @Test fun `stale missing and future verification dates do not bypass login`(){assertFalse(SessionPolicy.canUseOffline(Long.MAX_VALUE,0,5000,true));assertFalse(SessionPolicy.canUseOffline(Long.MAX_VALUE,1000,86401000,true));assertFalse(SessionPolicy.canUseOffline(20000,10000,5000,true))}
 @Test fun `revocation and invalid credentials require login but outages do not`(){assertTrue(SessionPolicy.mustReauthenticate(401));assertTrue(SessionPolicy.mustReauthenticate(403));assertFalse(SessionPolicy.mustReauthenticate(503))}
}
