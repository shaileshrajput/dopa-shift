package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.repository.GoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcGoalRepository(
    private val jdbcTemplate: JdbcTemplate
) : GoalRepository {

    override suspend fun findById(id: UUID): GoalProfile? = withContext(Dispatchers.IO) {
        val goals = jdbcTemplate.query(
            "SELECT * FROM goals WHERE id = ?",
            { rs, _ -> mapGoal(rs) },
            id
        )
        goals.firstOrNull()?.let { attachKeywords(it) }
    }

    override suspend fun findByUserId(userId: UUID): List<GoalProfile> = withContext(Dispatchers.IO) {
        val goals = jdbcTemplate.query(
            "SELECT * FROM goals WHERE user_id = ? ORDER BY created_at DESC",
            { rs, _ -> mapGoal(rs) },
            userId
        )
        goals.map { attachKeywords(it) }
    }

    override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? = withContext(Dispatchers.IO) {
        val goals = jdbcTemplate.query(
            "SELECT * FROM goals WHERE user_id = ? AND LOWER(name) = LOWER(?)",
            { rs, _ -> mapGoal(rs) },
            userId, name
        )
        goals.firstOrNull()?.let { attachKeywords(it) }
    }

    override suspend fun save(goal: GoalProfile): GoalProfile = withContext(Dispatchers.IO) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM goals WHERE id = ?",
            Int::class.java,
            goal.id
        ) ?: 0

        if (exists > 0) {
            jdbcTemplate.update(
                """UPDATE goals SET name = ?, category = ?, is_active = ?, updated_at = ?
                   WHERE id = ?""",
                goal.name, goal.category, goal.isActive,
                Timestamp.from(goal.updatedAt), goal.id
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO goals (id, user_id, name, category, is_active, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                goal.id, goal.userId, goal.name, goal.category, goal.isActive,
                Timestamp.from(goal.createdAt), Timestamp.from(goal.updatedAt)
            )
        }

        // Sync keywords
        jdbcTemplate.update("DELETE FROM keywords WHERE goal_id = ?", goal.id)
        goal.keywords.forEach { keyword ->
            jdbcTemplate.update(
                "INSERT INTO keywords (id, goal_id, keyword) VALUES (?, ?, ?)",
                UUID.randomUUID(), goal.id, keyword
            )
        }

        goal
    }

    override suspend fun delete(id: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM goals WHERE id = ?", id)
    }

    override suspend fun countActiveByUserId(userId: UUID): Int = withContext(Dispatchers.IO) {
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM goals WHERE user_id = ? AND is_active = true",
            Int::class.java,
            userId
        ) ?: 0
    }

    private fun attachKeywords(goal: GoalProfile): GoalProfile {
        val keywords = jdbcTemplate.query(
            "SELECT keyword FROM keywords WHERE goal_id = ?",
            { rs, _ -> rs.getString("keyword") },
            goal.id
        )
        return goal.copy(keywords = keywords)
    }

    private fun mapGoal(rs: ResultSet): GoalProfile = GoalProfile(
        id = rs.getObject("id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        name = rs.getString("name"),
        category = rs.getString("category"),
        keywords = emptyList(), // attached separately
        isActive = rs.getBoolean("is_active"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant()
    )
}
