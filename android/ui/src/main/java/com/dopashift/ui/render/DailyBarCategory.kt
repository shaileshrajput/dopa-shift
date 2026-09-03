package com.dopashift.ui.render

/**
 * The category a single daily-score bar falls into, driving its rendered color in the Analytics
 * daily-scores chart (spec `android-ui-upgrade`, AUI-6.4; Property 4 — Daily Bar Color Matches
 * Category).
 *
 * This is the minimal enum the pure [barColor] derivation needs so it can be property-tested off
 * device. Task 4.1 defines the full set of UI-state types (`DailyBarUi`, `AnalyticsUiState`, …);
 * when it lands it references this same enum rather than redeclaring it, keeping a single mapping
 * source for the chart color logic.
 *
 *  - [PRODUCTIVE]  — a day whose efficiency was productive; rendered in Momentum Teal.
 *  - [DISTRACTING] — a day whose distraction time exceeded its limits; rendered in Danger Coral.
 *  - [NO_DATA]     — an inactive / untracked day; rendered in the subtle no-data chart fill.
 */
enum class DailyBarCategory {
    PRODUCTIVE,
    DISTRACTING,
    NO_DATA,
}
