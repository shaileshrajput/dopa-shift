package com.dopashift.domain.repository

import com.dopashift.domain.entity.DailyTodoItem
import java.time.LocalDate
import java.util.UUID

/**
 * Port interface for daily to-do item persistence.
 * Implementations live in the infrastructure layer.
 */
interface DailyTodoRepository {
    suspend fun findById(id: UUID): DailyTodoItem?
    suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem>
    suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int
    suspend fun save(item: DailyTodoItem): DailyTodoItem
    suspend fun delete(id: UUID)
}
