package com.dopashift.infrastructure.job

import com.dopashift.application.job.JobQueueRepository
import com.dopashift.application.job.JobRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * PostgreSQL implementation of [JobQueueRepository] using the SKIP LOCKED pattern
 * for safe concurrent job dequeuing.
 *
 * The SKIP LOCKED clause ensures multiple worker instances can poll simultaneously
 * without blocking each other or processing the same job twice.
 *
 * Requirements: 11.2, 11.3
 */
@Repository
class JdbcJobQueueRepository(
    private val jdbcTemplate: JdbcTemplate
) : JobQueueRepository {

    @Transactional
    override suspend fun claimPendingJobs(limit: Int): List<JobRecord> = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE job_queue
            SET status = 'PROCESSING', started_at = NOW()
            WHERE id IN (
                SELECT id FROM job_queue
                WHERE status = 'PENDING' AND scheduled_at <= NOW()
                ORDER BY priority DESC, scheduled_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            RETURNING id, job_type, payload, status, priority, attempts, max_attempts,
                      scheduled_at, started_at, completed_at, error_message, created_at
        """.trimIndent()

        jdbcTemplate.query(sql, { rs, _ -> mapRow(rs) }, limit)
    }

    @Transactional
    override suspend fun markCompleted(jobId: UUID, completedAt: Instant): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            "UPDATE job_queue SET status = 'COMPLETED', completed_at = ? WHERE id = ?",
            Timestamp.from(completedAt),
            jobId
        )
    }

    @Transactional
    override suspend fun markFailed(jobId: UUID, attempts: Int, errorMessage: String): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            "UPDATE job_queue SET status = 'FAILED', attempts = ?, error_message = ? WHERE id = ?",
            attempts,
            errorMessage.take(2000), // Truncate to avoid overflow
            jobId
        )
    }

    @Transactional
    override suspend fun markForRetry(jobId: UUID, attempts: Int, errorMessage: String): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            "UPDATE job_queue SET status = 'PENDING', started_at = NULL, attempts = ?, error_message = ? WHERE id = ?",
            attempts,
            errorMessage.take(2000),
            jobId
        )
    }

    private fun mapRow(rs: ResultSet): JobRecord = JobRecord(
        id = rs.getObject("id", UUID::class.java),
        jobType = rs.getString("job_type"),
        payload = rs.getString("payload"),
        status = rs.getString("status"),
        priority = rs.getInt("priority"),
        attempts = rs.getInt("attempts"),
        maxAttempts = rs.getInt("max_attempts"),
        scheduledAt = rs.getTimestamp("scheduled_at").toInstant(),
        startedAt = rs.getTimestamp("started_at")?.toInstant(),
        completedAt = rs.getTimestamp("completed_at")?.toInstant(),
        errorMessage = rs.getString("error_message"),
        createdAt = rs.getTimestamp("created_at").toInstant()
    )
}
