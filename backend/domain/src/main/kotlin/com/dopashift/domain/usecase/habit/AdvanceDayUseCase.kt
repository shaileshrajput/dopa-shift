package com.dopashift.domain.usecase.habit

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.HabitTrackRepository
import java.util.UUID

/**
 * Advances the current-day pointer to the next uncompleted day after the user
 * marks the current day as complete. If all 30 days are completed or missed,
 * marks the track as finished.
 *
 * Requirements: 6.3, 6.6
 */
class AdvanceDayUseCase(
    private val habitTrackRepository: HabitTrackRepository
) {

    /**
     * @param trackId The habit track to advance.
     * @return The updated HabitTrack with advanced currentDay (or marked as finished).
     * @throws IllegalArgumentException if the track is not found.
     * @throws IllegalStateException if the track is already finished.
     */
    suspend fun execute(trackId: UUID): HabitTrack {
        val track = habitTrackRepository.findById(trackId)
        requireNotNull(track) {
            "Habit track not found: $trackId"
        }

        check(!track.isFinished) {
            "Cannot advance day on a finished habit track"
        }

        // Find the next uncompleted (PENDING) checkpoint after the current day
        val nextPendingDay = track.checkpoints
            .filter { it.dayNumber > track.currentDay && it.status == CheckpointStatus.PENDING }
            .minByOrNull { it.dayNumber }

        val updatedTrack = if (nextPendingDay != null) {
            // Advance to the next pending day
            track.copy(currentDay = nextPendingDay.dayNumber)
        } else {
            // All days from currentDay onward are completed or missed — check if track is done
            val allResolved = track.checkpoints.all { it.status != CheckpointStatus.PENDING }
            if (allResolved) {
                track.copy(isFinished = true)
            } else {
                // There are still pending days before current day — find earliest pending
                val earliestPending = track.checkpoints
                    .filter { it.status == CheckpointStatus.PENDING }
                    .minByOrNull { it.dayNumber }

                if (earliestPending != null) {
                    track.copy(currentDay = earliestPending.dayNumber)
                } else {
                    track.copy(isFinished = true)
                }
            }
        }

        return habitTrackRepository.save(updatedTrack)
    }
}
