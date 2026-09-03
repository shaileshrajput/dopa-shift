package com.dopashift.data.repository

import androidx.test.core.app.ApplicationProvider
import com.dopashift.data.remote.dto.InterceptionRuleResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Property test for consent revocation semantics of [AggregatedCounterSync].
 *
 * // Feature: screen-time-interception-engine, Property 23: Consent revocation cancels
 * // sync and halts further syncing
 *
 * Validates: Requirements 6.5
 *
 * The consent flag is backed by a Context/DataStore ("sync_preferences"), so the sync path
 * requires a real Android [android.content.Context]. Robolectric supplies one on the JVM.
 * Because the component runs uploads on a real SupervisorJob + Dispatchers.IO scope and
 * revocation coordinates with in-flight work via cancelAndJoin, the tests use [runBlocking]
 * (real time + latch coordination) rather than virtual-time [kotlinx.coroutines.test.runTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AggregatedCounterSyncConsentPropertyTest {

    private companion object {
        // Each iteration drives real Dispatchers.IO coroutines, latch coordination, and a
        // Context-backed DataStore round-trip, so iteration count is kept modest while still
        // randomizing payloads/timing across runs.
        const val ITERATIONS = 25
    }

    /**
     * Controllable uploader: each [upload] awaits a per-call gate so a test can revoke consent
     * while the upload is in-flight. Records how many uploads were started and completed.
     */
    private class ControllableUploader : AggregatedCounterUploader {
        val started = AtomicInteger(0)
        val completed = AtomicInteger(0)

        /** Signals that the most recent upload has actually begun. */
        @Volatile
        var uploadStarted: CompletableDeferred<Unit> = CompletableDeferred()

        /** When completed, allows the in-flight upload to finish. */
        @Volatile
        var gate: CompletableDeferred<Unit> = CompletableDeferred()

        fun reset() {
            uploadStarted = CompletableDeferred()
            gate = CompletableDeferred()
        }

        override suspend fun upload(payload: AggregatedCounterPayload) {
            started.incrementAndGet()
            uploadStarted.complete(Unit)
            gate.await() // suspends until released; cancellation propagates here
            completed.incrementAndGet()
        }
    }

    private lateinit var uploader: ControllableUploader
    private lateinit var sync: AggregatedCounterSync

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        uploader = ControllableUploader()
        sync = AggregatedCounterSync(context, uploader)
        // Ensure a clean, revoked baseline for each test (DataStore file persists in-process).
        runBlocking { sync.revokeConsent() }
    }

    private fun randomPayload(rnd: Random): AggregatedCounterPayload {
        val appCount = rnd.nextInt(0, 4)
        val counters = (0 until appCount).map {
            AggregatedCounterPayload.AppCounter(
                appPackageName = "com.example.app${rnd.nextInt(0, 100)}",
                date = "2024-0${rnd.nextInt(1, 9)}-1${rnd.nextInt(0, 9)}",
                totalForegroundMinutes = rnd.nextLong(0, 1440),
                breachCount = rnd.nextInt(0, 5)
            )
        }
        val rules = (0 until rnd.nextInt(0, 3)).map {
            InterceptionRuleResponse(
                id = "rule-${rnd.nextInt(0, 1000)}",
                goalId = null,
                appPackageName = "com.example.app${rnd.nextInt(0, 100)}",
                siteDomain = null,
                dailyAllowanceMinutes = rnd.nextInt(1, 480),
                isActive = rnd.nextBoolean(),
                createdAt = "2024-01-01T00:00:00Z"
            )
        }
        return AggregatedCounterPayload(appCounters = counters, rules = rules)
    }

    /**
     * Property (part 1): with consent granted, sync uploads (uploader is invoked and the
     * attempt reports [SyncResult.Synced]).
     */
    @Test
    fun consentGranted_syncUploads() = runBlocking {
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 31 + 7)
            uploader.reset()
            val before = uploader.completed.get()

            sync.grantConsent()

            // Release the gate as soon as the upload starts so the sync can complete.
            val releaser = launch(Dispatchers.IO) {
                uploader.uploadStarted.await()
                uploader.gate.complete(Unit)
            }

            val result = withTimeout(5_000) { sync.sync(randomPayload(rnd)) }
            releaser.join()

            assertEquals(
                "iteration $iteration: consent granted must upload",
                SyncResult.Synced, result
            )
            assertEquals(
                "iteration $iteration: uploader must complete exactly once",
                before + 1, uploader.completed.get()
            )
        }
    }

    /**
     * Property (part 2): revoking consent while an upload is in-flight cancels it (the
     * uploader never completes and the attempt reports [SyncResult.Cancelled]), and after
     * revocation subsequent sync() calls no-op ([SyncResult.SkippedNoConsent]) without ever
     * invoking the uploader again — until consent is re-granted.
     */
    @Test
    fun revokeMidFlight_cancelsAndHaltsFurtherSyncing() = runBlocking {
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 97 + 13)
            uploader.reset()

            sync.grantConsent()

            // Start a sync that will block inside the uploader (gate not released).
            val syncDeferred = async(Dispatchers.IO) { sync.sync(randomPayload(rnd)) }

            // Wait until the upload is genuinely in-flight, then revoke.
            withTimeout(5_000) { uploader.uploadStarted.await() }
            val startedCount = uploader.started.get()
            val completedBefore = uploader.completed.get()

            sync.revokeConsent() // cancels the in-flight job (cancelAndJoin)

            val inFlightResult = withTimeout(5_000) { syncDeferred.await() }

            assertEquals(
                "iteration $iteration: in-flight sync must be Cancelled",
                SyncResult.Cancelled, inFlightResult
            )
            assertEquals(
                "iteration $iteration: cancelled upload must NOT complete",
                completedBefore, uploader.completed.get()
            )
            assertFalse(
                "iteration $iteration: consent must be revoked",
                sync.isConsentGranted()
            )

            // Subsequent attempts no-op and never touch the uploader again.
            val startedAfterRevoke = uploader.started.get()
            repeat(rnd.nextInt(1, 4)) {
                val skipped = withTimeout(5_000) { sync.sync(randomPayload(rnd)) }
                assertEquals(
                    "iteration $iteration: post-revoke sync must be SkippedNoConsent",
                    SyncResult.SkippedNoConsent, skipped
                )
            }
            assertEquals(
                "iteration $iteration: uploader must not be invoked while consent revoked",
                startedAfterRevoke, uploader.started.get()
            )
            // Sanity: an upload really had started before revocation.
            assertTrue(
                "iteration $iteration: upload should have started before revoke",
                startedCount >= 1
            )
        }
    }

    /**
     * Property (part 3): re-granting consent after a revocation allows sync to upload again.
     */
    @Test
    fun regrantConsent_allowsSyncAgain() = runBlocking {
        repeat(ITERATIONS) { iteration ->
            val rnd = Random(iteration.toLong() * 53 + 5)
            uploader.reset()

            // Start revoked (from setUp); a sync must be skipped.
            val skipped = withTimeout(5_000) { sync.sync(randomPayload(rnd)) }
            assertEquals(
                "iteration $iteration: sync while revoked must be skipped",
                SyncResult.SkippedNoConsent, skipped
            )

            // Re-grant and confirm sync now uploads.
            sync.grantConsent()
            val completedBefore = uploader.completed.get()

            val releaser = launch(Dispatchers.IO) {
                uploader.uploadStarted.await()
                uploader.gate.complete(Unit)
            }
            val result = withTimeout(5_000) { sync.sync(randomPayload(rnd)) }
            releaser.join()

            assertEquals(
                "iteration $iteration: re-granted consent must allow upload",
                SyncResult.Synced, result
            )
            assertEquals(
                "iteration $iteration: uploader must complete after re-grant",
                completedBefore + 1, uploader.completed.get()
            )

            // Reset consent for the next iteration.
            sync.revokeConsent()
        }
    }
}
