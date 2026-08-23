package com.dopashift.domain.repository

import com.dopashift.domain.entity.DailyTodoItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/**
 * Repository port interface for daily to-do item persistence.
 */
interface DailyTodoRepository {
    suspend fun findById(id: UUID): DailyTodoItem?
    suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem>
    suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int
    suspend fun save(item: DailyTodoItem): DailyTodoItem
    suspend fun delete(id: UUID)

    /**
     * Observes to-do items for a user on a given date as a reactive Flow.
     * Room emits a new list whenever the underlying data changes.
     */
    fun observeByUserIdAndDate(userId: UUID, date: LocalDate): Flow<List<DailyTodoItem>>
}
