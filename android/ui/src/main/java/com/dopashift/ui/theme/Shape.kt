package com.dopashift.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * DopaShift Kinetic Design System — Shapes
 *
 * Maps the `rounded` shape tokens from `dopashift_kinetic/DESIGN.md` to Material 3 [Shapes]
 * slots (DUX-4.4):
 * - `rounded.sm` (0.25rem / 4dp)      -> [Shapes.extraSmall]
 * - `rounded.DEFAULT` (0.5rem / 8dp)  -> [Shapes.small]      (buttons, checkboxes, inputs)
 * - `rounded.md` (0.75rem / 12dp)     -> input/list radii
 * - `rounded.lg` (1rem / 16dp)        -> [Shapes.medium]     (cards, progress containers)
 * - `rounded.xl` (1.5rem / 24dp)      -> [Shapes.large]
 * - `rounded.full` (9999px)           -> [ShapeTokens.chipPill] (chips)
 *
 * These token-named radii are the single source of truth for corner rounding; Composable code
 * references them by name (via [ShapeTokens]) rather than hard-coding dp literals (DUX-4.1).
 */
@Immutable
data class ShapeTokens(
    /** `rounded.sm` — 4dp: extra-small / compact intra-component rounding. */
    val extraSmall: Dp = 4.dp,

    /** `rounded.DEFAULT` — 8dp: buttons, checkboxes, and inputs. */
    val default: Dp = 8.dp,

    /** `rounded.md` — 12dp: input fields and list rows. */
    val medium: Dp = 12.dp,

    /** `rounded.lg` — 16dp: cards and progress containers. */
    val large: Dp = 16.dp,

    /** `rounded.xl` — 24dp: extra-large containers. */
    val extraLarge: Dp = 24.dp,

    /** `rounded.full` — pill radius for chips (e.g. LLM provider chips). */
    val chipPill: Dp = 9999.dp,
)

val LocalDopaShiftShapes = staticCompositionLocalOf { ShapeTokens() }

/**
 * The pill shape used for chips (`rounded.full`). Exposed as a ready-to-use [RoundedCornerShape]
 * so Composable code can reference it by name without a dp literal.
 */
val ChipPillShape: RoundedCornerShape = RoundedCornerShape(percent = 50)

/**
 * Material 3 [Shapes] built from the [ShapeTokens] so the standard M3 shape slots resolve to the
 * DopaShift Kinetic radii:
 * - [Shapes.small]  = 8dp  (`rounded.DEFAULT`) — buttons, checkboxes, inputs
 * - [Shapes.medium] = 16dp (`rounded.lg`)      — cards, progress containers
 */
internal fun dopaShiftShapes(tokens: ShapeTokens = ShapeTokens()): Shapes = Shapes(
    extraSmall = RoundedCornerShape(tokens.extraSmall),
    small = RoundedCornerShape(tokens.default),
    medium = RoundedCornerShape(tokens.large),
    large = RoundedCornerShape(tokens.extraLarge),
    extraLarge = RoundedCornerShape(tokens.extraLarge),
)

/**
 * Material 3 [Shapes] for the DopaShift Kinetic system, sourced *exclusively* from
 * [DopaShiftTokens.Radii] (spec `android-ui-upgrade`, AUI-8.1 / AUI-8.2). Slot assignment follows
 * the design's shape contract — 16dp cards, 12–14dp interactive controls, and full pills:
 *
 *  - [Shapes.extraSmall] = `Radii.small` (4dp) — compact intra-component rounding.
 *  - [Shapes.small]      = `Radii.control` (12dp) — interactive controls (segmented pills, inputs,
 *    list rows), the lower bound of the 12–14dp control range.
 *  - [Shapes.medium]     = `Radii.card` (16dp) — cards & structural surfaces (`KineticCard`).
 *  - [Shapes.large]      = `Radii.large` (24dp) — large containers / sheets.
 *  - [Shapes.extraLarge] = `Radii.large` (24dp) — the largest structural surfaces.
 *
 * The 14dp button radius (`Radii.button`) and the full-pill radius (`Radii.pill`) sit outside the
 * five M3 slots and are exposed directly as [DopaShiftButtonShape] and [DopaShiftPillShape] for the
 * components that need them (buttons, chips, segmented pills). No raw `dp` literal appears here —
 * every radius is read from the token object.
 */
val DopaShiftShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(DopaShiftTokens.Radii.small),
    small = RoundedCornerShape(DopaShiftTokens.Radii.control),
    medium = RoundedCornerShape(DopaShiftTokens.Radii.card),
    large = RoundedCornerShape(DopaShiftTokens.Radii.large),
    extraLarge = RoundedCornerShape(DopaShiftTokens.Radii.large),
)

/** 14dp control radius for primary/secondary buttons (`Radii.button`, AUI-8.2). */
val DopaShiftButtonShape: RoundedCornerShape = RoundedCornerShape(DopaShiftTokens.Radii.button)

/** Full-pill radius (`Radii.pill`) for status chips, category pills, and segmented pickers. */
val DopaShiftPillShape: RoundedCornerShape = RoundedCornerShape(DopaShiftTokens.Radii.pill)
