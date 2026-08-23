package com.dopashift.domain.usecase

/**
 * Encapsulates the sync push retry policy with exponential backoff.
 *
 * The policy specifies:
 * - Maximum of 5 retry attempts
 * - Backoff sequence: 1s, 2s, 4s, 8s, 16s (INITIAL_BACKOFF * 2^(N-1))
 * - After all retries are exhausted, queued changes are retained locally
 *
 * This is a pure domain service with no framework dependencies.
 *
 * Requirements: 4.10
 */
class SyncRetryPolicy(
    val maxAttempts: Int = MAX_RETRY_ATTEMPTS,
    val initialBackoffMs: Long = INITIAL_BACKOFF_MS
) {

    companion object {
        const val MAX_RETRY_ATTEMPTS = 5
        const val INITIAL_BACKOFF_MS = 1000L
    }

    /**
     * Calculates exponential backoff delay for the given attempt number.
     * Backoff sequence: 1s, 2s, 4s, 8s, 16s (doubling per attempt).
     *
     * @param attempt 1-based attempt number (1 = first attempt/retry).
     * @return Delay in milliseconds.
     * @throws IllegalArgumentException if attempt is not in range 1..maxAttempts
     */
    fun calculateBackoff(attempt: Int): Long {
        require(attempt in 1..maxAttempts) {
            "Attempt number must be between 1 and $maxAttempts, got $attempt"
        }
        return initialBackoffMs * (1L shl (attempt - 1))
    }

    /**
     * Determines whether a retry should be attempted after a failure.
     *
     * @param attemptNumber The number of the attempt that just failed (1-based).
     * @return true if another retry should be attempted, false if retries are exhausted.
     */
    fun shouldRetry(attemptNumber: Int): Boolean {
        return attemptNumber < maxAttempts
    }

    /**
     * Executes a sync push operation with retry logic.
     * If all retries are exhausted, returns a failure result.
     * Queued changes remain in the local store regardless of outcome.
     *
     * @param queuedChanges The list of changes to push.
     * @param pushAction The actual push operation that may throw on network failure.
     * @param delayAction An action to wait for the backoff duration (injectable for testing).
     * @return [SyncPushResult] indicating success or failure.
     */
    suspend fun <T> executeWithRetry(
        queuedChanges: List<T>,
        pushAction: suspend (List<T>) -> Unit,
        delayAction: suspend (Long) -> Unit = { }
    ): SyncPushResult {
        if (queuedChanges.isEmpty()) {
            return SyncPushResult.NothingToSync
        }

        var lastError: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                pushAction(queuedChanges)
                return SyncPushResult.Success(syncedCount = queuedChanges.size)
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxAttempts) {
                    val backoffMs = calculateBackoff(attempt)
                    delayAction(backoffMs)
                }
            }
        }

        // All retries exhausted — queued changes remain in local store
        return SyncPushResult.Failure(
            reason = lastError?.message ?: "Unknown error",
            attemptsExhausted = true,
            retainedChangeCount = queuedChanges.size
        )
    }
}

/**
 * Result of a sync push operation.
 */
sealed class SyncPushResult {
    /** Push succeeded, all changes synced. */
    data class Success(val syncedCount: Int) : SyncPushResult()

    /** Nothing to push (queue was empty). */
    data object NothingToSync : SyncPushResult()

    /** Push failed after all retries. Queued changes are retained locally. */
    data class Failure(
        val reason: String,
        val attemptsExhausted: Boolean,
        val retainedChangeCount: Int
    ) : SyncPushResult()
}
