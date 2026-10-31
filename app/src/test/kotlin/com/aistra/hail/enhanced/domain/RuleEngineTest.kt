package com.aistra.hail.enhanced.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private fun rule(id: String = "screen-off") = FreezeRule(
        id, "Screen off", true, RuleTrigger.SCREEN_OFF, RuleAction.FREEZE,
        setOf("com.example.app"), cooldownMillis = 10_000L
    )

    @Test fun evaluatesMatchingOptInRuleAndUpdatesState() {
        val evaluation = RuleEngine.evaluate(
            listOf(rule()), RuleEvent("event-1", RuleTrigger.SCREEN_OFF), 20_000L,
            RuleExecutionState()
        )
        assertEquals(RuleDecisionStatus.READY, evaluation.decisions.single().status)
        assertEquals(setOf("com.example.app"), evaluation.decisions.single().packages)
        assertEquals(20_000L, evaluation.nextState.lastRunAtByRule["screen-off"])
    }

    @Test fun enforcesCooldown() {
        val evaluation = RuleEngine.evaluate(
            listOf(rule()), RuleEvent("event-2", RuleTrigger.SCREEN_OFF), 25_000L,
            RuleExecutionState(lastRunAtByRule = mapOf("screen-off" to 20_000L))
        )
        assertEquals(RuleDecisionStatus.SKIPPED, evaluation.decisions.single().status)
    }

    @Test fun eventIsIdempotent() {
        val state = RuleExecutionState(processedEventKeys = setOf("screen-off:event-1"))
        val evaluation = RuleEngine.evaluate(
            listOf(rule()), RuleEvent("event-1", RuleTrigger.SCREEN_OFF), 30_000L, state
        )
        assertEquals(RuleDecisionStatus.SKIPPED, evaluation.decisions.single().status)
    }

    @Test fun protectedPackagesAreNeverIncluded() {
        val evaluation = RuleEngine.evaluate(
            listOf(rule()), RuleEvent("event-1", RuleTrigger.SCREEN_OFF), 20_000L,
            RuleExecutionState(), setOf("com.example.app")
        )
        assertEquals(RuleDecisionStatus.SKIPPED, evaluation.decisions.single().status)
        assertTrue(evaluation.decisions.single().packages.isEmpty())
    }
}
