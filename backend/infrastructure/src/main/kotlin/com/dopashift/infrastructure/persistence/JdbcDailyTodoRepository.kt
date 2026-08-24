package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.repository.DailyTodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

@Repository
class JdbcDailyTodoRepository(
    private val jdbcTemplate: JdbcTemplate
) : DailyTodoRepository {

    override suspend fun findById(id: UUID): DailyTodoItem? = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM daily_todo_items WHERE id = ?",
            { rs, _ -> mapRow(rs) },
            id
        ).firstOrNull()
    }

    override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> =
        withContext(Dispatchers.IO) {
            jdbcTemplate.query(
                "SELECT * FROM daily_todo_items WHERE user_id = ? AND day_date = ? ORDER BY created_at",
                { rs, _ -> mapRow(rs) },
                userId, Date.valueOf(date)
            )
        }

    override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int =
        withContext(Dispatchers.IO) {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_todo_items WHERE user_id = ? AND day_date = ?",
                Int::class.java,
                userId, Date.valueOf(date)
            ) ?: 0
        }

    override suspend fun save(item: DailyTodoItem): DailyTodoItem = withContext(Dispatchers.IO) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM daily_todo_items WHERE id = ?",
            Int::class.java,
            item.id
        ) ?: 0

        if (exists > 0) {
            jdbcTemplate.update(
                """UPDATE daily_todo_items SET text = ?, due_date_time = ?, is_completed = ?, updated_at = ?
                   WHERE id = ?""",
                item.text,
                item.dueDateTime?.let { Timestamp.from(it) },
                item.isCompleted,
                Timestamp.from(item.updatedAt),
                item.id
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO daily_todo_items (id, user_id, text, due_date_time, is_completed, day_date, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                item.id, item.userId, item.text,
                item.dueDateTime?.let { Timestamp.from(it) },
                item.isCompleted, Date.valueOf(item.dayDate),
                Timestamp.from(item.createdAt), Timestamp.from(item.updatedAt)
            )
        }
        item
    }

    override suspend fun delete(id: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM daily_todo_items WHERE id = ?", id)
    }

    private fun mapRow(rs: ResultSet): DailyTodoItem = DailyTodoItem(
        id = rs.getObject("id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        text = rs.getString("text"),
        dueDateTime = rs.getTimestamp("due_date_time")?.toInstant(),
        isCompleted = rs.getBoolean("is_completed"),
        dayDate = rs.getDate("day_date").toLocalDate(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )
}
