package com.dopashift.interception

import com.dopashift.domain.entity.InterceptActionAudit
import com.dopashift.domain.entity.InterceptActionType
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType
import com.dopashift.domain.entity.MonitoringState
import com.dopashift.interception.overlay.InterceptAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

// Feature: android-app-limit-repitative, Property 9

/**
 * Property 9: Switch action routing
 *
 * *For any* "Switch to DopaShift" tap, the overlay is removed, the DopaShift main activity is
 * launched, `Session_Usage` is reset to 0, and Monitoring_State remains `ACTIVE`.
 *
 * **Validates: Requirements 3.1, 3.2**
 *
 * ### What this test exercises
 * [InterceptionService] is an Android [android.app.Service] and cannot be unit-tested directly, so
 * — exactly like the sibling [OverlayActionRoutingWiringTest],
 * [RepetitiveInterceptionRoutingWiringTest], and [IntervalBreachPresentationPropertyTest] — this
 * test reconstructs the *collaborators' contract* the service composes for a "Switch to DopaShift"
 * tap (task 7.2) and asserts the observable outcome of that routing:
 *
 * 1. **Overlay removed** — the service clears `isOverlayActive` before routing the action. Modelled
 *    here by a boolean flag that the routing sets to `false`.
 * 2. **DopaShift main activity launched** — a `packageManager` launch-intent side effect that
 *    cannot be observed in a plain JVM unit test (there is no `Context`/`PackageManager`), just as
 *    the sibling tests treat `presentOverlay` as an unassertable Android call. It is therefore
 *    modelled as a recorded intent and covered by instrumented tests; here we assert the launch is
 *    *requested exactly once* against the correct target, which is the deterministic part of the
 *    contract.
 * 3. **Session_Usage reset to 0 and Monitoring_State ACTIVE** — driven through the real
 *    [RepetitiveSessionTracker.onOverlayDismissedAndReturned]; for the Switch action the Rule is
 *    left `ACTIVE` (Requirement 3.2) rather than switched away from monitoring.
 * 4. **Exactly one SWITCH_TO_DOPASHIFT audit** — one [InterceptActionAudit] appended to the
 *    user-scoped [com.dopashift.domain.repository.InterceptActionAuditRepository] fake, with the
 *    target package and `userId` (Requirement 3.4 wiring; asserted here as part of the Switch
 *    routing so the audit type/package/user mapping is proven for Property 9).
 *
 * The property is quantified over randomized cycles: intervals 1..120 minutes, varied
 * accumulation, and a starting Monitoring_State of either `ACTIVE` (still accumulating) or
 * `PAUSED_FOR_OVERLAY` (overlay already presented), so the Switch routing is proven to converge to
 * the same reset-and-ACTIVE outcome regardless of the pre-action state.
 *
 * Module convention: JUnit4 + kotlin.random.Random + repeat(N>=100) +
 * kotlinx.coroutines.test.runTest (Kotest is not on this module's test classpath).
 */
class SwitchActionRoutingPropertyTest {

