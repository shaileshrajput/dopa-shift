package com.dopashift.interception

import com.dopashift.domain.entity.InterceptActionAudit
import com.dopashift.domain.entity.InterceptActionType
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType
import com.dopashift.domain.entity.MonitoringState
import com.dopashift.interception.overlay.InterceptAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

// Feature: android-app-limit-repitative, Property 8

/**
 * Property 8: Continue action routing
 *
 * For any "Continue to app" tap, the overlay is removed, focus returns to the Monitored_App,
 * Session_Usage is reset to 0, and Monitoring_State is [MonitoringState.ACTIVE]; and exactly one
 * [InterceptActionAudit] of type [InterceptActionType.CONTINUE] is appended locally with the
 * correct package name and authenticated userId.
 *
 * [InterceptionService] is an Android `Service` that cannot be unit-tested directly, so — like
 * the sibling [OverlayActionRoutingWiringTest], [RepetitiveInterceptionRoutingWiringTest], and
 * [IntervalBreachPresentationPropertyTest] — this property reconstructs the *collaborators'
 * contract* the service wires together for a Continue tap:
 *
 *  1. reset + resume via [RepetitiveSessionTracker.onOverlayDismissedAndReturned]
 *     (Session_Usage -> 0, Monitoring_State -> ACTIVE), which stands in for "overlay removed /
 *     focus returns to the app"; and
 *  2. append exactly one CONTINUE [InterceptActionAudit] via the domain
 *     [com.dopashift.domain.repository.InterceptActionAuditRepository] port
 *     (here [FakeInterceptActionAuditRepository]).
 *
 * The property is exercised over randomized cycles: varied intervals (1..120 minutes), varied
 * accumulation, and starting from either [MonitoringState.ACTIVE] (breach reached without an
 * explicit pause) or [MonitoringState.PAUSED_FOR_OVERLAY] (overlay was presented). Regardless of
 * the pre-Continue state, the post-Continue invariant must hold.
 *
 * **Validates: Requirements 3.1, 3.3**
 *
 * Module convention: JUnit4 + kotlin.random.Random + repeat(N>=100) +
 * kotlinx.coroutines.test.runTest + a fixed [Clock] (Kotest is not on this module's classpath).
 */
class ContinueActionRoutingPropertyTest {

