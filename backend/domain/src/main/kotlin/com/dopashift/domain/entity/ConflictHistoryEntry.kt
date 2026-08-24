package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * Records a superseded change log entry that lost conflict resolution.
 * Retained for at least 90 days per Requirement 4.7.
 *
 * @property id unique identifier for this history record
 * @property userId the user who owns this conflict history record
 * @property changeLogId the winning change_log entry ID that caused this supersession
 * @property supersededValue the JSON-encoded value that was overridden (null if the superseded entry was a delete)
 * @property supersededDeviceId the device that produced the losing entry
 * @property supersededTimestamp the timestamp of the losing entry
 * @property resolutionReason why this entry was superseded (e.g., "LWW", "DELETE_WINS")
 * @property resolvedAt when the conflict was resolved
 * @property createdAt when this history record was created
 */
data class ConflictHistoryEntry(
    val id: UUID,
    val userId: UUID,
    val changeLogId: UUID,
    val supersededValue: String?,
    val supersededDeviceId: String,
    val supersededTimestamp: Instant,
    val resolutionReason: String,
    val resolvedAt: Instant,
    val createdAt: Instant
) {
    init {
        require(supersededDeviceId.isNotBlank()) {
            "Superseded device ID must not be blank"
        }
        require(resolutionReason.isNotBlank()) {
            "Resolution reason must not be blank"
        }
    }

    companion object {
        const val REASON_LWW = "LWW"
        const val REASON_DELETE_WINS = "DELETE_WINS"
    }
}
