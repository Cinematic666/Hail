package com.aistra.hail.enhanced.domain

internal enum class RuleTrigger { SCREEN_OFF, CHARGING, NOT_CHARGING }
internal enum class RuleAction { FREEZE, UNFREEZE }
internal enum class RuleDecisionStatus { READY, SKIPPED }

internal data class FreezeRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: RuleTrigger,
    val action: RuleAction,
    val packageNames: Set<String>,
    val cooldownMillis: Long = 30_000L
)

internal data class RuleEvent(val id: String, val trigger: RuleTrigger)

internal data class RuleExecutionState(
    val lastRunAtByRule: Map<String, Long> = emptyMap(),
    val processedEventKeys: Set<String> = emptySet()
)

internal data class RuleDecision(
    val ruleId: String,
    val status: RuleDecisionStatus,
    val packages: Set<String>,
    val action: RuleAction,
    val reason: String? = null
)

internal data class RuleEvaluation(
    val decisions: List<RuleDecision>,
    val nextState: RuleExecutionState
)

internal object RuleEngine {
    private const val MAX_RULES = 100
    private const val MAX_PACKAGES_PER_RULE = 20_000
    private const val MAX_PROCESSED_EVENTS = 256
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

    fun validate(rules: List<FreezeRule>): List<FreezeRule> {
        require(rules.size <= MAX_RULES) { "Too many automation rules" }
        require(rules.map { it.id }.toSet().size == rules.size) { "Duplicate rule ID" }
        rules.forEach { rule ->
            require(rule.id.isNotBlank() && rule.id.length <= 128) { "Invalid rule ID" }
            require(rule.name.isNotBlank() && rule.name.length <= 64) { "Invalid rule name" }
            require(rule.cooldownMillis in 0L..86_400_000L) { "Invalid rule cooldown" }
            require(rule.packageNames.size <= MAX_PACKAGES_PER_RULE) { "Rule contains too many packages" }
            require(rule.packageNames.all { packagePattern.matches(it) }) { "Invalid package in rule" }
        }
        return rules
    }

    fun evaluate(
        rules: List<FreezeRule>,
        event: RuleEvent,
        nowEpochMs: Long,
        state: RuleExecutionState,
        protectedPackages: Set<String> = emptySet()
    ): RuleEvaluation {
        validate(rules)
        require(event.id.isNotBlank() && event.id.length <= 256) { "Invalid rule event ID" }
        val nextTimes = state.lastRunAtByRule.toMutableMap()
        val processed = state.processedEventKeys.toMutableSet()
        val decisions = rules.map { rule ->
            val eventKey = rule.id + ":" + event.id
            val reason = when {
                !rule.enabled -> "Rule is disabled"
                rule.trigger != event.trigger -> "Trigger does not match"
                eventKey in processed -> "Event was already processed"
                nowEpochMs < (state.lastRunAtByRule[rule.id] ?: Long.MIN_VALUE) -> "Clock moved backwards"
                nowEpochMs - (state.lastRunAtByRule[rule.id] ?: Long.MIN_VALUE) < rule.cooldownMillis ->
                    "Rule cooldown has not elapsed"
                else -> null
            }
            if (reason != null) {
                RuleDecision(rule.id, RuleDecisionStatus.SKIPPED, emptySet(), rule.action, reason)
            } else {
                val packages = rule.packageNames - protectedPackages
                if (packages.isEmpty()) {
                    processed += eventKey
                    RuleDecision(rule.id, RuleDecisionStatus.SKIPPED, emptySet(), rule.action, "No eligible packages")
                } else {
                    processed += eventKey
                    nextTimes[rule.id] = nowEpochMs
                    RuleDecision(rule.id, RuleDecisionStatus.READY, packages, rule.action)
                }
            }
        }
        val boundedProcessed = processed.toList().takeLast(MAX_PROCESSED_EVENTS).toSet()
        return RuleEvaluation(
            decisions,
            RuleExecutionState(nextTimes, boundedProcessed)
        )
    }
}
