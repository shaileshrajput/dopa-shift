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
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Wiring tests for the overlay-action routing contract that [InterceptionService] composes for a
 * Continue/Switch tap (task 7.2). [InterceptionService] is an Android `Service` that cannot be
 * unit-tested directly, so — like the sibling [RepetitiveInterceptionRoutingWiringTest] and
 * [InterceptionServiceWiringTest] — these tests exercise the *collaborators' contract* the service
 * wires together: the real [RepetitiveSessionTracker] (Session_Usage reset + ACTIVE transition on
 * dismissal) and a [FakeInterceptActionAuditRepository] standing in for the domain
 * [com.dopashift.domain.repository.InterceptActionAuditRepository] port.
 *
 * The routing rule the service depends on for an executed overlay action is:
 * 1. Both "Continue to app" and "Switch to DopaShift" reset the Rule's `Session_Usage` to 0 and
 *    return its Monitoring_State to `ACTIVE` via
 *    [RepetitiveSessionTracker.onOverlayDismissedAndReturned] (Requirements 3.2, 3.3).
 * 2. Either action appends exactly ONE [InterceptActionAudit] entry scoped to the authenticated
 *    user, with the target package and the correct [InterceptActionType] mapped from the tapped
 *    [InterceptAction] (Requirement 3.4).
 * 3. The audit repository has no network path — the record is on-device only (Requirement 3.5).
 * 4. An audit write failure never changes the tracker outcome (the action UX is not gated on the
 *    audit append).
 *
 * Validates: Requirements 3.2, 3.3, 3.4
 */
class OverlayActionRoutingWiringTest {

    private val zone = ZoneOffset.UTC
    private val fixedInstant = Instant.parse("2024-06-15T09:00:00Z")
    private val testPackage = "com.social.distracting"
    private val testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private lateinit var fakeRuleRepository: FakeInterceptionRuleRepository
    private lateinit var tracker: RepetitiveSessionTracker
    private lateinit var auditRepository: FakeInterceptActionAuditRepository
    private lateinit var clock: Clock

    @Before
    fun setup() {
        fakeRuleRepository = FakeInterceptionRuleRepository()
        tracker = RepetitiveSessionTracker(fakeRuleRepository)
        tracker.userId = testUserId
        auditRepository = FakeInterceptActionAuditRepository()
        clock = Clock.fixed(fixedInstant, zone)
    }

    private fun repetitiveRule(intervalMinutes: Int): InterceptionRule = InterceptionRule(
        id = testUuid(testPackage),
        userId = testUserId,
        appPackageName = testPackage,
        dailyLimitMinutes = 60,
        limitType = LimitType.Repetitive,
        repetitiveIntervalMinutes = intervalMinutes,
        enabled = true,
        pausedForDate = null,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )

    /** Mirrors [InterceptionService.appendActionAudit] — one entry, user-scoped, clock-timestamped. */
    private suspend fun appendActionAudit(packageName: String, action: InterceptAction) {
        val audit = InterceptActionAudit(
            id = UUID.randomUUID(),
            userId = testUserId,
            appPackageName = packageName,
            actionType = action.toAuditType(),
            recordedAt = clock.instant()
        )
        auditRepository.append(testUserId, audit)
    }

    /** Drives the tracker to a paused-for-overlay state, as the service does before an action. */
    private suspend fun accumulateAndPause(intervalMinutes: Int) {
        fakeRuleRepository.addRule(repetitiveRule(intervalMinutes))
        val intervalSeconds = intervalMinutes.toLong() * 60L
        var accumulated = 0L
        while (accumulated < intervalSeconds) {
            tracker.onForeground(testPackage, 5L)
            accumulated += 5L
        }
        tracker.markPausedForOverlay(testPackage)
        assertEquals(MonitoringState.PAUSED_FOR_OVERLAY, tracker.stateOf(testPackage))
    }

    // --- Requirement 3.3: Continue to app resets + ACTIVE and appends a CONTINUE audit ---

