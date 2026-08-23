package com.dopashift.domain.service

import com.dopashift.domain.entity.ChangeLogEntry

/**
 * Strategy interface for resolving sync conflicts between
 * local and remote change log entries.
 */
interface ConflictResolutionStrategy {
    fun resolve(local: ChangeLogEntry, remote: ChangeLogEntry): MergeResult
}

sealed class MergeResult {
    data class RemoteWins(val supersededEntry: ChangeLogEntry) : MergeResult()
    data class LocalWins(val supersededEntry: ChangeLogEntry) : MergeResult()
    data class FieldMerge(val merged: Map<String, ChangeLogEntry>) : MergeResult()
}
