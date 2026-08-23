package com.dopashift.domain.usecase.habit

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import java.time.LocalDate
import java.util.UUID

/**
 * Creates a new 30-day HabitTrack for a goal, initializing all 30 checkpoints.
 *
 * Preconditions:
 * - The referenced goal must exist and belong to the user.
 * - The user must have at least one active goal (Requirement 1.4).
 *
 * Requirements: 6.1
 */
class ActivateHabitTrackUseCase(
    private val goalRepository: GoalRepository,
    private val habitTrackRepository: HabitTrackRepository
) {

    /**
     * @param userId The user activating the habit track.
     * @param goalId The goal to associate the track with.
     * @param startDate The start date for the track (defaults to today).
     * @param descriptions A list of 30 descriptions for each day's checkpoint (1–200 chars each).
     * @return The created and persisted HabitTrack with all 30 checkpoints.
     * @throws IllegalArgumentException if preconditions are not met.
     */
    suspend fun execute(
        userId: UUID,
        goalId: UUID,
        startDate: LocalDate,
        descriptions: List<String>
    ): HabitTrack {
        require(descriptions.size == 30) {
            "Exactly 30 checkpoint descriptions must be provided"
        }

        val activeGoalCount = goalRepository.countActiveByUserId(userId)
        require(activeGoalCount > 0) {
            "User must have at least one active goal to activate a habit track"
        }

        val goal = goalRepository.findById(goalId)
        requireNotNull(goal) {
            "Goal not found: $goalId"
        }
        require(goal.userId == userId) {
            "Goal does not belong to the specified user"
        }
        require(goal.isActive) {
            "Cannot activate a habit track for an inactive goal"
        }

        val trackId = UUID.randomUUID()

        val checkpoints = descriptions.mapIndexed { index, description ->
            HabitCheckpoint(
                id = UUID.randomUUID(),
                habitTrackId = trackId,
                dayNumber = index + 1,
                description = description,
                status = CheckpointStatus.PENDING,
                completedAt = null
            )
        }

        val track = HabitTrack(
            id = trackId,
            goalId = goalId,
            userId = userId,
            startDate = startDate,
            currentDay = 1,
            isFinished = false,
            checkpoints = checkpoints
        )

        return habitTrackRepository.save(track)
    }
}
