package com.dopashift.application.job

/**
 * Supported background job types for the PostgreSQL-backed job queue.
 *
 * Requirements: 11.2, 11.3
 */
enum class JobType(val value: String) {
    /** Archives audit log entries older than the configured retention period (default 1 year). */
    AUDIT_ARCHIVAL("audit_archival"),

    /** Archives change log entries older than 90 days or when table exceeds 1M rows. */
    CHANGE_LOG_ARCHIVAL("change_log_archival"),

    /** Evicts expired entries from the video recommendation cache. */
    VIDEO_CACHE_EVICTION("video_cache_eviction"),

    /** Detects missed habit track checkpoints at end of day. */
    MISSED_HABIT_DETECTION("missed_habit_detection");

    companion object {
        private val valueMap = entries.associateBy { it.value }

        /**
         * Resolves a [JobType] from its string value.
         * @throws IllegalArgumentException if the value does not match a known job type.
         */
        fun fromValue(value: String): JobType =
            valueMap[value] ?: throw IllegalArgumentException("Unknown job type: $value")
    }
}
