package com.aistra.hail.ui.home

import com.aistra.hail.app.TagAssignment
import org.junit.Assert.*
import org.junit.Test

class TagEditSessionTest {
    private fun session() = TagEditSession(
        listOf("Default" to 0, "Games" to 11, "Work" to 22),
        listOf(
            TagAssignment("a.games", listOf(11)),
            TagAssignment("b.work", listOf(22)),
            TagAssignment("c.both", listOf(11,22))
        )
    )

    @Test fun cancelByDiscardLeavesTheOriginalInputUnchanged() {
        val tags = mutableListOf("Default" to 0, "Games" to 11)
        val apps = listOf(TagAssignment("pkg", listOf(11)))
        val edited = TagEditSession(tags, apps)
        edited.renameTag(11, "Leisure")
        edited.assign(11, 0, setOf("pkg"), true)
        assertEquals("Games", tags[1].first)
        assertEquals(listOf(11), apps[0].tagIds)
        assertTrue(edited.isDirty)
    }

    @Test fun moveAndRenamePreserveStableIds() {
        val s = session()
        assertTrue(s.move(2, 1))
        assertTrue(s.renameTag(22, "Office"))
        assertEquals(listOf(0, 22, 11), s.tags.map { it.second })
        assertEquals(setOf("b.work","c.both"), s.packagesIn(22))
        assertFalse(s.move(1,0))
    }

    @Test fun moveTransfersOnlySelectedAndKeepsUnrelatedTags() {
        val s = session()
        assertEquals(1, s.assign(11, 22, setOf("a.games"), true))
        assertEquals(listOf(22), s.tagsFor("a.games"))
        assertEquals(listOf(11,22), s.tagsFor("c.both"))
    }

    @Test fun addDoesNotRemoveSource() {
        val s = session()
        assertEquals(1, s.assign(11,22,setOf("a.games"),false))
        assertEquals(listOf(11,22),s.tagsFor("a.games"))
    }

    @Test fun deletionUpdatesAssignmentsButNotOriginal() {
        val s = session()
        assertTrue(s.deleteTag(11))
        assertEquals(emptyList<Int>(),s.tagsFor("a.games"))
        assertEquals(listOf(22),s.tagsFor("c.both"))
        assertFalse(s.deleteTag(0))
    }

    @Test fun onlyNewlyOrphanedAppsAreRemoved() {
        val s = session()
        assertTrue(s.newlyUnassignedPackages().isEmpty())
        assertTrue(s.deleteTag(11))
        assertEquals(setOf("a.games"), s.newlyUnassignedPackages())
        assertEquals(listOf(22), s.tagsFor("c.both"))
    }

    @Test fun preexistingUntaggedEntriesAreNotTreatedAsNewlyOrphaned() {
        val s = TagEditSession(
            listOf("Default" to 0, "Work" to 22),
            listOf(
                TagAssignment("legacy.untagged", emptyList()),
                TagAssignment("live.work", listOf(22))
            )
        )
        assertTrue(s.renameTag(22, "Office"))
        assertTrue(s.newlyUnassignedPackages().isEmpty())
        assertTrue(s.deleteTag(22))
        assertEquals(setOf("live.work"), s.newlyUnassignedPackages())
    }

    @Test fun duplicateAndBlankNamesAreRejected() {
        val s = session()
        assertNull(s.addTag(" "))
        assertNull(s.addTag("games"))
        assertFalse(s.renameTag(22,"games"))
        assertNull(s.addTag("DEFAULT"))
        assertNotNull(s.addTag("Personal"))
    }

    @Test fun createAndBulkAssignNewCategoryBeforeSave() {
        val s = session()
        val id=s.addTag("Favorites")!!
        assertEquals(1,s.assign(11,id,setOf("a.games"),true))
        assertEquals(listOf(id),s.tagsFor("a.games"))
        assertEquals(1,s.appCount(id))
    }
}
