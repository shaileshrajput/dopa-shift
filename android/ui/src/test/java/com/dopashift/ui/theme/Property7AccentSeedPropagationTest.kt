package com.dopashift.ui.theme

// Feature: dynamic-ui-experience, Property 7: Accent seed propagates to the color scheme

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Property test for **Property 7: Accent seed propagates to the color scheme**.
 *
 * // Feature: dynamic-ui-experience, Property 7: Accent seed propagates to the color scheme
 *
 * Validates: Requirements DUX-4.6
 *
 * The property: for any non-default accent [seed], the [ColorScheme] produced by
 * [kineticScheme] derives its accent-highlight roles — `primary`,
 * `primaryContainer`, and `surfaceTint` (the roles that drive buttons, toggles,
 * progress indicators, and navigation highlights) — from that seed, such that
 * changing the seed changes those roles accordingly.
 *
 * `kineticScheme` is `internal` to [com.dopashift.ui.theme], so this test lives
 * in the same package/module. It exercises pure `ColorScheme` construction on the
 * JVM: `androidx.compose.ui.graphics.Color(Int)` is inline value-class math with
 * no Android runtime dependency, so no Robolectric host is required.
 *
 * Each property runs a minimum of 100 generated cases.
 */
class Property7AccentSeedPropagationTest : StringSpec({

    val minIterations = PropTestConfig(iterations = 200)

    // Arbitrary opaque ARGB accent seeds, excluding the sentinel default
    // (DEFAULT_ACCENT_SEED == 0) which intentionally leaves the palette primary
    // untouched. Force full alpha so the seed maps to a concrete accent color.
    val accentSeeds: Arb<Long> = Arb.long(0L, 0xFFFFFFL)
        .map { rgb -> 0xFF000000L or rgb }
        .filter { it != DEFAULT_ACCENT_SEED }

    "primary, primaryContainer and surfaceTint derive from a non-default accent seed" {
        checkAll(minIterations, accentSeeds) { seed ->
            val expectedAccent = Color(seed.toInt())

            // Property holds across every mode / dark-override combination.
            for (mode in ThemeMode.entries) {
                for (darkOverride in listOf(true, false)) {
                    val scheme = kineticScheme(seed = seed, mode = mode, darkOverride = darkOverride)
                    scheme.primary shouldBe expectedAccent
                    scheme.primaryContainer shouldBe expectedAccent
                    scheme.surfaceTint shouldBe expectedAccent
                }
            }
        }
    }

    "changing the accent seed changes the derived accent-highlight roles" {
        checkAll(minIterations, accentSeeds, accentSeeds) { seedA, seedB ->
            // Only meaningful when the two seeds are actually different.
            if (seedA != seedB) {
                val schemeA = kineticScheme(seedA, ThemeMode.DARK, darkOverride = true)
                val schemeB = kineticScheme(seedB, ThemeMode.DARK, darkOverride = true)

                schemeA.primary shouldNotBe schemeB.primary
                schemeA.primaryContainer shouldNotBe schemeB.primaryContainer
                schemeA.surfaceTint shouldNotBe schemeB.surfaceTint
            }
        }
    }

    "the default sentinel seed leaves the palette primary untouched (accent not applied)" {
        // Guards the boundary the property excludes: DEFAULT_ACCENT_SEED must NOT
        // recolor the base palette, so seed propagation is distinguishable from it.
        for (mode in ThemeMode.entries) {
            for (darkOverride in listOf(true, false)) {
                val base = kineticScheme(DEFAULT_ACCENT_SEED, mode, darkOverride)
                val useDark = when (mode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> darkOverride
                }
                val expectedBase = if (useDark) DopaShiftDarkColorScheme else DopaShiftLightColorScheme
                base.primary shouldBe expectedBase.primary
                base.primaryContainer shouldBe expectedBase.primaryContainer
                base.surfaceTint shouldBe expectedBase.surfaceTint
            }
        }
    }
})
