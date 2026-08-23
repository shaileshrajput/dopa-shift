package com.dopashift.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * DopaShift Kinetic Design System — Color Palette
 *
 * Primary (Productive Blue): Main actions, active streaks, productive state indicators
 * Secondary (Momentum Teal): Progress rings, completion checkmarks, positive trends
 * Tertiary (Focus Indigo): Secondary categories, habit tracks, educational content markers
 */

// === Brand Colors ===
val ProductiveBlue = Color(0xFF0066FF)
val MomentumTeal = Color(0xFF2DD4BF)
val FocusIndigo = Color(0xFF6366F1)

// === Semantic Colors ===
val SuccessGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRose = Color(0xFFE11D48)

// === Dark Theme Surface Colors (Deep Navy-Slate base) ===
private val DarkSurface = Color(0xFF0B1326)
private val DarkSurfaceDim = Color(0xFF0B1326)
private val DarkSurfaceBright = Color(0xFF31394D)
private val DarkSurfaceContainerLowest = Color(0xFF060E20)
private val DarkSurfaceContainerLow = Color(0xFF131B2E)
private val DarkSurfaceContainer = Color(0xFF171F33)
private val DarkSurfaceContainerHigh = Color(0xFF222A3D)
private val DarkSurfaceContainerHighest = Color(0xFF2D3449)
private val DarkOnSurface = Color(0xFFDAE2FD)
private val DarkOnSurfaceVariant = Color(0xFFC2C6D8)
private val DarkSurfaceVariant = Color(0xFF2D3449)
private val DarkOutline = Color(0xFF8C90A1)
private val DarkOutlineVariant = Color(0xFF424656)
private val DarkInverseSurface = Color(0xFFDAE2FD)
private val DarkInverseOnSurface = Color(0xFF283044)
private val DarkSurfaceTint = Color(0xFFB3C5FF)

// Dark theme primary colors
private val DarkPrimary = Color(0xFFB3C5FF)
private val DarkOnPrimary = Color(0xFF002B75)
private val DarkPrimaryContainer = Color(0xFF0066FF)
private val DarkOnPrimaryContainer = Color(0xFFF8F7FF)
private val DarkInversePrimary = Color(0xFF0054D6)

// Dark theme secondary colors
private val DarkSecondary = Color(0xFF44E2CD)
private val DarkOnSecondary = Color(0xFF003731)
private val DarkSecondaryContainer = Color(0xFF03C6B2)
private val DarkOnSecondaryContainer = Color(0xFF004D44)

// Dark theme tertiary colors
private val DarkTertiary = Color(0xFFD0BCFF)
private val DarkOnTertiary = Color(0xFF3C0091)
private val DarkTertiaryContainer = Color(0xFF8252EC)
private val DarkOnTertiaryContainer = Color(0xFFFDF6FF)

// Dark theme error colors
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

// Dark theme background
private val DarkBackground = Color(0xFF0B1326)
private val DarkOnBackground = Color(0xFFDAE2FD)

// === Light Theme Colors ===
private val LightSurface = Color(0xFFF8FAFC)
private val LightSurfaceDim = Color(0xFFDBDFE4)
private val LightSurfaceBright = Color(0xFFF8FAFC)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF1F4F9)
private val LightSurfaceContainer = Color(0xFFECEFF4)
private val LightSurfaceContainerHigh = Color(0xFFE6E9EE)
private val LightSurfaceContainerHighest = Color(0xFFE0E3E9)
private val LightOnSurface = Color(0xFF191C20)
private val LightOnSurfaceVariant = Color(0xFF44474E)
private val LightSurfaceVariant = Color(0xFFE0E3E9)
private val LightOutline = Color(0xFF74777F)
private val LightOutlineVariant = Color(0xFFC4C6CF)
private val LightInverseSurface = Color(0xFF2E3036)
private val LightInverseOnSurface = Color(0xFFF0F0F7)
private val LightSurfaceTint = Color(0xFF0066FF)

// Light theme primary colors
private val LightPrimary = Color(0xFF0054D6)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFDAE1FF)
private val LightOnPrimaryContainer = Color(0xFF001849)
private val LightInversePrimary = Color(0xFFB3C5FF)

// Light theme secondary colors
private val LightSecondary = Color(0xFF006B60)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFF62FAE3)
private val LightOnSecondaryContainer = Color(0xFF00201C)

// Light theme tertiary colors
private val LightTertiary = Color(0xFF6930D4)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFE9DDFF)
private val LightOnTertiaryContainer = Color(0xFF23005C)

// Light theme error colors
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

// Light theme background
private val LightBackground = Color(0xFFF8FAFC)
private val LightOnBackground = Color(0xFF191C20)

// === ColorScheme Builders ===

internal val DopaShiftDarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    surfaceTint = DarkSurfaceTint,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
)

internal val DopaShiftLightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    surfaceTint = LightSurfaceTint,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
)

/**
 * Extended DopaShift color tokens not covered by the M3 ColorScheme.
 * Access via [DopaShiftTheme.colors].
 */
data class DopaShiftColors(
    val productiveBlue: Color = ProductiveBlue,
    val momentumTeal: Color = MomentumTeal,
    val focusIndigo: Color = FocusIndigo,
    val successGreen: Color = SuccessGreen,
    val warningAmber: Color = WarningAmber,
    val errorRose: Color = ErrorRose,
    val interceptOverlay: Color = Color(0xF20F172A), // rgba(15, 23, 42, 0.95)
)
