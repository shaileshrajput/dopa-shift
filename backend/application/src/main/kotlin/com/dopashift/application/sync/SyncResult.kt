package com.dopashift.application.sync

import com.dopashift.domain.entity.ChangeLogEntry

/**
 * Result of processing a batch of sync entries through the [SyncProcessor].
 *
 * @property accepted entries that were appended without conflict
 * @property resolved entries that triggered conflict resolution
 */
data class SyncResult(
    val accepted: List<AcceptedEntry>,
    val resolved: List<ResolvedConflict>
) {
    /** Total number of entries processed (accepted + resolved). */
    val totalProcessed: Int get() = accepted.size + resolved.size

    /** Whether any conflicts were detected and resolved. */
    val hasConflicts: Boolean get() = resolved.isNotEmpty()
}

/**
 * An entry that was accepted without conflict.
 *
 * @property entry the change log entry with server-assigned timestamp
 */
data class AcceptedEntry(
    val entry: ChangeLogEntry
)

/**
 * Details of a conflict that was detected and resolved.
 *
 * @property incomingEntry the entry that arrived from the client (with server timestamp assigned)
 * @property winningEntry the entry that won conflict resolution
 * @property supersededEntry the entry that lost and was stored in conflict history
 * @property reason the resolution strategy applied (e.g., "LWW", "DELETE_WINS")
 */
data class ResolvedConflict(
    val incomingEntry: ChangeLogEntry,
    val winningEntry: ChangeLogEntry,
    val supersededEntry: ChangeLogEntry,
    val reason: String
)
