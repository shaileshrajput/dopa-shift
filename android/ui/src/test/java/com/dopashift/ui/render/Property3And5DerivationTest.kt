package com.dopashift.ui.render

// Feature: android-ui-upgrade, Property 3: Roadmap Progress Equals completed/30
// Feature: android-ui-upgrade, Property 5: Efficiency Ring Renders an Integer Percentage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property tests for the two pure render-derivation helpers extracted in task 2.3:
 * [roadmapFraction] and [formatPercent] (spec `android-ui-upgrade`).
 *
 * // Feature: android-ui-upgrade, Property 3: Roadmap Progress Equals completed/30
 * // Feature: android-ui-upgrade, Property 5: Efficiency Ring Renders an Integer Percentage
 *
 * Validates: Requirements 5.5, 1.1, 8.4
 * Traceability tags: AUI-9.6, AUI-9.2, AUI-5.5, AUI-1.1, AUI-8.4
 *
 * Both helpers are pure JVM functions (no Composable, no Android runtime), so these
 * properties run under plain Kotest without an emulator or Robolectric host. Each
 * property runs a minimum of 100 generated cases and deliberately spans the input
 * space below, inside, and above the valid clamp range.
 */
class Property3And5DerivationTest : StringSpec({

    val config = PropTestConfig(iterations = 200)

    // Integer completedDays spanning below 0, the valid 0..30 window, and above 30.
    // Bounds are chosen so the generator reliably visits each region across 200 runs.
    val completedDays: Arb<Int> = Arb.int(min = -50, max = 90)

    // Integer efficiency scores spanning below 0, the valid 0..100 window, and above 100.
    val scores: Arb<Int> = Arb.int(min = -200, max = 300)

    // ---- Property 3: Roadmap Progress Equals completed/30 --------------------------------

    "Feature: android-ui-upgrade, Property 3 (AUI-5.5): roadmapFraction equals coerced(completed,0..30)/30f" {
        checkAll(config, completedDays) { completed ->
            val expected = completed.coerceIn(0, 30) / 30f
            roadmapFraction(completed) shouldBe expected
        }
    }

    "Feature: android-ui-upgrade, Property 3 (AUI-5.5): roadmapFraction is always within 0f..1f" {
        checkAll(config, completedDays) { completed ->
            val fraction = roadmapFraction(completed)
            fraction shouldBeGreaterThanOrEqual 0f
            fraction shouldBeLessThanOrEqual 1f
        }
    }

    // ---- Property 5: Efficiency Ring Renders an Integer Percentage -----------------------

    "Feature: android-ui-upgrade, Property 5 (AUI-1.1, AUI-8.4): formatPercent equals coerced(score,0..100)+\"%\"" {
        checkAll(config, scores) { score ->
            val expected = score.coerceIn(0, 100).toString() + "%"
            formatPercent(score) shouldBe expected
        }
    }

    "Feature: android-ui-upgrade, Property 5 (AUI-1.1, AUI-8.4): formatPercent is an integer percent with no fractional digits and a trailing %" {
        checkAll(config, scores) { score ->
            val rendered = formatPercent(score)

            // Ends with a single percent glyph.
            rendered.last() shouldBe '%'

            // The numeric portion is a base-10 integer with no fractional separator.
            val numeric = rendered.dropLast(1)
            numeric.contains('.') shouldBe false
            numeric.contains(',') shouldBe false
            (numeric.toIntOrNull() != null) shouldBe true

            // The rendered integer equals the clamped score.
            numeric.toInt() shouldBe score.coerceIn(0, 100)
        }
    }
})
