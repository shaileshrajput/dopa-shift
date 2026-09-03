package com.dopashift.ui.state

import androidx.compose.ui.graphics.Color
import com.dopashift.ui.render.DailyBarCategory

/**
 * Shared row / item view-models for the DopaShift Kinetic Android screens (spec
 * `android-ui-upgrade`, task 4.1).
 *
 * These are **presentation-only** types: plain data holders built from primitives
 * (`String`/`Int`/`Boolean`/enum/[Color]) that the seven screen composables render. They carry **no
 * persistence identity**, emit **no** `Change_Log` events, and import **no** Room/domain framework
 * types. The existing view-models map the parent domain models (`Goal_Profile`, `DailyTodoItem`,
 * `Habit_Track`, `Efficiency_Score`, `Rule`) into these shapes (task 4.2); this file only defines
 * the shapes.
 *
 * Any color default is sourced from [com.dopashift.ui.theme.DopaShiftTokens] by the mapper, never
 * as a raw literal here (AUI-8.1).
 */

/**
 * A single to-do / focus-task row (Today's Focus list, overlay pending tasks, checklist rows).
 *
 * @property id opaque stable key for list diffing / click dispatch — a string rendering of the
 *   source domain id, **not** a persistence identity owned by the UI layer.
 * @property text the task label.
 * @property done whether the item is completed; drives the struck-through / dimmed style
 *   (Property 6, AUI-1.5, AUI-5.3).
 * @property typeTag optional type-tag chip label (e.g. `Focus`, `Errand`); `null` when untagged.
 * @property estimatedTimeLabel optional monospace estimate badge (e.g. `15m`); `null` when absent.
 * @property deletable whether an inline delete control is shown (checklist rows, AUI-5.3).
 */
data class TodoRowUi(
    val id: String,
    val text: String,
    val done: Boolean,
    val typeTag: String? = null,
    val estimatedTimeLabel: String? = null,
    val deletable: Boolean = false,
)

/**
 * A goal summary card on the Dashboard grid and the Goals & Habits overview (AUI-1.6, AUI-4.1,
 * AUI-4.2).
 *
 * @property id opaque stable key for list diffing / navigation dispatch.
 * @property title the goal name.
 * @property category the goal category label (rendered as a pill badge).
 * @property completionPercent integer completion for the mini [com.dopashift.ui.components.CompletionRing] (`0..100`).
 * @property pendingTaskLabel subtitle counter copy (e.g. `1/2 tasks pending`).
 * @property keywords keyword chips.
 * @property accentColor the goal-icon / accent color for the card.
 * @property streakCompletedDays completed days in the dot-matrix streak row.
 * @property streakTotalDays total days in the dot-matrix streak row.
 * @property roadmapStatusLabel roadmap status line copy (e.g. `Day 6/30`).
 */
data class GoalCardUi(
    val id: String,
    val title: String,
    val category: String,
    val completionPercent: Int,
    val pendingTaskLabel: String,
    val keywords: List<String>,
    val accentColor: Color,
    val streakCompletedDays: Int,
    val streakTotalDays: Int,
    val roadmapStatusLabel: String,
)

/**
 * The current-day anchor / rescue micro-habit checkpoint (Dashboard Primary Anchor, overlay Rescue
 * Card — AUI-1.2, AUI-2.4).
 *
 * @property id opaque stable key for the checkpoint.
 * @property title the micro-habit title.
 * @property subtitle category / notes sub-line.
 * @property estimatedTimeLabel monospace estimated-time pill copy (e.g. `10m`).
 * @property dayLabel the roadmap-day label (e.g. `Day 6/30`).
 * @property completed whether today's checkpoint is done.
 * @property rewardPrompt optional reward-prompt line shown on the overlay rescue card; `null` on
 *   the Dashboard anchor.
 */
data class AnchorHabitUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val estimatedTimeLabel: String,
    val dayLabel: String,
    val completed: Boolean,
    val rewardPrompt: String? = null,
)

