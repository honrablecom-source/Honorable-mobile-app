package app.honorable

enum class Plan { FREE, PLUS, PRO, SUPER, ULTIMATE }

enum class HonorableFeature {
    PHOTO_SEARCH, OCR_SEARCH, VIDEO_SEARCH, EXACT_VIDEO_MOMENT, SEARCH_REFINEMENT,
    SAVED_SEARCHES, ADVANCED_FILTERS, DEEP_RERANKING, COMPARE, FIND_SIMILAR,
    FIND_OVER_TIME, MEMORY_CONNECTIONS, ASK_YOUR_LIBRARY, VISUAL_COMPARISON, FAMILY
}

data class PlanPolicy(
    val plan: Plan,
    val storageBytes: Long,
    val videoMinutesPerWindow: Int,
    val videoCountPerWindow: Int = 4,
    val features: Set<HonorableFeature>,
    val searchLimit: Int? = null,
    val familyMembersTotal: Int = 1
) {
    fun allows(feature: HonorableFeature) = feature in features
}

object EntitlementMatrix {
    private const val GIB = 1024L * 1024L * 1024L
    private val free = setOf(HonorableFeature.PHOTO_SEARCH, HonorableFeature.OCR_SEARCH)
    private val plus = free + setOf(HonorableFeature.VIDEO_SEARCH, HonorableFeature.ADVANCED_FILTERS)
    private val pro = plus + setOf(HonorableFeature.EXACT_VIDEO_MOMENT, HonorableFeature.SEARCH_REFINEMENT, HonorableFeature.SAVED_SEARCHES, HonorableFeature.DEEP_RERANKING)
    private val superPlan = pro + setOf(HonorableFeature.COMPARE, HonorableFeature.FIND_SIMILAR, HonorableFeature.FIND_OVER_TIME, HonorableFeature.MEMORY_CONNECTIONS, HonorableFeature.ASK_YOUR_LIBRARY, HonorableFeature.VISUAL_COMPARISON)
    val policies = mapOf(
        Plan.FREE to PlanPolicy(Plan.FREE, 15L * GIB, 5, features = free),
        Plan.PLUS to PlanPolicy(Plan.PLUS, 100L * GIB, 20, features = plus),
        Plan.PRO to PlanPolicy(Plan.PRO, 350L * GIB, 45, features = pro),
        Plan.SUPER to PlanPolicy(Plan.SUPER, 700L * GIB, 120, features = superPlan),
        Plan.ULTIMATE to PlanPolicy(Plan.ULTIMATE, 1024L * GIB, 600, features = superPlan + HonorableFeature.FAMILY, familyMembersTotal = 5)
    )
    fun policy(plan: Plan) = requireNotNull(policies[plan])
}

data class VideoQuotaWindow(
    val windowId: String,
    val tier: Plan,
    val videosConsumed: Int,
    val durationConsumedSeconds: Long
) {
    val policy get() = EntitlementMatrix.policy(tier)
    val durationLimitSeconds get() = policy.videoMinutesPerWindow * 60L
    val durationRemainingSeconds get() = (durationLimitSeconds - durationConsumedSeconds).coerceAtLeast(0)
    val videosRemaining get() = (policy.videoCountPerWindow - videosConsumed).coerceAtLeast(0)
    fun canConsume(durationSeconds: Long) = durationSeconds >= 0 && videosRemaining > 0 && durationSeconds <= durationRemainingSeconds
    fun consume(durationSeconds: Long) = require(canConsume(durationSeconds)) { "Video allowance exceeded" }.let { copy(videosConsumed = videosConsumed + 1, durationConsumedSeconds = durationConsumedSeconds + durationSeconds) }
}

data class TrustedEntitlementState(
    val tier: Plan,
    val verified: Boolean,
    val expirationEpochMs: Long? = null,
    val offlineGraceUntilEpochMs: Long? = null
) {
    /** Unverified clients always receive Free authority, regardless of displayed JS state. */
    fun effectivePolicy() = EntitlementMatrix.policy(if (verified) tier else Plan.FREE)
}
