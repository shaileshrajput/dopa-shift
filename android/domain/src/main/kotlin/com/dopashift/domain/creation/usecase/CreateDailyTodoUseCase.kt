package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreateTodoCommand
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.entity.DailyTodoItem

/**
 * Creates a standalone [DailyTodoItem] from any [com.dopashift.domain.creation.CreationOrigin].
 *
 * Shared verbatim by the Dashboard quick-add / full to-do sheet and the dedicated Daily
 * Tasks screen (DQC-1.10): the same instance is invoked, so acceptance/rejection and the
 * emitted Change_Log events are identical regardless of origin (Property 1).
 */
interface CreateDailyTodoUseCase {
    /**
     * Validate and create a to-do, writing local Room first and enqueuing create
     * Change_Log events regardless of connectivity (DQC-4.2, DQC-4.9).
     *
     * @param command the to-do fields plus the origin [com.dopashift.domain.creation.CorrelationId].
     * @return [CreationResult.Success] with an Undo token, or [CreationResult.ValidationError]
     *   when the text is blank/too long or the day already holds the daily cap (DQC-4.5, DQC-4.6, DQC-4.7).
     */
    suspend operator fun invoke(command: CreateTodoCommand): CreationResult<DailyTodoItem>
}
