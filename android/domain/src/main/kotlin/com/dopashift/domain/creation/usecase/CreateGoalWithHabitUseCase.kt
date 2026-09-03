package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.GoalWithHabit
import com.dopashift.domain.creation.InlineGoalWithHabitCommand

/**
 * Inline_Goal_Capture: creates a [com.dopashift.domain.entity.GoalProfile] and a dependent
 * [com.dopashift.domain.entity.HabitTrack] as ONE atomic local transaction (DQC-3.2, DQC-3.3,
 * DQC-6.2).
 *
 * This is the mechanism that closes the parent spec's zero-goal dead end without weakening the
 * no-orphan-references invariant: rather than bypassing the "a habit requires a goal" rule, the
 * client creates the goal first, inside the same transaction, then the habit referencing the
 * just-saved goal id (DQC-3.4).
 *
 * On any failure the whole unit rolls back — zero goals and zero habits persisted, and no
 * Change_Log events committed for the aborted unit (DQC-3.3). On success the goal's create
 * Change_Log events are appended BEFORE the habit's (and its checkpoints') so a replay on
 * another device never sees a habit before its goal exists (DQC-6.2).
 *
 * Reused verbatim by the Dashboard habit sheet's Inline_Goal_Capture step and by the
 * Intercept_Overlay (DQC-5.10) — there is no parallel data path.
 */
interface CreateGoalWithHabitUseCase {
    /**
     * Validate the inline goal, resolve the habit's 30 checkpoints, and persist both atomically.
     *
     * @param command the inline goal fields plus the habit authoring mode, sharing one
     *   Correlation_ID across the whole transaction.
     * @return [CreationResult.Success] carrying the created [GoalWithHabit] and an Undo token
     *   referencing both entities (goal first, then habit); [CreationResult.ValidationError]
     *   when the goal fields are invalid (nothing persisted); or [CreationResult.Failure] when
     *   the transaction rolls back (nothing persisted, no committed events).
     */
    suspend operator fun invoke(command: InlineGoalWithHabitCommand): CreationResult<GoalWithHabit>
}
