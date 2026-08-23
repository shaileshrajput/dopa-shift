package com.dopashift.domain.usecase.todo

import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.exception.LimitExceededException
import com.dopashift.domain.repository.DailyTodoRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Creates a new DailyTodoItem for a user on a given day.
 * Enforces the 100-item-per-user-per-day limit (Requirement 4.2).
 */
class CreateTodoUseCase(private val repository: DailyTodoRepository) {

    companion object {
        const val MAX_ITEMS_PER_DAY = 100
    }

    /**
     * @throws LimitExceededException if the user already has 100 items on the given day
     * @throws IllegalArgumentException if text is blank or exceeds 500 characters (from entity init)
     */
    suspend fun execute(
        userId: UUID,
        text: String,
        dayDate: LocalDate,
        dueDateTime: Instant? = null
    ): DailyTodoItem {
        val currentCount = repository.countByUserIdAndDate(userId, dayDate)
        if (currentCount >= MAX_ITEMS_PER_DAY) {
            throw LimitExceededException(
                "Cannot create todo: maximum of $MAX_ITEMS_PER_DAY items per user per day reached"
            )
        }

        val now = Instant.now()
        val item = DailyTodoItem(
            id = UUID.randomUUID(),
            userId = userId,
            text = text,
            dueDateTime = dueDateTime,
            isCompleted = false,
            createdAt = now,
            updatedAt = now,
            dayDate = dayDate
        )

        return repository.save(item)
    }
}
