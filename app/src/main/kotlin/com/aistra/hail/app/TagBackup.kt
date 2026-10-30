package com.aistra.hail.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class TagBackupTag(val name: String, val id: Int)
internal data class TagBackupApp(
    val packageName: String,
    val tagIds: List<Int>,
    val pinned: Boolean = false,
    val whitelisted: Boolean = false
)
internal data class TagBackup(val tags: List<TagBackupTag>, val apps: List<TagBackupApp>)
internal data class TagBackupAppState(
    val packageName: String,
    val pinned: Boolean,
    val whitelisted: Boolean,
    val tagIds: List<Int>
)
internal data class TagBackupImportPlan(
    val tags: List<TagBackupTag>,
    val apps: List<TagBackupAppState>
)

internal object TagBackupArchive {
    private const val FORMAT = "com.aistra.hail.tag-backup"
    private const val SCHEMA_VERSION = 1
    private const val MAX_TAGS = 500
    private const val MAX_APPS = 20_000
    private const val MAX_ENTRIES = 32
    private const val MAX_ENTRY_BYTES = 5 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 6 * 1024 * 1024
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

    fun encode(backup: TagBackup, applicationId: String, versionName: String): ByteArray {
        val valid = normalizeAndValidate(backup)
        val tagsJson = JSONArray().apply {
            valid.tags.forEach { put(JSONObject().put("tag", it.name).put("id", it.id)) }
        }
        val appsJson = JSONArray().apply {
            valid.apps.forEach { app ->
                put(
                    JSONObject()
                        .put("package", app.packageName)
                        .put("pinned", app.pinned)
                        .put("whitelisted", app.whitelisted)
                        .put("tags", JSONArray(app.tagIds))
                )
            }
        }
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("applicationId", applicationId)
            .put("versionName", versionName)
            .put("createdAtEpochMs", System.currentTimeMillis())

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeEntry("manifest.json", manifest.toString())
            // Keep the original Hail v1 filenames and JSON keys for legacy compatibility.
            zip.writeEntry("tags.json", tagsJson.toString())
            zip.writeEntry("apps.json", appsJson.toString())
        }
        return output.toByteArray()
    }

    fun decode(input: InputStream): TagBackup {
        val files = linkedMapOf<String, String>()
        var totalBytes = 0
        var entryCount = 0

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRIES) { "Backup contains too many files" }

                if (!entry.isDirectory) {
                    val bytes = zip.readBounded(MAX_ENTRY_BYTES)
                    totalBytes += bytes.size
                    require(totalBytes <= MAX_TOTAL_BYTES) { "Backup is too large" }
                    val key = classifyEntry(entry.name)
                    if (key != null) {
                        require(!files.containsKey(key)) { "Backup contains duplicate $key" }
                        files[key] = String(bytes, StandardCharsets.UTF_8)
                    }
                }
                zip.closeEntry()
            }
        }

        val tagsText = files["tags.json"] ?: throw IllegalArgumentException("tags.json is missing")
        val appsText = files["apps.json"] ?: throw IllegalArgumentException("apps.json is missing")
        files["manifest.json"]?.let(::validateManifest)
        return normalizeAndValidate(TagBackup(parseTags(tagsText), parseApps(appsText)))
    }

    internal fun normalizeAndValidate(backup: TagBackup): TagBackup {
        require(backup.tags.isNotEmpty()) { "The backup has no tags" }
        require(backup.tags.size <= MAX_TAGS) { "The backup contains too many tags" }
        require(backup.apps.size <= MAX_APPS) { "The backup contains too many app assignments" }

        val ids = backup.tags.map { it.id }
        require(ids.toSet().size == ids.size) { "Tag identifiers are not unique" }
        require(ids.count { it == 0 } == 1) { "The Default tag (ID 0) is missing or duplicated" }

        val names = backup.tags.map { it.name.trim().lowercase(Locale.ROOT) }
        require(names.all { it.isNotBlank() && it.length <= 128 }) { "A tag name is empty or too long" }
        require(names.toSet().size == names.size) { "Tag names must be unique" }

        val knownIds = ids.toSet()
        val packages = HashSet<String>()
        backup.apps.forEach { app ->
            require(app.packageName.length <= 255 && packagePattern.matches(app.packageName)) {
                "Invalid package name: ${app.packageName}"
            }
            require(packages.add(app.packageName)) { "Duplicate app assignment: ${app.packageName}" }
            require(app.tagIds.isNotEmpty()) { "An app has no tag assignment: ${app.packageName}" }
            require(app.tagIds.toSet().size == app.tagIds.size) {
                "Duplicate tag assignment for ${app.packageName}"
            }
            require(app.tagIds.all { it in knownIds }) {
                "Unknown tag identifier in assignment for ${app.packageName}"
            }
        }

        val defaultTag = backup.tags.single { it.id == 0 }
        return backup.copy(tags = listOf(defaultTag) + backup.tags.filterNot { it.id == 0 })
    }

    private fun parseTags(jsonText: String): List<TagBackupTag> {
        val json = JSONArray(jsonText)
        require(json.length() in 1..MAX_TAGS) { "Invalid tag count" }
        return List(json.length()) { index ->
            val item = json.getJSONObject(index)
            TagBackupTag(item.getString("tag").trim(), item.getInt("id"))
        }
    }

    private fun parseApps(jsonText: String): List<TagBackupApp> {
        val json = JSONArray(jsonText)
        require(json.length() <= MAX_APPS) { "Invalid app assignment count" }
        return List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val ids = item.optJSONArray("tags")?.let { array ->
                List(array.length()) { tagIndex -> array.getInt(tagIndex) }
            } ?: listOf(item.optInt("tag", 0))
            TagBackupApp(
                packageName = item.getString("package"),
                tagIds = ids.ifEmpty { listOf(0) },
                pinned = item.optBoolean("pinned", false),
                whitelisted = item.optBoolean("whitelisted", false)
            )
        }
    }

    private fun validateManifest(jsonText: String) {
        val manifest = JSONObject(jsonText)
        require(manifest.optString("format") == FORMAT) { "This is not a Hail tag backup" }
        require(manifest.optInt("schemaVersion", -1) == SCHEMA_VERSION) {
            "Unsupported Hail backup version"
        }
    }

    private fun classifyEntry(path: String): String? {
        val name = path.replace('\\', '/').trimStart('/')
        return when {
            name == "tags.json" || name == "v1/tags.json" || name.endsWith("/v1/tags.json") -> "tags.json"
            name == "apps.json" || name == "v1/apps.json" || name.endsWith("/v1/apps.json") -> "apps.json"
            name == "manifest.json" || name == "v1/manifest.json" -> "manifest.json"
            else -> null
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var count = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            count += read
            require(count <= limit) { "A backup entry is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

internal object TagBackupMerger {
    fun merge(
        backup: TagBackup,
        existingApps: List<TagBackupAppState>
    ): TagBackupImportPlan {
        val validBackup = TagBackupArchive.normalizeAndValidate(backup)
        val validTagIds = validBackup.tags.mapTo(HashSet()) { it.id }
        val importedByPackage = validBackup.apps.associateBy { it.packageName }
        val existingPackages = existingApps.mapTo(HashSet()) { it.packageName }

        val mergedApps = existingApps.map { current ->
            val imported = importedByPackage[current.packageName]
            val nextTagIds = imported?.tagIds
                ?: current.tagIds.filter { it in validTagIds }.ifEmpty { listOf(0) }
            current.copy(tagIds = nextTagIds)
        }.toMutableList()

        validBackup.apps.forEach { imported ->
            if (imported.packageName !in existingPackages) {
                mergedApps += TagBackupAppState(
                    packageName = imported.packageName,
                    pinned = imported.pinned,
                    whitelisted = imported.whitelisted,
                    tagIds = imported.tagIds
                )
            }
        }
        return TagBackupImportPlan(validBackup.tags, mergedApps)
    }
}