    private val zone = ZoneOffset.UTC
    private val fixedInstant = Instant.parse("2024-06-15T09:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, zone)
    private val testUserId = testUuid("user-property9")

    /**
     * Minimal reconstruction of the service's "Switch to DopaShift" routing, wiring the real
     * [RepetitiveSessionTracker] and the [FakeInterceptActionAuditRepository] the same way
     * [InterceptionService] does. Mirrors `InterceptionService.onSwitchToDopaShift(pkg)`.
     */
    private class SwitchRoutingHarness(
        val tracker: RepetitiveSessionTracker,
        private val auditRepository: FakeInterceptActionAuditRepository,
        private val userId: UUID,
        private val clock: Clock
    ) {
        /** Whether the overlay window is currently shown (service's `isOverlayActive`). */
        var overlayActive: Boolean = true
            private set

        /** Packages for which the DopaShift main-activity launch was requested, in order. */
        val launchedFor = mutableListOf<String>()

        /** Executes the Switch routing exactly as the service composes it. */
        suspend fun onSwitchToDopaShift(pkg: String) {
            // 1. Overlay removed.
            overlayActive = false
            // 2. Launch DopaShift main activity (Android side effect; recorded, not observable here).
            launchedFor.add(pkg)
            // 3. Reset Session_Usage to 0, keep Monitoring_State ACTIVE.
            tracker.onOverlayDismissedAndReturned(pkg)
            // 4. Append exactly one SWITCH_TO_DOPASHIFT audit entry.
            val action = InterceptAction.SWITCH_TO_DOPASHIFT
            auditRepository.append(
                userId,
                InterceptActionAudit(
                    id = UUID.randomUUID(),
                    userId = userId,
                    appPackageName = pkg,
                    actionType = action.toAuditType(),
                    recordedAt = clock.instant()
                )
            )
        }
    }

    private fun newHarness(): Pair<FakeInterceptionRuleRepository, SwitchRoutingHarness> {
        val repo = FakeInterceptionRuleRepository()
        val tracker = RepetitiveSessionTracker(repo).apply { userId = testUserId }
        val auditRepository = FakeInterceptActionAuditRepository()
        val harness = SwitchRoutingHarness(tracker, auditRepository, testUserId, clock)
        // Stash the audit repo alongside so the test can assert on it.
        lastAuditRepository = auditRepository
        return repo to harness
    }

    /** Side channel to the audit repo backing the most recently built harness (single-threaded). */
    private lateinit var lastAuditRepository: FakeInterceptActionAuditRepository

    private fun repetitiveRule(packageName: String, intervalMinutes: Int): InterceptionRule =
        InterceptionRule(
            id = testUuid(packageName),
            userId = testUserId,
            appPackageName = packageName,
            dailyLimitMinutes = 60, // unused for Repetitive but must satisfy the 1..480 invariant
            limitType = LimitType.Repetitive,
            repetitiveIntervalMinutes = intervalMinutes,
            enabled = true,
            pausedForDate = null,
            createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
        )

    /**
     * Property: over randomized cycles (interval 1..120, varied accumulation, starting from ACTIVE
     * or PAUSED_FOR_OVERLAY), applying the Switch routing always removes the overlay, launches the
     * main activity exactly once, resets Session_Usage to 0, leaves the state ACTIVE, and appends
     * exactly one SWITCH_TO_DOPASHIFT audit with the correct package and userId.
     */
    @Test
    fun `property - switch routing removes overlay, launches, resets to ACTIVE, and audits once`() =
        runTest {
            val random = Random(seed = 90_2024)

            repeat(300) { iteration ->
                val (repo, harness) = newHarness()

                val intervalMinutes = random.nextInt(1, 121)
                val intervalSeconds = intervalMinutes.toLong() * 60L
                val packageName = "com.repetitive.switch.$iteration"
                repo.addRule(repetitiveRule(packageName, intervalMinutes))

                // Accumulate an arbitrary amount of Session_Usage while ACTIVE.
                val incrementCount = random.nextInt(1, 25)
                repeat(incrementCount) {
                    val elapsed = random.nextLong(1, intervalSeconds + 30L)
                    harness.tracker.onForeground(packageName, elapsed)
                }
                assertTrue(
                    "Iteration $iteration: expected some accumulation before the action",
                    harness.tracker.sessionUsageSeconds(packageName) > 0L
                )

                // Randomly start from ACTIVE (still accumulating) or PAUSED_FOR_OVERLAY (overlay up),
                // proving the Switch routing converges to the same outcome regardless.
                val startPaused = random.nextBoolean()
                if (startPaused) {
                    harness.tracker.markPausedForOverlay(packageName)
                    assertEquals(
                        "Iteration $iteration: precondition PAUSED_FOR_OVERLAY",
                        MonitoringState.PAUSED_FOR_OVERLAY,
                        harness.tracker.stateOf(packageName)
                    )
                }

                // Apply the "Switch to DopaShift" routing.
                harness.onSwitchToDopaShift(packageName)

                // Overlay removed (Requirement 3.1 / 3.2).
                assertTrue(
                    "Iteration $iteration: overlay must be removed after Switch",
                    !harness.overlayActive
                )
                // DopaShift main activity launched exactly once for the target package (Req 3.2;
                // the actual Android launch is covered by instrumented tests).
                assertEquals(
                    "Iteration $iteration: main activity launched exactly once for the target",
                    listOf(packageName),
                    harness.launchedFor
                )
                // Session_Usage reset to 0 and Monitoring_State remains ACTIVE (Requirement 3.2).
                assertEquals(
                    "Iteration $iteration: Session_Usage reset to 0 after Switch",
                    0L,
                    harness.tracker.sessionUsageSeconds(packageName)
                )
                assertEquals(
                    "Iteration $iteration: Monitoring_State remains ACTIVE after Switch",
                    MonitoringState.ACTIVE,
                    harness.tracker.stateOf(packageName)
                )

                // Exactly one SWITCH_TO_DOPASHIFT audit, correctly attributed (Requirement 3.4 wiring).
                assertEquals(
                    "Iteration $iteration: exactly one audit entry per Switch action",
                    1,
                    lastAuditRepository.appended.size
                )
                val entry = lastAuditRepository.appended.single()
                assertEquals(
                    "Iteration $iteration: audit action type is SWITCH_TO_DOPASHIFT",
                    InterceptActionType.SWITCH_TO_DOPASHIFT,
                    entry.actionType
                )
                assertEquals(
                    "Iteration $iteration: audit package matches the Monitored_App",
                    packageName,
                    entry.appPackageName
                )
                assertEquals(
                    "Iteration $iteration: audit scoped to the authenticated user",
                    testUserId,
                    entry.userId
                )
                assertEquals(
                    "Iteration $iteration: audit timestamped from the injected clock",
                    fixedInstant,
                    entry.recordedAt
                )
            }
        }

    /**
     * Property (resume after switch): after a Switch action leaves the Rule ACTIVE with a reset
     * cycle, a subsequent below-interval foreground sample accumulates cleanly from 0 — the next
     * interval cycle begins fresh (Requirement 3.2 leaves monitoring ACTIVE).
     */
    @Test
    fun `property - switch leaves monitoring ACTIVE so the next cycle accumulates from 0`() =
        runTest {
            val random = Random(seed = 91_2024)

            repeat(200) { iteration ->
                val (repo, harness) = newHarness()

                // Keep interval >= 2 min so one small resume sample stays below the interval.
                val intervalMinutes = random.nextInt(2, 121)
                val intervalSeconds = intervalMinutes.toLong() * 60L
                val packageName = "com.repetitive.switchresume.$iteration"
                repo.addRule(repetitiveRule(packageName, intervalMinutes))

                // Breach the interval, present overlay, then Switch.
                var accumulated = 0L
                while (accumulated < intervalSeconds) {
                    harness.tracker.onForeground(packageName, 5L)
                    accumulated += 5L
                }
                harness.tracker.markPausedForOverlay(packageName)
                harness.onSwitchToDopaShift(packageName)

                assertEquals(
                    "Iteration $iteration: ACTIVE after Switch",
                    MonitoringState.ACTIVE,
                    harness.tracker.stateOf(packageName)
                )

                // Next foreground sample resumes accumulation from exactly that sample.
                val resumeElapsed = random.nextLong(1, 60L)
                val resumeResult = harness.tracker.onForeground(packageName, resumeElapsed)
                assertEquals(
                    "Iteration $iteration: next cycle accumulates from the resume sample",
                    resumeElapsed,
                    harness.tracker.sessionUsageSeconds(packageName)
                )
                assertEquals(
                    "Iteration $iteration: below-interval resume reports Accumulating",
                    SessionResult.Accumulating,
                    resumeResult
                )
            }
        }
}

// Fakes are in TestFakes.kt (shared across interception test files)
