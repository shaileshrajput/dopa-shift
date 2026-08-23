package com.dopashift.api.dto

import com.dopashift.domain.entity.GoalChecklistItem
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * Request body for POST /v1/goals/{id}/checklist — create a new checklist item.
 * Requirements: 1.3
 */
data class CreateChecklistItemRequest(
    @field:NotBlank(message = "Text is required")
    @field:Size(min = 1, max = 500, message = "Text must be between 1 and 500 characters")
    val text: String
)

/**
 * Request body for PUT /v1/goals/{goalId}/checklist/{itemId} — update a checklist item.
 * Requirements: 1.3
 */
data class UpdateChecklistItemRequest(
    @field:Size(min = 1, max = 500, message = "Text must be between 1 and 500 characters")
    val text: String? = null,

    val isCompleted: Boolean? = null
)

/**
 * Response DTO for a single goal checklist item.
 */
data class GoalChecklistItemResponse(
    val id: UUID,
    val goalId: UUID,
    val text: String,
    val isCompleted: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(item: GoalChecklistItem): GoalChecklistItemResponse = GoalChecklistItemResponse(
            id = item.id,
            goalId = item.goalId,
            text = item.text,
            isCompleted = item.isCompleted,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt
        )
    }
}
