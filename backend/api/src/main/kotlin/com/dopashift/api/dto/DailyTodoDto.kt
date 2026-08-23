package com.dopashift.api.dto

import com.dopashift.domain.entity.DailyTodoItem
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Request body for POST /v1/todos — create a new daily todo item.
 * Requirements: 4.1, 4.2, 4.12
 */
data class CreateTodoRequest(
    @field:NotBlank(message = "Text is required")
    @field:Size(min = 1, max = 500, message = "Text must be between 1 and 500 characters")
    val text: String,

    val dueDateTime: Instant? = null,

    val dayDate: LocalDate? = null
)

/**
 * Request body for PUT /v1/todos/{id} — update an existing daily todo item.
 * Requirements: 4.2, 4.3, 4.12
 */
data class UpdateTodoRequest(
    @field:Size(min = 1, max = 500, message = "Text must be between 1 and 500 characters")
    val text: String? = null,

    val dueDateTime: Instant? = null,

    val clearDueDateTime: Boolean = false,

    val isCompleted: Boolean? = null
)

/**
 * Response DTO for a single daily todo item.
 */
data class DailyTodoResponse(
    val id: UUID,
    val text: String,
    val dueDateTime: Instant?,
    val isCompleted: Boolean,
    val dayDate: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(item: DailyTodoItem): DailyTodoResponse = DailyTodoResponse(
            id = item.id,
            text = item.text,
            dueDateTime = item.dueDateTime,
            isCompleted = item.isCompleted,
            dayDate = item.dayDate,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt
        )
    }
}
