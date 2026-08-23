package com.dopashift.domain.strategy

import com.dopashift.domain.entity.ChangeLogEntry

/**
 * Default conflict resolution strategy: the entry with the later
 * server-assigned logical timestamp wins.
 *
 * When timestamps are equal, the remote entry wins to ensure deterministic
 * resolution regardless of which device processes the conflict.
 *
 * Requirement 4.7: same-field conflicts resolved using server-assigned logical timestamp.
 */
class LastWriteWinsStrategy : ConflictResolutionStrategy {

    override fun resolve(local: ChangeLogEntry, remote: ChangeLogEntry): MergeResult {
        return if (remote.timestamp >= local.timestamp) {
            MergeResult.RemoteWins(supersededEntry = local)
        } else {
            MergeResult.LocalWins(supersededEntry = remote)
        }
    }
}
