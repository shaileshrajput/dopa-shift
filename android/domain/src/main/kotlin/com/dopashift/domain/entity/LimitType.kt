package com.dopashift.domain.entity

/**
 * The enforcement pattern for an [InterceptionRule], strictly one of two
 * mutually exclusive options (Requirement 1.1).
 *
 * Pure Kotlin — no Android/framework dependencies.
 */
enum class LimitType {
    /** A single daily cumulative foreground-time threshold. */
    Once,

    /** A recurring interception every configured interval of continued use. */
    Repetitive
}
