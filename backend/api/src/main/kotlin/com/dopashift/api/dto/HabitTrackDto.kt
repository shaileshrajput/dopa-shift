package com.dopashift.api.dto

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Request to activate a new 30-day habit track for a goal.
 * Requirements: 6.1
 */
data class ActivateHabitTrackRequest(
    @field:NotNull(message = "Goal ID is required")
    val goalId: UUID,

    val startDate: LocalDate? = null,

    @field:Size(min = 1, max = 30, message = "Descriptions must contain between 1 and 30 items")
    val descriptions: List<@Size(min = 1, max = 200, message = "Each description must be between 1 and 200 characters") String>
)

/**
 * Request body for PUT /v1/habits/{id}/checkpoints/{day}.
 * Accepts a status of COMPLETED or MISSED.
 * Requirements: 6.3, 6.4
 */
data class UpdateCheckpointRequest(
    @field:NotNull(message = "Status is required")
    val status: CheckpointStatus
)

/**
 * Response DTO representing a habit track summary (without full checkpoint list).
 */
data class HabitTrackSummaryResponse(
    val id: UUID,
    val goalId: UUID,
    val startDate: LocalDate,
    val currentDay: Int,
    val isFinished: Boolean
) {
    companion object {
        fun from(track: HabitTrack): HabitTrackSummaryResponse = HabitTrackSummaryResponse(
            id = track.id,
            goalId = track.goalId,
            startDate = track.startDate,
            currentDay = track.currentDay,
            isFinished = track.isFinished
        )
    }
}

/**
 * Response DTO representing a habit track with all checkpoints.
 */
data class HabitTrackDetailResponse(
    val id: UUID,
    val goalId: UUID,
    val startDate: LocalDate,
    val currentDay: Int,
    val isFinished: Boolean,
    val checkpoints: List<CheckpointResponse>
) {
    companion object {
        fun from(track: HabitTrack): HabitTrackDetailResponse = HabitTrackDetailResponse(
            id = track.id,
            goalId = track.goalId,
            startDate = track.startDate,
            currentDay = track.currentDay,
            isFinished = track.isFinished,
            checkpoints = track.checkpoints.map { CheckpointResponse.from(it) }
        )
    }
}

/**
 * Response DTO for a single habit checkpoint.
 */
data class CheckpointResponse(
    val id: UUID,
    val dayNumber: Int,
    val description: String,
    val status: CheckpointStatus,
    val completedAt: Instant?
) {
    companion object {
        fun from(checkpoint: HabitCheckpoint): CheckpointResponse = CheckpointResponse(
            id = checkpoint.id,
            dayNumber = checkpoint.dayNumber,
            description = checkpoint.description,
            status = checkpoint.status,
            completedAt = checkpoint.completedAt
        )
    }
}
