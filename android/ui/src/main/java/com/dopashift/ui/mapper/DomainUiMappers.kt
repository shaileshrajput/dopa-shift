package com.dopashift.ui.mapper

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.EfficiencyScore
import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.ui.render.DailyBarCategory
import com.dopashift.ui.render.continueEnabled
import com.dopashift.ui.render.formatPercent
import com.dopashift.ui.render.roadmapFraction
import com.dopashift.ui.state.AnchorHabitUi
import com.dopashift.ui.state.DailyBarUi
import com.dopashift.ui.state.GoalCardUi
import com.dopashift.ui.state.GoalDetailUiState
import com.dopashift.ui.state.OverlayUiState
import com.dopashift.ui.state.RuleRowUi
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.state.LimitType as UiLimitType
import com.dopashift.domain.entity.LimitType as DomainLimitType

/**
 * Domain → UI-state mappers for the DopaShift Kinetic Android screens
 * (spec `android-ui-upgrade`, task 4.2; design → "Data Models", "Screen Composables and their
 * UI-State contracts").
 *
 * These are **presentation-only** conversions from the parent domain models
 * (`GoalProfile`, `DailyTodoItem`, `HabitTrack`/`HabitCheckpoint`, `EfficiencyScore`,
 * `InterceptionRule`) into the `*Ui` / `*UiState` shapes defined by task 4.1. Following the design's
 * central principle (presentation-only, no parallel data path), this file:
 *
 *  - adds **no** domain models, repositories, Room entities, or endpoints;
 *  - never fabricates data — every field is derived from the supplied domain value or an explicit
 *    caller-supplied label;
 *  - turns opaque persistence identities into **string keys** (`id.toString()`) — the UI layer owns
 *    no identity;
 *  - delegates every input-varying render decision to the pure helpers extracted in task 2.3
 *    ([continueEnabled], [roadmapFraction], [formatPercent], and the [DailyBarCategory] mapping fed
 *    to `barColor`) so the mappers and the on-device rendering agree by construction;
 *  - sources any color exclusively from [com.dopashift.ui.theme.DopaShiftTokens]; the row/state
 *    types that carry a `Color` (e.g. [GoalCardUi.accentColor]) receive it from the caller, so this
 *    file inlines no raw literal (AUI-8.1).
 *
 * The `*Label` / `*Copy` parameters (greetings, counters, sub-captions, rule-type text) are passed
 * in already-localized by the caller (the existing view-models), keeping user-facing copy out of the
 * mapper and in string resources (AUI-8.6).
 */

// -------------------------------------------------------------------------------------------------
// LimitType (AUI-3.3)
// -------------------------------------------------------------------------------------------------

/**
 * Maps the domain enforcement enum to the presentation authoring-toggle enum.
 *
 *  - [DomainLimitType.Once] → [UiLimitType.ONCE]
 *  - [DomainLimitType.Repetitive] → [UiLimitType.REPETITIVE]
 *
 * The `when` is exhaustive so a new domain value forces an explicit UI decision here.
 */
fun DomainLimitType.toUi(): UiLimitType = when (this) {
    DomainLimitType.Once -> UiLimitType.ONCE
    DomainLimitType.Repetitive -> UiLimitType.REPETITIVE
}

// -------------------------------------------------------------------------------------------------
// DailyTodoItem → TodoRowUi (AUI-1.4, AUI-1.5, AUI-2.5)
// -------------------------------------------------------------------------------------------------

/**
 * Maps a [DailyTodoItem] into a Today's-Focus / overlay-pending [TodoRowUi].
 *
 * `done` carries the completion flag straight through so [com.dopashift.ui.render.completedRowStyle]
 * can dim + strike the row (Property 6). The domain id becomes an opaque string key.
 *
 * @param typeTag optional already-localized type-tag chip label.
 * @param estimatedTimeLabel optional already-formatted monospace estimate badge (e.g. `15m`).
 * @param deletable whether the row shows an inline delete control (checklist rows set this true).
 */
fun DailyTodoItem.toTodoRowUi(
    typeTag: String? = null,
    estimatedTimeLabel: String? = null,
    deletable: Boolean = false,
): TodoRowUi = TodoRowUi(
    id = id.toString(),
    text = text,
    done = isCompleted,
    typeTag = typeTag,
    estimatedTimeLabel = estimatedTimeLabel,
    deletable = deletable,
)

