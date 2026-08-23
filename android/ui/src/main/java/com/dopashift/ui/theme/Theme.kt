package com.dopashift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * DopaShift Kinetic Design System — Theme
 *
 * Supports:
 * - Light/dark theme (system setting or user toggle)
 * - Dynamic user accent color override via custom ColorScheme seed
 * - Extended color tokens via [DopaShiftColors]
 * - 8px spacing rhythm via [DopaShiftSpacing]
 * - Custom typography (Hanken Grotesk, Inter, JetBrains Mono)
 */

val LocalDopaShiftColors = staticCompositionLocalOf { DopaShiftColors() }

/**
 * The main entry point for applying the DopaShift Kinetic design system.
 *
 * @param darkTheme Whether to use the dark color scheme. Defaults to system setting.
 * @param userAccentColor Optional user-selected accent color (#RRGGBB). When provided, it
 *   overrides the primary color in the ColorScheme. Corresponds to Requirement 19.15.
 * @param content The composable content tree to theme.
 */
@Composable
fun DopaShiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    userAccentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val baseColorScheme = if (darkTheme) DopaShiftDarkColorScheme else DopaShiftLightColorScheme

    // Apply user accent color override to primary slots when provided
    val colorScheme: ColorScheme = if (userAccentColor != null) {
        baseColorScheme.copy(
            primary = userAccentColor,
            primaryContainer = userAccentColor,
            surfaceTint = userAccentColor,
        )
    } else {
        baseColorScheme
    }

    val extendedColors = DopaShiftColors()
    val spacing = DopaShiftSpacing()

    CompositionLocalProvider(
        LocalDopaShiftColors provides extendedColors,
        LocalDopaShiftSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DopaShiftTypography,
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
 * ```
 */
object DopaShiftTheme {
    /**
     * Extended brand colors not part of the M3 [ColorScheme].
     */
    val colors: DopaShiftColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDopaShiftColors.current

    /**
     * Design system spacing tokens (8dp rhythm).
     */
    val spacing: DopaShiftSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalDopaShiftSpacing.current
}
