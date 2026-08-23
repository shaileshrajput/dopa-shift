package com.dopashift.domain.usecase.todo

import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.exception.EntityNotFoundException
import com.dopashift.domain.repository.DailyTodoRepository
import java.time.Instant
import java.util.UUID

/**
 * Marks an existing DailyTodoItem as completed.
 * Requirement 4.3: item is retained with checked state, user can un-check to revert.
 */
class MarkTodoCompleteUseCase(private val repository: DailyTodoRepository) {

    /**
     * @throws EntityNotFoundException if the item does not exist
     */
    suspend fun execute(id: UUID, isCompleted: Boolean = true): DailyTodoItem {
        val existing = repository.findById(id)
            ?: throw EntityNotFoundException("DailyTodoItem not found: $id")

        val updated = existing.copy(
            isCompleted = isCompleted,
            updatedAt = Instant.now()
        )

        return repository.save(updated)
    }
}
