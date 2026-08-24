package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.repository.InterceptionRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcInterceptionRuleRepository(
    private val jdbcTemplate: JdbcTemplate
) : InterceptionRuleRepository {

    override suspend fun findById(id: UUID): InterceptionRule? = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM interception_rules WHERE id = ?",
            { rs, _ -> mapRow(rs) },
            id
        ).firstOrNull()
    }

    override suspend fun findByGoalId(goalId: UUID): List<InterceptionRule> = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM interception_rules WHERE goal_id = ?",
            { rs, _ -> mapRow(rs) },
            goalId
        )
    }

    override suspend fun findByUserId(userId: UUID): List<InterceptionRule> = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM interception_rules WHERE user_id = ? ORDER BY created_at",
            { rs, _ -> mapRow(rs) },
            userId
        )
    }

    override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            "UPDATE interception_rules SET goal_id = ? WHERE goal_id = ?",
            toGoalId, fromGoalId
        )
    }

    override suspend fun deleteByGoalId(goalId: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM interception_rules WHERE goal_id = ?", goalId)
    }

    override suspend fun delete(id: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM interception_rules WHERE id = ?", id)
    }

    override suspend fun save(rule: InterceptionRule): InterceptionRule = withContext(Dispatchers.IO) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM interception_rules WHERE id = ?",
            Int::class.java, rule.id
        ) ?: 0

        if (exists > 0) {
            jdbcTemplate.update(
                """UPDATE interception_rules SET goal_id = ?, app_package_name = ?, site_domain = ?,
                   daily_allowance_minutes = ?, is_active = ? WHERE id = ?""",
                rule.goalId, rule.appPackageName, rule.siteDomain,
                rule.dailyAllowanceMinutes, rule.isActive, rule.id
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO interception_rules (id, user_id, goal_id, app_package_name, site_domain, daily_allowance_minutes, is_active, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                rule.id, rule.userId, rule.goalId, rule.appPackageName, rule.siteDomain,
                rule.dailyAllowanceMinutes, rule.isActive, Timestamp.from(rule.createdAt)
            )
        }
        rule
    }

    private fun mapRow(rs: ResultSet): InterceptionRule = InterceptionRule(
        id = rs.getObject("id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        goalId = rs.getObject("goal_id", UUID::class.java),
        appPackageName = rs.getString("app_package_name"),
        siteDomain = rs.getString("site_domain"),
        dailyAllowanceMinutes = rs.getInt("daily_allowance_minutes"),
        isActive = rs.getBoolean("is_active"),
        createdAt = rs.getTimestamp("created_at").toInstant()
    )
}
