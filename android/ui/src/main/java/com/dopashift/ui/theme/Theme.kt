package com.dopashift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * DopaShift Kinetic Design System — Theme
 *
 * Implements the token-to-Compose mapping for DUX-4:
 * - A Material 3 [ColorScheme] seeded from the user's accent (DUX-4.6), built by [kineticScheme].
 * - [ThemeMode] DARK (default) | LIGHT | SYSTEM (DUX-4.5); dark is the default on first launch.
 * - Typography mapping Hanken Grotesk / Inter / JetBrains Mono to the M3 type slots (DUX-4.2),
 *   defined in [DopaShiftTypography].
 * - Shape mapping 8dp / 16dp / pill to [MaterialTheme.shapes] `small` / `medium` / chip pill
 *   (DUX-4.4), defined in [dopaShiftShapes] / [ShapeTokens].
 * - Spacing constants (8dp base, 4dp compact) exposed via [DopaShiftSpacing].
 *
 * Theme mode and accent are sourced from the existing `data` DataStore (`theme_mode`,
 * `accent_seed`) via a StateFlow (see `ThemeState`), so a change recolors the visible screen
 * without an app restart and without losing screen state (DUX-4.7).
 *
 * No raw color or dimension literals appear here except the token values that ARE the design
 * system definition; Composable UI code references tokens by name (DUX-4.1).
 */

/** Theme mode selection persisted in DataStore. Dark is the default (DUX-4.5). */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

val LocalDopaShiftColors = staticCompositionLocalOf { DopaShiftColors() }

/**
 * Builds the DopaShift Kinetic [ColorScheme] for the given accent [seed] and [mode].
 *
 * The base scheme is the design-system dark or light palette (resolved from [mode] and, for
 * [ThemeMode.SYSTEM], the [darkOverride] flag). The [seed] then drives BOTH the structural accent
 * roles (primary, primaryContainer) AND the reward/highlight roles (secondary, tertiary and their
 * containers, plus surfaceTint) so buttons, toggles, progress rings, completion checkmarks, and
 * navigation highlights all derive from the user's accent (DUX-4.6, AUI-7.4).
 *
 * The reward roles must be re-tinted here because the accent-driven UI (efficiency/goal progress
 * rings, completion checkmarks, goal cards) reads `secondary`/`tertiary`/`secondaryContainer`, not
 * `primary`. Tinting only `primary` — as an earlier version did — left every visible reward element
 * on the default Momentum Teal, so a Settings accent change had no visible effect. A seed of
 * [DEFAULT_ACCENT_SEED] leaves the design-system palette untouched.
 *
 * @param seed accent seed as an ARGB color long.
 * @param mode selected [ThemeMode].
 * @param darkOverride the effective dark/light decision for [ThemeMode.SYSTEM]; ignored otherwise.
 */
internal fun kineticScheme(
    seed: Long,
    mode: ThemeMode,
    darkOverride: Boolean,
): ColorScheme {
    val useDark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> darkOverride
    }
    val base = if (useDark) DopaShiftDarkColorScheme else DopaShiftLightColorScheme

    return if (seed == DEFAULT_ACCENT_SEED) {
        base
    } else {
        val accent = Color(seed.toInt())
        val onAccent = DopaShiftTokens.Colors.onMomentumTeal
        base.copy(
            // Structural accent roles (primary actions, some icons).
            primary = accent,
            primaryContainer = accent,
            // Reward/highlight roles — the ones the accent-driven UI actually reads
            // (progress rings, checkmarks, active nav pills, accent card strokes).
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accent.copy(alpha = 0.15f),
            onSecondaryContainer = accent,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = accent.copy(alpha = 0.15f),
            onTertiaryContainer = accent,
            surfaceTint = accent,
        )
    }
}

/** Sentinel accent seed meaning "use the design-system primary" (no override). */
const val DEFAULT_ACCENT_SEED: Long = 0L

/**
 * Primary entry point for the DopaShift Kinetic design system (DUX-4).
 *
 * Applies the accent-seeded [ColorScheme] ([kineticScheme]), the token-mapped [DopaShiftTypography]
 * and [MaterialTheme.shapes], and provides the extended color / spacing / shape tokens through
 * composition locals. Because [themeMode] and [accentSeed] are read reactively from state, a change
 * recomposes the theme in place without a restart (DUX-4.7).
 *
 * @param themeMode DARK (default) | LIGHT | SYSTEM (DUX-4.5).
 * @param accentSeed user accent as an ARGB color long; [DEFAULT_ACCENT_SEED] for the default (DUX-4.6).
 * @param content the composable content tree to theme.
 */
@Composable
fun DopaShiftTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentSeed: Long = DEFAULT_ACCENT_SEED,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = kineticScheme(seed = accentSeed, mode = themeMode, darkOverride = systemDark)

    CompositionLocalProvider(
        LocalDopaShiftColors provides DopaShiftColors(),
        LocalDopaShiftSpacing provides DopaShiftSpacing(),
        LocalDopaShiftShapes provides ShapeTokens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DopaShiftTypography,
            shapes = dopaShiftShapes(),
            content = content,
        )
    }
}