/**
 * Maps a goal-scoped [GoalChecklistItem] into a checklist [TodoRowUi] (AUI-5.3).
 *
 * Checklist rows are deletable by default (inline coral delete control) and carry no type tag or
 * estimate badge.
 */
fun GoalChecklistItem.toTodoRowUi(): TodoRowUi = TodoRowUi(
    id = id.toString(),
    text = text,
    done = isCompleted,
    typeTag = null,
    estimatedTimeLabel = null,
    deletable = true,
)

// -------------------------------------------------------------------------------------------------
// HabitCheckpoint / HabitTrack → AnchorHabitUi (AUI-1.2, AUI-2.4)
// -------------------------------------------------------------------------------------------------

/**
 * Maps a [HabitCheckpoint] within its [HabitTrack] into an anchor / rescue [AnchorHabitUi].
 *
 * `completed` is derived from the checkpoint status ([CheckpointStatus.COMPLETED]); the day label is
 * built from the checkpoint's `dayNumber` over the fixed 30-day roadmap span.
 *
 * @param dayLabel already-localized `Day N/30` copy for the checkpoint.
 * @param subtitle already-localized category / notes sub-line.
 * @param estimatedTimeLabel already-formatted monospace estimate pill (e.g. `10m`).
 * @param rewardPrompt optional reward-prompt line shown on the overlay rescue card; `null` on the
 *   Dashboard anchor.
 */
fun HabitCheckpoint.toAnchorHabitUi(
    dayLabel: String,
    subtitle: String,
    estimatedTimeLabel: String,
    rewardPrompt: String? = null,
): AnchorHabitUi = AnchorHabitUi(
    id = id.toString(),
    title = description,
    subtitle = subtitle,
    estimatedTimeLabel = estimatedTimeLabel,
    dayLabel = dayLabel,
    completed = status == CheckpointStatus.COMPLETED,
    rewardPrompt = rewardPrompt,
)

/**
 * Selects the current-day checkpoint of a [HabitTrack] and maps it to an [AnchorHabitUi], or returns
 * `null` when the track exposes no checkpoint for its `currentDay` (the screen then renders the
 * start-a-habit prompt instead of a blank card).
 *
 * @param dayLabel already-localized `Day N/30` copy for the current checkpoint.
 * @param subtitle already-localized category / notes sub-line.
 * @param estimatedTimeLabel already-formatted monospace estimate pill.
 * @param rewardPrompt optional overlay reward-prompt line.
 */
fun HabitTrack.currentAnchorHabitUiOrNull(
    dayLabel: String,
    subtitle: String,
    estimatedTimeLabel: String,
    rewardPrompt: String? = null,
): AnchorHabitUi? =
    checkpoints.firstOrNull { it.dayNumber == currentDay }?.toAnchorHabitUi(
        dayLabel = dayLabel,
        subtitle = subtitle,
        estimatedTimeLabel = estimatedTimeLabel,
        rewardPrompt = rewardPrompt,
    )

// -------------------------------------------------------------------------------------------------
// EfficiencyScore (AUI-1.1, AUI-6.4)
// -------------------------------------------------------------------------------------------------

/**
 * The integer efficiency percent for a [EfficiencyScore], suitable for the Completion Ring center as
 * an `Int` (AUI-1.1). A `null` `scorePercent` (no data computed yet) maps to `0`.
 *
 * The value is not formatted here; the ring formats it via [formatPercent]. Use
 * [EfficiencyScore.efficiencyPercentLabel] when a formatted string is needed instead.
 */
fun EfficiencyScore.efficiencyPercent(): Int = (scorePercent ?: 0).coerceIn(0, 100)

/**
 * The formatted integer efficiency percent string (e.g. `"72%"`) for a [EfficiencyScore], delegating
 * to the pure [formatPercent] helper so the string matches the ring center exactly (Property 5).
 * A `null` `scorePercent` renders `"0%"`.
 */
fun EfficiencyScore.efficiencyPercentLabel(): String = formatPercent(scorePercent ?: 0)

/**
 * Classifies a [EfficiencyScore] into the [DailyBarCategory] driving its Analytics bar color
 * (Property 4, AUI-6.4). The resulting category is fed to [com.dopashift.ui.render.barColor] for the
 * actual token color.
 *
 *  - `null` `scorePercent` (untracked / not yet computed) → [DailyBarCategory.NO_DATA].
 *  - a score at or above [productiveThreshold] → [DailyBarCategory.PRODUCTIVE].
 *  - a score below the threshold → [DailyBarCategory.DISTRACTING].
 *
 * @param productiveThreshold the inclusive score at/above which a tracked day counts as productive.
 */
