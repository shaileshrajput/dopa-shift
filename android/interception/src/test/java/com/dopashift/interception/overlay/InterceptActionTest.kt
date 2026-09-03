package com.dopashift.interception.overlay

import com.dopashift.domain.entity.InterceptActionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [InterceptAction] — the overlay-facing result emitted by [OverlayViewModel]
 * when the user taps one of the two primary Intercept_Screen controls (Requirement 3.1).
 *
 * These verify the 1:1 mapping to the domain [InterceptActionType] used for the local-only
 * audit record (Requirement 3.4). The service-side routing and audit append behavior are
 * covered separately (tasks 7.2–7.4); here we only pin down the pure enum contract this task
 * introduces.
 */
class InterceptActionTest {

    @Test
    fun `there are exactly two primary actions`() {
        // Requirement 3.1: exactly two primary controls.
        assertEquals(2, InterceptAction.entries.size)
    }

    @Test
    fun `continue-to-app maps to the CONTINUE audit type`() {
        // Requirement 3.3 / 3.4: "Continue to app" is recorded as CONTINUE.
        assertEquals(
            InterceptActionType.CONTINUE,
            InterceptAction.CONTINUE_TO_APP.toAuditType()
        )
    }

    @Test
    fun `switch-to-dopashift maps to the SWITCH_TO_DOPASHIFT audit type`() {
        // Requirement 3.2 / 3.4: "Switch to DopaShift" is recorded as SWITCH_TO_DOPASHIFT.
        assertEquals(
            InterceptActionType.SWITCH_TO_DOPASHIFT,
            InterceptAction.SWITCH_TO_DOPASHIFT.toAuditType()
        )
    }

    @Test
    fun `each action maps to a distinct audit type`() {
        val auditTypes = InterceptAction.entries.map { it.toAuditType() }
        assertEquals(auditTypes.size, auditTypes.toSet().size)
    }
}
