package com.aistra.hail.enhanced.domain

/** Request intent is distinct from the state actually observed on Android. */
internal enum class FreezeAction { FREEZE, UNFREEZE }
internal enum class FreezeOutcome { SUCCESS, FAILED, SKIPPED }

/** A successful backend call is not necessarily a verified frozen state. */
internal data class FreezeOperationResult(
    val packageName: String,
    val userId: Int,
    val action: FreezeAction,
    val backendId: String,
    val outcome: FreezeOutcome,
    val stateVerified: Boolean,
    val reason: String? = null
)

internal data class FreezeBatchSummary(
    val total: Int,
    val successful: Int,
    val failed: Int,
    val skipped: Int,
    val verified: Int
) {
    val incomplete: Boolean get() = failed > 0 || skipped > 0 || verified < successful
}

internal object FreezeOperationReport {
    fun summarize(results: List<FreezeOperationResult>): FreezeBatchSummary =
        FreezeBatchSummary(
            total = results.size,
            successful = results.count { it.outcome == FreezeOutcome.SUCCESS },
            failed = results.count { it.outcome == FreezeOutcome.FAILED },
            skipped = results.count { it.outcome == FreezeOutcome.SKIPPED },
            verified = results.count {
                it.outcome == FreezeOutcome.SUCCESS && it.stateVerified
            }
        )
}
