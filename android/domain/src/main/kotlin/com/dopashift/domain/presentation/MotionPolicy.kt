package com.dopashift.domain.presentation

/**
 * Pure decision logic for how motion behaves under the OS reduced-motion accessibility
 * setting (DUX-2 AC9, DUX-7 AC6). Framework-free so it runs under JVM tests.
 *
 * The policy only decides the *effective* [MotionSpec] to animate with — it never applies
 * the underlying state change. Callers still perform the state change; when reduced motion
 * is active, that change simply happens (near-)instantly rather than being animated. This
 * guarantees information is never conveyed by animation alone.
 */
object MotionPolicy {

    /** Upper bound on any decorative animation under reduced motion: `motion-instant` (100 ms). */
    const val MOTION_INSTANT_MILLIS: Int = 100

    /**
     * Returns the [MotionSpec] to animate with, given whether the OS reduced-motion setting
     * is enabled.
     *
     * When [reducedMotion] is `true`, every decorative duration collapses so it does not exceed
     * `motion-instant` (100 ms); a spec already at or below that bound is returned unchanged (aside
     * from identity), and the associated state change is left to the caller. When [reducedMotion]
     * is `false`, the [base] spec is returned unchanged.
     *
     * @param base the spec the UI would use with motion enabled
     * @param reducedMotion whether the OS "Remove animations" / reduced-motion setting is on
     * @return the effective spec to animate with (DUX-2 AC9, DUX-7 AC6)
     */
    fun effective(base: MotionSpec, reducedMotion: Boolean): MotionSpec {
        if (!reducedMotion) return base
        if (base.durationMillis <= MOTION_INSTANT_MILLIS) return base
        return base.copy(durationMillis = MOTION_INSTANT_MILLIS)
    }
}
