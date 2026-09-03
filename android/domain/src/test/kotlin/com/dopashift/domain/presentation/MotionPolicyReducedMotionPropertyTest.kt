package com.dopashift.domain.presentation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll

/**
 * Property-based test for [MotionPolicy.effective] under the OS reduced-motion setting.
 *
 * Feature: dynamic-ui-experience, Property 3: Reduced motion collapses all decorative durations
 * Validates: Requirements DUX-2.9, DUX-7.6
 *
 * `MotionTokens` lives in the `ui` module, which the pure `domain` module must not depend on,
 * so this test generates [MotionSpec] values directly here. The generator always includes the
 * four canonical durations exposed by `MotionTokens` (Instant 100 / Quick 200 / Standard 300 /
 * Emphasized 450 ms) alongside arbitrary durations and easing names, so the property holds over
 * every spec the UI layer can hand the policy.
 */
class MotionPolicyReducedMotionPropertyTest : StringSpec({

    // motion-instant upper bound for any decorative animation under reduced motion (DUX-2.9).
    val motionInstantMillis = MotionPolicy.MOTION_INSTANT_MILLIS

    // The four canonical MotionTokens durations, mirrored from the ui module's MotionTokens.
    val canonicalDurations = listOf(100, 200, 300, 450)

    val easingNames = Arb.of("linear", "fastOutSlowIn", "emphasized", "easeIn", "easeOut")

    // Generate MotionSpecs: bias toward the four canonical durations, but also cover arbitrary
    // non-negative durations (including sub-instant ones) so the whole input space is exercised.
    val motionSpecs: Arb<MotionSpec> = arbitrary { rs ->
        val duration = if (rs.random.nextBoolean()) {
            canonicalDurations.random(rs.random)
        } else {
            Arb.int(0, 5_000).sample(rs).value
        }
        MotionSpec(durationMillis = duration, easingName = easingNames.sample(rs).value)
    }

    val config = PropTestConfig(iterations = 200)

    "Property 3 (DUX-2.9): reduced motion collapses every duration to at most motion-instant" {
        checkAll(config, motionSpecs) { spec ->
            val effective = MotionPolicy.effective(spec, reducedMotion = true)
            effective.durationMillis shouldBeLessThanOrEqual motionInstantMillis
        }
    }

    "Property 3 (DUX-2.9): the four canonical MotionTokens durations all collapse under reduced motion" {
        canonicalDurations.forEach { duration ->
            val effective = MotionPolicy.effective(
                MotionSpec(durationMillis = duration, easingName = "fastOutSlowIn"),
                reducedMotion = true
            )
            effective.durationMillis shouldBeLessThanOrEqual motionInstantMillis
        }
    }

    "Property 3 (DUX-2.9): reduced motion preserves the easing name (only duration is collapsed)" {
        checkAll(config, motionSpecs) { spec ->
            val effective = MotionPolicy.effective(spec, reducedMotion = true)
            effective.easingName shouldBe spec.easingName
        }
    }

    "Property 3 (DUX-7.6): with motion enabled the base spec is returned unchanged" {
        checkAll(config, motionSpecs) { spec ->
            val effective = MotionPolicy.effective(spec, reducedMotion = false)
            (effective == spec).shouldBeTrue()
        }
    }
})
