package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.DeviceIdProvider
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.creation.EntityRef
import com.dopashift.domain.creation.GoalValidator
import com.dopashift.domain.creation.GoalWithHabit
import com.dopashift.domain.creation.HabitAuthoringResolver
import com.dopashift.domain.creation.InlineGoalWithHabitCommand
import com.dopashift.domain.creation.TransactionRunner
import com.dopashift.domain.creation.UndoToken
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import java.util.UUID
import javax.inject.Inject

/**
 * The single implementation of [CreateGoalWithHabitUseCase] (Inline_Goal_Capture), reused verbatim
 * by the Dashboard habit sheet and the Intercept_Overlay (DQC-3.2, DQC-3.3, DQC-6.2, DQC-5.10).
 *
 * It reuses the same [GoalValidator] as [CreateGoalInteractor] and the same
 * [HabitAuthoringResolver] as [CreateHabitTrackInteractor], so a goal-with-habit created inline
 * validates and resolves identically to one created via the single-entity path — there is no
 * parallel implementation (DQC-1.10).
 *
 * Flow (DQC-3.3, DQC-6.2):
 * 1. Validate the goal fields with the shared [GoalValidator], building `existingNamesLower` from
 *    the user's own goals so uniqueness is case-insensitive and scoped to the authenticated user.
 *    On any field error, return [CreationResult.ValidationError] and persist nothing — no
 *    transaction is opened.
 * 2. Otherwise run the whole unit inside [TransactionRunner.inTransaction]:
 *    - Build and [GoalRepository.save] the [GoalProfile].
 *    - Resolve the authoring mode to exactly 30 checkpoints via [HabitAuthoringResolver], then
 *      [HabitTrackRepository.save] the [HabitTrack] referencing the just-saved `goalId` — the
 *      client never writes a track with a missing/unsaved goal id (no-orphan, DQC-3.4).
 *    - Append Change_Log events in fixed order: all goal `create` events FIRST, then the habit
 *      `create` events and one per checkpoint (DQC-6.2).
 * 3. If any step throws, the transaction rolls back: zero goals and zero habits persisted and no
 *    Change_Log events committed for the aborted unit (DQC-3.3). The interactor maps the thrown
 *    cause to [CreationResult.Failure].
 *
 * The goal command's [InlineGoalWithHabitCommand.correlationId] is shared across the transaction,
 * so both the goal and habit writes and the returned [UndoToken] carry one Correlation_ID.
 */
