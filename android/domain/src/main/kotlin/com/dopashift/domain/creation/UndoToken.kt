package com.dopashift.domain.creation

/**
 * Returned with a successful creation so the UI can offer a sync-safe Undo (DQC-1.6, DQC-1.7).
 *
 * Undo deletes every referenced entity through the standard delete path — emitting
 * a Change_Log deletion event through the Sync_Engine — rather than performing a
 * local-only rollback. For an Inline_Goal_Capture creation the token references both
 * the goal and its dependent habit, so Undo reverts the whole transaction (OQ-3 default).
 *
 * @property entityRefs the entities to delete on Undo, in creation order.
 * @property correlationId the [CorrelationId] shared by the originating creation action.
 */
data class UndoToken(
    val entityRefs: List<EntityRef>,
    val correlationId: CorrelationId
) {
    init {
        require(entityRefs.isNotEmpty()) { "UndoToken must reference at least one entity" }
    }
}
