package com.dopashift.domain.strategy

import com.dopashift.domain.entity.ChangeLogEntry

/**
 * Conflict resolution strategy where a delete operation is authoritative
 * over an edit operation.
 *
 * A delete is represented by a ChangeLogEntry with a null [ChangeLogEntry.value].
 *
 * Resolution logic:
 * - If either entry is a delete (value == null), the delete wins.
 * - If the remote is a delete, RemoteWins.
 * - If the local is a delete, LocalWins.
 * - If neither is a delete, falls back to [LastWriteWinsStrategy].
 *
 * Requirement 4.11: delete is authoritative over edit; the edit appears
 * in conflict history with the attempted edit noted.
 */
class DeleteWinsStrategy : ConflictResolutionStrategy {

    override fun resolve(local: ChangeLogEntry, remote: ChangeLogEntry): MergeResult {
        val hasDelete = local.value == null || remote.value == null
        return when {
            hasDelete && remote.value == null -> MergeResult.RemoteWins(supersededEntry = local)
            hasDelete && local.value == null -> MergeResult.LocalWins(supersededEntry = remote)
            else -> LastWriteWinsStrategy().resolve(local, remote)
        }
    }
}
