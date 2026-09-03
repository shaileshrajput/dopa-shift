package com.dopashift.ui.theme

// Feature: android-ui-upgrade, Property 7: Accent Selection Propagates App-Wide

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Property test for **Property 7: Accent Selection Propagates App-Wide**.
 *
 * // Feature: android-ui-upgrade, Property 7: Accent Selection Propagates App-Wide
 *
 * Validates: Requirements AUI-7.4 (and the Settings-driven propagation exercised by AUI-9.8).
 *
 * This is the `android-ui-upgrade` spec's Property 7 — distinct from the
 * `dynamic-ui-experience` Property 7 in [Property7AccentSeedPropagationTest], which
 * exercises the [kineticScheme] accent-seed `Long` path (`primary`/`primaryContainer`/
 * `surfaceTint`). Here we validate the Kinetic reward-accent path: a Settings accent-swatch
 * selection is modeled as an [AccentSeed], and [ColorScheme.withAccent] re-tints the reward
 * roles so every subsequently composed screen reads the same accent.
 *
 * The property, stated over the pure derivation helpers:
 *  - **Application:** for any non-default accent color, [ColorScheme.withAccent] sets the reward
 *    roles (`secondary`, `tertiary`, `surfaceTint`) to that accent — the roles that drive rings,
 *    checkboxes, active nav pills, and accent card strokes across the seven screens.
 *  - **Propagation / idempotence:** applying the SAME [AccentSeed] always yields the same reward
 *    accent, and re-applying an already-tinted accent does not drift it. This models
 *    "propagates app-wide": two independently composed screens, given the same selection,
 *    resolve to the same accent.
 *  - **Distinctness:** different accent selections yield different reward accents, so a Settings
 *    change is observable.
 *
 * [ColorScheme.withAccent] and [AccentSeed] are `internal`/token-level to
 * [com.dopashift.ui.theme], so this test lives in the same package/module. It is pure JVM
 * `ColorScheme` math — `androidx.compose.ui.graphics.Color` is an inline value class with no
 * Android runtime dependency, so no emulator or Robolectric host is required.
 *
 * Each property runs a minimum of 200 generated cases.
 */
class Property7AccentPropagationAuiTest : StringSpec({

    val config = PropTestConfig(iterations = 200)

    val base = DopaShiftKineticDarkColorScheme
    val defaultAccentColor = DopaShiftTokens.Colors.momentumTeal
    val onAccent = DopaShiftTokens.Colors.onMomentumTeal

    // Arbitrary opaque ARGB accent colors selected from the Settings picker, excluding the
    // system-default Momentum Teal (which intentionally leaves the reward roles untouched) so
    // every generated accent actually re-tints the scheme.
    val accentColors: Arb<Color> = Arb.int()
        .map { rgb -> Color(0xFF000000.toInt() or (rgb and 0x00FFFFFF)) }
        .filter { it != defaultAccentColor }

    "Feature: android-ui-upgrade, Property 7 - selected accent re-tints the reward roles (AUI-7.4)" {
        checkAll(config, accentColors) { accentColor ->
            val scheme = base.withAccent(AccentSeed(accentColor))

            // The reward-accent roles all derive from the selected accent.
            scheme.secondary shouldBe accentColor
            scheme.tertiary shouldBe accentColor
            scheme.surfaceTint shouldBe accentColor
            scheme.onSecondary shouldBe onAccent
            scheme.onTertiary shouldBe onAccent

            // The structural primary role stays stable so primary actions do not shift (AUI-7.4).
            scheme.primary shouldBe base.primary
        }
    }

    "Feature: android-ui-upgrade, Property 7 - accent propagates deterministically to every screen (AUI-7.4, AUI-9.8)" {
        checkAll(config, accentColors) { accentColor ->
            // Two independently composed screens given the SAME selection resolve identically.
            val screenA = base.withAccent(AccentSeed(accentColor))
            val screenB = base.withAccent(AccentSeed(accentColor))

            screenA.secondary shouldBe screenB.secondary
            screenA.tertiary shouldBe screenB.tertiary
            screenA.surfaceTint shouldBe screenB.surfaceTint
            screenA.secondaryContainer shouldBe screenB.secondaryContainer
            screenA.tertiaryContainer shouldBe screenB.tertiaryContainer
        }
    }

    "Feature: android-ui-upgrade, Property 7 - re-applying the same accent is idempotent (AUI-7.4)" {
        checkAll(config, accentColors) { accentColor ->
            val once = base.withAccent(AccentSeed(accentColor))
            // Re-applying the same selection to an already-tinted scheme must not drift the accent.
            val twice = once.withAccent(AccentSeed(accentColor))

            twice.secondary shouldBe once.secondary
            twice.tertiary shouldBe once.tertiary
            twice.surfaceTint shouldBe once.surfaceTint
        }
    }

    "Feature: android-ui-upgrade, Property 7 - distinct selections yield distinct reward accents (AUI-7.4)" {
        checkAll(config, accentColors, accentColors) { accentA, accentB ->
            if (accentA != accentB) {
                val schemeA = base.withAccent(AccentSeed(accentA))
                val schemeB = base.withAccent(AccentSeed(accentB))

                schemeA.secondary shouldNotBe schemeB.secondary
                schemeA.tertiary shouldNotBe schemeB.tertiary
                schemeA.surfaceTint shouldNotBe schemeB.surfaceTint
            }
        }
    }

    "Feature: android-ui-upgrade, Property 7 - the default accent leaves the reward roles untouched (AUI-7.4)" {
        // Boundary the property excludes: the system-default selection must return the base
        // scheme unchanged, so a real selection is distinguishable from "no selection".
        val unchanged = base.withAccent(AccentSeed(defaultAccentColor))
        unchanged.secondary shouldBe base.secondary
        unchanged.tertiary shouldBe base.tertiary
        unchanged.surfaceTint shouldBe base.surfaceTint

        // AccentSeed.Default is the Momentum Teal system default and is likewise a no-op.
        val viaDefault = base.withAccent(AccentSeed.Default)
        viaDefault.secondary shouldBe base.secondary
        viaDefault.surfaceTint shouldBe base.surfaceTint
    }
})
