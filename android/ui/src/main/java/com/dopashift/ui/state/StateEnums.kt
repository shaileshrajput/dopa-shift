package com.dopashift.ui.state

/**
 * Presentation-only enums for the DopaShift Kinetic Android screens (spec `android-ui-upgrade`,
 * task 4.1).
 *
 * These carry no persistence identity and are pure UI-state discriminators. Two enums the design
 * references already live elsewhere in the `ui` module and are **reused rather than redeclared**:
 *
 *  - [com.dopashift.ui.render.DailyBarCategory] (`PRODUCTIVE`, `DISTRACTING`, `NO_DATA`) — the
 *    Analytics bar-color mapping source ([com.dopashift.ui.render.barColor]); [DailyBarUi]
 *    references it directly.
 *  - [com.dopashift.ui.adaptive.TopDestination] (`HOME`, `GOALS`, `ANALYTICS`, `SETTINGS`) — the
 *    four bottom-nav destinations already defined for the adaptive scaffold; the screens and nav
 *    bar reference that enum rather than a duplicate here.
 *
 * Only the two enums with no existing home — [LimitType] and [DateRange] — are defined below.
 */

/**
 * The App Limits authoring mode toggle (AUI-3.3).
 *
 *  - [ONCE] — a single daily cumulative limit ("Once (Daily Limit)").
 *  - [REPETITIVE] — a recurring interval interception ("Repetitive (Interval)"); only in this mode
 *    are the interval preset chips shown (AUI-3.4, Property 2).
 *
 * This is the *presentation* discriminator; the domain enforcement enum
 * (`com.dopashift.domain.entity.LimitType`) stays separate and is mapped to this by task 4.2.
 */
enum class LimitType {
    ONCE,
    REPETITIVE,
}

/**
 * The Analytics date-range filter (AUI-6.1).
 *
 *  - [LAST_7] — last 7 days.
 *  - [LAST_30] — last 30 days (default range).
 *  - [LAST_90] — last 90 days.
 */
enum class DateRange {
    LAST_7,
    LAST_30,
    LAST_90,
}
