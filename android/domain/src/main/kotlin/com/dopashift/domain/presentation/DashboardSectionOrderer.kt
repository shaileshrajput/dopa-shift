package com.dopashift.domain.presentation

/**
 * Resolves the Dashboard's section layout order.
 *
 * Pure, total, and deterministic — no Android/Compose dependency — so it runs under fast
 * JVM tests (DUX-3 AC1, AC2).
 */
object DashboardSectionOrderer {

    /**
     * The fixed display priority of Dashboard sections (DUX-3 AC1, a→f). The output of
     * [order] always follows this exact sequence regardless of input order or membership.
     */
    private val PRIORITY: List<DashboardSectionType> = listOf(
        DashboardSectionType.HABIT_CHECKPOINT_DUE, // (a)
        DashboardSectionType.OVERDUE_TODOS,        // (b)
        DashboardSectionType.TODAY_TODOS,          // (c)
        DashboardSectionType.ACTIVE_GOALS,         // (d)
        DashboardSectionType.EFFICIENCY_TREND,     // (e)
        DashboardSectionType.ACTIVITY_FEED         // (f)
    )

    /**
     * Returns all six [DashboardSectionType] values exactly once, in the fixed priority
     * order of DUX-3 AC1, marking each section as a collapsed inline prompt when it is
     * empty and full content otherwise.
     *
     * A section is treated as empty (`isCollapsedPrompt == true`) when it is absent from
     * [inputs] or when its `itemCount` is `0`. If [inputs] contains duplicate entries for a
     * type, the entry with the greatest `itemCount` wins so a non-empty snapshot is never
     * masked by an empty duplicate; ordering is unaffected either way.
     *
     * The function is pure, total, and deterministic: identical input always yields
     * identical output (same sequence, same collapse flags) — DUX-3 AC2.
     */
    fun order(inputs: List<DashboardSectionInput>): List<OrderedSection> {
        val countsByType: Map<DashboardSectionType, Int> = inputs
            .groupBy { it.type }
            .mapValues { (_, entries) -> entries.maxOf { it.itemCount } }

        return PRIORITY.map { type ->
            val itemCount = countsByType[type] ?: 0
            OrderedSection(
                type = type,
                isCollapsedPrompt = itemCount == 0
            )
        }
    }
}
