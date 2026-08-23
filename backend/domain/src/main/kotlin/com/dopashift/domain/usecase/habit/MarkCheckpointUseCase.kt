package com.dopashift.domain.usecase.habit

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.HabitTrackRepository
import java.time.Instant
import java.util.UUID

/**
 * Marks a specific checkpoint as COMPLETED and records the completion timestamp.
 *
 * Requirements: 6.3
 */
class MarkCheckpointUseCase(
    private val habitTrackRepository: HabitTrackRepository
) {

    /**
     * @param trackId The habit track containing the checkpoint.
     * @param dayNumber The day number (1–30) to mark as completed.
     * @param completedAt The timestamp of completion (UTC).
     * @return The updated HabitTrack.
     * @throws IllegalArgumentException if preconditions are not met.
     * @throws IllegalStateException if the checkpoint is not in PENDING status.
     */
    suspend fun execute(
        trackId: UUID,
        dayNumber: Int,
        completedAt: Instant
    ): HabitTrack {
        require(dayNumber in 1..30) {
            "Day number must be between 1 and 30"
        }

        val track = habitTrackRepository.findById(trackId)
        requireNotNull(track) {
            "Habit track not found: $trackId"
        }

        check(!track.isFinished) {
            "Cannot mark a checkpoint on a finished habit track"
        }

        val checkpoint = track.checkpoints.find { it.dayNumber == dayNumber }
        requireNotNull(checkpoint) {
            "Checkpoint for day $dayNumber not found in track $trackId"
        }

        check(checkpoint.status == CheckpointStatus.PENDING) {
            "Checkpoint for day $dayNumber is already ${checkpoint.status}"
        }

        val updatedCheckpoints = track.checkpoints.map { cp ->
            if (cp.dayNumber == dayNumber) {
                cp.copy(
                    status = CheckpointStatus.COMPLETED,
                    completedAt = completedAt
                )
            } else {
                cp
            }
        }

        val updatedTrack = track.copy(checkpoints = updatedCheckpoints)
        return habitTrackRepository.save(updatedTrack)
    }
}
