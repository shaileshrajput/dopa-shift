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
 * [ThemeMode.SYSTEM], the [darkOverride] flag). The [seed] then drives the Material 3 accent
 * roles — primary, primaryContainer, and surfaceTint — so buttons, toggles, progress indicators,
 * and navigation highlights all derive from the user's accent (DUX-4.6). A seed of
 * [DEFAULT_ACCENT_SEED] leaves the design-system primary untouched.
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
        base.copy(
            primary = accent,
            primaryContainer = accent,
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