fun EfficiencyScore.toDailyBarCategory(productiveThreshold: Int = DEFAULT_PRODUCTIVE_THRESHOLD): DailyBarCategory =
    when (val score = scorePercent) {
        null -> DailyBarCategory.NO_DATA
        else -> if (score >= productiveThreshold) DailyBarCategory.PRODUCTIVE else DailyBarCategory.DISTRACTING
    }

/**
 * Maps a [EfficiencyScore] into a single Analytics [DailyBarUi]. The bar height comes from the
 * clamped integer percent ([efficiencyPercent]) and the category from [toDailyBarCategory]; the
 * chart then colors the bar via [com.dopashift.ui.render.barColor].
 *
 * @param dayLabel already-localized x-axis label for the day (e.g. `Mon`, `12`).
 * @param productiveThreshold the productive/distracting cutoff passed to [toDailyBarCategory].
 */
fun EfficiencyScore.toDailyBarUi(
    dayLabel: String,
    productiveThreshold: Int = DEFAULT_PRODUCTIVE_THRESHOLD,
): DailyBarUi = DailyBarUi(
    dayLabel = dayLabel,
    scorePercent = efficiencyPercent(),
    category = toDailyBarCategory(productiveThreshold),
)

// -------------------------------------------------------------------------------------------------
// InterceptionRule → RuleRowUi (AUI-3.5)
// -------------------------------------------------------------------------------------------------

/**
 * Maps an [InterceptionRule] into an Active-Rules [RuleRowUi] (AUI-3.5).
 *
 * `paused` is derived from whether the rule is paused for [today] via [InterceptionRule.isPausedOn],
 * so the row's Pause / Resume control reflects the domain state. The rule-type indicator copy is
 * supplied already-localized by the caller (e.g. `Daily limit: 30m` or
 * `Repetitive: Every 30m • 3 loops`).
 *
 * @param appLabel already-resolved human-readable app label.
 * @param ruleTypeLabel already-localized rule-type indicator copy.
 * @param today the device local date used to evaluate the paused-for-today flag.
 */
fun InterceptionRule.toRuleRowUi(
    appLabel: String,
    ruleTypeLabel: String,
    today: java.time.LocalDate,
): RuleRowUi = RuleRowUi(
    id = id.toString(),
    appLabel = appLabel,
    packageName = appPackageName,
    ruleTypeLabel = ruleTypeLabel,
    paused = isPausedOn(today),
)

// -------------------------------------------------------------------------------------------------
// OverlayUiState (AUI-2.7)
// -------------------------------------------------------------------------------------------------

/**
 * Assembles an [OverlayUiState] from the state supplied by `screen-time-interception-engine`,
 * deriving [OverlayUiState.continueEnabled] from [cooldownSeconds] via the pure [continueEnabled]
 * helper (Property 1, AUI-2.7) — the overlay never runs its own timer.
 *
 * @param targetAppName the blocked app named in the overlay body copy.
 * @param cooldownSeconds the remaining cooldown from the interception engine.
 * @param rescueHabit the mapped rescue micro-habit checkpoint, or `null` when none is available.
 * @param pendingTasks up to three pending focus-task rows (AUI-2.5); trimmed to the first three.
 */
fun buildOverlayUiState(
    targetAppName: String,
    cooldownSeconds: Int,
    rescueHabit: AnchorHabitUi?,
    pendingTasks: List<TodoRowUi>,
): OverlayUiState = OverlayUiState(
    targetAppName = targetAppName,
    cooldownSeconds = cooldownSeconds,
    continueEnabled = continueEnabled(cooldownSeconds),
    rescueHabit = rescueHabit,
    pendingTasks = pendingTasks.take(MAX_OVERLAY_PENDING_TASKS),
)

// -------------------------------------------------------------------------------------------------
// GoalProfile / HabitTrack → GoalCardUi (AUI-1.6, AUI-4.1, AUI-4.2)
// -------------------------------------------------------------------------------------------------

