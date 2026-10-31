package com.aistra.hail.app

import android.content.Context
import com.aistra.hail.enhanced.domain.FreezeProfile
import com.aistra.hail.enhanced.domain.FreezeProfileEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class FreezeProfileStore(context: Context) {
    private val file = File(File(context.applicationContext.filesDir, "enhanced"), "profiles.json")

    @Synchronized
    fun getAll(): List<FreezeProfile> = runCatching {
        val json = JSONArray(if (file.isFile) file.readText() else "[]")
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
        writeProfiles(profiles)
    }

    @Synchronized
    fun delete(id: String) {
        writeProfiles(getAll().filterNot { it.id == id })
    }

    private fun writeProfiles(profiles: List<FreezeProfile>) {
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
        writeAtomically(file, json.toString())
    }

    private fun writeAtomically(target: File, content: String) {
        val parent = target.parentFile ?: throw IOException("Profile storage has no parent")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create profile storage")
        val temporary = File.createTempFile(".profiles.", ".tmp", parent)
        try {
            FileOutputStream(temporary).use {
                it.write(content.toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            if (target.exists() && !target.delete()) throw IOException("Could not replace profile storage")
            if (!temporary.renameTo(target)) throw IOException("Could not save profile storage")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}
