package com.aistra.hail.enhanced.domain

internal data class BackupEntryMetadata(
    val name: String,
    val size: Long,
    val sha256: String
) {
    companion object {
        private val hashPattern = Regex("[a-f0-9]{64}")

        fun validate(name: String, size: Long, sha256: String) {
            require(name.isNotBlank() && name.length <= 512) { "Invalid backup entry name" }
            require(!name.startsWith("/") && '\\' !in name) { "Unsafe backup entry name" }
            require(name.split('/').all { it.isNotBlank() && it != "." && it != ".." }) {
                "Unsafe backup entry name"
            }
            require(size >= 0L && size <= 8L * 1024 * 1024) { "Invalid backup entry size" }
            require(hashPattern.matches(sha256)) { "Invalid SHA-256 digest" }
        }
    }
}
