package com.dopashift.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DopaShift Kinetic — single source of truth for the Android UI upgrade design tokens
 * (spec `android-ui-upgrade`, requirements AUI-8.1 / AUI-8.2).
 *
 * Every color, text role, corner radius, spacing step, and touch-target value referenced by the
 * seven upgraded screens and their Kinetic components is defined here and *only* here — screens
 * never inline raw literals (AUI-8.1). All values are transcribed from the authoritative token
 * file `.kiro/specs/android-ui-upgrade/design_requirement_android.md`:
 *
 *  - **Colors** come from the "Colors", "Elevation & Depth", and "Components" sections (the deep
 *    oceanic canvas, the Productive Blue / Momentum Teal / Clarity Cyan accents, the semantic
 *    warning/danger hues, the scrim, and the tonal text tiers).
 *  - **Text roles** come from the `typography` front-matter block, mapping the three font families
 *    (Hanken Grotesk for display/headline/title, Inter for body/label, JetBrains Mono for the
 *    metric/badge readouts) onto the exact size / weight / line-height / letter-spacing tuples.
 *  - **Corner radii** come from the `rounded` block and the "Shapes" section (16dp cards, 12–14dp
 *    controls, full pills).
 *  - **Spacing steps** come from the `spacing` block (the 4dp micro-step through the 48dp step,
 *    plus the mobile/tablet margins and grid gutter).
 *  - **Touch-target** comes from `touch-target-min` (`3rem` = 48dp) and the "Layout & Spacing"
 *    ergonomics rule, alongside the derived list-row and primary-button minimum heights called out
 *    in the "Components" section.
 *
 * This object holds the raw primitives; [DopaShiftTheme] wires the color/type/shape subsets into
 * the Material 3 `ColorScheme` / `Typography` / `Shapes`. Because this is a `*Tokens` file it is
 * the only place raw `Color(0x...)` / `dp` / `sp` literals are permitted (the `verifyDesignTokens`
 * governance gate exempts token-definition files).
 */
object DopaShiftTokens {

    // ---------------------------------------------------------------------------------------------
    // Colors
    // ---------------------------------------------------------------------------------------------

    /** Colors from the "Colors", "Elevation & Depth", and "Components" sections of the token file. */
    object Colors {
        // --- Surfaces (tonal layering, no heavy shadows) ---

        /** Base canvas / background — Surface Neutral Canvas `#0B0F17`. */
        val background: Color = Color(0xFF0B0F17)

        /** Level 1 resting cards & list buckets — `surface-container-low` `#131B28`. */
        val surface: Color = Color(0xFF131B28)

        /** Level 2 selected cards, modals, menus, segmented pickers — `surface-container-high` `#1A2436`. */
        val surfaceElevated: Color = Color(0xFF1A2436)

        /** Hairline boundary stroke on every container — `rgba(255,255,255,0.08)`. */
        val surfaceBorder: Color = Color(0x14FFFFFF)

        // --- Accents ---

        /** Productive Blue `#2563EB` — primary actions, key buttons, navigation nodes. */
        val primaryBlue: Color = Color(0xFF2563EB)

        /** Momentum Teal `#14B8A6` — completion rings, streaks, confirmed checkpoints. */
        val momentumTeal: Color = Color(0xFF14B8A6)

        /** Momentum Teal completion underglow — `rgba(20,184,166,0.15)`. */
        val momentumTealContainer: Color = Color(0x2614B8A6)

        /** Productive Blue focus underglow — `rgba(37,99,235,0.20)`. */
        val primaryBlueGlow: Color = Color(0x332563EB)

        /** Clarity Cyan `#38BDF8` — temporal progression, live intervals, active timers. */
        val clarityCyan: Color = Color(0xFF38BDF8)

        // --- Semantic ---

        /** Warning Amber `#F59E0B` — streak chip, cautionary banners. */
        val warningAmber: Color = Color(0xFFF59E0B)

        /** Danger Coral `#EF4444` — destructive controls, offline/partial banner. */
        val dangerCoral: Color = Color(0xFFEF4444)

        // --- Overlay ---

