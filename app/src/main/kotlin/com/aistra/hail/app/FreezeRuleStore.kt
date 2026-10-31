package com.aistra.hail.app

import android.content.Context
import com.aistra.hail.enhanced.domain.FreezeRule
import com.aistra.hail.enhanced.domain.RuleAction
import com.aistra.hail.enhanced.domain.RuleEngine
import com.aistra.hail.enhanced.domain.RuleExecutionState
import com.aistra.hail.enhanced.domain.RuleTrigger
import org.json.JSONArray
import org.json.JSONObject

internal class FreezeRuleStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "hail_enhanced_rules", Context.MODE_PRIVATE
    )

    var isEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) {
            check(preferences.edit().putBoolean(KEY_ENABLED, value).commit())
        }

    @Synchronized
    fun getRules(): List<FreezeRule> = runCatching {
        val json = JSONArray(preferences.getString(KEY_RULES, "[]"))
        val rules = List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val packages = item.getJSONArray("packages")
            FreezeRule(
                id = item.getString("id"),
                name = item.getString("name"),
                enabled = item.optBoolean("enabled", true),
                trigger = RuleTrigger.valueOf(item.getString("trigger")),
                action = RuleAction.valueOf(item.getString("action")),
                packageNames = buildSet {
                    for (i in 0 until packages.length()) add(packages.getString(i))
                },
                cooldownMillis = item.optLong("cooldownMillis", 30_000L)
            )
        }
        RuleEngine.validate(rules)
    }.getOrDefault(emptyList())

    @Synchronized
    fun saveRules(rules: List<FreezeRule>) {
        RuleEngine.validate(rules)
        val json = JSONArray()
        rules.forEach { rule ->
            json.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("name", rule.name)
                    .put("enabled", rule.enabled)
                    .put("trigger", rule.trigger.name)
                    .put("action", rule.action.name)
                    .put("cooldownMillis", rule.cooldownMillis)
                    .put("packages", JSONArray(rule.packageNames.sorted()))
            )
        }
        check(preferences.edit().putString(KEY_RULES, json.toString()).commit()) {
            "Could not save automation rules"
        }
    }

    @Synchronized
    fun getExecutionState(): RuleExecutionState = runCatching {
        val json = JSONObject(preferences.getString(KEY_STATE, "{}"))
        val timesJson = json.optJSONObject("lastRunAtByRule") ?: JSONObject()
        val times = linkedMapOf<String, Long>()
        timesJson.keys().forEach { times[it] = timesJson.getLong(it) }
        val processedJson = json.optJSONArray("processedEventKeys") ?: JSONArray()
        val processed = buildSet {
            for (index in 0 until processedJson.length()) add(processedJson.getString(index))
        }
        RuleExecutionState(times, processed)
    }.getOrDefault(RuleExecutionState())

    @Synchronized
    fun saveExecutionState(state: RuleExecutionState) {
        val times = JSONObject()
        state.lastRunAtByRule.forEach { (id, time) -> times.put(id, time) }
        val processed = JSONArray()
        state.processedEventKeys.forEach(processed::put)
        val json = JSONObject()
            .put("lastRunAtByRule", times)
            .put("processedEventKeys", processed)
        check(preferences.edit().putString(KEY_STATE, json.toString()).commit())
    }

    @Synchronized
    fun appendAudit(message: String) {
        val entries = runCatching {
            val old = JSONArray(preferences.getString(KEY_AUDIT, "[]"))
            List(old.length()) { old.getString(it) }
        }.getOrDefault(emptyList())
        val next = JSONArray()
        (entries + (System.currentTimeMillis().toString() + ": " + message))
            .takeLast(MAX_AUDIT_ENTRIES).forEach(next::put)
        check(preferences.edit().putString(KEY_AUDIT, next.toString()).commit())
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_RULES = "rules"
        const val KEY_STATE = "execution_state"
        const val KEY_AUDIT = "audit"
        const val MAX_AUDIT_ENTRIES = 100
    }
}
