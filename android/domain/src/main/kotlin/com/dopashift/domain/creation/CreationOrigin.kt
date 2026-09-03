package com.dopashift.domain.creation

/**
 * Identifies where a creation action was initiated from, so that Dashboard-originated
 * creation can be shown to drive the same use cases as the dedicated screens
 * (DQC-1.10) and remain fully traceable (DQC-6.3).
 *
 * The Dashboard is not a parallel data path: [DASHBOARD], [DEDICATED_SCREEN], and
 * [INTERCEPT_OVERLAY] all flow through the identical domain use cases and validators.
 */
enum class CreationOrigin {
    /** Initiated from the Dashboard Quick_Create_Menu, a Creation_Sheet, or Today's Focus quick-add. */
    DASHBOARD,

    /** Initiated from a dedicated feature screen (Goals, Habits, Daily Tasks). */
    DEDICATED_SCREEN,

    /** Initiated from within the Intercept_Overlay (Inline_Goal_Capture, DQC-5.10). */
    INTERCEPT_OVERLAY
}