        /** Intercept overlay scrim — 90% opaque canvas over the blocked app. */
        val scrimOverlay: Color = Color(0xE60B0F17)

        // --- Text tiers ---

        /** Primary text on dark surfaces — Slate-50 `#F8FAFC`. */
        val textPrimary: Color = Color(0xFFF8FAFC)

        /** Secondary / caption text — Slate-400 `#94A3B8`. */
        val textSecondary: Color = Color(0xFF94A3B8)

        /** Completed / struck-through text — Slate-600 `#475569`. */
        val textCompleted: Color = Color(0xFF475569)

        /** No-data chart segment fill — Slate-800 `#1E293B`. */
        val chartNoData: Color = Color(0xFF1E293B)

        // --- On-accent foregrounds (for text/icons drawn on the accent fills) ---

        /** Foreground on Productive Blue buttons — `#FFFFFF`. */
        val onPrimaryBlue: Color = Color(0xFFFFFFFF)

        /** Checkmark drawn inside a checked Momentum Teal box — canvas `#0B0F17`. */
        val onMomentumTeal: Color = Color(0xFF0B0F17)

        /** Secondary / surface button text — `#E2E8F0`. */
        val onSurfaceButton: Color = Color(0xFFE2E8F0)
    }

    // ---------------------------------------------------------------------------------------------
    // Text roles (typography front-matter block)
    // ---------------------------------------------------------------------------------------------

    /**
     * The named text roles from the `typography` block, expressed as [TextStyle]s over the three
     * bundled families ([HankenGroteskFamily], [InterFamily], [JetBrainsMonoFamily] defined in
     * `Type.kt`). Numeric readouts (metric/counter/mono-label) exclusively use JetBrains Mono to
     * eliminate layout jitter as values tick (AUI-8.4).
     */
    object TextRoles {
        /** `display-lg` — Hanken Grotesk 40sp / 48 / 700 / -0.02em. */
        val displayLarge: TextStyle = TextStyle(
            fontFamily = HankenGroteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 48.sp,
            letterSpacing = (-0.8).sp, // -0.02em of 40sp
        )

        /** `display-lg-mobile` — Hanken Grotesk 32sp / 40 / 700 / -0.02em. */
        val displayLargeMobile: TextStyle = TextStyle(
            fontFamily = HankenGroteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.64).sp, // -0.02em of 32sp
        )

