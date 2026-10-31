package com.aistra.hail.app

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.aistra.hail.BuildConfig
import com.aistra.hail.HailApp.Companion.app
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal object FullBackupStorage {
    private const val TAGS_PATH = "v1/tags.json"
    private const val APPS_PATH = "v1/apps.json"

    fun createArchive(): ByteArray {
        HailData.saveTags()
        HailData.saveApps()

        val root = app.filesDir.canonicalFile
        val rootPrefix = root.path + File.separator
        val files = root.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val canonical = file.canonicalFile
                require(canonical.path.startsWith(rootPrefix)) { "App data contains an unsafe file path" }
                FullBackupFile(
                    path = canonical.relativeTo(root).path.replace(File.separatorChar, '/'),
                    bytes = canonical.readBytes()
                )
            }
            .toList()

        return FullBackupArchive.encode(
            files = files,
            preferences = PreferenceManager.getDefaultSharedPreferences(app).all,
            applicationId = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE
        )
    }

    fun restore(payload: FullBackupPayload) {
        val root = app.filesDir.canonicalFile
        val rootPrefix = root.path + File.separator
        val filesToRestore = payload.files.filterNot { it.path == TAGS_PATH || it.path == APPS_PATH }
        val previousFiles = linkedMapOf<String, ByteArray?>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(app)
        val previousPreferences = preferences.all
        val previousTagsAndApps = HailData.createTagBackup()

        try {
            filesToRestore.forEach { backupFile ->
                val target = File(root, backupFile.path).canonicalFile
                require(target.path.startsWith(rootPrefix)) { "Unsafe restore path" }
                previousFiles[backupFile.path] = target.takeIf { it.isFile }?.readBytes()
                writeAtomically(target, backupFile.bytes)
            }

            val editor = preferences.edit()
            payload.preferences.forEach { (key, value) -> editor.putTyped(key, value) }
            // Device-bound permissions and Keystore-backed biometric state cannot be restored.
            editor.putString(HailData.WORKING_MODE, HailData.MODE_DEFAULT)
            editor.putBoolean(HailData.BIOMETRIC_LOGIN, false)
            editor.putBoolean(HailData.AUTO_FREEZE_AFTER_LOCK, false)
            check(editor.commit()) { "Could not save restored preferences" }

            HailData.restoreFullBackup(payload.tagBackup)
        } catch (error: Exception) {
            previousFiles.forEach { (path, bytes) ->
                runCatching {
                    val target = File(root, path).canonicalFile
                    if (bytes == null) target.delete() else writeAtomically(target, bytes)
                }
            }
            rollbackPreferences(
                preferences,
                previousPreferences,
                payload.preferences.keys
            )
            runCatching { HailData.restoreFullBackup(previousTagsAndApps) }
            throw error
        }
    }

    private fun rollbackPreferences(
        preferences: SharedPreferences,
        previous: Map<String, *>,
        restoredKeys: Set<String>
    ) {
        val editor = preferences.edit()
        val affectedKeys = restoredKeys + setOf(
            HailData.WORKING_MODE,
            HailData.BIOMETRIC_LOGIN,
            HailData.AUTO_FREEZE_AFTER_LOCK
        )
        affectedKeys.forEach { key ->
            val value = previous[key]
            if (value == null) editor.remove(key) else editor.putTyped(key, value)
        }
        editor.commit()
    }

    private fun SharedPreferences.Editor.putTyped(key: String, value: Any): SharedPreferences.Editor =
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> this
        }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val parent = target.parentFile ?: throw IOException("Restore target has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create restore directory")
        val temporary = File(parent, ".${target.name}.restore.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IOException("Could not replace ${target.name}")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Could not write ${target.name}")
        }
    }
}
