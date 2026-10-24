package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class EditorAccessModeTest {
    private val instance = "11111111-1111-4111-8111-111111111111"
    private val source = "content://photos/one"

    @Test fun legacyLaunchKeepsAccountAuthorization() {
        val mode = EditorAccessMode.fromExtra(null)
        var calls = 0
        mode.authorizeAccount { calls++ }
        assertEquals(EditorAccessMode.ACCOUNT,mode);assertEquals(1,calls)
        assertTrue(mode.allowsOnDeviceAi);assertTrue(mode.allowsAccountDrafts)
    }

    @Test fun explicitGuestDoesNotReadOrAuthorizeAnAccount() {
        val mode = EditorAccessMode.fromExtra("GUEST_BASIC")
        mode.authorizeAccount { error("Guest must not authorize an account") }
        val scope = mode.lifecycleScope(source,instance) { error("Guest must not read an account") }
        assertNotNull(scope);assertTrue(mode.allowsOnDeviceAi);assertFalse(mode.allowsAccountDrafts)
    }

    @Test fun malformedModeCannotGrantGuestOrAccountAccess() {
        for(value in listOf("", "guest_basic", "FREE", "ACCOUNT ", "GUEST_BASIC\u0000")) {
            assertTrue(runCatching { EditorAccessMode.fromExtra(value) }.isFailure)
        }
    }

    @Test fun accountAuthorizationFailureStillPropagates() {
        val failure = SecurityException("Account authorization denied")
        assertSame(failure,runCatching { EditorAccessMode.ACCOUNT.authorizeAccount { throw failure } }.exceptionOrNull())
    }

    @Test fun accountLifecycleScopeKeepsItsOriginalOwner() {
        val accountScope = PhotoSessionCodec.scope("account-one","https://account.invalid",source)
        assertEquals(accountScope,EditorAccessMode.ACCOUNT.lifecycleScope(source,null) { accountScope })
        assertNull(EditorAccessMode.ACCOUNT.lifecycleScope(source,instance) { null })
    }

    @Test fun guestRecoveryCannotDecodeAccountStateOrAnotherGuestOrSource() {
        val guest = EditorAccessMode.GUEST_BASIC
        val scope = requireNotNull(guest.lifecycleScope(source,instance) { error("Account read") })
        val bytes = PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,PhotoEdit(rotation=90)))
        assertEquals(90,PhotoSessionCodec.decode(bytes,scope).edit.rotation)
        val otherScopes = listOf(
            PhotoSessionCodec.scope("account-one","https://account.invalid",source),
            requireNotNull(guest.lifecycleScope(source,"22222222-2222-4222-8222-222222222222") { error("Account read") }),
            requireNotNull(guest.lifecycleScope("content://photos/two",instance) { error("Account read") }),
        )
        for(other in otherScopes)assertTrue(runCatching { PhotoSessionCodec.decode(bytes,other) }.isFailure)
    }

    @Test fun guestRecoveryRequiresAnExplicitActivityInstance() {
        for(instance in listOf(null,"shared-guest","", "-".repeat(36))) {
            assertTrue(runCatching { EditorAccessMode.GUEST_BASIC.lifecycleScope(source,instance) { error("Account read") } }.isFailure)
        }
    }
}
