package com.aistra.hail.app

internal data class TagAssignment(
    val packageName: String,
    val tagIds: List<Int>
)

internal data class TagAssignmentPlan(
    val assignments: List<TagAssignment>,
    val changedCount: Int
)

/**
 * Pure assignment logic shared by the tag editor and unit tests.
 *
 * Add keeps the source tag and adds the target tag.
 * Move removes the source tag and adds the target tag.
 * Other tags and app metadata are left untouched.
 */
internal object TagAssignmentManager {
    fun apply(
        assignments: List<TagAssignment>,
        sourceTagId: Int,
        targetTagId: Int,
        selectedPackages: Set<String>,
        move: Boolean
    ): TagAssignmentPlan {
        require(sourceTagId != targetTagId) { "Source and target categories must differ" }
        if (selectedPackages.isEmpty()) return TagAssignmentPlan(assignments, 0)

        var changedCount = 0
        val updated = assignments.map { current ->
            if (current.packageName !in selectedPackages || sourceTagId !in current.tagIds) {
                current
            } else {
                val nextTagIds = current.tagIds.toMutableList()
                if (move) nextTagIds.removeAll { it == sourceTagId }
                if (targetTagId !in nextTagIds) nextTagIds.add(targetTagId)
                if (nextTagIds != current.tagIds) changedCount++
                current.copy(tagIds = nextTagIds)
            }
        }
        return TagAssignmentPlan(updated, changedCount)
    }
}
