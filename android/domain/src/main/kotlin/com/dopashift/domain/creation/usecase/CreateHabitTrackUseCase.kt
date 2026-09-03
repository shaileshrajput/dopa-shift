package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreateHabitCommand
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.entity.HabitTrack

/**
 * Creates a [HabitTrack] (with exactly 30 checkpoints) for an existing, saved goal, from
 * any [com.dopashift.domain.creation.CreationOrigin].
 *
 * Shared verbatim by the Dashboard habit sheet and the dedicated Habits screen (DQC-1.10):
 * the same instance is invoked, so the resolved track, local persistence, and Change_Log
 * events are identical regardless of origin (Property 1). A zero-goal user instead uses the
 * atomic Inline_Goal_Capture use case; this use case never writes a track for a missing goal.
 */
interface CreateHabitTrackUseCase {
    /**
     * Resolve the authoring mode to 30 checkpoints and create the habit, writing local Room
     * first and enqueuing create Change_Log events regardless of connectivity (DQC-6.1).
     *
     * @param command the habit fields, including the existing `goalId` and authoring mode.
     * @return [CreationResult.Success] with an Undo token, or [CreationResult.Rejected] when
     *   the referenced goal does not exist (no-orphan invariant, DQC-3.4).
     */
    suspend operator fun invoke(command: CreateHabitCommand): CreationResult<HabitTrack>
}
