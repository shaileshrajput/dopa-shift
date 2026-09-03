package com.dopashift.ui.theme

// Feature: android-ui-upgrade, Task 1.4 — design-conformance tests
// Validates: Requirements AUI-8.1, AUI-8.2, AUI-8.4

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Design-conformance tests for the DopaShift Kinetic design-system layer
 * (spec `android-ui-upgrade`, task 1.4).
 *
 * These are pure JVM assertions over the token / theme / shape values established in tasks
 * 1.1–1.3 — no emulator or Compose host is needed. They pin the two design invariants that this
 * task owns:
 *
 *  - **Cards** render at a 16dp corner radius, with a 1dp `surfaceBorder` stroke and zero
 *    elevation (AUI-8.2). The card surface is the `DopaShiftShapes.medium` slot; the stroke color
 *    is `DopaShiftTokens.Colors.surfaceBorder`.
 *  - **Numeric / metric readouts** use the JetBrains Mono family (AUI-8.4), both at the token-role
 *    level (`metricDisplay`, `metricCounter`, `labelMono`) and at the M3 slots those roles map to
 *    (`labelMedium`, `labelSmall`), while text `label` roles (`labelLarge`) stay on Inter.
 *
 * Card-stroke-and-elevation note (AUI-8.2): the `KineticCard` composable that actually draws the
 * 1dp stroke and zero-elevation surface is implemented in task 2.1 (in parallel). Until it exists,
 * this test asserts the *token/theme inputs* a conforming `KineticCard` must consume — the 16dp
 * `DopaShiftShapes.medium` radius and the `surfaceBorder` stroke token. Once `KineticCard` lands,
 * the render-level stroke-width (1dp) and elevation (0dp) assertions will be completed against the
 * composable itself in that task's UI test.
 */
class DesignConformanceTest {

    private val density = Density(density = 1f)

    // =============================================================================================
    // AUI-8.2 — Cards: 16dp radius, 1dp surfaceBorder stroke, zero elevation
    // =============================================================================================

    /**
     * AUI-8.2 — the card surface slot (`DopaShiftShapes.medium`, used by `KineticCard`) resolves to
     * a 16dp corner radius.
     */
    @Test
    fun `AUI-8_2 card shape renders at 16dp corner radius`() {
        assertCornerRadius(
            message = "KineticCard surface (Shapes.medium) must render at 16dp radius (AUI-8.2)",
            shape = DopaShiftShapes.medium as CornerBasedShape,
            expectedDp = 16f,
        )
    }

    /**
     * AUI-8.2 — the card radius token is sourced from the design tokens (`Radii.card`), not a
     * screen-local literal, and equals 16dp (AUI-8.1 / AUI-8.2).
     */
    @Test
    fun `AUI-8_2 card radius token equals 16dp`() {
        assertEquals(
            "Card radius token (DopaShiftTokens.Radii.card) must be 16dp (AUI-8.2)",
            16.dp,
            DopaShiftTokens.Radii.card,
        )
    }

    /**
     * AUI-8.2 — the 1dp card border uses the `surfaceBorder` stroke token, which is a defined,
     * non-transparent translucent-white hairline (the stroke a conforming `KineticCard` draws at
     * 1dp width). The render-level 1dp width / 0dp elevation assertion is completed against the
     * `KineticCard` composable in task 2.1.
     */
    @Test
    fun `AUI-8_2 card stroke uses surfaceBorder token`() {
        val stroke = DopaShiftTokens.Colors.surfaceBorder
        // The token is defined (not the theme's "unspecified" sentinel) so a card has a real stroke.
        assertNotEquals(
            "Card stroke token (surfaceBorder) must be a defined color (AUI-8.2)",
            Color.Unspecified,
            stroke,
        )
        // A hairline outline: translucent (alpha < 1f) rather than an opaque fill, and visible
        // (alpha > 0f) so the 1dp boundary actually renders.
        assertEquals(
            "surfaceBorder must be a translucent white hairline (AUI-8.2)",
            Color.White.red,
            stroke.red,
            0.001f,
        )
        assertEquals("surfaceBorder green channel must be white", Color.White.green, stroke.green, 0.001f)
        assertEquals("surfaceBorder blue channel must be white", Color.White.blue, stroke.blue, 0.001f)
        org.junit.Assert.assertTrue(
            "surfaceBorder must be a translucent hairline (0 < alpha < 1), got alpha=${stroke.alpha}",
            stroke.alpha in 0.001f..0.999f,
        )
    }

