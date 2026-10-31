package com.aistra.hail.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FullBackupArchiveTest {
    private val tagsJson = """[{"tag":"Default","id":0},{"tag":"Work","id":101}]"""
    private val appsJson =
        """[{"package":"com.example.reader","pinned":true,"whitelisted":false,"tags":[0,101]}]"""

    private val files = listOf(
        FullBackupFile("v1/tags.json", tagsJson.toByteArray()),
        FullBackupFile("v1/apps.json", appsJson.toByteArray()),
        FullBackupFile("notes/state.json", """{"theme":"dark"}""".toByteArray())
    )

    @Test
    fun fullBackupRoundTripsFilesTagsAppsAndTypedPreferences() {
        val bytes = FullBackupArchive.encode(
            files = files,
            preferences = mapOf(
                "app_theme" to "theme_dark",
                "filter_system_apps" to true,
                "sort_limit" to 12,
                "long_value" to 1234567890123L,
                "font_scale" to 14.5f,
                "hidden_packages" to setOf("com.example.a", "com.example.b"),
                "working_mode" to "su_disable",
                "biometric_login" to true,
                "auto_freeze_after_lock" to true,
                "api_token" to "must-not-be-exported"
            ),
            applicationId = "com.aistra.hail.enhanced",
            versionName = "1.11.0-enhanced.9",
            versionCode = 111009
        )

        val restored = FullBackupArchive.decode(ByteArrayInputStream(bytes))
        assertEquals("com.aistra.hail.enhanced", restored.sourceApplicationId)
        assertEquals("1.11.0-enhanced.9", restored.sourceVersionName)
        assertEquals(setOf("v1/tags.json", "v1/apps.json", "notes/state.json"), restored.files.map { it.path }.toSet())
        assertEquals("""{"theme":"dark"}""", String(restored.files.single { it.path == "notes/state.json" }.bytes))
        assertEquals(listOf("Default", "Work"), restored.tagBackup.tags.map { it.name })
        assertEquals("com.example.reader", restored.tagBackup.apps.single().packageName)
        assertTrue(restored.tagBackup.apps.single().pinned)
        assertEquals("theme_dark", restored.preferences["app_theme"])
        assertEquals(true, restored.preferences["filter_system_apps"])
        assertEquals(12, restored.preferences["sort_limit"])
        assertEquals(1234567890123L, restored.preferences["long_value"])
        assertEquals(14.5f, restored.preferences["font_scale"])
        assertEquals(setOf("com.example.a", "com.example.b"), restored.preferences["hidden_packages"])
        assertFalse(restored.preferences.containsKey("working_mode"))
        assertFalse(restored.preferences.containsKey("biometric_login"))
        assertFalse(restored.preferences.containsKey("auto_freeze_after_lock"))
        assertFalse(restored.preferences.containsKey("api_token"))
    }

    @Test
    fun missingTrackedAppFilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FullBackupArchive.encode(
                files = listOf(FullBackupFile("notes/state.json", byteArrayOf(1))),
                preferences = emptyMap<String, Any>(),
                applicationId = "com.aistra.hail.enhanced",
                versionName = "test",
                versionCode = 1
            )
        }
    }

    @Test
    fun zipSlipPathsAreRejected() {
        val archive = rawArchive(
            entries = listOf(
                "files/v1/tags.json" to tagsJson,
                "files/v1/apps.json" to appsJson,
                "files/../../shared_prefs/prefs.xml" to "bad"
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            FullBackupArchive.decode(ByteArrayInputStream(archive))
        }
    }

    @Test
    fun duplicateFilePathsAreRejectedBeforeExport() {
        assertThrows(IllegalArgumentException::class.java) {
            FullBackupArchive.encode(
                files = files + FullBackupFile("v1/tags.json", tagsJson.toByteArray()),
                preferences = emptyMap<String, Any>(),
                applicationId = "com.aistra.hail.enhanced",
                versionName = "test",
                versionCode = 1
            )
        }
    }

    @Test
    fun protectedPreferencesAreRejectedOnImport() {
        val preferences = JSONObject()
            .put("schemaVersion", 1)
            .put(
                "entries",
                JSONArray().put(
                    JSONObject().put("key", "working_mode")
                        .put("type", "string").put("value", "su_disable")
                )
            )
        val archive = rawArchive(
            entries = listOf(
                "files/v1/tags.json" to tagsJson,
                "files/v1/apps.json" to appsJson
            ),
            preferences = preferences.toString()
        )
        assertThrows(IllegalArgumentException::class.java) {
            FullBackupArchive.decode(ByteArrayInputStream(archive))
        }
    }

    @Test
    fun unsupportedManifestVersionIsRejected() {
        val archive = rawArchive(
            manifestVersion = 99,
            entries = listOf(
                "files/v1/tags.json" to tagsJson,
                "files/v1/apps.json" to appsJson
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            FullBackupArchive.decode(ByteArrayInputStream(archive))
        }
    }

    private fun rawArchive(
        entries: List<Pair<String, String>>,
        manifestVersion: Int = 1,
        preferences: String = """{"schemaVersion":1,"entries":[]}"""
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val manifest = JSONObject()
                .put("format", "com.aistra.hail.full-backup")
                .put("schemaVersion", manifestVersion)
                .put("applicationId", "com.example.legacy")
                .put("versionName", "legacy")
                .put("createdAtEpochMs", 1L)
                .put("fileCount", entries.size)
                .put("preferenceCount", JSONObject(preferences).getJSONArray("entries").length())
            zip.writeEntry("manifest.json", manifest.toString())
            zip.writeEntry("preferences.json", preferences)
            entries.forEach { (name, value) -> zip.writeEntry(name, value) }
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.writeEntry(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }
}
