package com.aistra.hail.enhanced.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest

class BackupIntegrityTest {
    @Test fun sha256IsStableAndLowercaseHex() {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("hail".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("1f6639a8a51c6f74348cb84ed74a5d8f95da74152eabd1c460218efe8f4d57b0", digest)
    }

    @Test fun rejectsNegativeOrOversizedEntryMetadata() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupEntryMetadata.validate("files/../bad", -1, "not-a-hash")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupEntryMetadata.validate("files/state.json", 1024, "short")
        }
    }

    @Test fun validatesSafeRelativePath() {
        BackupEntryMetadata.validate("files/v1/apps.json", 10, "a".repeat(64))
        assertThrows(IllegalArgumentException::class.java) {
            BackupEntryMetadata.validate("../escape", 10, "a".repeat(64))
        }
    }
}
