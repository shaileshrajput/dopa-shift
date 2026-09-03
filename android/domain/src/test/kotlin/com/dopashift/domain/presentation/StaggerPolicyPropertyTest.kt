package com.dopashift.domain.presentation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property-based test for the capped staggered-entry delay rule.
 *
 * Feature: dynamic-ui-experience, Property 4: Staggered entry delay follows the capped rule
 * Validates: Requirements DUX-2.6
 *
 * For any non-negative item index `i` in a first-render list, the computed entry delay equals
 * `i * 40 ms` when `i < 8`, and `0 ms` when `i >= 8`, so no list produces a stagger sequence
 * longer than the first eight items.
 *
 * Uses Kotest property testing (min 100 iterations) per the project tech stack, matching the
 * sibling `domain` presentation property tests.
 */
class StaggerPolicyPropertyTest : StringSpec({

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    "Property 4 (DUX-2.6): entry delay follows the capped rule for any non-negative index" {
        // Full non-negative Int range so the cap boundary and very large indices are exercised.
        checkAll(config, Arb.int(min = 0, max = Int.MAX_VALUE)) { index ->
            val expected =
                if (index < StaggerPolicy.STAGGER_CAP) {
                    index * StaggerPolicy.STAGGER_STEP_MILLIS
                } else {
                    0
                }
            StaggerPolicy.entryDelayMillis(index) shouldBe expected
        }
    }

    "Property 4 (DUX-2.6): indices below the cap get index x 40 ms" {
        checkAll(config, Arb.int(min = 0, max = StaggerPolicy.STAGGER_CAP - 1)) { index ->
            StaggerPolicy.entryDelayMillis(index) shouldBe index * StaggerPolicy.STAGGER_STEP_MILLIS
        }
    }

    "Property 4 (DUX-2.6): indices at or beyond the cap get 0 ms" {
        checkAll(config, Arb.int(min = StaggerPolicy.STAGGER_CAP, max = Int.MAX_VALUE)) { index ->
            StaggerPolicy.entryDelayMillis(index) shouldBe 0
        }
    }
})
