package com.aistra.hail.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TagBackupArchiveTest {
    private val sample = TagBackup(
        tags = listOf(TagBackupTag("Default", 0), TagBackupTag("Work", 101), TagBackupTag("Media", 202)),
        apps = listOf(
            TagBackupApp("com.example.reader", listOf(0, 101), pinned = true, whitelisted = true),
            TagBackupApp("com.example.player", listOf(202))
        )
    )

    @Test
    fun exportedArchiveCanBeImportedAgain() {
        val bytes = TagBackupArchive.encode(sample, "com.aistra.hail.enhanced", "1.11.0-enhanced.2")
        assertEquals(sample, TagBackupArchive.decode(ByteArrayInputStream(bytes)))
    }

    @Test
    fun legacyHailArchiveWithoutManifestIsAccepted() {
        val archive = legacyArchive(
            """[{"tag":"Default","id":0},{"tag":"Work","id":101}]""",
            """[{"package":"com.example.reader","pinned":true,"whitelisted":false,"tags":[0,101]}]"""
        )
        val restored = TagBackupArchive.decode(ByteArrayInputStream(archive))
        assertEquals(listOf(TagBackupTag("Default", 0), TagBackupTag("Work", 101)), restored.tags)
        assertEquals(listOf(TagBackupApp("com.example.reader", listOf(0, 101), true, false)), restored.apps)
    }

    @Test
    fun legacySingleTagFieldIsAccepted() {
        val archive = legacyArchive(
            """[{"tag":"Default","id":0},{"tag":"Work","id":101}]""",
            """[{"package":"com.example.reader","tag":101}]"""
        )
        assertEquals(listOf(101), TagBackupArchive.decode(ByteArrayInputStream(archive)).apps.single().tagIds)
    }

    @Test
    fun missingDefaultTagIsRejected() {
        val invalid = sample.copy(tags = listOf(TagBackupTag("Work", 101)))
        assertThrows(IllegalArgumentException::class.java) {
            TagBackupArchive.encode(invalid, "com.aistra.hail.enhanced", "test")
        }
    }

    @Test
    fun unknownTagReferenceIsRejected() {
        val invalid = sample.copy(apps = listOf(TagBackupApp("com.example.reader", listOf(999))))
        assertThrows(IllegalArgumentException::class.java) {
            TagBackupArchive.encode(invalid, "com.aistra.hail.enhanced", "test")
        }
    }

    @Test
    fun mergerPreservesExistingFlagsAndAddsImportedPackages() {
        val existing = listOf(
            TagBackupAppState("com.example.reader", true, true, listOf(101)),
            TagBackupAppState("com.example.other", true, false, listOf(999))
        )
        val plan = TagBackupMerger.merge(sample, existing)
        val reader = plan.apps.first { it.packageName == "com.example.reader" }
        val other = plan.apps.first { it.packageName == "com.example.other" }
        val player = plan.apps.first { it.packageName == "com.example.player" }

        assertTrue(reader.pinned)
        assertTrue(reader.whitelisted)
        assertEquals(listOf(0, 101), reader.tagIds)
        assertEquals(listOf(0), other.tagIds)
        assertFalse(player.pinned)
        assertFalse(player.whitelisted)
        assertEquals(listOf(202), player.tagIds)
        assertEquals(listOf("Default", "Work", "Media"), plan.tags.map { it.name })
    }

    @Test
    fun defaultTagIsNormalizedToFirstPosition() {
        val unordered = sample.copy(tags = listOf(TagBackupTag("Work", 101), TagBackupTag("Default", 0)))
        assertEquals(0, TagBackupArchive.normalizeAndValidate(unordered).tags.first().id)
    }

    private fun legacyArchive(tags: String, apps: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("v1/tags.json"))
            zip.write(tags.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("v1/apps.json"))
            zip.write(apps.toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
