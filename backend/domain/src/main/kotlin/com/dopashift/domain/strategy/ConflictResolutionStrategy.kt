package com.dopashift.domain.strategy

import com.dopashift.domain.entity.ChangeLogEntry

/**
 * Strategy interface for resolving conflicts between two ChangeLogEntry instances
 * that modify the same entity and field.
 *
 * Implementations are pure domain logic with no framework dependencies.
 */
interface ConflictResolutionStrategy {
    /**
     * Resolves a conflict between a local and remote ChangeLogEntry.
     *
     * @param local the entry from the local device
     * @param remote the entry from a remote device (with server-assigned timestamp)
     * @return the merge result indicating which entry wins
     */
    fun resolve(local: ChangeLogEntry, remote: ChangeLogEntry): MergeResult
}