    @Test
    fun `continue action resets Session_Usage to ACTIVE and appends one CONTINUE audit`() = runTest {
        accumulateAndPause(intervalMinutes = 1)

        // Service routing for "Continue to app": reset+ACTIVE, then append the audit.
        tracker.onOverlayDismissedAndReturned(testPackage)
        appendActionAudit(testPackage, InterceptAction.CONTINUE_TO_APP)

        assertEquals(MonitoringState.ACTIVE, tracker.stateOf(testPackage))
        assertEquals(0L, tracker.sessionUsageSeconds(testPackage))

        assertEquals(1, auditRepository.appended.size)
        val entry = auditRepository.appended.single()
        assertEquals(InterceptActionType.CONTINUE, entry.actionType)
        assertEquals(testPackage, entry.appPackageName)
        assertEquals(testUserId, entry.userId)
        assertEquals(fixedInstant, entry.recordedAt)
    }

    // --- Requirement 3.2: Switch to DopaShift resets + ACTIVE and appends a SWITCH audit ---

    @Test
    fun `switch action resets Session_Usage to ACTIVE and appends one SWITCH audit`() = runTest {
        accumulateAndPause(intervalMinutes = 2)

        // Service routing for "Switch to DopaShift": reset+ACTIVE, launch main activity (untestable
        // here), then append the audit.
        tracker.onOverlayDismissedAndReturned(testPackage)
        appendActionAudit(testPackage, InterceptAction.SWITCH_TO_DOPASHIFT)

        assertEquals(MonitoringState.ACTIVE, tracker.stateOf(testPackage))
        assertEquals(0L, tracker.sessionUsageSeconds(testPackage))

        assertEquals(1, auditRepository.appended.size)
        val entry = auditRepository.appended.single()
        assertEquals(InterceptActionType.SWITCH_TO_DOPASHIFT, entry.actionType)
        assertEquals(testPackage, entry.appPackageName)
        assertEquals(testUserId, entry.userId)
    }

    // --- Requirement 3.4: exactly ONE audit entry per executed action ---

    @Test
    fun `each executed action appends exactly one audit entry`() = runTest {
        accumulateAndPause(intervalMinutes = 1)

        tracker.onOverlayDismissedAndReturned(testPackage)
        appendActionAudit(testPackage, InterceptAction.CONTINUE_TO_APP)
        assertEquals(1, auditRepository.appended.size)

        // A subsequent cycle + Switch appends exactly one more (two total).
        accumulateAndPause(intervalMinutes = 1)
        tracker.onOverlayDismissedAndReturned(testPackage)
        appendActionAudit(testPackage, InterceptAction.SWITCH_TO_DOPASHIFT)
        assertEquals(2, auditRepository.appended.size)
    }

    // --- Audit failure must not block the action (tracker still resets + ACTIVE) ---

    @Test
    fun `audit write failure does not change the action outcome`() = runTest {
        accumulateAndPause(intervalMinutes = 1)
        auditRepository.failNext = true

        // The service resets the tracker regardless of the (best-effort) audit append.
        tracker.onOverlayDismissedAndReturned(testPackage)
        val result = auditRepository.append(
            testUserId,
            InterceptActionAudit(
                id = UUID.randomUUID(),
                userId = testUserId,
                appPackageName = testPackage,
                actionType = InterceptActionType.CONTINUE,
                recordedAt = clock.instant()
            )
        )

        assertTrue("audit append reports failure", result.isFailure)
        // Action outcome is unaffected: still ACTIVE with a reset cycle, no entry recorded.
        assertEquals(MonitoringState.ACTIVE, tracker.stateOf(testPackage))
        assertEquals(0L, tracker.sessionUsageSeconds(testPackage))
        assertTrue(auditRepository.appended.isEmpty())
    }

    // --- InterceptAction maps to the correct domain audit type (Requirement 3.4) ---

    @Test
    fun `intercept action maps to the correct domain audit type`() {
        assertEquals(InterceptActionType.CONTINUE, InterceptAction.CONTINUE_TO_APP.toAuditType())
        assertEquals(
            InterceptActionType.SWITCH_TO_DOPASHIFT,
            InterceptAction.SWITCH_TO_DOPASHIFT.toAuditType()
        )
    }
}
