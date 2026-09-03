package com.dopashift.domain.entity

/**
 * The operational state of the Engine for a specific [InterceptionRule]
 * (Requirement 2.5).
 *
 * The pause-and-freeze behavior is modeled explicitly rather than as an implicit
 * boolean because time spent on the overlay must never count against a limit.
 * Pure Kotlin — no Android/framework dependencies.
 */
enum class MonitoringState {
    /** Actively sampling and accumulating foreground usage toward the limit. */
    ACTIVE,

    /** The Intercept_Screen is showing; accumulation is frozen. */
    PAUSED_FOR_OVERLAY,

    /** A Suppression_Context is active; interception is withheld. */
    SUPPRESSED
}