class CreateGoalWithHabitInteractor @Inject constructor(
    private val goalRepository: GoalRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val changeLogRepository: ChangeLogRepository,
    private val authoringResolver: HabitAuthoringResolver,
    private val transactionRunner: TransactionRunner,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: DomainClock
) : CreateGoalWithHabitUseCase {

    private val changeLog = ChangeLogEmitter(changeLogRepository, deviceIdProvider, clock)

    override suspend fun invoke(
        command: InlineGoalWithHabitCommand
    ): CreationResult<GoalWithHabit> {
        val goalCommand = command.goal

        val trimmedName = goalCommand.name.trim()
        val trimmedCategory = goalCommand.category.trim()
        val trimmedKeywords = goalCommand.keywords.map { it.trim() }

        val existingNamesLower = goalRepository.findByUserId(goalCommand.userId)
            .asSequence()
            .map { it.name.trim().lowercase() }
            .toSet()

        val errors = GoalValidator.validate(
            name = goalCommand.name,
            category = goalCommand.category,
            keywords = goalCommand.keywords,
            existingNamesLower = existingNamesLower
        )
        if (errors.isNotEmpty()) {
            return CreationResult.ValidationError(errors)
        }

        return try {
            val result = transactionRunner.inTransaction {
                val now = clock.now()

                // 1. Persist the goal first so the habit can reference its saved id.
                val goal = GoalProfile(
                    id = UUID.randomUUID(),
                    userId = goalCommand.userId,
                    name = trimmedName,
                    category = trimmedCategory,
                    keywords = trimmedKeywords,
                    createdAt = now,
                    updatedAt = now,
                    isActive = true
                )
                val savedGoal = goalRepository.save(goal)

                // 2. Resolve 30 checkpoints and persist the habit referencing the saved goal id
                //    (never a missing/unsaved goalId — DQC-3.4).
                val resolution = authoringResolver.resolve(
                    authoring = command.authoring,
                    category = savedGoal.category,
                    goalId = savedGoal.id,
                    userId = goalCommand.userId,
                    today = clock.today(),
                    existingActiveTrackPresent = false,
                    withReminder = command.withReminder
                )
                val savedHabit = habitTrackRepository.save(resolution.track)

                // 3. Append Change_Log events in fixed order: goal FIRST, then habit + checkpoints
                //    (DQC-6.2). All within the same transaction so nothing commits on rollback.
                emitGoalCreateEvents(savedGoal)
                emitHabitCreateEvents(savedHabit)

                GoalWithHabit(goal = savedGoal, habit = savedHabit)
            }

            CreationResult.Success(
                value = result,
                undo = UndoToken(
                    entityRefs = listOf(
                        EntityRef(type = GOAL_REF_TYPE, id = result.goal.id),
                        EntityRef(type = HABIT_REF_TYPE, id = result.habit.id)
                    ),
                    correlationId = command.correlationId
                )
            )
        } catch (t: Throwable) {
            // The transaction rolled back: zero goals, zero habits, no committed events (DQC-3.3).
            CreationResult.Failure(t)
        }
    }

    /** Append the goal's create events (DQC-6.2 — emitted before any habit event). */
    private suspend fun emitGoalCreateEvents(goal: GoalProfile) {
        changeLog.appendCreate(
            entityId = goal.id,
            entityType = GOAL_ENTITY_TYPE,
            userId = goal.userId,
            fields = buildList {
                add("name" to goal.name)
                add("category" to goal.category)
                add("keywords" to goal.keywords.joinToString(","))
                add("isActive" to goal.isActive.toString())
            }
        )
    }

    /**
     * Append the habit track's create events, then one create event per checkpoint, matching the
     * habit Change_Log shape produced by [CreateHabitTrackInteractor].
     */
    private suspend fun emitHabitCreateEvents(track: HabitTrack) {
        changeLog.appendCreate(
            entityId = track.id,
            entityType = TRACK_ENTITY_TYPE,
            userId = track.userId,
            fields = buildList {
                add("goalId" to track.goalId.toString())
                add("startDate" to track.startDate.toString())
                add("currentDay" to track.currentDay.toString())
                add("isFinished" to track.isFinished.toString())
            }
        )

        for (checkpoint in track.checkpoints) {
            changeLog.appendCreate(
                entityId = checkpoint.id,
                entityType = CHECKPOINT_ENTITY_TYPE,
                userId = track.userId,
                fields = buildList {
                    add("habitTrackId" to checkpoint.habitTrackId.toString())
                    add("dayNumber" to checkpoint.dayNumber.toString())
                    add("description" to checkpoint.description)
                    add("status" to checkpoint.status.name)
                }
            )
        }
    }

    private companion object {
        /** Change_Log `entityType` for the goal, matching the entity's class name convention. */
        const val GOAL_ENTITY_TYPE = "GoalProfile"

        /** Change_Log `entityType` for the track. */
        const val TRACK_ENTITY_TYPE = "HabitTrack"

        /** Change_Log `entityType` for each checkpoint. */
        const val CHECKPOINT_ENTITY_TYPE = "HabitCheckpoint"

        /** [EntityRef] logical type for the goal in the Undo token (goal first — DQC-6.2, OQ-3). */
        const val GOAL_REF_TYPE = "goal"

        /** [EntityRef] logical type for the habit track in the Undo token. */
        const val HABIT_REF_TYPE = "habit_track"
    }
}
