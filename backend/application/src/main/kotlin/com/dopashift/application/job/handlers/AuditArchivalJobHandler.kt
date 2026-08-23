package com.dopashift.application.job.handlers

import com.dopashift.application.job.JobHandler
import com.dopashift.application.job.JobType
import com.dopashift.domain.repository.AuditLogRepository
import java.time.Clock
import java.time.Instant

/**
 * Background job handler for audit log retention policy enforcement.
 *
 * Implements the dual-retention policy defined in Requirement 18.5:
 * - Application-level log entries (operation types prefixed with "APP_LOG_"): retained for 30 days (configurable)
 * - Audit log entries (all other operation types): retained for 1 year (configurable)
 *
 * The job payload specifies retention durations:
 * ```json
 * {"auditRetentionDays": 365, "appLogRetentionDays": 30}
 * ```
 *
 * Retry with exponential backoff is handled by the [com.dopashift.application.job.JobQueueProcessor]:
 * - Max 5 attempts with backoff
 * - On exhausted retries, the job is marked FAILED (triggering critical alert via monitoring)
 *
 * Requirements: 18.5, 18.12
 */
class AuditArchivalJobHandler(
    private val auditLogRepository: AuditLogRepository,
    private val clock: Clock = Clock.systemUTC()
) : JobHandler {

    override val jobType: JobType = JobType.AUDIT_ARCHIVAL

    companion object {
        /** Default retention period for audit log entries (1 year). */
        const val DEFAULT_AUDIT_RETENTION_DAYS = 365L

        /** Default retention period for application-level log entries (30 days). */
        const val DEFAULT_APP_LOG_RETENTION_DAYS = 30L

        /** Operation type prefix used to distinguish application-level logs from audit entries. */
        const val APP_LOG_OPERATION_PREFIX = "APP_LOG_"
    }

    /**
     * Executes the archival/purge job.
     *
     * 1. Parses payload to extract retention configuration
     * 2. Purges application-level log entries older than appLogRetentionDays
     * 3. Purges audit log entries older than auditRetentionDays
     *
     * @param payload JSON payload: {"auditRetentionDays": 365, "appLogRetentionDays": 30}
     * @throws Exception on failure — the processor handles retry logic
     */
    override suspend fun handle(payload: String) {
        val config = parsePayload(payload)

        val now = Instant.now(clock)

        // Phase 1: Purge application-level logs older than appLogRetentionDays
        val appLogCutoff = now.minusDays(config.appLogRetentionDays)
        auditLogRepository.purgeOlderThan(
            cutoff = appLogCutoff,
            operationTypes = setOf(APP_LOG_OPERATION_PREFIX)
        )

        // Phase 2: Purge audit entries older than auditRetentionDays
        val auditCutoff = now.minusDays(config.auditRetentionDays)
        auditLogRepository.purgeOlderThan(
            cutoff = auditCutoff,
            operationTypes = emptySet()
        )
    }

    /**
     * Parses the job payload JSON into retention configuration.
     * Uses simple string parsing to avoid external JSON library dependency at the application layer.
     */
    internal fun parsePayload(payload: String): ArchivalConfig {
        val auditRetentionDays = extractLongValue(payload, "auditRetentionDays")
            ?: DEFAULT_AUDIT_RETENTION_DAYS
        val appLogRetentionDays = extractLongValue(payload, "appLogRetentionDays")
            ?: DEFAULT_APP_LOG_RETENTION_DAYS

        require(auditRetentionDays > 0) { "auditRetentionDays must be positive" }
        require(appLogRetentionDays > 0) { "appLogRetentionDays must be positive" }

        return ArchivalConfig(
            auditRetentionDays = auditRetentionDays,
            appLogRetentionDays = appLogRetentionDays
        )
    }

    private fun extractLongValue(json: String, key: String): Long? {
        val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun Instant.minusDays(days: Long): Instant =
        this.minusSeconds(days * 24 * 60 * 60)
}

/**
 * Configuration for the audit archival job, parsed from the job payload.
 */
data class ArchivalConfig(
    /** Number of days to retain audit log entries (default: 365). */
    val auditRetentionDays: Long,
    /** Number of days to retain application-level log entries (default: 30). */
    val appLogRetentionDays: Long
)
