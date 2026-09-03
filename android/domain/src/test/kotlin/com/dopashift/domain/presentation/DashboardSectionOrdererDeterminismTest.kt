package com.dopashift.domain.presentation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Property-based test for [DashboardSectionOrderer.order] determinism.
 *
 * Feature: dynamic-ui-experience, Property 2: Section ordering is deterministic
 * Validates: Requirements DUX-3.2, DUX-7.7
 *
 * For any [DashboardSectionInput] list, invoking [DashboardSectionOrderer.order] repeatedly
 * on identical input always produces identical output (same sequence, same collapse flags).
 *
 * This is a separate spec from the canonical-ordering property test (task 2.2) to avoid
 * collision; it exercises determinism specifically (DUX-3.2).
 */
class DashboardSectionOrdererDeterminismTest : StringSpec({

    // Runs a minimum of 100 generated cases per the design's Testing Strategy.
    val config = PropTestConfig(iterations = 100)

    // Smart generator: a section input with any of the six types and a non-negative
    // itemCount (bounded to keep values realistic; 0 exercises the collapsed-prompt path).
    val sectionInput = arbitrary { rs ->
        val type = Arb.enum<DashboardSectionType>().bind()
        val count = Arb.int(min = 0, max = 500).bind()
        DashboardSectionInput(type = type, itemCount = count)
    }

    // Lists range from empty (all sections collapse) up to more entries than there are
    // section types, so duplicates and every ordering are exercised.
    val sectionInputList = Arb.list(sectionInput, 0..12)

    "order is deterministic: identical input yields identical output" {
        checkAll(config, sectionInputList) { inputs ->
            val first = DashboardSectionOrderer.order(inputs)
            val second = DashboardSectionOrderer.order(inputs)

            // Same sequence AND same collapse flags across repeated invocations.
            second shouldBe first
        }
    }

    "order is deterministic across many repeated invocations on the same input" {
        checkAll(config, sectionInputList) { inputs ->
            val baseline = DashboardSectionOrderer.order(inputs)

            repeat(5) {
                DashboardSectionOrderer.order(inputs) shouldBe baseline
            }
        }
    }
})
