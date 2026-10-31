package com.aistra.hail.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TagAssignmentManagerTest {
    private val apps = listOf(
        TagAssignment("com.example.one", listOf(0, 10)),
        TagAssignment("com.example.two", listOf(10)),
        TagAssignment("com.example.three", listOf(10, 20)),
        TagAssignment("com.example.four", listOf(20))
    )

    @Test
    fun addKeepsSourceAndAddsTargetForSelectedApps() {
        val plan = TagAssignmentManager.apply(
            apps, sourceTagId = 10, targetTagId = 20,
            selectedPackages = setOf("com.example.one", "com.example.two"),
            move = false
        )
        assertEquals(2, plan.changedCount)
        assertEquals(listOf(0, 10, 20), plan.assignments[0].tagIds)
        assertEquals(listOf(10, 20), plan.assignments[1].tagIds)
        assertEquals(listOf(10, 20), plan.assignments[2].tagIds)
        assertEquals(listOf(20), plan.assignments[3].tagIds)
    }

    @Test
    fun moveRemovesSourceAndAddsTargetWithoutTouchingOtherTags() {
        val plan = TagAssignmentManager.apply(
            apps, sourceTagId = 10, targetTagId = 20,
            selectedPackages = setOf("com.example.one", "com.example.two"),
            move = true
        )
        assertEquals(2, plan.changedCount)
        assertEquals(listOf(0, 20), plan.assignments[0].tagIds)
        assertEquals(listOf(20), plan.assignments[1].tagIds)
        assertEquals(listOf(10, 20), plan.assignments[2].tagIds)
        assertEquals(listOf(20), plan.assignments[3].tagIds)
    }

    @Test
    fun movingToAnAlreadyAssignedTargetOnlyRemovesTheSource() {
        val plan = TagAssignmentManager.apply(
            apps, sourceTagId = 10, targetTagId = 20,
            selectedPackages = setOf("com.example.three"),
            move = true
        )
        assertEquals(1, plan.changedCount)
        assertEquals(listOf(20), plan.assignments[2].tagIds)
    }

    @Test
    fun unselectedAndAppsOutsideSourceCategoryRemainUnchanged() {
        val plan = TagAssignmentManager.apply(
            apps, sourceTagId = 10, targetTagId = 30,
            selectedPackages = setOf("com.example.four", "com.example.one"),
            move = true
        )
        assertEquals(1, plan.changedCount)
        assertEquals(listOf(0, 30), plan.assignments[0].tagIds)
        assertEquals(apps[1], plan.assignments[1])
        assertEquals(apps[2], plan.assignments[2])
        assertEquals(apps[3], plan.assignments[3])
    }

    @Test
    fun emptySelectionIsANoOp() {
        val plan = TagAssignmentManager.apply(
            apps, sourceTagId = 10, targetTagId = 20,
            selectedPackages = emptySet(), move = true
        )
        assertEquals(0, plan.changedCount)
        assertEquals(apps, plan.assignments)
    }

    @Test
    fun sourceAndTargetMustDiffer() {
        assertThrows(IllegalArgumentException::class.java) {
            TagAssignmentManager.apply(
                apps, sourceTagId = 10, targetTagId = 10,
                selectedPackages = setOf("com.example.one"), move = true
            )
        }
    }
}
