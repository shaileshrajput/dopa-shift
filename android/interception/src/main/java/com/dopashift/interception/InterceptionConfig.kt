package com.dopashift.interception

/**
 * Configuration for the interception foreground service.
 *
 * Validates: Requirements 2.3, 16.1
 * - Polling interval is configurable between 3 and 30 seconds (default 5s).
 * - Uses batched/interval-based polling, not continuous tight-loop.
 */
data class InterceptionConfig(
    /**
     * Polling interval in milliseconds.
     * Valid range: [MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS].
     * Default: [DEFAULT_POLL_INTERVAL_MS].
     */
    val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    init {
        require(pollIntervalMs in MIN_POLL_INTERVAL_MS..MAX_POLL_INTERVAL_MS) {
            "Poll interval must be between ${MIN_POLL_INTERVAL_MS}ms and ${MAX_POLL_INTERVAL_MS}ms, " +
                "got ${pollIntervalMs}ms"
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 5_000L
        const val MIN_POLL_INTERVAL_MS = 3_000L
        const val MAX_POLL_INTERVAL_MS = 30_000L

        /**
         * Creates a config from seconds value, clamping to valid range.
         */
        fun fromSeconds(seconds: Int): InterceptionConfig {
            val clampedMs = (seconds * 1000L).coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
            return InterceptionConfig(pollIntervalMs = clampedMs)
        }
    }
}