    private val zone = ZoneOffset.UTC
    private val fixedInstant = Instant.parse("2024-06-15T09:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, zone)

    private val testUserId = testUuid("continue-routing-user")

    private fun newTracker(): Pair<FakeInterceptionRuleRepository, RepetitiveSessionTracker> {
        val repo = FakeInterceptionRuleRepository()
        val tracker = RepetitiveSessionTracker(repo)
        tracker.userId = testUserId
        return repo to tracker
    }

    /** Builds an enabled `Repetitive` domain rule with the given interval (whole minutes, 1..120). */
    private fun repetitiveRule(packageName: String, intervalMinutes: Int): InterceptionRule =
        InterceptionRule(
            id = testUuid(packageName),
            userId = testUserId,
            appPackageName = packageName,
            dailyLimitMinutes = 60,
            limitType = LimitType.Repetitive,
            repetitiveIntervalMinutes = intervalMinutes,
            enabled = true,
            pausedForDate = null,
            createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
        )

    /**
     * Mirrors [InterceptionService]'s Continue routing: reset + resume the tracker, then append
     * exactly one CONTINUE audit scoped to the authenticated user, clock-timestamped.
     */
    private suspend fun routeContinue(
        tracker: RepetitiveSessionTracker,
        auditRepository: FakeInterceptActionAuditRepository,
        packageName: String
    ) {
        // "Continue to app": overlay removed / focus returns -> reset Session_Usage + ACTIVE.
        tracker.onOverlayDismissedAndReturned(packageName)
        // Append exactly one audit for the executed action (Requirement 3.4 append path).
        val audit = InterceptActionAudit(
            id = UUID.randomUUID(),
            userId = testUserId,
            appPackageName = packageName,
            actionType = InterceptAction.CONTINUE_TO_APP.toAuditType(),
            recordedAt = clock.instant()
        )
        auditRepository.append(testUserId, audit)
    }

    /**
     * Property: over randomized repetitive cycles, applying the Continue routing always yields
     * Session_Usage == 0, Monitoring_State == ACTIVE, and exactly one CONTINUE audit with the
     * correct package + userId — from either an ACTIVE (unpaused breach) or a
     * PAUSED_FOR_OVERLAY pre-state.
     */
    @Test
    fun `property - continue routing resets to ACTIVE with Session_Usage 0 and one CONTINUE audit`() = runTest {
        val random = Random(seed = 8_2024)

        repeat(200) { iteration ->
            val (repo, tracker) = newTracker()
            val auditRepository = FakeInterceptActionAuditRepository()

            val intervalMinutes = random.nextInt(1, 121)
            val intervalSeconds = intervalMinutes.toLong() * 60L
            val packageName = "com.repetitive.continue.$iteration"
            repo.addRule(repetitiveRule(packageName, intervalMinutes))

            // Accumulate varied usage while ACTIVE (may or may not breach the interval).
            val incrementCount = random.nextInt(1, 20)
            repeat(incrementCount) {
                val elapsed = random.nextLong(1, intervalSeconds + 30L)
                tracker.onForeground(packageName, elapsed)
            }

            // Randomly present the overlay first (PAUSED_FOR_OVERLAY) vs. Continue straight from
            // ACTIVE — the post-Continue invariant must hold either way.
            val pauseFirst = random.nextBoolean()
            if (pauseFirst) {
                tracker.markPausedForOverlay(packageName)
                assertEquals(
                    "Iteration $iteration: pre-Continue state should be PAUSED_FOR_OVERLAY",
                    MonitoringState.PAUSED_FOR_OVERLAY,
                    tracker.stateOf(packageName)
                )
            }

            // Apply the Continue routing.
            routeContinue(tracker, auditRepository, packageName)

            // Invariant: focus returns / overlay removed -> ACTIVE with Session_Usage reset to 0.
            assertEquals(
                "Iteration $iteration: Continue must return Monitoring_State to ACTIVE",
                MonitoringState.ACTIVE,
                tracker.stateOf(packageName)
            )
            assertEquals(
                "Iteration $iteration: Continue must reset Session_Usage to 0",
                0L,
                tracker.sessionUsageSeconds(packageName)
            )

            // Invariant: exactly one CONTINUE audit with the correct package + user.
            assertEquals(
                "Iteration $iteration: exactly one audit entry per Continue action",
                1,
                auditRepository.appended.size
            )
            val entry = auditRepository.appended.single()
            assertEquals(
                "Iteration $iteration: audit action type must be CONTINUE",
                InterceptActionType.CONTINUE,
                entry.actionType
            )
            assertEquals(
                "Iteration $iteration: audit package must match the Monitored_App",
                packageName,
                entry.appPackageName
            )
            assertEquals(
                "Iteration $iteration: audit must be scoped to the authenticated user",
                testUserId,
                entry.userId
            )
            assertEquals(
                "Iteration $iteration: only the authenticated user's audits are listed",
                1,
                auditRepository.listForUser(testUserId).size
            )
        }
    }

    /**
     * Property (resume-after-continue): once Continue has reset the cycle, a following
     * below-interval foreground sample resumes accumulation from exactly that sample's elapsed
     * value and reports [SessionResult.Accumulating] — confirming the next interval cycle begins
     * cleanly from 0 (Requirement 3.3 "begin the next interval cycle").
     */
    @Test
    fun `property - after continue the next cycle resumes accumulation from 0`() = runTest {
        val random = Random(seed = 8_1_2024)

        repeat(150) { iteration ->
            val (repo, tracker) = newTracker()
            val auditRepository = FakeInterceptActionAuditRepository()

            // interval >= 2 min so a single small resume sample stays below the interval.
            val intervalMinutes = random.nextInt(2, 121)
            val intervalSeconds = intervalMinutes.toLong() * 60L
            val packageName = "com.repetitive.continue.resume.$iteration"
            repo.addRule(repetitiveRule(packageName, intervalMinutes))

            // Drive usage up to a breach, then present + Continue.
            var accumulated = 0L
            while (accumulated < intervalSeconds) {
                val elapsed = random.nextLong(1, intervalSeconds + 1L)
                tracker.onForeground(packageName, elapsed)
                accumulated += elapsed
            }
            tracker.markPausedForOverlay(packageName)
            routeContinue(tracker, auditRepository, packageName)

            // Post-Continue baseline.
            assertEquals(0L, tracker.sessionUsageSeconds(packageName))
            assertEquals(MonitoringState.ACTIVE, tracker.stateOf(packageName))

            // Next cycle resumes from 0 on a below-interval sample.
            val resumeElapsed = random.nextLong(1, 60L) // < interval (interval >= 120s)
            val resumeResult = tracker.onForeground(packageName, resumeElapsed)
            assertEquals(
                "Iteration $iteration: post-Continue accumulation must start from the resume sample",
                resumeElapsed,
                tracker.sessionUsageSeconds(packageName)
            )
            assertEquals(
                "Iteration $iteration: below-interval resume must report Accumulating",
                SessionResult.Accumulating,
                resumeResult
            )
            // Continue appended exactly one audit for this cycle.
            assertEquals(1, auditRepository.appended.size)
        }
    }
}

// Fakes are in TestFakes.kt (shared across interception test files)
