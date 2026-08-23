package com.dopashift.domain.strategy

import com.dopashift.domain.entity.ChangeLogEntry

/**
 * Represents the outcome of a conflict resolution between two ChangeLogEntry instances.
 */
sealed class MergeResult {
    /**
     * The remote entry wins; the local entry is superseded.
     */
    data class RemoteWins(val supersededEntry: ChangeLogEntry) : MergeResult()

    /**
     * The local entry wins; the remote entry is superseded.
     */
    data class LocalWins(val supersededEntry: ChangeLogEntry) : MergeResult()

    /**
     * A field-level merge was performed, combining non-conflicting edits.
     */
    data class FieldMerge(val merged: Map<String, ChangeLogEntry>) : MergeResult()
}
