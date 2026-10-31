package com.aistra.hail.app

import android.content.Context
import com.aistra.hail.enhanced.domain.FreezeProfile
import com.aistra.hail.enhanced.domain.FreezeProfileEngine
import org.json.JSONArray
import org.json.JSONObject

internal class FreezeProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "hail_enhanced_profiles", Context.MODE_PRIVATE
    )

    @Synchronized
    fun getAll(): List<FreezeProfile> = runCatching {
        val json = JSONArray(preferences.getString(KEY_PROFILES, "[]"))
        val profiles = List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val statesJson = item.getJSONObject("packageStates")
            val states = linkedMapOf<String, Boolean>()
            statesJson.keys().forEach { packageName -> states[packageName] = statesJson.getBoolean(packageName) }
            FreezeProfile(
                id = item.getString("id"),
                name = item.getString("name"),
                packageStates = states,
                createdAtEpochMs = item.getLong("createdAtEpochMs"),
                updatedAtEpochMs = item.optLong("updatedAtEpochMs", item.getLong("createdAtEpochMs"))
            )
        }
        FreezeProfileEngine.validate(profiles)
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(profile: FreezeProfile) {
        val profiles = getAll().filterNot { it.id == profile.id } + profile
        FreezeProfileEngine.validate(profiles)
        val json = JSONArray()
        profiles.forEach { item ->
            val states = JSONObject()
            item.packageStates.forEach { (packageName, frozen) -> states.put(packageName, frozen) }
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("createdAtEpochMs", item.createdAtEpochMs)
                    .put("updatedAtEpochMs", item.updatedAtEpochMs)
                    .put("packageStates", states)
            )
        }
        check(preferences.edit().putString(KEY_PROFILES, json.toString()).commit()) {
            "Could not save freeze profiles"
        }
    }

    @Synchronized
    fun delete(id: String) {
        val json = JSONArray()
        getAll().filterNot { it.id == id }.forEach { item ->
            val states = JSONObject()
            item.packageStates.forEach { (packageName, frozen) -> states.put(packageName, frozen) }
            json.put(
                JSONObject().put("id", item.id).put("name", item.name)
                    .put("createdAtEpochMs", item.createdAtEpochMs)
                    .put("updatedAtEpochMs", item.updatedAtEpochMs)
                    .put("packageStates", states)
            )
        }
        check(preferences.edit().putString(KEY_PROFILES, json.toString()).commit())
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
    }
}
