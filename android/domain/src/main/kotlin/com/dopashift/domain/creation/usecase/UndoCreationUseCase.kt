package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.UndoToken

/**
 * Undo of a Dashboard creation (DQC-1.7).
 *
 * Deletes every [com.dopashift.domain.creation.EntityRef] carried by the supplied
 * [UndoToken] through the **standard delete path**, so a Change_Log **deletion** event
 * flows through the existing Sync_Engine for each entity — this is explicitly **not** a
 * local-only rollback (parent Req 4 AC4–AC5).
 *
 * For an Inline_Goal_Capture token (which references both a goal and its dependent habit),
 * Undo reverts the whole transaction as created — every referenced entity is deleted
 * (OQ-3 default). The token's Correlation_ID is preserved on the emitted deletion events so
 * the undo remains traceable to the originating action.
 */
interface UndoCreationUseCase {
    /**
     * Delete every entity referenced by [token] and emit a Change_Log deletion event for each.
     *
     * @param token the entities to delete, produced by a successful creation.
     * @return [CreationResult.Success] with [Unit] when all referenced entities were deleted
     *   (its own [UndoToken] echoes the input token); [CreationResult.Failure] if any deletion
     *   throws.
     */
    suspend operator fun invoke(token: UndoToken): CreationResult<Unit>
}
