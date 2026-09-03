package com.dopashift.domain.presentation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll

/**
 * Property-based test for [ContrastCalculator] WCAG AA contrast invariants.
 *
 * Feature: dynamic-ui-experience, Property 12: Token color pairs meet WCAG AA in both themes and every accent
 * Validates: Requirements DUX-5.3
 *
 * The concrete token foreground/background pairs live in the `ui` module, which the pure
 * `domain` module must not depend on. Per the task, this test exercises the underlying
 * [ContrastCalculator] invariants directly against generated foreground/background ARGB
 * [Long] color pairs (the same math the `ui` layer relies on to guarantee every accent seed
 * and theme surface pairing meets AA):
 *
 *  - [ContrastCalculator.ratio] always lands in `[1.0, 21.0]`,
 *  - the ratio is symmetric in its arguments,
 *  - [ContrastCalculator.meetsAA] correctly reflects the 4.5:1 (body) / 3:1 (large & non-text)
 *    thresholds derived from that ratio.
 *
 * Uses Kotest property testing (min 100 iterations) per the project tech stack, matching the
 * sibling `domain` presentation property tests.
 */
class ContrastAaThresholdPropertyTest : StringSpec({

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    // Representative theme surfaces spanning the dark-default and light themes (DUX-4.5),
    // so the accent-vs-surface pairings cover both theme modes.
    val themeSurfaces = Arb.of(
        0xFF000000L, // pure black
        0xFF101014L, // dark theme surface
        0xFF1C1B1F, // dark theme surface-container
        0xFFFFFFFFL, // pure white
        0xFFF7F7FA, // light theme surface
        0xFFECEAF0 // light theme surface-container
    )

    // Full-range opaque ARGB colors act as generated accent seeds / foregrounds; alpha is fixed
    // opaque because contrast is only defined for composited (opaque) colors.
    val opaqueColors: Arb<Long> = arbitrary { rs ->
        val r = Arb.int(0, 255).sample(rs).value.toLong()
        val g = Arb.int(0, 255).sample(rs).value.toLong()
        val b = Arb.int(0, 255).sample(rs).value.toLong()
        0xFF000000L or (r shl 16) or (g shl 8) or b
    }

    "Property 12 (DUX-5.3): ratio is always within the WCAG bounds [1.0, 21.0]" {
        checkAll(config, opaqueColors, opaqueColors) { fg, bg ->
            val ratio = ContrastCalculator.ratio(fg, bg)
            ratio shouldBeGreaterThanOrEqual 1.0
            ratio shouldBeLessThanOrEqual 21.0
        }
    }

    "Property 12 (DUX-5.3): ratio is symmetric in foreground and background" {
        checkAll(config, opaqueColors, opaqueColors) { fg, bg ->
            ContrastCalculator.ratio(fg, bg) shouldBe ContrastCalculator.ratio(bg, fg)
        }
    }

    "Property 12 (DUX-5.3): a color against itself has the minimum ratio of 1.0" {
        checkAll(config, opaqueColors) { color ->
            ContrastCalculator.ratio(color, color) shouldBe 1.0
        }
    }

    "Property 12 (DUX-5.3): black on white meets the maximum ratio of 21.0" {
        ContrastCalculator.ratio(0xFF000000L, 0xFFFFFFFFL) shouldBe 21.0
    }

    // Tolerance mirrors ContrastCalculator's internal threshold epsilon so a pair sitting
    // exactly on a boundary is classified consistently with the implementation.
    val thresholdEpsilon = 1e-9

    "Property 12 (DUX-5.3): meetsAA(body) reflects the 4.5:1 threshold for any fg/bg pair" {
        checkAll(config, opaqueColors, themeSurfaces) { fg, surface ->
            val expected =
                ContrastCalculator.ratio(fg, surface) >= ContrastCalculator.AA_BODY - thresholdEpsilon
            ContrastCalculator.meetsAA(fg, surface, largeText = false) shouldBe expected
        }
    }

    "Property 12 (DUX-5.3): meetsAA(large) reflects the 3:1 threshold for any fg/bg pair" {
        checkAll(config, opaqueColors, themeSurfaces) { fg, surface ->
            val expected =
                ContrastCalculator.ratio(fg, surface) >= ContrastCalculator.AA_LARGE - thresholdEpsilon
            ContrastCalculator.meetsAA(fg, surface, largeText = true) shouldBe expected
        }
    }

    "Property 12 (DUX-5.3): the large-text threshold is never stricter than the body threshold" {
        // Any pair that meets the body (4.5:1) threshold must also meet the large (3:1) one,
        // since 3:1 <= 4.5:1. This guards the threshold ordering the token pairs rely on.
        checkAll(config, opaqueColors, themeSurfaces) { fg, surface ->
            if (ContrastCalculator.meetsAA(fg, surface, largeText = false)) {
                ContrastCalculator.meetsAA(fg, surface, largeText = true) shouldBe true
            }
        }
    }
})
