package com.dopashift.domain.presentation

/**
 * Presentation-agnostic description of a single animation: how long it runs and which
 * easing curve it uses. The [easingName] is a token name (e.g. "linear", "fastOutSlowIn",
 * "emphasized") resolved to a concrete easing curve by the UI layer — this type carries
 * no Android/Compose dependency so it runs under JVM tests (DUX-2 AC1).
 *
 * @property durationMillis animation duration in whole milliseconds; must be non-negative
 * @property easingName name of the easing token the UI layer maps to a concrete curve
 */
data class MotionSpec(
    val durationMillis: Int,
    val easingName: String
) {
    init {
        require(durationMillis >= 0) { "durationMillis must be non-negative, was $durationMillis" }
        require(easingName.isNotBlank()) { "easingName must not be blank" }
    }
}
