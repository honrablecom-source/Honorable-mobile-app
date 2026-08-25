package app.honorable

enum class Screen { ONBOARDING, PERMISSIONS, HOME, MEMORIES, AI_SEARCH, RESULTS, VIEWER, TERMS, TERMS_ANALYSIS, ACTIVITY, SETTINGS, PRIVACY, GOOGLE_SIGN_IN, PAYWALL, INDEXING, EMPTY, ERROR }
enum class Plan { FREE, PLUS }
enum class PurchasePeriod { MONTHLY, ANNUAL }
sealed interface SignInState { data object SignedOut : SignInState; data object ConfigurationRequired : SignInState; data class SignedIn(val displayName: String) : SignInState }
sealed interface PurchaseState { data object StoreConfigurationRequired : PurchaseState; data object Free : PurchaseState; data class Plus(val period: PurchasePeriod) : PurchaseState }

enum class EntitlementSource { STORE_VERIFIED, SERVER_VERIFIED, CACHED_VERIFIED }
data class PlusEntitlement(val active: Boolean, val source: EntitlementSource, val verifiedAtEpochMs: Long, val expiresAtEpochMs: Long?)

/**
 * Premium access must come from a verified store receipt/token or a bounded cache
 * of one. Arbitrary preferences and client-only integrity verdicts are not valid.
 */
interface EntitlementRepository {
    suspend fun current(): PlusEntitlement
    suspend fun refresh(): PlusEntitlement
    suspend fun restore(): PlusEntitlement
}

sealed interface AppIntegritySignal {
    data object NotRequested : AppIntegritySignal
    data object ServerVerificationRequired : AppIntegritySignal
    data class ServerVerified(val recognizedBuild: Boolean, val licensedInstall: Boolean) : AppIntegritySignal
}

/** Play Integrity tokens must be sent to a trusted backend for verification. */
interface AppIntegrityService { suspend fun signalForSensitiveServerAction(): AppIntegritySignal }
class UnconfiguredAppIntegrityService : AppIntegrityService {
    override suspend fun signalForSensitiveServerAction() = AppIntegritySignal.ServerVerificationRequired
}

/** UI-safe integration boundaries. Implementations must use Google OAuth/store SDK configuration supplied outside source control. */
interface AccountService { val state: SignInState; suspend fun signIn(): SignInState; suspend fun signOut() }
interface SubscriptionService { val state: PurchaseState; suspend fun purchase(period: PurchasePeriod): PurchaseState; suspend fun restore(): PurchaseState }

class UnconfiguredAccountService : AccountService {
    override val state = SignInState.ConfigurationRequired
    override suspend fun signIn() = state
    override suspend fun signOut() = Unit
}
class UnconfiguredSubscriptionService : SubscriptionService {
    override val state = PurchaseState.StoreConfigurationRequired
    override suspend fun purchase(period: PurchasePeriod) = state
    override suspend fun restore() = state
}
