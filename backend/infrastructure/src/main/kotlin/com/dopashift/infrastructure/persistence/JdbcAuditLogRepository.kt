package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.AuditLogEntry
import com.dopashift.domain.repository.AuditLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcAuditLogRepository(
    private val jdbcTemplate: JdbcTemplate
) : AuditLogRepository {

    override suspend fun append(entry: AuditLogEntry): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            """INSERT INTO audit_log (id, correlation_id, user_id, operation_type, entity_id, before_state, after_state, timestamp, previous_hash, entry_hash)
               VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)""",
            entry.id, entry.correlationId, entry.userId, entry.operationType,
            entry.entityId, entry.beforeState, entry.afterState,
            Timestamp.from(entry.timestamp), entry.previousHash, entry.entryHash
        )
    }

    override suspend fun findByCorrelationId(correlationId: String): List<AuditLogEntry> =
        withContext(Dispatchers.IO) {
            jdbcTemplate.query(
                "SELECT * FROM audit_log WHERE correlation_id = ? ORDER BY timestamp",
                { rs, _ -> mapRow(rs) },
                correlationId
            )
        }

    override suspend fun getLastEntry(): AuditLogEntry? = withContext(Dispatchers.IO) {
        jdbcTemplate.query(
            "SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 1",
            { rs, _ -> mapRow(rs) }
        ).firstOrNull()
    }

    override suspend fun purgeOlderThan(cutoff: Instant, operationTypes: Set<String>): Long =
        withContext(Dispatchers.IO) {
            if (operationTypes.isEmpty()) {
                jdbcTemplate.update(
                    "DELETE FROM audit_log WHERE timestamp < ?",
                    Timestamp.from(cutoff)
                ).toLong()
            } else {
                val placeholders = operationTypes.joinToString(",") { "?" }
                val args = mutableListOf<Any>(Timestamp.from(cutoff))
                args.addAll(operationTypes)
                jdbcTemplate.update(
                    "DELETE FROM audit_log WHERE timestamp < ? AND operation_type IN ($placeholders)",
                    *args.toTypedArray()
                ).toLong()
            }
        }

    private fun mapRow(rs: ResultSet): AuditLogEntry = AuditLogEntry(
        id = rs.getObject("id", UUID::class.java),
        correlationId = rs.getString("correlation_id"),
        userId = rs.getObject("user_id", UUID::class.java),
        operationType = rs.getString("operation_type"),
        entityId = rs.getObject("entity_id", UUID::class.java),
        beforeState = rs.getString("before_state"),
        afterState = rs.getString("after_state"),
        timestamp = rs.getTimestamp("timestamp").toInstant(),
        previousHash = rs.getString("previous_hash"),
        entryHash = rs.getString("entry_hash")
    )
}
