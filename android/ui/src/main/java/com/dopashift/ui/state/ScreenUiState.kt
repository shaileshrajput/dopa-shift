package com.dopashift.ui.state

/**
 * Screen-level UI-state contracts for the seven DopaShift Kinetic Android screens (spec
 * `android-ui-upgrade`, task 4.1; design → "Screen Composables and their UI-State contracts").
 *
 * Each `*UiState` is the **shape of the rendered state** for one screen composable — a
 * presentation-only view model built entirely from primitives, enums, and the row/item types in
 * this package. Per the design's central principle (presentation-only, no parallel data path),
 * these types:
 *
 *  - hold **no persistence identity** and emit **no** `Change_Log` events;
 *  - import **no** Room / domain framework types (the string `id`s on the row types are opaque keys
 *    rendered from domain ids, not identities owned here);
 *  - reference existing domain models only insofar as the existing view-models **map** those models
 *    into these shapes (task 4.2).
 *
 * The reused enums [com.dopashift.ui.adaptive.TopDestination] and
 * [com.dopashift.ui.render.DailyBarCategory] are referenced (not redeclared) per task 4.1.
 */

/**
 * Home Dashboard state (AUI-1).
 *
 * @property greeting the greeting headline.
 * @property subCaption the calming sub-caption beneath the greeting.
 * @property streakDays the current streak length rendered in the amber streak pill.
 * @property efficiencyPercent the daily `Efficiency_Score` as an integer percent for the 64dp
 *   Completion Ring (`0..100`, Property 5).
 * @property anchorHabit the current-day `Habit_Track` anchor checkpoint, or `null` when none is
 *   active (renders the start-a-habit prompt instead).
 * @property focusTodos the Today's Focus to-do rows.
 * @property activeGoals the active-goal grid cards.
 */
data class DashboardUiState(
    val greeting: String,
    val subCaption: String,
    val streakDays: Int,
    val efficiencyPercent: Int,
    val anchorHabit: AnchorHabitUi?,
    val focusTodos: List<TodoRowUi>,
    val activeGoals: List<GoalCardUi>,
)

/**
 * Intercept Overlay state (AUI-2).
 *
 * @property targetAppName the blocked app named in the overlay body copy.
 * @property cooldownSeconds the remaining cooldown supplied by `screen-time-interception-engine`;
 *   the composable renders this value and does not run its own timer.
 * @property continueEnabled whether the secondary "Continue to app" button is enabled; equals
 *   `cooldownSeconds == 0` (Property 1, AUI-2.7) — derived by the mapper via
 *   [com.dopashift.ui.render.continueEnabled].
 * @property rescueHabit the rescue micro-habit card checkpoint, or `null` when none is available.
 * @property pendingTasks up to three pending focus tasks (AUI-2.5).
 */
data class OverlayUiState(
    val targetAppName: String,
    val cooldownSeconds: Int,
    val continueEnabled: Boolean,
    val rescueHabit: AnchorHabitUi?,
    val pendingTasks: List<TodoRowUi>,
)

/**
 * App Limits & Rules authoring state (AUI-3).
 *
 * @property query the current search-bar text filtering [installedApps].
 * @property installedApps the filtered installed-app rows.
 * @property limitType the selected authoring mode; drives interval-preset visibility (Property 2).
 * @property intervalPreset the chosen repetitive interval preset, shown **only** when
 *   [limitType] is [LimitType.REPETITIVE]; `null` otherwise (AUI-3.4).
 * @property intervalPresets the available interval presets rendered in [LimitType.REPETITIVE] mode.
 * @property perAppLimits the per-package daily limit (whole minutes) for each selected app; the
 *   daily-limit input in [LimitType.ONCE] mode renders and edits these values (AUI-3.2).
 * @property limitErrorPackage the package whose daily-limit input is currently showing a range
 *   error (out of the 1..480 whole-minute range), or `null` when no error is shown.
 * @property customIntervalMinutes the free-selection repetitive interval (whole minutes) typed into
 *   the "Custom interval" field, shown alongside the presets in [LimitType.REPETITIVE] mode; `null`
 *   when the field is empty.
 * @property intervalError whether the custom-interval input is currently showing a range error
 *   (out of the 1..120 whole-minute range).
 * @property activeRules the active interception-rule rows.
 */
data class AppLimitsUiState(
    val query: String,
    val installedApps: List<InstalledAppUi>,
    val limitType: LimitType,
    val intervalPreset: IntervalPreset?,
    val intervalPresets: List<IntervalPreset>,
    val perAppLimits: Map<String, Int> = emptyMap(),
    val limitErrorPackage: String? = null,
    val customIntervalMinutes: Int? = null,
    val intervalError: Boolean = false,
    val activeRules: List<RuleRowUi>,
)

/**
 * Goals & Habits overview state (AUI-4).
 *
 * @property goals the goal summary cards.
 */
data class GoalsUiState(
    val goals: List<GoalCardUi>,
)

/**
 * Goal Detail & 30-day Roadmap state (AUI-5).
 *
 * @property goalId opaque stable key for the goal being viewed.
 * @property title the goal name shown in the top app bar.
 * @property category the goal category chip.
 * @property keywords the keyword chips.
 * @property checklist the interactive checklist rows (56dp, with inline delete).
 * @property roadmapDayLabel the current-day counter copy (e.g. `Day 6/30`).
 * @property roadmapActive whether the `Active` status badge is shown.
 * @property roadmapCompletedDays completed days for the `completed/30` progress bar (`0..30`,
 *   Property 3); the fraction is derived by [com.dopashift.ui.render.roadmapFraction].
 * @property roadmapSubCaption the `M of 30 days completed` sub-caption.
 */
data class GoalDetailUiState(
    val goalId: String,
    val title: String,
    val category: String,
    val keywords: List<String>,
    val checklist: List<TodoRowUi>,
    val roadmapDayLabel: String,
    val roadmapActive: Boolean,
    val roadmapCompletedDays: Int,
    val roadmapSubCaption: String,
)

/**
 * Analytics & Efficiency state (AUI-6).
 *
 * @property range the selected date-range filter.
 * @property offlineOrPartial whether the offline / partial-data warning banner is shown (AUI-6.2).
 * @property summary the 4-column summary metric grid values.
 * @property dailyBars the daily-score bars, each carrying its category for color mapping
 *   (Property 4).
 * @property categoryUsage the horizontal category usage bars.
 */
data class AnalyticsUiState(
    val range: DateRange,
    val offlineOrPartial: Boolean,
    val summary: SummaryMetrics,
    val dailyBars: List<DailyBarUi>,
    val categoryUsage: List<UsageBarUi>,
)

/**
 * Settings & Profile state (AUI-7).
 *
 * @property avatar the profile avatar (photo or monogram fallback).
 * @property displayName the editable display-name value.
 * @property accentSwatches the 12 accent swatches in the picker.
 * @property selectedAccent the currently selected swatch, whose seed propagates app-wide
 *   (Property 7, AUI-7.4).
 */
data class SettingsUiState(
    val avatar: AvatarUi,
    val fullName: String,
    val displayName: String,
    val accentSwatches: List<AccentSwatch>,
    val selectedAccent: AccentSwatch,
)
