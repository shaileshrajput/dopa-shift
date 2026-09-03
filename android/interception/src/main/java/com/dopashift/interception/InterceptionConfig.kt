package com.dopashift.interception

/**
 * Configuration for the interception foreground service.
 *
 * Validates: Requirements 1.2, 1.3, 2.3, 4.10, 16.1
 * - Polling interval is configurable between 3 and 30 seconds (default 5s).
 * - Uses batched/interval-based polling, not continuous tight-loop.
 * - Focus_Extension duration is configurable between 1 and 15 minutes (default 5).
 */
data class InterceptionConfig(
    /**
     * Polling interval in milliseconds.
     * Valid range: [MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS].
     * Default: [DEFAULT_POLL_INTERVAL_MS].
     */
    val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    /**
     * Focus_Extension grace duration in whole minutes.
     * Valid range: [MIN_FOCUS_EXTENSION_MINUTES, MAX_FOCUS_EXTENSION_MINUTES].
     * Default: [DEFAULT_FOCUS_EXTENSION_MINUTES] (Requirement 4.9).
     */
    val focusExtensionMinutes: Int = DEFAULT_FOCUS_EXTENSION_MINUTES
) {
    init {
        require(pollIntervalMs in MIN_POLL_INTERVAL_MS..MAX_POLL_INTERVAL_MS) {
            "Poll interval must be between ${MIN_POLL_INTERVAL_MS}ms and ${MAX_POLL_INTERVAL_MS}ms, " +
                "got ${pollIntervalMs}ms"
        }
        require(focusExtensionMinutes in MIN_FOCUS_EXTENSION_MINUTES..MAX_FOCUS_EXTENSION_MINUTES) {
            "Focus extension duration must be between $MIN_FOCUS_EXTENSION_MINUTES and " +
                "$MAX_FOCUS_EXTENSION_MINUTES minutes, got $focusExtensionMinutes"
        }
    }

    /**
     * Requests a new sampling interval, in seconds.
     *
     * Per Requirement 1.3: if the requested interval falls outside the valid range
     * of [MIN_POLL_INTERVAL_SECONDS] to [MAX_POLL_INTERVAL_SECONDS] seconds inclusive,
     * the value is **rejected** and the previous valid interval is retained — this
     * config is returned unchanged (no silent [Int.coerceIn] clamp).
     *
     * @param seconds the requested sampling interval in seconds.
     * @return a config with the updated interval when [seconds] is in range, or this
     *   config unchanged when the request is out of range.
     */
    fun setSamplingIntervalSeconds(seconds: Int): InterceptionConfig {
        if (seconds !in MIN_POLL_INTERVAL_SECONDS..MAX_POLL_INTERVAL_SECONDS) {
            // Reject: retain the previous valid interval.
            return this
        }
        return copy(pollIntervalMs = seconds * 1000L)
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 5_000L
        const val MIN_POLL_INTERVAL_MS = 3_000L
        const val MAX_POLL_INTERVAL_MS = 30_000L

        const val MIN_POLL_INTERVAL_SECONDS = 3
        const val MAX_POLL_INTERVAL_SECONDS = 30

        const val DEFAULT_FOCUS_EXTENSION_MINUTES = 5
        const val MIN_FOCUS_EXTENSION_MINUTES = 1
        const val MAX_FOCUS_EXTENSION_MINUTES = 15

        /**
         * Creates a config from seconds value, clamping to valid range.
         */
        fun fromSeconds(seconds: Int): InterceptionConfig {
            val clampedMs = (seconds * 1000L).coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
            return InterceptionConfig(pollIntervalMs = clampedMs)
        }
    }
}
