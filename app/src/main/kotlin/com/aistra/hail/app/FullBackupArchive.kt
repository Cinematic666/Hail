package com.aistra.hail.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class FullBackupFile(val path: String, val bytes: ByteArray)

internal data class FullBackupPayload(
    val files: List<FullBackupFile>,
    val preferences: Map<String, Any>,
    val tagBackup: TagBackup,
    val sourceApplicationId: String,
    val sourceVersionName: String
)

internal object FullBackupArchive {
    private const val FORMAT = "com.aistra.hail.full-backup"
    private const val SCHEMA_VERSION = 1
    private const val MAX_ENTRIES = 256
    private const val MAX_FILE_BYTES = 8 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 40 * 1024 * 1024
    private const val MAX_PREFERENCES = 512
    private const val MAX_KEY_LENGTH = 256
    private const val MAX_STRING_LENGTH = 8192
    private const val MAX_SET_ITEMS = 256

    // These values are tied to Android permissions or device-local cryptographic material.
    // They must be configured again after restore instead of being copied from a backup.
    private val excludedPreferenceKeys = setOf(
        "working_mode",
        "biometric_login",
        "auto_freeze_after_lock"
    )
    private val sensitiveKeyFragments = listOf("password", "token", "secret", "credential")

    fun encode(
        files: List<FullBackupFile>,
        preferences: Map<String, *>,
        applicationId: String,
        versionName: String,
        versionCode: Int
    ): ByteArray {
        val normalizedFiles = validateFiles(files)
        val tagBackup = decodeTagBackup(normalizedFiles)
        val safePreferences = normalizePreferences(preferences)

        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("applicationId", applicationId)
            .put("versionName", versionName)
            .put("versionCode", versionCode)
            .put("createdAtEpochMs", System.currentTimeMillis())
            .put("fileCount", normalizedFiles.size)
            .put("preferenceCount", safePreferences.size)

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeEntry("manifest.json", manifest.toString().toByteArray(StandardCharsets.UTF_8))
            zip.writeEntry(
                "preferences.json",
                encodePreferences(safePreferences).toString().toByteArray(StandardCharsets.UTF_8)
            )
            normalizedFiles.sortedBy { it.path }.forEach { file ->
                zip.writeEntry("files/${file.path}", file.bytes)
            }
        }
        return output.toByteArray()
    }

    fun decode(input: InputStream): FullBackupPayload {
        val files = linkedMapOf<String, ByteArray>()
        var manifestText: String? = null
        var preferencesText: String? = null
        var totalBytes = 0
        var entryCount = 0

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRIES) { "Backup contains too many files" }

                if (!entry.isDirectory) {
                    val bytes = zip.readBounded(MAX_FILE_BYTES)
                    totalBytes += bytes.size
                    require(totalBytes <= MAX_TOTAL_BYTES) { "Backup is too large" }

                    when {
                        entry.name == "manifest.json" -> {
                            require(manifestText == null) { "Backup contains duplicate manifest.json" }
                            manifestText = String(bytes, StandardCharsets.UTF_8)
                        }
                        entry.name == "preferences.json" -> {
                            require(preferencesText == null) { "Backup contains duplicate preferences.json" }
                            preferencesText = String(bytes, StandardCharsets.UTF_8)
                        }
                        entry.name.startsWith("files/") -> {
                            val path = entry.name.removePrefix("files/")
                            validateRelativePath(path)
                            require(!files.containsKey(path)) { "Backup contains duplicate file: $path" }
                            files[path] = bytes
                        }
                        else -> throw IllegalArgumentException("Unsupported backup entry: ${entry.name}")
                    }
                }
                zip.closeEntry()
            }
        }

        val manifest = JSONObject(manifestText ?: throw IllegalArgumentException("manifest.json is missing"))
        validateManifest(manifest)
        val preferenceJson = JSONObject(
            preferencesText ?: throw IllegalArgumentException("preferences.json is missing")
        )
        val decodedPreferences = decodePreferences(preferenceJson)
        val normalizedFiles = validateFiles(files.map { FullBackupFile(it.key, it.value) })
        val tagBackup = decodeTagBackup(normalizedFiles)

        return FullBackupPayload(
            files = normalizedFiles,
            preferences = decodedPreferences,
            tagBackup = tagBackup,
            sourceApplicationId = manifest.optString("applicationId", "unknown"),
            sourceVersionName = manifest.optString("versionName", "unknown")
        )
    }

    private fun validateManifest(manifest: JSONObject) {
        require(manifest.optString("format") == FORMAT) { "This is not a Hail full backup" }
        require(manifest.optInt("schemaVersion", -1) == SCHEMA_VERSION) {
            "Unsupported Hail full-backup version"
        }
        require(manifest.optLong("createdAtEpochMs", 0L) > 0L) {
            "Backup manifest has no valid creation time"
        }
    }

    private fun validateFiles(files: List<FullBackupFile>): List<FullBackupFile> {
        require(files.isNotEmpty()) { "Backup contains no app files" }
        require(files.size <= MAX_ENTRIES - 2) { "Backup contains too many app files" }
        val paths = HashSet<String>()
        var totalBytes = 0
        files.forEach { file ->
            validateRelativePath(file.path)
            require(paths.add(file.path)) { "Duplicate app file: ${file.path}" }
            require(file.bytes.size <= MAX_FILE_BYTES) { "App file is too large: ${file.path}" }
            totalBytes += file.bytes.size
            require(totalBytes <= MAX_TOTAL_BYTES) { "App data is too large to back up" }
        }
        require("v1/tags.json" in paths && "v1/apps.json" in paths) {
            "Hail app data is incomplete: tags.json or apps.json is missing"
        }
        return files
    }

    private fun validateRelativePath(path: String) {
        require(path.isNotBlank() && path.length <= 512) { "Invalid backup file path" }
        require(!path.startsWith("/") && '\\' !in path) { "Unsafe backup file path" }
        val segments = path.split('/')
        require(segments.all { it.isNotBlank() && it != "." && it != ".." }) {
            "Unsafe backup file path"
        }
    }

    private fun decodeTagBackup(files: List<FullBackupFile>): TagBackup {
        val byPath = files.associateBy { it.path }
        val tags = byPath["v1/tags.json"]?.bytes
            ?: throw IllegalArgumentException("Backup is missing files/v1/tags.json")
        val apps = byPath["v1/apps.json"]?.bytes
            ?: throw IllegalArgumentException("Backup is missing files/v1/apps.json")

        val nested = ByteArrayOutputStream()
        ZipOutputStream(nested).use { zip ->
            zip.writeEntry("tags.json", tags)
            zip.writeEntry("apps.json", apps)
        }
        return TagBackupArchive.decode(ByteArrayInputStream(nested.toByteArray()))
    }

    private fun normalizePreferences(preferences: Map<String, *>): Map<String, Any> {
        require(preferences.size <= MAX_PREFERENCES * 2) { "Too many app preferences" }
        val result = linkedMapOf<String, Any>()
        preferences.toSortedMap().forEach { (key, value) ->
            if (!isAllowedPreferenceKey(key)) return@forEach
            val normalized: Any? = when (value) {
                is String -> value.takeIf { it.length <= MAX_STRING_LENGTH }
                is Boolean, is Int, is Long -> value
                is Float -> value.takeIf { it.isFinite() }
                is Set<*> -> {
                    val strings = value.filterIsInstance<String>()
                    strings.takeIf { it.size == value.size && it.size <= MAX_SET_ITEMS }?.toSet()
                }
                else -> null
            }
            if (normalized != null) result[key] = normalized
        }
        require(result.size <= MAX_PREFERENCES) { "Too many restorable app preferences" }
        return result
    }

    private fun isAllowedPreferenceKey(key: String): Boolean {
        if (key.isBlank() || key.length > MAX_KEY_LENGTH || key in excludedPreferenceKeys) return false
        val normalized = key.lowercase()
        return sensitiveKeyFragments.none { it in normalized }
    }

    private fun encodePreferences(preferences: Map<String, Any>): JSONObject {
        val entries = JSONArray()
        preferences.forEach { (key, value) ->
            val type: String
            val encoded: Any
            when (value) {
                is String -> { type = "string"; encoded = value }
                is Boolean -> { type = "boolean"; encoded = value }
                is Int -> { type = "int"; encoded = value }
                is Long -> { type = "long"; encoded = value }
                is Float -> { type = "float"; encoded = value.toDouble() }
                is Set<*> -> {
                    type = "set"
                    encoded = JSONArray(value.filterIsInstance<String>().sorted())
                }
                else -> return@forEach
            }
            entries.put(JSONObject().put("key", key).put("type", type).put("value", encoded))
        }
        return JSONObject().put("schemaVersion", SCHEMA_VERSION).put("entries", entries)
    }

    private fun decodePreferences(json: JSONObject): Map<String, Any> {
        require(json.optInt("schemaVersion", -1) == SCHEMA_VERSION) {
            "Unsupported preferences backup version"
        }
        val entries = json.optJSONArray("entries")
            ?: throw IllegalArgumentException("Preference entries are missing")
        require(entries.length() <= MAX_PREFERENCES) { "Backup contains too many preferences" }

        val result = linkedMapOf<String, Any>()
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val key = entry.getString("key")
            require(isAllowedPreferenceKey(key)) { "Backup contains a protected preference" }
            require(!result.containsKey(key)) { "Backup contains duplicate preference: $key" }
            val value = entry.get("value")
            val decoded: Any = when (entry.getString("type")) {
                "string" -> (value as? String)?.also {
                    require(it.length <= MAX_STRING_LENGTH) { "Preference value is too long" }
                } ?: throw IllegalArgumentException("Invalid string preference: $key")
                "boolean" -> value as? Boolean
                    ?: throw IllegalArgumentException("Invalid boolean preference: $key")
                "int" -> (value as? Number)?.toInt()
                    ?: throw IllegalArgumentException("Invalid integer preference: $key")
                "long" -> (value as? Number)?.toLong()
                    ?: throw IllegalArgumentException("Invalid long preference: $key")
                "float" -> {
                    val number = (value as? Number)?.toFloat()
                        ?: throw IllegalArgumentException("Invalid float preference: $key")
                    require(number.isFinite()) { "Invalid float preference: $key" }
                    number
                }
                "set" -> {
                    val array = value as? JSONArray
                        ?: throw IllegalArgumentException("Invalid string-set preference: $key")
                    require(array.length() <= MAX_SET_ITEMS) { "String set is too large: $key" }
                    buildSet {
                        for (itemIndex in 0 until array.length()) {
                            val item = array.getString(itemIndex)
                            require(item.length <= MAX_STRING_LENGTH) { "String set item is too long" }
                            add(item)
                        }
                    }
                }
                else -> throw IllegalArgumentException("Unsupported preference type for $key")
            }
            result[key] = decoded
        }
        return result
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var count = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read == 0) continue
            count += read
            require(count <= limit) { "A backup entry is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
