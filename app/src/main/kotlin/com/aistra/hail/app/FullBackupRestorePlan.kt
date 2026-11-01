package com.aistra.hail.app

import java.util.Locale

/** Pure decisions shared by the restore UI and the storage transaction. */
internal object FullBackupRestorePlan {
    fun select(payload: FullBackupPayload, selection: FullBackupSelection): FullBackupPayload {
        val archiveKeys = payload.selection.preferenceKeys
        val requestedKeys = selection.preferenceKeys
        val keys = when {
            archiveKeys == null -> requestedKeys
            requestedKeys == null -> archiveKeys
            else -> archiveKeys intersect requestedKeys
        }
        val effective = FullBackupSelection(
            includeAdditionalFiles = payload.selection.includeAdditionalFiles && selection.includeAdditionalFiles,
            includePreferences = payload.selection.includePreferences && selection.includePreferences,
            preferenceKeys = keys
        )
        return payload.copy(
            files = if (effective.includeAdditionalFiles) payload.files else {
                payload.files.filter { it.path == "v1/tags.json" || it.path == "v1/apps.json" }
            },
            preferences = if (effective.includePreferences) {
                payload.preferences.filterKeys { keys == null || it in keys }
            } else emptyMap(),
            selection = effective
        )
    }

    fun filesToDelete(payload: FullBackupPayload, mode: FullBackupRestoreMode, currentPaths: Set<String>): Set<String> =
        if (mode == FullBackupRestoreMode.REPLACE && payload.selection.includeAdditionalFiles) {
            currentPaths - payload.files.map { it.path }.toSet()
        }
        else emptySet()

    fun preferencesToRemove(payload: FullBackupPayload, mode: FullBackupRestoreMode, currentKeys: Set<String>): Set<String> =
        if (mode == FullBackupRestoreMode.REPLACE && payload.selection.includePreferences) {
            val selectedKeys = payload.selection.preferenceKeys
            currentKeys.filterTo(linkedSetOf()) {
                FullBackupArchive.isAllowedPreferenceKey(it) && it !in payload.preferences &&
                    (selectedKeys == null || it in selectedKeys)
            }
        } else emptySet()

    fun tagState(current: TagBackup, incoming: TagBackup, mode: FullBackupRestoreMode): TagBackup {
        val validIncoming = TagBackupArchive.normalizeAndValidate(incoming)
        if (mode == FullBackupRestoreMode.REPLACE) return validIncoming
        val validCurrent = TagBackupArchive.normalizeAndValidate(current)
        val tags = validCurrent.tags.toMutableList()
        val usedIds = (tags.map { it.id } + validIncoming.tags.map { it.id }).toMutableSet()
        val idMap = mutableMapOf(0 to 0)
        validIncoming.tags.filter { it.id != 0 }.forEach { tag ->
            val sameName = tags.firstOrNull {
                it.name.trim().lowercase(Locale.ROOT) == tag.name.trim().lowercase(Locale.ROOT)
            }
            val id = sameName?.id ?: if (tags.any { it.id == tag.id }) {
                generateSequence(1) { it + 1 }.first { it !in usedIds }
            } else tag.id
            idMap[tag.id] = id
            if (sameName == null) {
                tags += tag.copy(id = id)
                usedIds += id
            }
        }
        val apps = validCurrent.apps.associateByTo(linkedMapOf()) { it.packageName }
        validIncoming.apps.forEach { imported ->
            val tagIds = imported.tagIds.map { idMap.getValue(it) }
            val existing = apps[imported.packageName]
            apps[imported.packageName] = existing?.copy(tagIds = (existing.tagIds + tagIds).distinct())
                ?: imported.copy(tagIds = tagIds)
        }
        return TagBackupArchive.normalizeAndValidate(TagBackup(tags, apps.values.toList()))
    }
}
