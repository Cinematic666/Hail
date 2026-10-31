package com.aistra.hail.ui.home

import com.aistra.hail.app.TagAssignment
import com.aistra.hail.app.TagAssignmentManager

/**
 * A complete, in-memory editing session. No disk changes occur until the user taps Save.
 * This includes reorder, rename, delete and bulk app assignment operations.
 */
internal class TagEditSession(
    initialTags: List<Pair<String, Int>>,
    initialApps: List<TagAssignment>
) {
    private val baselineTags = initialTags.toList()
    private val baselineApps = initialApps.map { it.copy(tagIds = it.tagIds.toList()) }
    val tags: MutableList<Pair<String, Int>> = baselineTags.toMutableList()
    private val appTags = linkedMapOf<String, List<Int>>().apply {
        baselineApps.forEach { put(it.packageName, it.tagIds) }
    }

    val isDirty: Boolean
        get() = tags != baselineTags ||
            baselineApps.any { appTags[it.packageName] != it.tagIds }

    fun appCount(tagId: Int): Int = appTags.values.count { tagId in it }
    fun packagesIn(tagId: Int): Set<String> = appTags.filterValues { tagId in it }.keys
    fun tagsFor(packageName: String): List<Int> = appTags[packageName].orEmpty()
    fun allAssignments(): Map<String, List<Int>> = appTags.mapValues { it.value.toList() }

    fun addTag(rawName: String): Int? {
        val name = rawName.trim()
        if (name.isEmpty() || tags.any { it.first.equals(name, ignoreCase = true) }) return null
        // Don't tie future identity to a name: collisions are possible.
        var id = name.hashCode()
        while (id == 0 || tags.any { it.second == id }) {
            id = if (id == Int.MAX_VALUE) 1 else id + 1
        }
        tags.add(name to id)
        return id
    }

    fun renameTag(tagId: Int, rawName: String): Boolean {
        if (tagId == 0) return false
        val name = rawName.trim()
        if (name.isEmpty() || tags.any { it.second != tagId && it.first.equals(name, true) })
            return false
        val position = tags.indexOfFirst { it.second == tagId }
        if (position < 1) return false
        tags[position] = name to tagId
        return true
    }

    fun deleteTag(tagId: Int): Boolean {
        if (tagId == 0 || tags.none { it.second == tagId }) return false
        tags.removeAll { it.second == tagId }
        appTags.keys.toList().forEach { key ->
            appTags[key] = appTags.getValue(key).filterNot { it == tagId }
        }
        return true
    }

    fun move(from: Int, to: Int): Boolean = TagOrder.move(tags, from, to)

    fun assign(sourceId: Int, targetId: Int, selected: Set<String>, move: Boolean): Int {
        if (sourceId == targetId || tags.none { it.second == sourceId } ||
            tags.none { it.second == targetId }) return 0

        val plan = TagAssignmentManager.apply(
            appTags.map { TagAssignment(it.key, it.value) },
            sourceId, targetId, selected, move
        )
        plan.assignments.forEach { appTags[it.packageName] = it.tagIds }
        return plan.changedCount
    }
}
