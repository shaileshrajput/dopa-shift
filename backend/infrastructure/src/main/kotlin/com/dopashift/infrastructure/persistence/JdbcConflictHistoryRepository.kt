package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.ConflictHistoryEntry
import com.dopashift.domain.repository.ConflictHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcConflictHistoryRepository(
    private val jdbcTemplate: JdbcTemplate
) : ConflictHistoryRepository {

    override suspend fun save(entry: ConflictHistoryEntry): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            """INSERT INTO conflict_history (id, change_log_id, superseded_value, superseded_device_id, superseded_timestamp, resolution_reason, created_at)
               VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)""",
            entry.id, entry.changeLogId, entry.supersededValue,
            entry.supersededDeviceId, Timestamp.from(entry.supersededTimestamp),
            entry.resolutionReason, Timestamp.from(entry.createdAt)
        )
    }

    override suspend fun findByUserId(userId: UUID): List<ConflictHistoryEntry> =
        withContext(Dispatchers.IO) {
            jdbcTemplate.query(
                """SELECT ch.* FROM conflict_history ch
                   JOIN change_log cl ON cl.id = ch.change_log_id
                   WHERE cl.user_id = ?
                   ORDER BY ch.created_at DESC""",
                { rs, _ -> mapRow(rs, userId) },
                userId
            )
        }

    override suspend fun findSince(userId: UUID, since: Instant): List<ConflictHistoryEntry> =
        withContext(Dispatchers.IO) {
            jdbcTemplate.query(
                """SELECT ch.* FROM conflict_history ch
                   JOIN change_log cl ON cl.id = ch.change_log_id
                   WHERE cl.user_id = ? AND ch.created_at > ?
                   ORDER BY ch.created_at""",
                { rs, _ -> mapRow(rs, userId) },
                userId, Timestamp.from(since)
            )
        }

    override suspend fun deleteOlderThan(cutoff: Instant): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            "DELETE FROM conflict_history WHERE created_at < ?",
            Timestamp.from(cutoff)
        )
    }

    private fun mapRow(rs: ResultSet, userId: UUID): ConflictHistoryEntry = ConflictHistoryEntry(
        id = rs.getObject("id", UUID::class.java),
        userId = userId,
        changeLogId = rs.getObject("change_log_id", UUID::class.java),
        supersededValue = rs.getString("superseded_value"),
        supersededDeviceId = rs.getString("superseded_device_id") ?: "",
        supersededTimestamp = rs.getTimestamp("superseded_timestamp")?.toInstant() ?: Instant.now(),
        resolutionReason = rs.getString("resolution_reason"),
        resolvedAt = rs.getTimestamp("created_at").toInstant(),
        createdAt = rs.getTimestamp("created_at").toInstant()
    )
}
