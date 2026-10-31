package com.aistra.hail.enhanced.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeOperationReportTest {
    private fun result(outcome: FreezeOutcome, verified: Boolean = false) =
        FreezeOperationResult("com.example.app", 0, FreezeAction.FREEZE, "test", outcome, verified)

    @Test fun emptyReportIsComplete() {
        val summary = FreezeOperationReport.summarize(emptyList())
        assertEquals(0, summary.total)
        assertFalse(summary.incomplete)
    }

    @Test fun failedAndSkippedAreNotCountedAsSuccessful() {
        val summary = FreezeOperationReport.summarize(
            listOf(result(FreezeOutcome.SUCCESS, true), result(FreezeOutcome.FAILED),
                result(FreezeOutcome.SKIPPED))
        )
        assertEquals(3, summary.total)
        assertEquals(1, summary.successful)
        assertEquals(1, summary.failed)
        assertEquals(1, summary.skipped)
        assertEquals(1, summary.verified)
        assertTrue(summary.incomplete)
    }

    @Test fun successfulButUnverifiedBatchIsIncomplete() {
        val summary = FreezeOperationReport.summarize(listOf(result(FreezeOutcome.SUCCESS)))
        assertEquals(1, summary.successful)
        assertEquals(0, summary.verified)
        assertTrue(summary.incomplete)
    }

    @Test fun allVerifiedSuccessIsComplete() {
        val summary = FreezeOperationReport.summarize(
            listOf(result(FreezeOutcome.SUCCESS, true), result(FreezeOutcome.SUCCESS, true))
        )
        assertEquals(2, summary.verified)
        assertFalse(summary.incomplete)
    }
}
