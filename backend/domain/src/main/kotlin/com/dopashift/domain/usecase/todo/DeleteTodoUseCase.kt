package com.dopashift.domain.usecase.todo

import com.dopashift.domain.exception.EntityNotFoundException
import com.dopashift.domain.repository.DailyTodoRepository
import java.util.UUID

/**
 * Deletes an existing DailyTodoItem by ID.
 */
class DeleteTodoUseCase(private val repository: DailyTodoRepository) {

    /**
     * @throws EntityNotFoundException if the item does not exist
     */
    suspend fun execute(id: UUID) {
        repository.findById(id)
            ?: throw EntityNotFoundException("DailyTodoItem not found: $id")

        repository.delete(id)
    }
}