        /** `headline-lg` — Hanken Grotesk 28sp / 36 / 600 / -0.01em. */
        val headlineLarge: TextStyle = TextStyle(
            fontFamily = HankenGroteskFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.28).sp, // -0.01em of 28sp
        )

        /** `headline-md` — Hanken Grotesk 22sp / 28 / 600. */
        val headlineMedium: TextStyle = TextStyle(
            fontFamily = HankenGroteskFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        )

        /** `title-md` — Hanken Grotesk 16sp / 24 / 600. */
        val titleMedium: TextStyle = TextStyle(
            fontFamily = HankenGroteskFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )

        /** `body-lg` — Inter 16sp / 24 / 400. */
        val bodyLarge: TextStyle = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )

        /** `body-md` — Inter 14sp / 20 / 400. */
        val bodyMedium: TextStyle = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        /** `body-sm` — Inter 12sp / 16 / 400. */
        val bodySmall: TextStyle = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

        /** `label-lg` — Inter 14sp / 20 / 500. */
        val labelLarge: TextStyle = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        /** `metric-display` — JetBrains Mono 32sp / 40 / 600 / -0.04em (ring/timer centers). */
        val metricDisplay: TextStyle = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-1.28).sp, // -0.04em of 32sp
        )

        /** `metric-counter` — JetBrains Mono 18sp / 24 / 500 / -0.02em (cadence counters). */
        val metricCounter: TextStyle = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.36).sp, // -0.02em of 18sp
        )

        /** `label-mono` — JetBrains Mono 11sp / 16 / 400 / 0.04em (badges, log metadata). */
        val labelMono: TextStyle = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.44.sp, // 0.04em of 11sp
        )
    }

    /** The three bundled families, re-exported by name for callers that only need the family. */
    object FontFamilies {
        /** Hanken Grotesk — display / headline / title roles. */
        val display: FontFamily = HankenGroteskFamily

        /** Inter — body / label roles. */
        val body: FontFamily = InterFamily

        /** JetBrains Mono — metric / counter / badge readouts. */
        val mono: FontFamily = JetBrainsMonoFamily
    }

    // ---------------------------------------------------------------------------------------------
    // Corner radii (`rounded` block + "Shapes" section)
    // ---------------------------------------------------------------------------------------------

    /** Corner radii from the `rounded` token block and the "Shapes" section. */
    object Radii {
        /** `rounded.sm` — 0.25rem / 4dp. */
        val small: Dp = 4.dp

        /** `rounded.DEFAULT` — 0.5rem / 8dp. */
        val default: Dp = 8.dp

        /** `rounded.md` — 0.75rem / 12dp: interactive controls (lower bound). */
        val control: Dp = 12.dp

        /** Button radius — 14dp (12–16dp control range, "Components → Buttons"). */
        val button: Dp = 14.dp

        /** `rounded.lg` — 1rem / 16dp: cards & structural surfaces. */
        val card: Dp = 16.dp

        /** `rounded.xl` — 1.5rem / 24dp: large containers. */
        val large: Dp = 24.dp

        /** `rounded.full` — 9999px: status chips, category pills, milestone badges. */
        val pill: Dp = 9999.dp
    }

    // ---------------------------------------------------------------------------------------------
    // Spacing steps (`spacing` block, 8dp baseline with a 4dp micro-step)
    // ---------------------------------------------------------------------------------------------

    /** Spacing steps from the `spacing` token block. */
    object Spacing {
        /** `spacing-4` — 0.25rem / 4dp micro-step. */
        val space4: Dp = 4.dp

        /** `spacing-8` — 0.5rem / 8dp baseline. */
        val space8: Dp = 8.dp

        /** `spacing-12` — 0.75rem / 12dp. */
        val space12: Dp = 12.dp

        /** `spacing-16` — 1rem / 16dp. */
        val space16: Dp = 16.dp

        /** `spacing-20` — 1.25rem / 20dp. */
        val space20: Dp = 20.dp

        /** `spacing-24` — 1.5rem / 24dp. */
        val space24: Dp = 24.dp

        /** `spacing-32` — 2rem / 32dp. */
        val space32: Dp = 32.dp

        /** `spacing-48` — 3rem / 48dp. */
        val space48: Dp = 48.dp

        /** `margin-mobile` — 1rem / 16dp mobile side margins. */
        val marginMobile: Dp = 16.dp

        /** `margin-tablet` — 1.5rem / 24dp tablet/foldable outer margins. */
        val marginTablet: Dp = 24.dp

        /** `gutter-grid` — 1rem / 16dp grid gutter. */
        val gutterGrid: Dp = 16.dp

        /** Standard internal card padding (16dp, "Components → Cards"). */
        val cardPadding: Dp = 16.dp
    }

    // ---------------------------------------------------------------------------------------------
    // Touch targets & control heights ("Layout & Spacing" + "Components")
    // ---------------------------------------------------------------------------------------------

    /** Touch-target and control-height minimums from `touch-target-min` and the "Components" section. */
    object TouchTargets {
        /** `touch-target-min` — 3rem / 48dp minimum interactive hit area (AUI-8.3). */
        val minTouchTarget: Dp = 48.dp

        /** Checkbox visual box (24dp) inside its 48dp hit area ("Momentum Checkboxes"). */
        val checkboxBox: Dp = 24.dp

        /** Minimum list-row height — 56dp ("Input Fields" / checklist rows). */
        val listRowMinHeight: Dp = 56.dp

        /** Input-field container height — 56dp ("Input Fields"). */
        val inputHeight: Dp = 56.dp

        /** Standard primary/secondary button height — 48dp ("Buttons"). */
        val buttonHeight: Dp = 48.dp

        /** Stacked overlay action-button height — 52dp (AUI-2.8). */
        val primaryButtonHeight: Dp = 52.dp

        /** Primary FAB size — 56dp (AUI-1.7). */
        val fabSize: Dp = 56.dp
    }
}
