package com.dopashift.domain.presentation

// Feature: dynamic-ui-experience, Property 1: Canonical Dashboard section ordering

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Property 1: Canonical Dashboard section ordering.
 *
 * **Validates: Requirements DUX-3.1, DUX-7.7**
 *
 * For any set of [DashboardSectionInput] values (any counts, in any input order),
 * [DashboardSectionOrderer.order]:
 *  - returns all six [DashboardSectionType] values exactly once, in the fixed priority
 *    order HABIT_CHECKPOINT_DUE → OVERDUE_TODOS → TODAY_TODOS → ACTIVE_GOALS →
 *    EFFICIENCY_TREND → ACTIVITY_FEED, regardless of the input's order or membership, and
 *  - marks every section whose effective `itemCount` is 0 as a collapsed inline prompt
 *    (`isCollapsedPrompt == true`) and every non-empty section as not collapsed.
 *
 * Kotest property testing with a minimum of 100 iterations over shuffled section-input
 * lists with arbitrary counts. Generators intentionally produce lists that may omit,
 * duplicate, and reorder section types so both the "all six exactly once" and the
 * collapse-flag behaviour are exercised across sparse and dense inputs.
 */
class CanonicalSectionOrderingPropertyTest : StringSpec({

    val config = PropTestConfig(iterations = 200)

    // The canonical priority order the orderer must always produce.
    val expectedOrder: List<DashboardSectionType> = listOf(
        DashboardSectionType.HABIT_CHECKPOINT_DUE,
        DashboardSectionType.OVERDUE_TODOS,
        DashboardSectionType.TODAY_TODOS,
        DashboardSectionType.ACTIVE_GOALS,
        DashboardSectionType.EFFICIENCY_TREND,
        DashboardSectionType.ACTIVITY_FEED
    )

    // Arbitrary section input: any type, non-negative count (including 0 for empties),
    // biased toward small values where the collapse boundary (itemCount == 0) matters.
    val sectionInput: Arb<DashboardSectionInput> =
        Arb.bind(Arb.enum<DashboardSectionType>(), Arb.int(0..50)) { type, count ->
            DashboardSectionInput(type, count)
        }

    // Lists that may omit, duplicate, and reorder section types (arbitrary shuffled input).
    val inputList: Arb<List<DashboardSectionInput>> =
        Arb.list(sectionInput, range = 0..30)

    "order returns all six types exactly once in the fixed priority order" {
        checkAll(config, inputList) { inputs ->
            val result = DashboardSectionOrderer.order(inputs)

            // Exactly six sections, each type present exactly once, in canonical order.
            result.map { it.type } shouldBe expectedOrder
        }
    }

    "each section is collapsed iff its effective itemCount is zero" {
        checkAll(config, inputList) { inputs ->
            val result = DashboardSectionOrderer.order(inputs)

            // Reference model computed independently of the orderer: the greatest itemCount
            // seen for a type (absent types count as 0), matching the duplicate-resolution
            // rule that a non-empty snapshot is never masked by an empty duplicate.
            val effectiveCount: (DashboardSectionType) -> Int = { type ->
                inputs.filter { it.type == type }.maxOfOrNull { it.itemCount } ?: 0
            }

            for (section in result) {
                val expectedCollapsed = effectiveCount(section.type) == 0
                section.isCollapsedPrompt shouldBe expectedCollapsed
            }
        }
    }
})