/**
 * An installed app row in the App Limits search results (AUI-3.1, AUI-3.2).
 *
 * @property packageName the app package id, used as the stable key and shown as the sub-label.
 * @property label the human-readable app name.
 * @property selected whether the high-contrast selection checkbox is checked.
 */
data class InstalledAppUi(
    val packageName: String,
    val label: String,
    val selected: Boolean,
)

/**
 * An active interception-rule row in the App Limits list (AUI-3.5).
 *
 * @property id opaque stable key for the rule.
 * @property appLabel the target app label.
 * @property packageName the target app package id (sub-label).
 * @property ruleTypeLabel the rule-type indicator copy (e.g. `Daily limit: 30m` or
 *   `Repetitive: Every 30m • 3 loops`).
 * @property paused whether the rule is paused today (drives the Pause/Resume control state).
 */
data class RuleRowUi(
    val id: String,
    val appLabel: String,
    val packageName: String,
    val ruleTypeLabel: String,
    val paused: Boolean = false,
)

/**
 * A single day's bar in the Analytics daily-scores chart (AUI-6.4, Property 4).
 *
 * @property dayLabel the x-axis label for the day (e.g. `Mon`, `12`).
 * @property scorePercent the day's efficiency score (`0..100`); drives the bar height.
 * @property category the day's category, mapped to a token color by
 *   [com.dopashift.ui.render.barColor].
 */
data class DailyBarUi(
    val dayLabel: String,
    val scorePercent: Int,
    val category: DailyBarCategory,
)

/**
 * A horizontal category usage bar in Analytics (AUI-6.5).
 *
 * @property label the category label.
 * @property value the absolute-time readout (e.g. `1h 20m`), rendered in the monospace role.
 * @property fraction the fill fraction in `0f..1f`.
 * @property percentLabel the percent readout (e.g. `35%`).
 * @property color the category's bar color (sourced from a token by the mapper).
 */
data class UsageBarUi(
    val label: String,
    val value: String,
    val fraction: Float,
    val percentLabel: String,
    val color: Color,
)

/**
 * One accent swatch in the Settings 12-swatch accent picker (AUI-7.3, AUI-7.4).
 *
 * @property id opaque stable key for the swatch.
 * @property label the dynamic accent name (e.g. `Momentum Teal`).
 * @property color the swatch fill color.
 * @property accentSeed the accent seed applied app-wide when this swatch is selected (Property 7).
 */
data class AccentSwatch(
    val id: String,
    val label: String,
    val color: Color,
    val accentSeed: Int,
)

/**
 * The user avatar rendered in the Settings profile section (AUI-7.1).
 *
 * @property imageUri the avatar image URI when a photo is set; `null` renders the [monogram]
 *   fallback.
 * @property monogram the default monogram (initials) shown when no photo is set.
 */
data class AvatarUi(
    val imageUri: String? = null,
    val monogram: String,
)

/**
 * A repetitive-interval preset shown only when [LimitType.REPETITIVE] is selected (AUI-3.4).
 *
 * @property label the preset chip label (e.g. `15 min`, `Custom...`).
 * @property minutes the interval in minutes; `null` for the `Custom...` preset.
 * @property selected whether this preset is the chosen one.
 */
data class IntervalPreset(
    val label: String,
    val minutes: Int?,
    val selected: Boolean = false,
)

/**
 * The Analytics 4-column summary metric grid values, derived from `Efficiency_Score` history
 * (AUI-6.3).
 *
 * @property averagePercent the average score across the range.
 * @property highestPercent the highest score in the range.
 * @property lowestPercent the lowest score in the range.
 * @property daysTracked the number of tracked days in the range.
 */
data class SummaryMetrics(
    val averagePercent: Int,
    val highestPercent: Int,
    val lowestPercent: Int,
    val daysTracked: Int,
)
