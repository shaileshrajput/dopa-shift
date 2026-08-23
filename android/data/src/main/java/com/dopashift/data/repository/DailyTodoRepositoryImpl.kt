package com.dopashift.data.repository

import com.dopashift.data.local.dao.DailyTodoDao
import com.dopashift.data.local.entity.LocalDailyTodo
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.repository.DailyTodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [DailyTodoRepository].
 * Maps between [LocalDailyTodo] (Room entity) and [DailyTodoItem] (domain entity),
 * handling UUID↔String, Instant↔Long, and LocalDate↔String conversions.
 */
@Singleton
class DailyTodoRepositoryImpl @Inject constructor(
    private val dailyTodoDao: DailyTodoDao
) : DailyTodoRepository {

    override suspend fun findById(id: UUID): DailyTodoItem? {
        return dailyTodoDao.findById(id.toString())?.toDomain()
    }

    override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> {
        return dailyTodoDao.findByUserIdAndDate(userId.toString(), date.toString()).map { it.toDomain() }
    }

    override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int {
        return dailyTodoDao.countByUserIdAndDate(userId.toString(), date.toString())
    }

    override suspend fun save(item: DailyTodoItem): DailyTodoItem {
        dailyTodoDao.upsert(item.toLocal())
        return item
    }

    override suspend fun delete(id: UUID) {
        dailyTodoDao.deleteById(id.toString())
    }

    override fun observeByUserIdAndDate(userId: UUID, date: LocalDate): Flow<List<DailyTodoItem>> {
        return dailyTodoDao.observeByUserIdAndDate(userId.toString(), date.toString())
            .map { locals -> locals.map { it.toDomain() } }
    }

    private fun LocalDailyTodo.toDomain(): DailyTodoItem {
        return DailyTodoItem(
            id = UUID.fromString(id),
            userId = UUID.fromString(userId),
            text = text,
            dueDateTime = dueDateTime?.let { Instant.ofEpochMilli(it) },
            isCompleted = isCompleted,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            dayDate = LocalDate.parse(dayDate)
        )
    }

    private fun DailyTodoItem.toLocal(): LocalDailyTodo {
        return LocalDailyTodo(
            id = id.toString(),
            userId = userId.toString(),
            text = text,
            dueDateTime = dueDateTime?.toEpochMilli(),
            isCompleted = isCompleted,
            dayDate = dayDate.toString(),
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli()
        )
    }
}
