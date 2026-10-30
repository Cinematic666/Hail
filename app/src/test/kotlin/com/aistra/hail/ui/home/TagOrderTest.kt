package com.aistra.hail.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagOrderTest {
    @Test
    fun defaultTagCannotBeMoved() {
        val tags = mutableListOf("Default" to 0, "Work" to 11, "Games" to 22)
        val original = tags.toList()
        assertFalse(TagOrder.move(tags, 1, 0))
        assertFalse(TagOrder.move(tags, 0, 1))
        assertEquals(original, tags)
    }

    @Test
    fun customTagsCanBeReorderedWithoutChangingTheirIds() {
        val tags = mutableListOf("Default" to 0, "Work" to 11, "Games" to 22, "Media" to 33)
        assertTrue(TagOrder.move(tags, 3, 1))
        assertEquals(listOf("Default" to 0, "Media" to 33, "Work" to 11, "Games" to 22), tags)
    }

    @Test
    fun invalidPositionsAreIgnored() {
        val tags = mutableListOf("Default" to 0, "Work" to 11)
        assertFalse(TagOrder.move(tags, -1, 1))
        assertFalse(TagOrder.move(tags, 1, 2))
        assertFalse(TagOrder.move(tags, 1, 1))
        assertEquals(listOf("Default" to 0, "Work" to 11), tags)
    }

    @Test
    fun signedTagIdsMapToStableNonNegativeItemIds() {
        assertEquals(0L, TagOrder.stableItemId(0))
        assertEquals(0xFFFF_FFFFL, TagOrder.stableItemId(-1))
        assertNotEquals(-1L, TagOrder.stableItemId(-1))
        assertNotEquals(TagOrder.stableItemId(-1), TagOrder.stableItemId(Int.MAX_VALUE))
    }
}
