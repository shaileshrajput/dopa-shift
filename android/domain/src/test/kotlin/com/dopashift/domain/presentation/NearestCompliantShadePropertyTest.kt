package com.dopashift.domain.presentation

// Feature: dynamic-ui-experience, Property 13: Nearest-compliant shade always meets AA

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property 13: Nearest-compliant shade always meets AA.
 *
 * **Validates: Requirements DUX-5.4**
 *
 * For any candidate accent color and any theme surface (with either the body or the
 * large-text/non-text threshold), [ContrastCalculator.nearestCompliant] returns a shade whose
 * contrast against the surface meets the AA threshold. In particular this must hold for the
 * candidates that *fail* AA against the surface — the case DUX-5.4 exists to handle — where the
 * calculator must nudge the color to a compliant shade rather than hand back the failing one.
 *
 * A second invariant guards the "already compliant" fast path: when the candidate already meets
 * AA against the surface it is returned unchanged.
 *
 * Kotest property testing with a minimum of 100 iterations. Colors are generated as full 32-bit
 * ARGB packed into a [Long] (`0xAARRGGBB`); the alpha channel is randomized as well so the test
 * exercises the calculator's alpha-preservation path, even though only the RGB channels affect
 * contrast.
 */
class NearestCompliantShadePropertyTest : StringSpec({

    val config = PropTestConfig(iterations = 200)

    // A full ARGB color as 0xAARRGGBB packed into a Long: each of the four bytes ranges 0..255,
    // so every combination of alpha and RGB channels is reachable.
    val argbColor: Arb<Long> = arbitrary { rs ->
        val a = Arb.int(0, 255).sample(rs).value.toLong()
        val r = Arb.int(0, 255).sample(rs).value.toLong()
        val g = Arb.int(0, 255).sample(rs).value.toLong()
        val b = Arb.int(0, 255).sample(rs).value.toLong()
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    val largeTextFlag: Arb<Boolean> = Arb.boolean()

    "Property 13 (DUX-5.4): nearestCompliant output always meets AA against the surface" {
        checkAll(config, argbColor, argbColor, largeTextFlag) { candidate, surface, largeText ->
            val shade = ContrastCalculator.nearestCompliant(candidate, surface, largeText)
            ContrastCalculator.meetsAA(shade, surface, largeText).shouldBeTrue()
        }
    }

    "Property 13 (DUX-5.4): candidates that fail AA are corrected to a compliant shade" {
        checkAll(config, argbColor, argbColor, largeTextFlag) { candidate, surface, largeText ->
            // Focus on the meaningful branch: candidates that do NOT already meet AA. The
            // returned shade must satisfy AA even though the input did not.
            if (!ContrastCalculator.meetsAA(candidate, surface, largeText)) {
                val shade = ContrastCalculator.nearestCompliant(candidate, surface, largeText)
                ContrastCalculator.meetsAA(shade, surface, largeText).shouldBeTrue()
            }
        }
    }

    "Property 13 (DUX-5.4): an already-compliant candidate is returned unchanged" {
        checkAll(config, argbColor, argbColor, largeTextFlag) { candidate, surface, largeText ->
            if (ContrastCalculator.meetsAA(candidate, surface, largeText)) {
                val shade = ContrastCalculator.nearestCompliant(candidate, surface, largeText)
                shade shouldBe candidate
            }
        }
    }
})
