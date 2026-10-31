package com.aistra.hail.app

import android.content.Context
import androidx.preference.PreferenceManager
import com.aistra.hail.enhanced.domain.FreezeRule
import com.aistra.hail.enhanced.domain.RuleAction
import com.aistra.hail.enhanced.domain.RuleEngine
import com.aistra.hail.enhanced.domain.RuleExecutionState
import com.aistra.hail.enhanced.domain.RuleTrigger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class FreezeRuleStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val file = File(File(applicationContext.filesDir, "enhanced"), "rules.json")
    private val defaultPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

    var isEnabled: Boolean
        get() = defaultPreferences.getBoolean(HailData.RULE_ENGINE_ENABLED, false)
        set(value) {
            check(defaultPreferences.edit().putBoolean(HailData.RULE_ENGINE_ENABLED, value).commit())
        }

    @Synchronized
    fun getRules(): List<FreezeRule> = runCatching {
        val json = readDocument().optJSONArray("rules") ?: JSONArray()
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
        val document = readDocument().put("rules", json)
        writeDocument(document)
    }

    @Synchronized
    fun getExecutionState(): RuleExecutionState = runCatching {
        val json = readDocument().optJSONObject("executionState") ?: JSONObject()
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
        val document = readDocument().put(
            "executionState",
            JSONObject().put("lastRunAtByRule", times).put("processedEventKeys", processed)
        )
        writeDocument(document)
    }

    @Synchronized
    fun appendAudit(message: String) {
        val entries = readDocument().optJSONArray("audit") ?: JSONArray()
        val next = JSONArray()
        val old = List(entries.length()) { entries.getString(it) }
        (old + (System.currentTimeMillis().toString() + ": " + message))
            .takeLast(MAX_AUDIT_ENTRIES).forEach(next::put)
        writeDocument(readDocument().put("audit", next))
    }

    private fun readDocument(): JSONObject = runCatching {
        if (file.isFile) JSONObject(file.readText()) else JSONObject()
    }.getOrDefault(JSONObject())

    private fun writeDocument(document: JSONObject) {
        val parent = file.parentFile ?: throw IOException("Rule storage has no parent")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create rule storage")
        val temporary = File.createTempFile(".rules.", ".tmp", parent)
        try {
            FileOutputStream(temporary).use {
                it.write(document.toString().toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            if (file.exists() && !file.delete()) throw IOException("Could not replace rule storage")
            if (!temporary.renameTo(file)) throw IOException("Could not save rule storage")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private companion object {
        const val MAX_AUDIT_ENTRIES = 100
    }
}
