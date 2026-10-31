package com.aistra.hail.enhanced.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeProfilesTest {
    private fun profile(id: String, states: Map<String, Boolean>) =
        FreezeProfile(id, id, states, 1L)

    @Test fun detectsContradictoryAssignmentsAcrossProfiles() {
        val conflicts = FreezeProfileEngine.findConflicts(
            listOf(profile("work", mapOf("com.example.app" to true)),
                profile("games", mapOf("com.example.app" to false)))
        )
        assertEquals(1, conflicts.size)
        assertEquals("com.example.app", conflicts.single().packageName)
    }

    @Test fun plansOnlyChangedAndUnprotectedPackages() {
        val active = profile("work", mapOf("com.example.work" to true, "com.example.keep" to true))
        val target = profile("game", mapOf("com.example.game" to true, "com.example.keep" to false))
        val plan = FreezeProfileEngine.planSwitch(
            active, target, listOf(active, target),
            mapOf("com.example.work" to true, "com.example.game" to false, "com.example.keep" to true),
            setOf("com.example.keep")
        )
        assertEquals(mapOf("com.example.game" to true), plan.operations)
        assertEquals(setOf("com.example.keep"), plan.skippedProtectedPackages)
        assertTrue(plan.isSafe)
    }

    @Test fun blocksTargetProfileWhenAnotherProfileHasOppositeState() {
        val target = profile("game", mapOf("com.example.app" to true))
        val other = profile("work", mapOf("com.example.app" to false))
        val plan = FreezeProfileEngine.planSwitch(null, target, listOf(target, other), emptyMap())
        assertFalse(plan.isSafe)
        assertTrue(plan.operations.isEmpty())
    }
}
