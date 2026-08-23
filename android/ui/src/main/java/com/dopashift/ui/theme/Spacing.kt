package com.dopashift.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * DopaShift Kinetic Design System — Spacing
 *
 * Strict 8px-based spacing rhythm.
 * - Base unit: 8dp
 * - Compact: 4dp (half-unit for tight grouping)
 * - Margins: 24dp (mobile standard, 3x base)
 * - Gutter: 16dp (2x base, between columns)
 */
@Immutable
data class DopaShiftSpacing(
    /** Half-unit spacing for tight element grouping (4dp) */
    val compact: Dp = 4.dp,

    /** Base spacing unit (8dp) */
    val base: Dp = 8.dp,

    /** 1.5x base (12dp) — small stack spacing */
    val stackSm: Dp = 12.dp,

    /** 2x base (16dp) — gutter between columns and standard internal padding */
    val gutter: Dp = 16.dp,

    /** 3x base (24dp) — mobile margins, medium stack spacing */
    val margin: Dp = 24.dp,

    /** 4x base (32dp) — section separator */
    val section: Dp = 32.dp,

    /** 6x base (48dp) — large stack spacing (e.g., Intercept Overlay between sections) */
    val stackLg: Dp = 48.dp,

    /** 8x base (64dp) — extra-large spacing */
    val xl: Dp = 64.dp,
)

val LocalDopaShiftSpacing = staticCompositionLocalOf { DopaShiftSpacing() }
