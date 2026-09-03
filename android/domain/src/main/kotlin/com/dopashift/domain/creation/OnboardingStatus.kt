package com.dopashift.domain.creation

/**
 * Persisted onboarding outcome used to resolve Dashboard state and to guarantee
 * onboarding is never re-presented after completion or skip (DQC-5.7, DQC-5.8).
 */
enum class OnboardingStatus {
    /** The user finished the onboarding flow. */
    COMPLETED,

    /** The user exited onboarding early via the Skip affordance (DQC-5.1). */
    SKIPPED,

    /** The user has not yet begun (or completed) onboarding. */
    NOT_STARTED
}
