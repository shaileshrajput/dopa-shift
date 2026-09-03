package com.dopashift.ui.render

// Feature: android-ui-upgrade, Property 1: Continue button gated strictly by cooldown

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.merge
import io.kotest.property.checkAll

/**
 * Property test for **Property 1: Continue Button Gated Strictly by Cooldown**.
 *
 * // Feature: android-ui-upgrade, Property 1: Continue button gated strictly by cooldown
 *
 * Validates: Requirements 2.7 (AUI-2.7), satisfies AUI-9.4
 *
 * The property: the overlay's secondary "Continue to app" button is enabled
 * **if and only if** the cooldown supplied by `screen-time-interception-engine`
 * has fully elapsed — i.e. [continueEnabled] returns `true` exactly when
 * `cooldownSeconds == 0`. Every positive cooldown keeps the button disabled
 * (WHILE the counter is greater than zero → disabled), and only reaching zero
 * enables it (WHEN the counter reaches zero → enabled). Spurious negative values
 * are treated as "not yet zero" and therefore keep the button disabled.
 *
 * [continueEnabled] is a pure JVM function (no Composable, no Android runtime),
 * so this property runs off-device under Kotest without an emulator, in lockstep
 * with the on-device overlay which reads the same helper rather than running its
 * own timer.
 *
 * Each property runs a minimum of 200 generated cases (>= the 100-iteration floor),
 * spanning zero, positive, and negative cooldown values.
 */
class Property1ContinueGatingTest : StringSpec({

    val minIterations = PropTestConfig(iterations = 200)

    // Cooldown seconds spanning the full Int space, biased to include the boundary
    // (0), positive countdown values, and spurious negatives. Arb.int() already
    // draws across negatives and positives and shrinks toward 0; merging an
    // explicit non-negative stream ensures dense coverage near the 0 boundary
    // where the gating flips.
    val cooldowns: Arb<Int> =
        Arb.int().merge(Arb.int(0, 600).map { it })

    "continueEnabled is true iff the cooldown is exactly zero" {
        checkAll(minIterations, cooldowns) { cooldownSeconds ->
            continueEnabled(cooldownSeconds) shouldBe (cooldownSeconds == 0)
        }
    }

    "any positive cooldown keeps the continue button disabled" {
        checkAll(minIterations, Arb.int(1, Int.MAX_VALUE)) { remaining ->
            continueEnabled(remaining) shouldBe false
        }
    }

    "any negative (spurious) cooldown keeps the continue button disabled" {
        checkAll(minIterations, Arb.int(Int.MIN_VALUE, -1)) { spurious ->
            continueEnabled(spurious) shouldBe false
        }
    }

    "the zero boundary enables the continue button" {
        // Boundary the property pivots on: WHEN the counter reaches zero → enabled.
        continueEnabled(0) shouldBe true
    }
})