/**
 * Backward-compatible overload retained for callers that only need an explicit dark/light choice
 * and an optional [Color] accent (e.g. the Intercept overlay). New code should prefer the
 * [ThemeMode] / [accentSeed] overload above.
 *
 * @param darkTheme whether to use the dark color scheme; defaults to the system setting.
 * @param userAccentColor optional user-selected accent color; overrides the primary when provided.
 */
@Composable
fun DopaShiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    userAccentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    DopaShiftTheme(
        themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        accentSeed = userAccentColor?.let { it.toArgb().toLong() and 0xFFFFFFFFL } ?: DEFAULT_ACCENT_SEED,
        content = content,
    )
}

/**
 * The reward/highlight accent applied across the DopaShift Kinetic surfaces (AUI-7.4).
 *
 * A Settings accent-swatch selection produces one of these; it re-tints the scheme's reward roles
 * (`secondary`/`tertiary` and their containers, plus `surfaceTint`) so every subsequently composed
 * screen — rings, checkboxes, active nav pills, accent card strokes — reads the same accent. The
 * `primary` (Productive Blue) structural role is intentionally left untouched so primary actions
 * stay stable while the reward accent personalizes (AUI-7.4).
 *
 * @property color the accent [Color]; defaults to [DopaShiftTokens.Colors.momentumTeal], the
 *   Momentum Teal reward accent that is the system default when no swatch is chosen.
 */
@JvmInline
value class AccentSeed(val color: Color = DopaShiftTokens.Colors.momentumTeal) {
    companion object {
        /** The system-default reward accent (Momentum Teal), used when Settings has no selection. */
        val Default: AccentSeed = AccentSeed(DopaShiftTokens.Colors.momentumTeal)
    }
}

/**
 * Re-tints [base] so its reward/highlight roles derive from [accent] (AUI-7.4). Leaves `primary`
 * and the structural surface/background roles intact — only the Momentum-Teal-family roles that the
 * accent picker personalizes are recolored. A [seed] equal to the token default returns [base]
 * unchanged.
 */
internal fun ColorScheme.withAccent(accent: AccentSeed): ColorScheme {
    if (accent.color == DopaShiftTokens.Colors.momentumTeal) return this
    val onAccent = DopaShiftTokens.Colors.onMomentumTeal
    return copy(
        secondary = accent.color,
        onSecondary = onAccent,
        secondaryContainer = accent.color.copy(alpha = 0.15f),
        onSecondaryContainer = accent.color,
        tertiary = accent.color,
        onTertiary = onAccent,
        tertiaryContainer = accent.color.copy(alpha = 0.15f),
        onTertiaryContainer = accent.color,
        surfaceTint = accent.color,
    )
}

/**
 * Accent-seed entry point for the DopaShift Kinetic design system (AUI-8.1, AUI-7.4).
 *
 * Wraps [MaterialTheme] with the token-derived dark [ColorScheme] ([DopaShiftKineticDarkColorScheme]
 * built from [DopaShiftTokens.Colors]), the token typography ([DopaShiftTypography]), and the token
 * shapes, then re-tints the reward roles from [accent] via [withAccent] so a Settings accent
 * selection propagates app-wide. [accent] defaults to [AccentSeed.Default] (Momentum Teal).
 *
 * This overload always renders the dark, dark-first Kinetic surface (Assumption 5); it delegates to
 * the reactive [ThemeMode]/[accentSeed] machinery so the extended color / spacing / shape tokens are
 * still provided through the composition locals, keeping the DUX behavior intact.
 *
 * @param accent the reward accent seed; defaults to Momentum Teal (AUI-7.4).
 * @param content the composable content tree to theme.
 */
@Composable
fun DopaShiftTheme(
    accent: AccentSeed,
    content: @Composable () -> Unit,
) {
    val colorScheme = DopaShiftKineticDarkColorScheme.withAccent(accent)

    CompositionLocalProvider(
        LocalDopaShiftColors provides DopaShiftColors(),
        LocalDopaShiftSpacing provides DopaShiftSpacing(),
        LocalDopaShiftShapes provides ShapeTokens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DopaShiftTypography,
            shapes = dopaShiftShapes(),
            content = content,
        )
    }
}

/**
 * Convenience accessor for the DopaShift design system tokens.
 *
 * Usage:
 * ```kotlin
 * val blue = DopaShiftTheme.colors.productiveBlue
 * val spacing = DopaShiftTheme.spacing.margin
 * val cardRadius = DopaShiftTheme.shapes.large
 * ```
 */
object DopaShiftTheme {
    /** Extended brand colors not part of the M3 [ColorScheme]. */
    val colors: DopaShiftColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDopaShiftColors.current

    /** Design system spacing tokens (8dp base, 4dp compact). */
    val spacing: DopaShiftSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalDopaShiftSpacing.current

    /** Design system shape radii tokens (8dp / 16dp / pill). */
    val shapes: ShapeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalDopaShiftShapes.current
}