    // =============================================================================================
    // AUI-8.4 — Numeric / metric readouts use JetBrains Mono
    // =============================================================================================

    /**
     * AUI-8.4 — every metric/badge readout text role is authored in the JetBrains Mono family so
     * numeric values do not jitter as they tick.
     */
    @Test
    fun `AUI-8_4 metric readout roles use JetBrains Mono`() {
        assertMono("metricDisplay (ring/timer centers)", DopaShiftTokens.TextRoles.metricDisplay.fontFamily)
        assertMono("metricCounter (cadence counters)", DopaShiftTokens.TextRoles.metricCounter.fontFamily)
        assertMono("labelMono (badges/metadata)", DopaShiftTokens.TextRoles.labelMono.fontFamily)
    }

    /**
     * AUI-8.4 — the M3 slots the metric readout roles map to (`labelMedium` ← `metricCounter`,
     * `labelSmall` ← `labelMono`) resolve to JetBrains Mono, so composables reading numeric UI by
     * M3 slot render in the monospace family.
     */
    @Test
    fun `AUI-8_4 metric label slots resolve to JetBrains Mono`() {
        assertMono("labelMedium slot (metric counter)", DopaShiftTypography.labelMedium.fontFamily)
        assertMono("labelSmall slot (mono badge)", DopaShiftTypography.labelSmall.fontFamily)
    }

    /**
     * AUI-8.4 — the text `labelLarge` role/slot is NOT a numeric readout and therefore stays on
     * Inter, per the AUI-8.4 cross-spec decision (label roles use Inter; only metric/badge readouts
     * use JetBrains Mono).
     */
    @Test
    fun `AUI-8_4 text label role stays on Inter`() {
        assertEquals(
            "labelLarge (text label) must use Inter, not JetBrains Mono (AUI-8.4)",
            InterFamily,
            DopaShiftTokens.TextRoles.labelLarge.fontFamily,
        )
        assertEquals(
            "labelLarge slot must use Inter (AUI-8.4)",
            InterFamily,
            DopaShiftTypography.labelLarge.fontFamily,
        )
    }

    /**
     * AUI-8.1 — the mono family exposed as a named token (`FontFamilies.mono`) is the JetBrains Mono
     * family the metric roles are built over, confirming numeric readouts source their family from
     * the tokens rather than a screen-local `FontFamily(...)`.
     */
    @Test
    fun `AUI-8_1 mono family token is JetBrains Mono`() {
        assertEquals(
            "FontFamilies.mono must be the JetBrains Mono family (AUI-8.1/8.4)",
            JetBrainsMonoFamily,
            DopaShiftTokens.FontFamilies.mono,
        )
    }

    // =============================================================================================
    // Helpers
    // =============================================================================================

    private fun assertMono(role: String, family: FontFamily?) {
        assertEquals("$role must use JetBrains Mono (AUI-8.4)", JetBrainsMonoFamily, family)
    }

    /** Resolves a [CornerBasedShape]'s corner size in dp against a fixed density (density == 1). */
    private fun assertCornerRadius(message: String, shape: CornerBasedShape, expectedDp: Float) {
        val sizePx = with(density) { 100.dp.toPx() }
        val radiusPx = shape.topStart.toPx(Size(sizePx, sizePx), density)
        val radiusDp = with(density) { radiusPx.toDp().value }
        assertEquals(message, expectedDp, radiusDp, 0.5f)
    }
}
