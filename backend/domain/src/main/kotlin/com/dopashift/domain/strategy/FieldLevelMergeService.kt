package com.dopashift.domain.strategy

import com.dopashift.domain.entity.ChangeLogEntry

/**
 * Service that merges ChangeLogEntry lists from two devices at field level.
 *
 * Entries targeting the same entityId but DIFFERENT fields do not conflict —
 * both edits coexist. Conflict resolution is only invoked when two entries
 * target the same (entityId, field) pair.
 *
 * Requirement 4.6: Field-level merge preserves non-conflicting edits.
 */
class FieldLevelMergeService(
    private val conflictResolutionStrategy: ConflictResolutionStrategy = LastWriteWinsStrategy()
) {

    /**
     * Merges two sets of ChangeLogEntries (one from each device).
     *
     * @param localEntries entries produced on the local device
     * @param remoteEntries entries produced on a remote device
     * @return a [FieldLevelMergeResult] containing preserved entries and any conflict resolutions
     */
    fun merge(
        localEntries: List<ChangeLogEntry>,
        remoteEntries: List<ChangeLogEntry>
    ): FieldLevelMergeResult {
        // Group entries by (entityId, field) key
        val localByKey = localEntries.associateBy { MergeKey(it.entityId, it.field) }
        val remoteByKey = remoteEntries.associateBy { MergeKey(it.entityId, it.field) }

        val allKeys = localByKey.keys + remoteByKey.keys

        val preserved = mutableListOf<ChangeLogEntry>()
        val conflicts = mutableListOf<ConflictResolution>()

        for (key in allKeys) {
            val local = localByKey[key]
            val remote = remoteByKey[key]

            when {
                // Only local has this field — no conflict, preserved
                local != null && remote == null -> preserved.add(local)
                // Only remote has this field — no conflict, preserved
                remote != null && local == null -> preserved.add(remote)
                // Both have the same (entityId, field) — conflict
                local != null && remote != null -> {
                    val result = conflictResolutionStrategy.resolve(local, remote)
                    when (result) {
                        is MergeResult.RemoteWins -> {
                            preserved.add(remote)
                            conflicts.add(ConflictResolution(winner = remote, superseded = local))
                        }
                        is MergeResult.LocalWins -> {
                            preserved.add(local)
                            conflicts.add(ConflictResolution(winner = local, superseded = remote))
                        }
                        is MergeResult.FieldMerge -> {
                            // All merged entries are preserved
                            preserved.addAll(result.merged.values)
                        }
                    }
                }
            }
        }

        return FieldLevelMergeResult(preserved = preserved, conflicts = conflicts)
    }
}

/**
 * Key used to group entries by entity and field for conflict detection.
 */
private data class MergeKey(val entityId: java.util.UUID, val field: String)

/**
 * Result of a field-level merge operation.
 *
 * @property preserved all entries that survived the merge (both non-conflicting and conflict winners)
 * @property conflicts details of any conflicts that required resolution
 */
data class FieldLevelMergeResult(
    val preserved: List<ChangeLogEntry>,
    val conflicts: List<ConflictResolution>
)

/**
 * Details of a single conflict resolution.
 */
data class ConflictResolution(
    val winner: ChangeLogEntry,
    val superseded: ChangeLogEntry
)
