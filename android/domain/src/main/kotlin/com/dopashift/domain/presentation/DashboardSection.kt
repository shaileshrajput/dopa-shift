package com.dopashift.domain.presentation

/**
 * Presentation-agnostic Dashboard section identity, in fixed display priority order
 * (DUX-3 AC1). Pure Kotlin — no Android/Compose dependencies — so it is fully
 * unit- and property-testable under the JVM.
 */
enum class DashboardSectionType {
    /** (a) unchecked habit checkpoint for today */
    HABIT_CHECKPOINT_DUE,

    /** (b) overdue DailyTodoItems */
    OVERDUE_TODOS,

    /** (c) today's remaining DailyTodoItems */
    TODAY_TODOS,

    /** (d) active goals grid */
    ACTIVE_GOALS,

    /** (e) efficiency trend */
    EFFICIENCY_TREND,

    /** (f) recent activity feed */
    ACTIVITY_FEED
}

/**
 * Presentation-agnostic snapshot of what content each Dashboard section currently has.
 *
 * @property type the section this snapshot describes
 * @property itemCount number of items the section currently holds; `0` means the section
 *   is empty and collapses to an inline prompt (DUX-3 AC1).
 */
data class DashboardSectionInput(
    val type: DashboardSectionType,
    val itemCount: Int
) {
    init {
        require(itemCount >= 0) { "itemCount must be non-negative, was $itemCount" }
    }
}

/**
 * A Dashboard section resolved into its render position.
 *
 * @property type the section identity
 * @property isCollapsedPrompt `true` when the section is empty (`itemCount == 0`) and should
 *   render as a collapsed inline prompt rather than full content (DUX-3 AC1).
 */
data class OrderedSection(
    val type: DashboardSectionType,
    val isCollapsedPrompt: Boolean
)
