package com.dopashift.domain.usecase.todo

import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.exception.EntityNotFoundException
import com.dopashift.domain.repository.DailyTodoRepository
import java.time.Instant
import java.util.UUID

/**
 * Updates an existing DailyTodoItem's text and/or due date.
 * Text validation (1–500 chars) is enforced by the DailyTodoItem entity init block.
 */
class UpdateTodoUseCase(private val repository: DailyTodoRepository) {

    /**
     * @throws EntityNotFoundException if the item does not exist
     * @throws IllegalArgumentException if text is blank or exceeds 500 characters (from entity init)
     */
    suspend fun execute(
        id: UUID,
        text: String? = null,
        dueDateTime: Instant? = null,
        clearDueDateTime: Boolean = false
    ): DailyTodoItem {
        val existing = repository.findById(id)
            ?: throw EntityNotFoundException("DailyTodoItem not found: $id")

        val updated = existing.copy(
            text = text ?: existing.text,
            dueDateTime = when {
                clearDueDateTime -> null
                dueDateTime != null -> dueDateTime
                else -> existing.dueDateTime
            },
            updatedAt = Instant.now()
        )

        return repository.save(updated)
    }
}
