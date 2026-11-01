package com.aistra.hail.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullBackupRestorePlanTest {
    private val current = TagBackup(
        listOf(TagBackupTag("Default", 0), TagBackupTag("Local", 1)),
        listOf(TagBackupApp("com.example.local", listOf(1), whitelisted = true))
    )
    private val incoming = TagBackup(
        listOf(TagBackupTag("Default", 0), TagBackupTag("Imported", 1)),
        listOf(TagBackupApp("com.example.imported", listOf(1), pinned = true))
    )
    private fun payload() = FullBackupPayload(
        files = listOf(
            FullBackupFile("v1/tags.json", byteArrayOf()),
            FullBackupFile("v1/apps.json", byteArrayOf()),
            FullBackupFile("enhanced/profiles.json", byteArrayOf())
        ),
        preferences = mapOf("app_theme" to "theme_dark"),
        tagBackup = incoming,
        sourceApplicationId = "com.aistra.hail.enhanced",
        sourceVersionName = "test",
        schemaVersion = 2
    )

    @Test fun replaceLeavesDeselectedCategoriesUntouched() {
        val selected = FullBackupRestorePlan.select(
            payload(), FullBackupSelection(includeAdditionalFiles = false, includePreferences = false)
        )
        assertTrue(FullBackupRestorePlan.filesToDelete(
            selected, FullBackupRestoreMode.REPLACE, setOf("enhanced/local.json")
        ).isEmpty())
        assertTrue(FullBackupRestorePlan.preferencesToRemove(
            selected, FullBackupRestoreMode.REPLACE, setOf("app_theme", "home_font_size")
        ).isEmpty())
        assertEquals(setOf("v1/tags.json", "v1/apps.json"), selected.files.map { it.path }.toSet())
        assertTrue(selected.preferences.isEmpty())
    }

    @Test fun archiveExclusionsCannotBeReenabledAtImport() {
        val partial = FullBackupRestorePlan.select(
            payload(), FullBackupSelection(includeAdditionalFiles = false, includePreferences = false)
        )
        val selected = FullBackupRestorePlan.select(partial, FullBackupSelection())
        assertFalse(selected.selection.includeAdditionalFiles)
        assertFalse(selected.selection.includePreferences)
    }

    @Test fun selectedPreferenceKeysLimitReplacementAndWrites() {
        val selected = FullBackupRestorePlan.select(
            payload(), FullBackupSelection(preferenceKeys = setOf("home_font_size"))
        )
        assertTrue(selected.preferences.isEmpty())
        assertEquals(setOf("home_font_size"), FullBackupRestorePlan.preferencesToRemove(
            selected, FullBackupRestoreMode.REPLACE, setOf("app_theme", "home_font_size")
        ))
    }

    @Test fun replaceDeletesOnlyIncludedMissingDataAndKeepsProtectedPreferences() {
        assertEquals(setOf("enhanced/local.json"), FullBackupRestorePlan.filesToDelete(
            payload(), FullBackupRestoreMode.REPLACE,
            setOf("v1/apps.json", "enhanced/profiles.json", "enhanced/local.json")
        ))
        assertEquals(setOf("home_font_size"), FullBackupRestorePlan.preferencesToRemove(
            payload(), FullBackupRestoreMode.REPLACE,
            setOf("app_theme", "home_font_size", "working_mode", "api_token")
        ))
    }

    @Test fun mergePreservesLocalAppsAndRemapsConflictingTagIds() {
        val merged = FullBackupRestorePlan.tagState(current, incoming, FullBackupRestoreMode.MERGE)
        assertEquals(setOf("Default", "Local", "Imported"), merged.tags.map { it.name }.toSet())
        assertEquals(setOf("com.example.local", "com.example.imported"), merged.apps.map { it.packageName }.toSet())
        val local = merged.apps.single { it.packageName == "com.example.local" }
        val imported = merged.apps.single { it.packageName == "com.example.imported" }
        assertEquals(listOf(1), local.tagIds)
        assertTrue(local.whitelisted)
        assertTrue(imported.pinned)
        assertEquals("Imported", merged.tags.single { it.id == imported.tagIds.single() }.name)
    }

    @Test fun mergeMatchesTagNamesAndPreservesExistingAppFlagsAndAssignments() {
        val overlapping = TagBackup(
            listOf(TagBackupTag("Default", 0), TagBackupTag("local", 20), TagBackupTag("Extra", 30)),
            listOf(TagBackupApp("com.example.local", listOf(20, 30)))
        )
        val merged = FullBackupRestorePlan.tagState(current, overlapping, FullBackupRestoreMode.MERGE)
        assertEquals(3, merged.tags.size)
        assertEquals(setOf(1, 30), merged.apps.single().tagIds.toSet())
        assertTrue(merged.apps.single().whitelisted)
    }

    @Test fun replaceUsesExactArchivedTagStateAndMergeDeletesNothing() {
        assertEquals(incoming, FullBackupRestorePlan.tagState(current, incoming, FullBackupRestoreMode.REPLACE))
        assertTrue(FullBackupRestorePlan.filesToDelete(
            payload(), FullBackupRestoreMode.MERGE, setOf("enhanced/local.json")
        ).isEmpty())
        assertTrue(FullBackupRestorePlan.preferencesToRemove(
            payload(), FullBackupRestoreMode.MERGE, setOf("home_font_size")
        ).isEmpty())
    }
}
