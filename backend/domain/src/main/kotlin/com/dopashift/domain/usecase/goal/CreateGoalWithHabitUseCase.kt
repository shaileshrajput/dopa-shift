package com.dopashift.domain.usecase.goal

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.exception.GoalNameAlreadyExistsException
import com.dopashift.domain.exception.KeywordValidationException
import com.dopashift.domain.port.TransactionRunner
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Creates a [GoalProfile] and, optionally, a dependent [HabitTrack] as a single
 * atomic server-side unit (Inline_Goal_Capture, DQC-3.3).
 *
 * When a nested habit track is supplied, both the goal and the habit (with its 30
 * checkpoints) are persisted inside one [TransactionRunner.execute] block: if habit
 * creation fails for any reason, the goal insert is rolled back too, so no partially
 * created state and no orphaned habit is ever committed.
 *
 * The habit always references the goal created in the SAME transaction, so the
 * API-layer no-orphan invariant (DQC-5.5) — reject any habit with an absent/invalid
 * goalId — holds by construction for the nested case.
 *
 * When no habit track is supplied this behaves exactly like [CreateGoalUseCase],
 * keeping the endpoint additive and existing payloads valid.
 *
 * Requirements: 3.3, 5.5
 */
class CreateGoalWithHabitUseCase(
    private val goalRepository: GoalRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val transactionRunner: TransactionRunner
) {

    suspend fun execute(command: CreateGoalWithHabitCommand): GoalWithHabit {
        validateKeywords(command.keywords)
        validateNameUniqueness(command.userId, command.name)

        val habit = command.habitTrack
        if (habit != null) {
            validateDescriptions(habit.descriptions)
        }

        // Persist the goal and (optionally) the dependent habit atomically.
        return transactionRunner.execute {
            val now = Instant.now()
            val savedGoal = goalRepository.save(
                GoalProfile(
                    id = UUID.randomUUID(),
                    userId = command.userId,
                    name = command.name,
                    category = command.category,
                    keywords = command.keywords,
                    createdAt = now,
                    updatedAt = now,
                    isActive = true
                )
            )

            if (habit == null) {
                GoalWithHabit(goal = savedGoal, habitTrack = null)
            } else {
                val savedHabit = createHabitTrack(
                    userId = command.userId,
                    goalId = savedGoal.id,
                    startDate = habit.startDate ?: LocalDate.now(),
                    descriptions = habit.descriptions
                )
                GoalWithHabit(goal = savedGoal, habitTrack = savedHabit)
            }
        }
    }

    private suspend fun createHabitTrack(
        userId: UUID,
        goalId: UUID,
        startDate: LocalDate,
        descriptions: List<String>
    ): HabitTrack {
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

    private suspend fun validateNameUniqueness(userId: UUID, name: String) {
        val existing = goalRepository.findByUserIdAndName(userId, name.trim())
        if (existing != null) {
            throw GoalNameAlreadyExistsException(name)
        }
    }

    private fun validateKeywords(keywords: List<String>) {
        if (keywords.size > 20) {
            throw KeywordValidationException("A goal can have at most 20 keywords")
        }
        if (keywords.isEmpty()) {
            throw KeywordValidationException("At least one keyword is required")
        }
        keywords.forEachIndexed { index, keyword ->
            if (keyword.isBlank() || keyword.length !in 1..50) {
                throw KeywordValidationException(
                    "Keyword at index $index must be between 1 and 50 characters"
                )
            }
        }
    }

    private fun validateDescriptions(descriptions: List<String>) {
        require(descriptions.size == 30) {
            "Exactly 30 checkpoint descriptions must be provided"
        }
    }
}

/**
 * Command for creating a goal with an optional nested habit track.
 * The [habitTrack] is null for a plain goal creation.
 */
data class CreateGoalWithHabitCommand(
    val userId: UUID,
    val name: String,
    val category: String,
    val keywords: List<String>,
    val habitTrack: NestedHabitTrack? = null
)

/**
 * Nested habit-track payload committed together with its goal.
 */
data class NestedHabitTrack(
    val startDate: LocalDate? = null,
    val descriptions: List<String>
)

/**
 * Result of [CreateGoalWithHabitUseCase]: the created goal and, when requested,
 * the dependent habit track created in the same transaction.
 */
data class GoalWithHabit(
    val goal: GoalProfile,
    val habitTrack: HabitTrack?
)
