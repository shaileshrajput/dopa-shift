package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * A single day's micro-habit checkpoint within a HabitTrack.
 */
data class HabitCheckpoint(
    val id: UUID,
    val habitTrackId: UUID,
    val dayNumber: Int,
    val description: String,
    val status: CheckpointStatus,
    val completedAt: Instant? = null
) {
    init {
        require(dayNumber in 1..30) { "Day number must be between 1 and 30" }
        require(description.length in 1..200) { "Description must be 1-200 characters" }
    }
}

enum class CheckpointStatus { PENDING, COMPLETED, MISSED }
