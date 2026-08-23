package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * A single day's micro-habit checkpoint within a 30-day HabitTrack.
 *
 * Validation constraints:
 * - dayNumber: 1–30
 * - description: 1–200 characters
 */
data class HabitCheckpoint(
    val id: UUID,
    val habitTrackId: UUID,
    val dayNumber: Int,
    val description: String,
    val status: CheckpointStatus,
    val completedAt: Instant?
) {
    init {
        require(dayNumber in 1..30) {
            "Day number must be between 1 and 30"
        }
        require(description.isNotBlank() && description.length in 1..200) {
            "Checkpoint description must be between 1 and 200 characters"
        }
    }
}
