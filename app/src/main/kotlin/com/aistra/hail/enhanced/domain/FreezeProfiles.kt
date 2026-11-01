package com.aistra.hail.enhanced.domain

internal data class FreezeProfile(
    val id: String,
    val name: String,
    /** true = frozen, false = unfrozen */
    val packageStates: Map<String, Boolean>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs
)

internal data class ProfileConflict(
    val packageName: String,
    val profileIds: Set<String>,
    val requestedStates: Set<Boolean>
)

internal data class ProfilePlan(
    val operations: Map<String, Boolean>,
    val conflicts: List<ProfileConflict>,
    val skippedProtectedPackages: Set<String>
) {
    val isSafe: Boolean get() = conflicts.isEmpty()
}

internal object FreezeProfileEngine {
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

    fun validate(profiles: List<FreezeProfile>): List<FreezeProfile> {
        require(profiles.size <= 100) { "Too many freeze profiles" }
        require(profiles.map { it.id }.toSet().size == profiles.size) { "Duplicate profile ID" }
        val normalizedNames = profiles.map { it.name.trim().lowercase() }
        require(normalizedNames.all { it.isNotBlank() && it.length <= 64 }) { "Invalid profile name" }
        require(normalizedNames.toSet().size == normalizedNames.size) { "Duplicate profile name" }
        profiles.forEach { profile ->
            require(profile.id.isNotBlank() && profile.id.length <= 128) { "Invalid profile ID" }
            require(profile.packageStates.size <= 20_000) { "Profile contains too many packages" }
            require(profile.packageStates.keys.all { packagePattern.matches(it) }) { "Invalid package in profile" }
        }
        return profiles
    }

    fun findConflicts(profiles: List<FreezeProfile>): List<ProfileConflict> {
        val assignments = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()
        profiles.forEach { profile ->
            profile.packageStates.forEach { (packageName, frozen) ->
                assignments.getOrPut(packageName) { mutableListOf() } += profile.id to frozen
            }
        }
        return assignments.mapNotNull { (packageName, values) ->
            val states = values.map { it.second }.toSet()
            if (states.size <= 1) null
            else ProfileConflict(packageName, values.map { it.first }.toSet(), states)
        }.sortedBy { it.packageName }
    }

    fun planSwitch(
        active: FreezeProfile?,
        target: FreezeProfile,
        allProfiles: List<FreezeProfile>,
        currentStates: Map<String, Boolean?>,
        protectedPackages: Set<String> = emptySet()
    ): ProfilePlan {
        validate(allProfiles)
        validate(listOf(target))
        val relevant = allProfiles.filter { it.id != active?.id && it.id != target.id }
        val conflicts = findConflicts(relevant + target)
        val desired = linkedMapOf<String, Boolean>()
        // Applying a profile changes only its explicit assignments. Packages absent from
        // the target may have been frozen manually and must not be silently unfrozen.
        target.packageStates.forEach { (packageName, frozen) -> desired[packageName] = frozen }

        val skipped = desired.keys.filterTo(LinkedHashSet()) { it in protectedPackages }
        val operations = desired.filter { (packageName, frozen) ->
            packageName !in skipped && currentStates[packageName] != frozen
        }
        return ProfilePlan(operations, conflicts, skipped)
    }
}