/**
 * Maps a [GoalProfile] (with its optional [HabitTrack]) into a summary [GoalCardUi] for the Dashboard
 * grid and the Goals & Habits overview.
 *
 * The mini Completion Ring reads [completionPercent] (clamped to `0..100`); the dot-matrix streak row
 * reads the track's `currentDay` as completed days over the 30-day span when a [habitTrack] is
 * present, otherwise a zero streak. The accent color and all label copy are supplied by the caller so
 * this mapper stays free of literals (AUI-8.1) and un-externalized strings (AUI-8.6).
 *
 * @param completionPercent integer completion for the mini ring; clamped to `0..100`.
 * @param pendingTaskLabel already-localized subtitle counter (e.g. `1/2 tasks pending`).
 * @param accentColor the goal-icon / accent color, sourced from a token by the caller.
 * @param roadmapStatusLabel already-localized roadmap status line (e.g. `Day 6/30`).
 * @param habitTrack the goal's active roadmap track for the streak row; `null` when none is active.
 */
fun GoalProfile.toGoalCardUi(
    completionPercent: Int,
    pendingTaskLabel: String,
    accentColor: androidx.compose.ui.graphics.Color,
    roadmapStatusLabel: String,
    habitTrack: HabitTrack? = null,
): GoalCardUi = GoalCardUi(
    id = id.toString(),
    title = name,
    category = category,
    completionPercent = completionPercent.coerceIn(0, 100),
    pendingTaskLabel = pendingTaskLabel,
    keywords = keywords,
    accentColor = accentColor,
    streakCompletedDays = (habitTrack?.currentDay ?: 0).coerceIn(0, ROADMAP_TOTAL_DAYS),
    streakTotalDays = ROADMAP_TOTAL_DAYS,
    roadmapStatusLabel = roadmapStatusLabel,
)

// -------------------------------------------------------------------------------------------------
// GoalProfile / HabitTrack / checklist → GoalDetailUiState (AUI-5)
// -------------------------------------------------------------------------------------------------

/**
 * Maps a [GoalProfile] with its checklist and optional [HabitTrack] into a [GoalDetailUiState]
 * (AUI-5).
 *
 * The roadmap section reflects the track: `roadmapCompletedDays` (fed to the pure [roadmapFraction]
 * helper for the `completed/30` bar — Property 3, AUI-5.5) is the track's `currentDay`, and
 * `roadmapActive` is `true` while the track is unfinished. When [habitTrack] is `null` the roadmap is
 * inactive with zero progress. All display copy (`roadmapDayLabel`, `roadmapSubCaption`) is supplied
 * already-localized by the caller.
 *
 * @param checklist the goal's checklist items (mapped to deletable [TodoRowUi] rows).
 * @param roadmapDayLabel already-localized `Day N/30` counter copy.
 * @param roadmapSubCaption already-localized `M of 30 days completed` sub-caption.
 * @param habitTrack the goal's roadmap track; `null` when none is active.
 */
fun GoalProfile.toGoalDetailUiState(
    checklist: List<GoalChecklistItem>,
    roadmapDayLabel: String,
    roadmapSubCaption: String,
    habitTrack: HabitTrack? = null,
): GoalDetailUiState = GoalDetailUiState(
    goalId = id.toString(),
    title = name,
    category = category,
    keywords = keywords,
    checklist = checklist.map { it.toTodoRowUi() },
    roadmapDayLabel = roadmapDayLabel,
    roadmapActive = habitTrack?.let { !it.isFinished } ?: false,
    roadmapCompletedDays = (habitTrack?.currentDay ?: 0).coerceIn(0, ROADMAP_TOTAL_DAYS),
    roadmapSubCaption = roadmapSubCaption,
)

/**
 * The rendered `completed/30` roadmap fill fraction for a [HabitTrack], delegating to the pure
 * [roadmapFraction] helper (Property 3, AUI-5.5). Returns `0f` when the track is `null`.
 */
fun HabitTrack?.roadmapProgressFraction(): Float =
    roadmapFraction(this?.currentDay ?: 0)

/** The 30-day span of a habit roadmap; matches `RenderDerivations.ROADMAP_TOTAL_DAYS`. */
private const val ROADMAP_TOTAL_DAYS: Int = 30

/** Up to three pending focus tasks render on the intercept overlay (AUI-2.5). */
private const val MAX_OVERLAY_PENDING_TASKS: Int = 3

/**
 * Default inclusive score at/above which a tracked day is classified [DailyBarCategory.PRODUCTIVE]
 * (below it is [DailyBarCategory.DISTRACTING]). Callers may override per the analytics threshold.
 */
private const val DEFAULT_PRODUCTIVE_THRESHOLD: Int = 50
