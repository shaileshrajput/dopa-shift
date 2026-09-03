package com.dopashift.ui.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * Pure state → render derivation helpers for the DopaShift Kinetic Android screens
 * (spec `android-ui-upgrade`).
 *
 * Each function is a **pure** mapping from UI state to a render decision: no Composable, no Android
 * framework side effects, no I/O. Extracting them here lets the input-varying rendering rules be
 * property-tested on the JVM under plain JUnit / Kotest without an emulator (design → Testing
 * Strategy, Properties 1, 3, 4, 5, 6). The screen composables and Kinetic components delegate to
 * these helpers so the on-device rendering and the off-device property tests agree by construction.
 *
 * All colors are sourced from [DopaShiftTokens]; no raw literals are inlined (AUI-8.1). Note that
 * `androidx.compose.ui.graphics.Color` is a Kotlin `inline value class` over a `ULong`, so referring
 * to a token [Color] value keeps these helpers pure-JVM testable — no Android runtime is required to
 * compare the returned values.
 */

/**
 * Whether the overlay's secondary "Continue to app" button is enabled.
 *
 * Property 1 (AUI-2.7): the button is enabled **if and only if** the cooldown has fully elapsed —
 * i.e. `cooldownSeconds == 0`. Every positive cooldown (and any spurious negative value) keeps the
 * button disabled. The overlay composable reads this rather than running its own timer; the
 * countdown value is supplied by `screen-time-interception-engine`.
 *
 * @param cooldownSeconds the remaining cooldown supplied by the interception engine.
 * @return `true` iff [cooldownSeconds] is exactly zero.
 */
fun continueEnabled(cooldownSeconds: Int): Boolean = cooldownSeconds == 0

/**
 * The rendered color for a single daily-score bar in the Analytics chart.
 *
 * Property 4 (AUI-6.4): the mapping is total and exclusive —
 *  - [DailyBarCategory.PRODUCTIVE]  → [DopaShiftTokens.Colors.momentumTeal]
 *  - [DailyBarCategory.DISTRACTING] → [DopaShiftTokens.Colors.dangerCoral]
 *  - [DailyBarCategory.NO_DATA]     → [DopaShiftTokens.Colors.chartNoData]
 *
 * No other mapping occurs. The `when` is exhaustive over the enum, so adding a category later forces
 * an explicit color decision here rather than silently falling through to a default.
 *
 * @param category the category the day falls into.
 * @return the token color the bar renders in.
 */
fun barColor(category: DailyBarCategory): Color = when (category) {
    DailyBarCategory.PRODUCTIVE -> DopaShiftTokens.Colors.momentumTeal
    DailyBarCategory.DISTRACTING -> DopaShiftTokens.Colors.dangerCoral
    DailyBarCategory.NO_DATA -> DopaShiftTokens.Colors.chartNoData
}

/**
 * The 30-day habit-roadmap progress fraction.
 *
 * Property 3 (AUI-5.5): for a `Habit_Track` with `completedDays` in `0..30`, the rendered fraction
 * equals `completedDays / 30`. Inputs outside that range are coerced into `0..30` first, so the
 * fraction is always in `0f..1f` and the progress bar never over- or under-draws.
 *
 * @param completedDays the number of completed roadmap days; coerced to `0..30`.
 * @return the fill fraction in `0f..1f` (`completedDays / 30f`).
 */
fun roadmapFraction(completedDays: Int): Float =
    completedDays.coerceIn(0, ROADMAP_TOTAL_DAYS) / ROADMAP_TOTAL_DAYS.toFloat()

/**
 * The efficiency score formatted for the Completion Ring center.
 *
 * Property 5 (AUI-1.1, AUI-8.4): for any `Efficiency_Score`, the value is clamped to `0..100` and
 * rendered as an **integer** percentage with a trailing percent sign and no fractional digits. This
 * matches how [com.dopashift.ui.components.CompletionRing] draws its center label, so the ring can
 * delegate its formatting here.
 *
 * @param score the raw efficiency score; clamped to `0..100`.
 * @return the integer percent string, e.g. `"72%"`.
 */
fun formatPercent(score: Int): String = score.coerceIn(0, 100).toString() + PERCENT_SIGN

/**
 * The rendering style for a to-do / checklist row given its completion state.
 *
 * Property 6 (AUI-1.5, AUI-5.3): a completed row renders dimmed (50% opacity) with strikethrough in
 * the completed-text color; an incomplete row renders fully opaque, without strikethrough, in the
 * primary text color. The Kinetic checkbox / row composables read this so the off-device property
 * test and the on-device styling stay in lockstep.
 *
 * @param done whether the item is completed.
 * @return the pure [CompletedRowStyle] describing opacity, strikethrough, and text color.
 */
fun completedRowStyle(done: Boolean): CompletedRowStyle =
    if (done) {
        CompletedRowStyle(
            opacity = COMPLETED_OPACITY,
            strikethrough = true,
            textColor = DopaShiftTokens.Colors.textCompleted,
        )
    } else {
        CompletedRowStyle(
            opacity = ACTIVE_OPACITY,
            strikethrough = false,
            textColor = DopaShiftTokens.Colors.textPrimary,
        )
    }

/**
 * A pure description of how a to-do / checklist row renders for a given completion state.
 *
 * Holds only render-decision primitives (no Composable, no Android framework types beyond the value
 * types [Color] and [TextDecoration]) so it is fully comparable in off-device property tests.
 *
 * @property opacity the row alpha — `1.0f` when active, `0.5f` when completed.
 * @property strikethrough whether the label is struck through (`true` only when completed).
 * @property textColor the label color token — primary when active, completed-text when done.
 */
data class CompletedRowStyle(
    val opacity: Float,
    val strikethrough: Boolean,
    val textColor: Color,
) {
    /** The [TextDecoration] the label renders with, derived from [strikethrough]. */
    val textDecoration: TextDecoration
        get() = if (strikethrough) TextDecoration.LineThrough else TextDecoration.None
}

/** The 30-day span of a habit roadmap (`Day N/30`); denominator of [roadmapFraction]. */
private const val ROADMAP_TOTAL_DAYS: Int = 30

/** Opacity of a completed row — 50% dimming (Property 6). */
private const val COMPLETED_OPACITY: Float = 0.5f

/** Opacity of an active (incomplete) row — fully opaque. */
private const val ACTIVE_OPACITY: Float = 1.0f

/** The percent glyph appended to the integer readout; concatenated rather than inlined as copy. */
private const val PERCENT_SIGN: String = "%"
