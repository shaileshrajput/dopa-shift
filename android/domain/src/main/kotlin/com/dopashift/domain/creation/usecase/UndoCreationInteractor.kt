package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.DeviceIdProvider
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.creation.EntityRef
import com.dopashift.domain.creation.UndoToken
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import java.util.UUID
import javax.inject.Inject

/**
 * The single implementation of [UndoCreationUseCase] (DQC-1.7), used by the Dashboard's Undo
 * snackbar. Pure Kotlin: it depends only on domain ports and performs no network call — each
 * repository writes local Room first and the deletion Change_Log event is enqueued for the
 * Sync_Engine (parent Req 4 AC4–AC5).
 *
 * For each [EntityRef] in the [UndoToken] it:
 * 1. Loads the entity via the matching repository so the deletion event can be scoped to the
 *    owning `userId` (a read cross-checks the authenticated identity rather than trusting a
 *    client-supplied id alone).
 * 2. Deletes it through the standard delete path ([GoalRepository.delete] /
 *    [HabitTrackRepository.delete] / [DailyTodoRepository.delete]) — never a local-only rollback.
 * 3. Appends one Change_Log **deletion** event (null value) through the shared [ChangeLogEmitter]
 *    so the deletion replays on other devices via the existing Sync_Engine (DQC-1.7).
 *
 * References are processed in **reverse creation order** — the token lists the goal first and its
 * dependent habit second (DQC-6.2), so undo removes the dependent habit before its goal. This
 * mirrors the create ordering: replay of the deletions never leaves a temporarily orphaned habit
 * pointing at an already-deleted goal.
 *
 * If a referenced entity is already gone (e.g. deleted on another device), that ref is skipped
 * idempotently. Any thrown failure is surfaced as [CreationResult.Failure] with nothing further
 * attempted.
 */
class UndoCreationInteractor @Inject constructor(
    private val goalRepository: GoalRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val dailyTodoRepository: DailyTodoRepository,
    changeLogRepository: ChangeLogRepository,
    deviceIdProvider: DeviceIdProvider,
    clock: DomainClock
) : UndoCreationUseCase {

    private val changeLog = ChangeLogEmitter(changeLogRepository, deviceIdProvider, clock)

    override suspend fun invoke(token: UndoToken): CreationResult<Unit> {
        return try {
            // Delete dependents before their parents (reverse creation order) so a replayed
            // deletion sequence never references an already-deleted goal (inverse of DQC-6.2).
            for (ref in token.entityRefs.asReversed()) {
                deleteRef(ref)
            }
            CreationResult.Success(value = Unit, undo = token)
        } catch (t: Throwable) {
            CreationResult.Failure(t)
        }
    }

    /**
     * Delete a single [EntityRef] through its repository's standard delete path and emit the
     * corresponding Change_Log deletion event. A no-op when the entity no longer exists so undo
     * is idempotent.
     */
    private suspend fun deleteRef(ref: EntityRef) {
        when (ref.type) {
            GOAL_REF_TYPE -> {
                val goal = goalRepository.findById(ref.id) ?: return
                goalRepository.delete(goal.id)
                changeLog.appendDelete(
                    entityId = goal.id,
                    entityType = GOAL_ENTITY_TYPE,
                    userId = goal.userId
                )
            }

            HABIT_REF_TYPE -> {
                val track = habitTrackRepository.findById(ref.id) ?: return
                habitTrackRepository.delete(track.id)
                changeLog.appendDelete(
                    entityId = track.id,
                    entityType = TRACK_ENTITY_TYPE,
                    userId = track.userId
                )
            }

            TODO_REF_TYPE -> {
                val todo = dailyTodoRepository.findById(ref.id) ?: return
                dailyTodoRepository.delete(todo.id)
                changeLog.appendDelete(
                    entityId = todo.id,
                    entityType = TODO_ENTITY_TYPE,
                    userId = todo.userId
                )
            }

            else -> throw IllegalArgumentException("Unknown EntityRef type for undo: ${ref.type}")
        }
    }

    private companion object {
        /** [EntityRef.type] values produced by the creation interactors' Undo tokens. */
        const val GOAL_REF_TYPE = "goal"
        const val HABIT_REF_TYPE = "habit_track"
        const val TODO_REF_TYPE = "todo"

        /** Change_Log `entityType`s, matching each domain entity's class simple name. */
        const val GOAL_ENTITY_TYPE = "GoalProfile"
        const val TRACK_ENTITY_TYPE = "HabitTrack"
        const val TODO_ENTITY_TYPE = "DailyTodoItem"
    }
}
