package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.EfficiencyScore
import com.dopashift.domain.repository.EfficiencyScoreRepository
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
class JdbcEfficiencyScoreRepository(
    private val jdbcTemplate: JdbcTemplate
) : EfficiencyScoreRepository {

    override suspend fun save(score: EfficiencyScore): EfficiencyScore = withContext(Dispatchers.IO) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM efficiency_scores WHERE id = ?",
            Int::class.java, score.id
        ) ?: 0

        if (exists > 0) {
            jdbcTemplate.update(
                """UPDATE efficiency_scores SET productive_seconds = ?, total_tracked_seconds = ?,
                   score_percent = ?, computed_at = ? WHERE id = ?""",
                score.productiveSeconds, score.totalTrackedSeconds,
                score.scorePercent, Timestamp.from(score.computedAt), score.id
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO efficiency_scores (id, user_id, score_date, productive_seconds, total_tracked_seconds, score_percent, computed_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                score.id, score.userId, Date.valueOf(score.date),
                score.productiveSeconds, score.totalTrackedSeconds,
                score.scorePercent, Timestamp.from(score.computedAt)
            )
        }
        score
    }

    override suspend fun findByUserIdAndDateRange(
        userId: UUID,
        from: LocalDate,
        to: LocalDate
    ): List<EfficiencyScore> = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM efficiency_scores WHERE user_id = ? AND score_date >= ? AND score_date <= ? ORDER BY score_date",
            { rs, _ -> mapRow(rs) },
            userId, Date.valueOf(from), Date.valueOf(to)
        )
    }

    private fun mapRow(rs: ResultSet): EfficiencyScore = EfficiencyScore(
        id = rs.getObject("id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        date = rs.getDate("score_date").toLocalDate(),
        productiveSeconds = rs.getLong("productive_seconds"),
        totalTrackedSeconds = rs.getLong("total_tracked_seconds"),
        scorePercent = rs.getObject("score_percent")?.let { (it as Number).toInt() },
        computedAt = rs.getTimestamp("computed_at").toInstant()
    )
}
