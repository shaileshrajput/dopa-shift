package com.dopashift.ui.theme

// Feature: dynamic-ui-experience, Task 6.3
// Validates: Requirements DUX-4.2, DUX-4.3, DUX-4.4, DUX-4.5, DUX-4.7

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI/snapshot tests for the DopaShift Kinetic theme ([DopaShiftTheme]).
 *
 * Written as Compose UI tests running under [RobolectricTestRunner] so they
 * execute on the JVM without an emulator (`createComposeRule` works under
 * Robolectric, matching the pattern already used by the interception UI tests).
 *
 * Coverage:
 *  - Typography slot mapping: Hanken Grotesk -> display/headline/title,
 *    Inter -> body, JetBrains Mono -> label (DUX-4.2, DUX-4.3).
 *  - Shape slot mapping: `Shapes.small` = 8dp, `Shapes.medium` = 16dp, chip pill
 *    (DUX-4.4).
 *  - Dark is the default [ThemeMode] and both themes render legible (non-blank,
 *    distinct on/surface) color roles (DUX-4.5).
 *  - Live theme/accent change recolors `MaterialTheme.colorScheme.primary` in
 *    place — no restart — while a `rememberSaveable` value is preserved (DUX-4.7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DopaShiftThemeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    // --- Typography slot mapping (DUX-4.2, DUX-4.3) -------------------------

    @Test
    fun typography_mapsHankenToDisplayHeadlineTitle() {
        // Hanken Grotesk drives the display/headline/title expressive slots.
        assertEquals(
            "displayLarge must use Hanken Grotesk",
            HankenGroteskFamily,
            DopaShiftTypography.displayLarge.fontFamily,
        )
        assertEquals(
            "displayMedium must use Hanken Grotesk",
            HankenGroteskFamily,
            DopaShiftTypography.displayMedium.fontFamily,
        )
        assertEquals(
            "displaySmall must use Hanken Grotesk",
            HankenGroteskFamily,
            DopaShiftTypography.displaySmall.fontFamily,
        )
        assertEquals(
            "headlineLarge must use Hanken Grotesk",
            HankenGroteskFamily,
            DopaShiftTypography.headlineLarge.fontFamily,
        )
        assertEquals(
            "headlineMedium must use Hanken Grotesk",
            HankenGroteskFamily,
            DopaShiftTypography.headlineMedium.fontFamily,
        )
        assertEquals(
            "headlineSmall must use Hanken Grotesk",
            HankenGroteskFamily,
            DopaShiftTypography.headlineSmall.fontFamily,
        )
        assertEquals(
            "titleLarge must use Hanken Grotesk",
            HankenGroteskFamily,
            DopaShiftTypography.titleLarge.fontFamily,
        )
    }

    @Test
    fun typography_mapsInterToBody() {
        assertEquals(
            "bodyLarge must use Inter",
            InterFamily,
            DopaShiftTypography.bodyLarge.fontFamily,
        )
        assertEquals(
            "bodyMedium must use Inter",
            InterFamily,
            DopaShiftTypography.bodyMedium.fontFamily,
        )
        assertEquals(
            "bodySmall must use Inter",
            InterFamily,
            DopaShiftTypography.bodySmall.fontFamily,
        )
    }

    @Test
    fun typography_mapsJetBrainsMonoToLabel() {
        // AUI-8.4 decision: label roles use Inter; only metric/badge readouts use
        // JetBrains Mono. `labelLarge` is a text label (Inter), while `labelMedium`
        // and `labelSmall` carry the metric/badge readouts (JetBrains Mono).
        assertEquals(
            "labelLarge must use Inter",
            InterFamily,
            DopaShiftTypography.labelLarge.fontFamily,
        )
        assertEquals(
            "labelMedium must use JetBrains Mono",
            JetBrainsMonoFamily,
            DopaShiftTypography.labelMedium.fontFamily,
        )
        assertEquals(
            "labelSmall must use JetBrains Mono",
            JetBrainsMonoFamily,
            DopaShiftTypography.labelSmall.fontFamily,
        )
    }

    @Test
    fun typography_isTheActiveMaterialThemeTypography() {
        // The MaterialTheme composed by DopaShiftTheme exposes the token typography.
        lateinit var displayFamily: Any
        lateinit var bodyFamily: Any
        lateinit var labelFamily: Any
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                displayFamily = MaterialTheme.typography.displayLarge.fontFamily!!
                bodyFamily = MaterialTheme.typography.bodyLarge.fontFamily!!
                labelFamily = MaterialTheme.typography.labelLarge.fontFamily!!
                Text("themed")
            }
        }
        composeRule.onNodeWithText("themed").assertExists()
        assertEquals(HankenGroteskFamily, displayFamily)
        assertEquals(InterFamily, bodyFamily)
        // AUI-8.4: labelLarge is a text label and uses Inter (not JetBrains Mono).
        assertEquals("labelLarge must use Inter", InterFamily, labelFamily)
    }

    // --- Shape slot mapping (DUX-4.4) --------------------------------------

    @Test
    fun shapes_smallIs8dp_mediumIs16dp() {
        // Assert the M3 Shapes built by the theme resolve to the Kinetic radii.
        val shapes = dopaShiftShapes()
        assertCornerRadius("Shapes.small (buttons/inputs) must be 8dp", shapes.small, 8f)
        assertCornerRadius("Shapes.medium (cards/progress) must be 16dp", shapes.medium, 16f)
    }

    @Test
    fun shapes_activeMaterialThemeExposesKineticRadii() {
        lateinit var small: CornerBasedShape
        lateinit var medium: CornerBasedShape
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                small = MaterialTheme.shapes.small as CornerBasedShape
                medium = MaterialTheme.shapes.medium as CornerBasedShape
                Text("shaped")
            }
        }
        composeRule.onNodeWithText("shaped").assertExists()
        assertCornerRadius("MaterialTheme.shapes.small must be 8dp", small, 8f)
        assertCornerRadius("MaterialTheme.shapes.medium must be 16dp", medium, 16f)
    }

    @Test
    fun shapes_chipPillIsFullyRounded() {
        // The chip pill token is a 50%-rounded (fully pill) shape.
        val pill = ChipPillShape
        assertEquals(
            "chip pill must be a 50% rounded corner (full pill)",
            RoundedCornerShape(percent = 50),
            pill,
        )
        // And the underlying token radius is the design system's `rounded.full`.
        assertEquals(9999.dp, ShapeTokens().chipPill)
    }

    /** Resolves a [CornerBasedShape]'s corner size in dp against a fixed density. */
    private fun assertCornerRadius(message: String, shape: CornerBasedShape, expectedDp: Float) {
        val density = Density(density = 1f)
        // 100dp square gives px == dp at density 1; topStart corner reports px radius.
        val sizePx = with(density) { 100.dp.toPx() }
        val radiusPx = shape.topStart.toPx(
            androidx.compose.ui.geometry.Size(sizePx, sizePx),
            density,
        )
        val radiusDp = with(density) { radiusPx.toDp().value }
        assertEquals(message, expectedDp, radiusDp, 0.5f)
    }

    // --- Dark default + legibility in both themes (DUX-4.5) ----------------

    @Test
    fun themeMode_defaultsToDark() {
        // The DopaShiftTheme composable and ThemeSettings both default to DARK.
        assertEquals(ThemeMode.DARK, ThemeSettings().themeMode)

        var defaultSurface = Color.Unspecified
        var darkSurface = Color.Unspecified
        var lightSurface = Color.Unspecified
        // Capture the default(DARK), explicit DARK, and LIGHT surfaces inside a
        // SINGLE setContent (Robolectric allows only one setContent per test).
        composeRule.setContent {
            Column {
                // Default themeMode parameter => DARK theme. `accentSeed` selects
                // the ThemeMode overload while leaving themeMode at its default.
                DopaShiftTheme(accentSeed = DEFAULT_ACCENT_SEED) {
                    defaultSurface = MaterialTheme.colorScheme.surface
                    Text("default-theme")
                }
                DopaShiftTheme(themeMode = ThemeMode.DARK) {
                    darkSurface = MaterialTheme.colorScheme.surface
                    Text("dark-theme")
                }
                DopaShiftTheme(themeMode = ThemeMode.LIGHT) {
                    lightSurface = MaterialTheme.colorScheme.surface
                    Text("light-theme")
                }
            }
        }
        composeRule.onNodeWithText("default-theme").assertExists()
        composeRule.onNodeWithText("dark-theme").assertExists()
        composeRule.onNodeWithText("light-theme").assertExists()

        // The default-parameter surface matches the explicit DARK surface...
        assertEquals(
            "default theme surface must equal the DARK surface (dark is the default)",
            darkSurface,
            defaultSurface,
        )
        // ...and differs from the LIGHT surface (dark is the default, not light).
        assertNotEquals(
            "default theme surface must differ from LIGHT surface (dark is the default)",
            lightSurface,
            defaultSurface,
        )
    }

    @Test
    fun darkTheme_rendersLegibleColorRoles() {
        // Legibility floor: text-on-surface and text-on-primary are distinct from
        // their backgrounds (never invisible) in the dark theme.
        assertLegible(ThemeMode.DARK)
    }

    @Test
    fun lightTheme_rendersLegibleColorRoles() {
        // Legibility floor: text-on-surface and text-on-primary are distinct from
        // their backgrounds (never invisible) in the light theme.
        assertLegible(ThemeMode.LIGHT)
    }

    /**
     * Composes [mode] via a single [composeRule.setContent] (Robolectric allows
     * only one setContent per test) and asserts the on/background color roles are
     * legible: distinct from their backgrounds and opaque.
     */
    private fun assertLegible(mode: ThemeMode) {
        var surface = Color.Unspecified
        var onSurface = Color.Unspecified
        var primary = Color.Unspecified
        var onPrimary = Color.Unspecified
        composeRule.setContent {
            DopaShiftTheme(themeMode = mode) {
                with(MaterialTheme.colorScheme) {
                    surface = this.surface
                    onSurface = this.onSurface
                    primary = this.primary
                    onPrimary = this.onPrimary
                }
                Text("legible-$mode")
            }
        }
        composeRule.onNodeWithText("legible-$mode").assertExists()

        assertNotEquals("$mode: onSurface must differ from surface", surface, onSurface)
        assertNotEquals("$mode: onPrimary must differ from primary", primary, onPrimary)
        assertTrue("$mode: onSurface must be opaque", onSurface.alpha > 0.9f)
        assertTrue("$mode: surface must be opaque", surface.alpha > 0.9f)
    }

    // --- Live theme/accent switch without restart or state loss (DUX-4.7) ---

    @Test
    fun liveThemeSwitch_recolorsAndPreservesSaveableState() {
        val darkPrimaryHolder = intArrayOf(0)
        val lightPrimaryHolder = intArrayOf(0)
        val counterHolder = intArrayOf(-1)

        composeRule.setContent {
            var mode by remember { mutableStateOf(ThemeMode.DARK) }
            // rememberSaveable state that must survive the theme change (no restart).
            var counter by rememberSaveable { mutableStateOf(0) }

            DopaShiftTheme(themeMode = mode) {
                val primary = MaterialTheme.colorScheme.primary.toArgb()
                when (mode) {
                    ThemeMode.DARK -> darkPrimaryHolder[0] = primary
                    ThemeMode.LIGHT -> lightPrimaryHolder[0] = primary
                    else -> {}
                }
                counterHolder[0] = counter
                Column {
                    Text("count-$counter")
                    Text(
                        text = "increment",
                        modifier = androidx.compose.ui.Modifier.clickable { counter++ },
                    )
                    Text(
                        text = "to-light",
                        modifier = androidx.compose.ui.Modifier.clickable {
                            mode = ThemeMode.LIGHT
                        },
                    )
                }
            }
        }

        // Mutate the saveable state before switching theme.
        composeRule.onNodeWithText("increment").performClick()
        composeRule.onNodeWithText("increment").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("count-2").assertExists()

        val darkPrimary = darkPrimaryHolder[0]

        // Flip the theme mode live — same composition, no restart.
        composeRule.onNodeWithText("to-light").performClick()
        composeRule.waitForIdle()

        // The primary color recolored...
        val lightPrimary = lightPrimaryHolder[0]
        assertNotEquals(
            "primary must recolor when theme mode changes live",
            darkPrimary,
            lightPrimary,
        )
        // ...and the rememberSaveable counter was preserved across the recolor.
        composeRule.onNodeWithText("count-2").assertExists()
        assertEquals("saveable state must survive live theme switch", 2, counterHolder[0])
    }

    @Test
    fun liveAccentChange_recolorsPrimaryFromSeed() {
        val defaultPrimaryHolder = intArrayOf(0)
        val seededPrimaryHolder = intArrayOf(0)
        val seed = 0xFFFF0000L // opaque red accent

        composeRule.setContent {
            var accent by remember { mutableLongStateOf(DEFAULT_ACCENT_SEED) }
            var kept by rememberSaveable { mutableStateOf("kept") }

            DopaShiftTheme(themeMode = ThemeMode.DARK, accentSeed = accent) {
                val primary = MaterialTheme.colorScheme.primary.toArgb()
                if (accent == DEFAULT_ACCENT_SEED) {
                    defaultPrimaryHolder[0] = primary
                } else {
                    seededPrimaryHolder[0] = primary
                }
                Column {
                    Text(kept)
                    Text(
                        text = "apply-accent",
                        modifier = androidx.compose.ui.Modifier.clickable { accent = seed },
                    )
                }
            }
        }

        composeRule.onNodeWithText("kept").assertExists()
        val defaultPrimary = defaultPrimaryHolder[0]

        composeRule.onNodeWithText("apply-accent").performClick()
        composeRule.waitForIdle()

        // Accent seed propagated into the primary role, live, without restart.
        assertEquals(
            "seeded primary must equal the accent seed",
            seed.toInt(),
            seededPrimaryHolder[0],
        )
        assertNotEquals(
            "accent change must recolor primary away from the default",
            defaultPrimary,
            seededPrimaryHolder[0],
        )
        // Saveable state preserved through the live accent recolor.
        composeRule.onNodeWithText("kept").assertExists()
    }
}
