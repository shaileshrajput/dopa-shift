package com.dopashift.domain.property

import com.dopashift.domain.usecase.SyncPushResult
import com.dopashift.domain.usecase.SyncRetryPolicy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest

/**
 * Property 19: Sync Push Retry with Exponential Backoff
 *
 * *For any* sync push that fails due to network error, the system SHALL retry with
 * exponential backoff (1s, 2s, 4s, 8s, 16s) up to 5 attempts, and if all fail,
 * queued changes SHALL be retained locally.
 *
 * **Validates: Requirements 4.10**
 *
 * Tag: Feature: dopa-shift, Property 19: Sync Push Retry with Exponential Backoff
 */
class SyncPushRetryBackoffPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 19: Sync Push Retry with Exponential Backoff")
    )

    val policy = SyncRetryPolicy()

    test("Property 19: For any attempt number N (1-5), backoff delay is exactly INITIAL_BACKOFF * 2^(N-1)") {
        checkAll(100, Arb.int(1..5)) { attempt ->
            val expectedMs = SyncRetryPolicy.INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
            val actualMs = policy.calculateBackoff(attempt)

            actualMs shouldBeExactly expectedMs
        }
    }

    test("Property 19: Backoff values match exact sequence 1000, 2000, 4000, 8000, 16000 ms") {
        checkAll(100, Arb.int(1..5)) { attempt ->
            val expectedSequence = mapOf(
                1 to 1000L,
                2 to 2000L,
                3 to 4000L,
                4 to 8000L,
                5 to 16000L
            )

            val actualMs = policy.calculateBackoff(attempt)
            actualMs shouldBeExactly expectedSequence[attempt]!!
        }
    }

    test("Property 19: After 5 failed attempts, the system does NOT retry further") {
        checkAll(100, Arb.list(Arb.string(1..10), 1..20)) { queuedChanges ->
            var attemptCount = 0

            val result = policy.executeWithRetry(
                queuedChanges = queuedChanges,
                pushAction = { _ ->
                    attemptCount++
                    throw RuntimeException("Network error")
                },
                delayAction = { _ -> /* no-op for test speed */ }
            )

            // Exactly 5 attempts, no more
            attemptCount shouldBe SyncRetryPolicy.MAX_RETRY_ATTEMPTS

            // Result indicates failure with retries exhausted
            result.shouldBeInstanceOf<SyncPushResult.Failure>()
            result.attemptsExhausted shouldBe true
        }
    }

    test("Property 19: Queued changes remain in local store after all retries are exhausted") {
        checkAll(100, Arb.list(Arb.string(1..10), 1..20)) { queuedChanges ->
            val result = policy.executeWithRetry(
                queuedChanges = queuedChanges,
                pushAction = { _ ->
                    throw RuntimeException("Network error")
                },
                delayAction = { _ -> /* no-op for test speed */ }
            )

            // Verify the failure result reports retained changes
            result.shouldBeInstanceOf<SyncPushResult.Failure>()
            result.retainedChangeCount shouldBe queuedChanges.size
        }
    }

    test("Property 19: For random failure counts (1-5), correct backoff timing is applied before each retry") {
        checkAll(100, Arb.int(1..5)) { failUntilAttempt ->
            val observedDelays = mutableListOf<Long>()
            var attemptCount = 0

            policy.executeWithRetry(
                queuedChanges = listOf("change1", "change2"),
                pushAction = { _ ->
                    attemptCount++
                    if (attemptCount <= failUntilAttempt) {
                        throw RuntimeException("Network error on attempt $attemptCount")
                    }
                    // Succeeds after failUntilAttempt attempts
                },
                delayAction = { delayMs ->
                    observedDelays.add(delayMs)
                }
            )

            // Verify each delay matches the exponential backoff formula
            for (i in observedDelays.indices) {
                val expectedDelay = SyncRetryPolicy.INITIAL_BACKOFF_MS * (1L shl i)
                observedDelays[i] shouldBeExactly expectedDelay
            }

            // Number of delays should be min(failUntilAttempt, MAX_RETRY_ATTEMPTS - 1)
            // since delay happens after a failed attempt, but not after the last attempt
            if (failUntilAttempt < SyncRetryPolicy.MAX_RETRY_ATTEMPTS) {
                observedDelays.size shouldBe failUntilAttempt
            }
        }
    }
})
