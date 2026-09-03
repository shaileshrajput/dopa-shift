package com.dopashift.interception

// Feature: screen-time-interception-engine, Property 2

import com.dopashift.interception.InterceptionConfig.Companion.MAX_POLL_INTERVAL_SECONDS
import com.dopashift.interception.InterceptionConfig.Companion.MIN_POLL_INTERVAL_SECONDS
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Property 2: Sampling interval integrity (reject and retain).
 *
 * For any current valid sampling interval and any requested interval outside
 * [MIN_POLL_INTERVAL_SECONDS]..[MAX_POLL_INTERVAL_SECONDS] (3..30s inclusive),
 * the effective stored interval remains unchanged (equal to the prior valid value);
 * for any requested interval within range, the effective interval equals the request
 * (`pollIntervalMs == seconds * 1000`).
 *
 * **Validates: Requirements 1.2, 1.3**
 *
 * Property-based test following existing module conventions (JUnit4 + randomized iterations
 * with `repeat(N >= 100)`, as in [SuppressionContextPropertyTest] /
 * [AllowanceDepletionPropertyTest]); Kotest is not on this module's test classpath, so the
 * established randomized-input style is used.
 */
class SamplingIntervalIntegrityPropertyTest {

    /**
     * Property: an in-range request is accepted and the resulting config's pollIntervalMs
     * equals `seconds * 1000`.
     */
    @Test
    fun `property - in-range request sets pollIntervalMs to seconds times 1000`() {
        val random = Random(seed = 1002)

        repeat(200) {
            // Start from an arbitrary valid prior interval so acceptance is independent of it.
            val priorSeconds = random.nextInt(MIN_POLL_INTERVAL_SECONDS, MAX_POLL_INTERVAL_SECONDS + 1)
            val prior = InterceptionConfig(pollIntervalMs = priorSeconds * 1000L)

            val requested = random.nextInt(MIN_POLL_INTERVAL_SECONDS, MAX_POLL_INTERVAL_SECONDS + 1)
            val result = prior.setSamplingIntervalSeconds(requested)

            assertEquals(
                "In-range request $requested s must set pollIntervalMs to ${requested * 1000L}",
                requested * 1000L,
                result.pollIntervalMs
            )
        }
    }

    /**
     * Property: an out-of-range request is rejected; the config is returned unchanged so the
     * previously valid interval is retained.
     */
    @Test
    fun `property - out-of-range request retains the prior valid interval`() {
        val random = Random(seed = 2003)

        repeat(200) { iteration ->
            val priorSeconds = random.nextInt(MIN_POLL_INTERVAL_SECONDS, MAX_POLL_INTERVAL_SECONDS + 1)
            val prior = InterceptionConfig(pollIntervalMs = priorSeconds * 1000L)

            // Half below the minimum, half above the maximum, spanning boundary-adjacent values.
            val requested = if (iteration % 2 == 0) {
                random.nextInt(Int.MIN_VALUE / 1000, MIN_POLL_INTERVAL_SECONDS) // < 3
            } else {
                random.nextInt(MAX_POLL_INTERVAL_SECONDS + 1, Int.MAX_VALUE / 1000) // > 30
            }

            val result = prior.setSamplingIntervalSeconds(requested)

            assertEquals(
                "Out-of-range request $requested s must retain the prior interval " +
                    "(${prior.pollIntervalMs}ms)",
                prior.pollIntervalMs,
                result.pollIntervalMs
            )
            // Unchanged means the exact same value object contents are preserved.
            assertEquals(prior, result)
        }
    }

    /**
     * Property: boundary values (exactly 3 and exactly 30 seconds) are in range and accepted;
     * the values just outside (2 and 31 seconds) are rejected and retain the prior interval.
     */
    @Test
    fun `property - boundaries are inclusive and just-outside values are rejected`() {
        val random = Random(seed = 3004)

        repeat(100) {
            val priorSeconds = random.nextInt(MIN_POLL_INTERVAL_SECONDS, MAX_POLL_INTERVAL_SECONDS + 1)
            val prior = InterceptionConfig(pollIntervalMs = priorSeconds * 1000L)

            // Inclusive boundaries accepted.
            assertEquals(
                MIN_POLL_INTERVAL_SECONDS * 1000L,
                prior.setSamplingIntervalSeconds(MIN_POLL_INTERVAL_SECONDS).pollIntervalMs
            )
            assertEquals(
                MAX_POLL_INTERVAL_SECONDS * 1000L,
                prior.setSamplingIntervalSeconds(MAX_POLL_INTERVAL_SECONDS).pollIntervalMs
            )

            // Just-outside values rejected, prior retained.
            assertEquals(
                prior.pollIntervalMs,
                prior.setSamplingIntervalSeconds(MIN_POLL_INTERVAL_SECONDS - 1).pollIntervalMs
            )
            assertEquals(
                prior.pollIntervalMs,
                prior.setSamplingIntervalSeconds(MAX_POLL_INTERVAL_SECONDS + 1).pollIntervalMs
            )
        }
    }

    /**
     * Property: chaining a sequence of requests always leaves pollIntervalMs at the last
     * in-range request, or at the default when no request in the sequence was in range.
     */
    @Test
    fun `property - chained requests settle on the last in-range value or default`() {
        val random = Random(seed = 4005)

        repeat(150) {
            var config = InterceptionConfig() // starts at the default interval
            var lastInRangeMs: Long = InterceptionConfig.DEFAULT_POLL_INTERVAL_MS

            val sequenceLength = random.nextInt(1, 20)
            repeat(sequenceLength) {
                // Mix in-range and out-of-range requests across the full int span.
                val requested = when (random.nextInt(3)) {
                    0 -> random.nextInt(MIN_POLL_INTERVAL_SECONDS, MAX_POLL_INTERVAL_SECONDS + 1) // in range
                    1 -> random.nextInt(Int.MIN_VALUE / 1000, MIN_POLL_INTERVAL_SECONDS) // below
                    else -> random.nextInt(MAX_POLL_INTERVAL_SECONDS + 1, Int.MAX_VALUE / 1000) // above
                }
                config = config.setSamplingIntervalSeconds(requested)
                if (requested in MIN_POLL_INTERVAL_SECONDS..MAX_POLL_INTERVAL_SECONDS) {
                    lastInRangeMs = requested * 1000L
                }
            }

            assertEquals(
                "After the chain, pollIntervalMs must equal the last in-range value " +
                    "(or the default if none were in range)",
                lastInRangeMs,
                config.pollIntervalMs
            )
        }
    }
}
