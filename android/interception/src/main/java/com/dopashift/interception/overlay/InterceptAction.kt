package com.dopashift.interception.overlay

import com.dopashift.domain.entity.InterceptActionType

/**
 * The primary action a user chose on the Intercept_Screen overlay.
 *
 * The overlay exposes exactly two primary controls (Requirement 3.1): "Continue to app"
 * and "Switch to DopaShift". [OverlayViewModel] emits one of these results when the user
 * taps a control; the hosting service consumes it to route the correct behavior and to
 * append a local-only audit entry (Requirements 3.2, 3.3, 3.4). Routing/audit wiring lives
 * in the service and is handled separately from this overlay-facing result.
 *
 * Pure enum — maps 1:1 to the domain [InterceptActionType] used for the audit record.
 */
enum class InterceptAction {
    /** User tapped "Continue to app" — return focus to the Monitored_App (Requirement 3.3). */
    CONTINUE_TO_APP,

    /** User tapped "Switch to DopaShift" — launch the DopaShift main activity (Requirement 3.2). */
    SWITCH_TO_DOPASHIFT;

    /** The domain audit action type this overlay action maps to (Requirement 3.4). */
    fun toAuditType(): InterceptActionType = when (this) {
        CONTINUE_TO_APP -> InterceptActionType.CONTINUE
        SWITCH_TO_DOPASHIFT -> InterceptActionType.SWITCH_TO_DOPASHIFT
    }
}
