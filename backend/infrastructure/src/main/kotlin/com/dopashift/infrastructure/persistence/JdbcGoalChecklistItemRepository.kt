package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.repository.GoalChecklistItemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcGoalChecklistItemRepository(
    private val jdbcTemplate: JdbcTemplate
) : GoalChecklistItemRepository {

    override suspend fun findById(id: UUID): GoalChecklistItem? = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM goal_checklist_items WHERE id = ?",
            { rs, _ -> mapRow(rs) },
            id
        ).firstOrNull()
    }

    override suspend fun findByGoalId(goalId: UUID): List<GoalChecklistItem> = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM goal_checklist_items WHERE goal_id = ? ORDER BY created_at",
            { rs, _ -> mapRow(rs) },
            goalId
        )
    }

    override suspend fun findByUserId(userId: UUID): List<GoalChecklistItem> = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM goal_checklist_items WHERE user_id = ? ORDER BY created_at",
            { rs, _ -> mapRow(rs) },
            userId
        )
    }

    override suspend fun save(item: GoalChecklistItem): GoalChecklistItem = withContext(Dispatchers.IO) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM goal_checklist_items WHERE id = ?",
            Int::class.java, item.id
        ) ?: 0

        if (exists > 0) {
            jdbcTemplate.update(
                """UPDATE goal_checklist_items SET text = ?, is_completed = ?, updated_at = ?
                   WHERE id = ?""",
                item.text, item.isCompleted, Timestamp.from(item.updatedAt), item.id
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO goal_checklist_items (id, goal_id, user_id, text, is_completed, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                item.id, item.goalId, item.userId, item.text, item.isCompleted,
                Timestamp.from(item.createdAt), Timestamp.from(item.updatedAt)
            )
        }
        item
    }

    override suspend fun delete(id: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM goal_checklist_items WHERE id = ?", id)
    }

    override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            "UPDATE goal_checklist_items SET goal_id = ? WHERE goal_id = ?",
            toGoalId, fromGoalId
        )
    }

    override suspend fun deleteByGoalId(goalId: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM goal_checklist_items WHERE goal_id = ?", goalId)
    }

    private fun mapRow(rs: ResultSet): GoalChecklistItem = GoalChecklistItem(
        id = rs.getObject("id", UUID::class.java),
        goalId = rs.getObject("goal_id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        text = rs.getString("text"),
        isCompleted = rs.getBoolean("is_completed"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )
}
