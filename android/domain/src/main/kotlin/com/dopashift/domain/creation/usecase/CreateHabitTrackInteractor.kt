package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreateHabitCommand
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.DeviceIdProvider
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.creation.EntityRef
import com.dopashift.domain.creation.HabitAuthoringResolver
import com.dopashift.domain.creation.UndoToken
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import javax.inject.Inject

/**
 * The single implementation of [CreateHabitTrackUseCase], shared verbatim by the Dashboard
 * and the dedicated Habits screen (DQC-1.10). Pure Kotlin, no network call: the repository
 * writes local Room first and the create Change_Log events are enqueued for the Sync_Engine
 * (DQC-6.1).
 *
 * Flow:
 * 1. Load the referenced goal via [GoalRepository.findById]; if it is missing (or belongs to
 *    another user), reject with [CreationResult.Rejected] and persist nothing — the client
 *    never writes a [HabitTrack] referencing a missing/unsaved goal (no-orphan, DQC-3.4).
 * 2. Resolve the [com.dopashift.domain.creation.HabitAuthoring] mode to exactly 30 valid
 *    checkpoints via the shared [HabitAuthoringResolver], using the goal's category and
 *    today's date in the user's tz; flag precedence when an active track already exists
 *    for the goal (DQC-3.10, DQC-3.11).
 * 3. [HabitTrackRepository.save] the track (local-first), append create Change_Log events —
 *    the track's fields first, then one per checkpoint — and return [CreationResult.Success]
 *    with an [UndoToken] referencing the habit track.
 */
class CreateHabitTrackInteractor @Inject constructor(
    private val goalRepository: GoalRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val changeLogRepository: ChangeLogRepository,
    private val authoringResolver: HabitAuthoringResolver,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: DomainClock
) : CreateHabitTrackUseCase {

    private val changeLog = ChangeLogEmitter(changeLogRepository, deviceIdProvider, clock)

    override suspend fun invoke(command: CreateHabitCommand): CreationResult<HabitTrack> {
        val goal = goalRepository.findById(command.goalId)
        if (goal == null || goal.userId != command.userId) {
            return CreationResult.Rejected(
                reason = "The goal for this habit no longer exists.",
                retainedInput = command
            )
        }

        return try {
            val existingActiveTrackPresent =
                habitTrackRepository.findActiveByGoalId(command.goalId).isNotEmpty()

            val resolution = authoringResolver.resolve(
                authoring = command.authoring,
                category = goal.category,
                goalId = command.goalId,
                userId = command.userId,
                today = clock.today(),
                existingActiveTrackPresent = existingActiveTrackPresent,
                withReminder = command.withReminder
            )

            val saved = habitTrackRepository.save(resolution.track)

            emitCreateEvents(saved)

            CreationResult.Success(
                value = saved,
                undo = UndoToken(
                    entityRefs = listOf(EntityRef(type = ENTITY_REF_TYPE, id = saved.id)),
                    correlationId = command.correlationId
                )
            )
        } catch (t: Throwable) {
            CreationResult.Failure(t)
        }
    }

    /**
     * Append the habit track's create events first, then one create event per checkpoint,
     * so the ordering matches the design's Change_Log shape for habits (DQC-6.2).
     */
    private suspend fun emitCreateEvents(track: HabitTrack) {
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
        /** Change_Log `entityType` for the track, matching the entity's class name convention. */
        const val TRACK_ENTITY_TYPE = "HabitTrack"

        /** Change_Log `entityType` for each checkpoint. */
        const val CHECKPOINT_ENTITY_TYPE = "HabitCheckpoint"

        /** [EntityRef] logical type for Undo, per the design's token vocabulary. */
        const val ENTITY_REF_TYPE = "habit_track"
    }
}
