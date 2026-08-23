package com.dopashift.application.job.handlers

import com.dopashift.application.job.JobHandler
import com.dopashift.application.job.JobType
import com.dopashift.domain.repository.ChangeLogRepository
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

/**
 * Handles archival of change_log entries older than the configured retention period
 * when the table exceeds a row-count threshold.
 *
 * Triggered weekly or when the table exceeds 1M rows (default).
 * Archives/purges entries older than 90 days (default).
 *
 * Payload format: {"thresholdRows": 1000000, "retentionDays": 90}
 *
 * Requirements: 11.2
 */
class ChangeLogArchivalJobHandler(
    private val changeLogRepository: ChangeLogRepository,
    private val clock: Clock = Clock.systemUTC()
) : JobHandler {

    override val jobType: JobType = JobType.CHANGE_LOG_ARCHIVAL

    private val logger = LoggerFactory.getLogger(ChangeLogArchivalJobHandler::class.java)

    companion object {
        /** Default row-count threshold that triggers archival. */
        const val DEFAULT_THRESHOLD_ROWS = 1_000_000L

        /** Default retention period in days for change log entries. */
        const val DEFAULT_RETENTION_DAYS = 90L
    }

    /**
     * Executes the change log archival job.
     *
     * 1. Parses payload to extract threshold and retention configuration
     * 2. Checks if total change_log row count exceeds the threshold
     * 3. If yes, archives/purges entries older than retentionDays
     *
     * @param payload JSON payload: {"thresholdRows": 1000000, "retentionDays": 90}
     * @throws Exception on failure — the processor handles retry logic
     */
    override suspend fun handle(payload: String) {
        val config = parsePayload(payload)
        val totalRows = changeLogRepository.countTotal()

        logger.info(
            "Change log archival check: totalRows={}, threshold={}",
            totalRows, config.thresholdRows
        )

        if (totalRows <= config.thresholdRows) {
            logger.info(
                "Change log row count ({}) does not exceed threshold ({}); skipping archival",
                totalRows, config.thresholdRows
            )
            return
        }

        val cutoff = Instant.now(clock).minusDays(config.retentionDays)
        logger.info(
            "Archiving change_log entries older than {} (retention={} days)",
            cutoff, config.retentionDays
        )

        changeLogRepository.archiveOlderThan(cutoff)

        logger.info("Change log archival completed successfully")
    }

    /**
     * Parses the job payload JSON into archival configuration.
     * Uses simple string parsing to avoid external JSON library dependency at the application layer.
     */
    internal fun parsePayload(payload: String): ChangeLogArchivalConfig {
        val thresholdRows = extractLongValue(payload, "thresholdRows")
            ?: DEFAULT_THRESHOLD_ROWS
        val retentionDays = extractLongValue(payload, "retentionDays")
            ?: DEFAULT_RETENTION_DAYS

        require(thresholdRows > 0) { "thresholdRows must be positive" }
        require(retentionDays > 0) { "retentionDays must be positive" }

        return ChangeLogArchivalConfig(
            thresholdRows = thresholdRows,
            retentionDays = retentionDays
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
 * Configuration for the change log archival job, parsed from the job payload.
 */
data class ChangeLogArchivalConfig(
    /** Number of rows beyond which archival is triggered (default: 1,000,000). */
    val thresholdRows: Long,
    /** Number of days to retain change log entries (default: 90). */
    val retentionDays: Long
)
