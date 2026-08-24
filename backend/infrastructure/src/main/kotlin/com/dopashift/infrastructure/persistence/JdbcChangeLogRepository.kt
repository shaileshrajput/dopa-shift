package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.repository.ChangeLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcChangeLogRepository(
    private val jdbcTemplate: JdbcTemplate
) : ChangeLogRepository {

    override suspend fun append(entry: ChangeLogEntry): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            """INSERT INTO change_log (id, entity_id, entity_type, field, value, timestamp, device_id, user_id)
               VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)""",
            entry.id, entry.entityId, entry.entityType, entry.field,
            entry.value, Timestamp.from(entry.timestamp), entry.deviceId, entry.userId
        )
    }

    override suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry> =
        withContext(Dispatchers.IO) {
            jdbcTemplate.query(
                "SELECT * FROM change_log WHERE user_id = ? AND timestamp > ? ORDER BY timestamp",
                { rs, _ -> mapRow(rs) },
                userId, Timestamp.from(since)
            )
        }

    override suspend fun findLatestByEntityIdAndField(entityId: UUID, field: String): ChangeLogEntry? =
        withContext(Dispatchers.IO) {
            jdbcTemplate.query(
                "SELECT * FROM change_log WHERE entity_id = ? AND field = ? ORDER BY timestamp DESC LIMIT 1",
                { rs, _ -> mapRow(rs) },
                entityId, field
            ).firstOrNull()
        }

    override suspend fun countByUserId(userId: UUID): Long = withContext(Dispatchers.IO) {
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM change_log WHERE user_id = ?",
            Long::class.java, userId
        ) ?: 0L
    }

    override suspend fun countTotal(): Long = withContext(Dispatchers.IO) {
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM change_log",
            Long::class.java
        ) ?: 0L
    }

    override suspend fun archiveOlderThan(cutoff: Instant): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            "DELETE FROM change_log WHERE timestamp < ?",
            Timestamp.from(cutoff)
        )
    }

    private fun mapRow(rs: ResultSet): ChangeLogEntry = ChangeLogEntry(
        id = rs.getObject("id", UUID::class.java),
        entityId = rs.getObject("entity_id", UUID::class.java),
        entityType = rs.getString("entity_type"),
        field = rs.getString("field"),
        value = rs.getString("value"),
        timestamp = rs.getTimestamp("timestamp").toInstant(),
        deviceId = rs.getString("device_id"),
        userId = rs.getObject("user_id", UUID::class.java)
    )
}
