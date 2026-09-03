package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreateGoalCommand
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.entity.GoalProfile

/**
 * Creates a [GoalProfile] from any [com.dopashift.domain.creation.CreationOrigin].
 *
 * This is the single goal-creation use case shared by BOTH the Dashboard quick-create
 * flow and the dedicated Goals screen (DQC-1.10): the Dashboard invokes the same instance,
 * so validation, local persistence, and Change_Log emission are identical regardless of
 * origin — there is no parallel data path (Property 1, DQC-6.1).
 */
interface CreateGoalUseCase {
    /**
     * Validate and create a goal, writing local Room first and enqueuing create
     * Change_Log events regardless of connectivity (DQC-2.8).
     *
     * @param command the goal fields plus the origin [com.dopashift.domain.creation.CorrelationId].
     * @return [CreationResult.Success] with an Undo token, or [CreationResult.ValidationError]
     *   when a field is invalid or the name duplicates an existing goal (DQC-2.6).
     */
    suspend operator fun invoke(command: CreateGoalCommand): CreationResult<GoalProfile>
}
