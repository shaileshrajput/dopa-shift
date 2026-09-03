package com.dopashift.domain.presentation

// Feature: dynamic-ui-experience, Property 5: Streak celebration fires only on unseen 7-day multiples

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Property 5: Streak celebration fires only on unseen 7-day multiples.
 *
 * **Validates: Requirements DUX-2.4**
 *
 * For any sequence of streak values, folding each value through
 * [StreakCelebrationPolicy.evaluate] seeded with [MilestoneMemory.EMPTY]:
 *  - a celebration is triggered for a value *if and only if* that value is a positive
 *    multiple of 7 that has not already been celebrated earlier in the sequence
 *    (the once-per-milestone guarantee), and
 *  - every triggered celebration reports a `durationMillis` of at most 1500 ms.
 *
 * Kotest property testing with a minimum of 100 iterations. The generators intentionally
 * bias toward small integers and include exact multiples of 7 so milestone values (and
 * their repeats) actually appear in the sampled sequences rather than being vanishingly
 * rare among arbitrary ints.
 */
class StreakCelebrationFiringPropertyTest : StringSpec({

    val interval = StreakCelebrationPolicy.MILESTONE_INTERVAL
    val maxDuration = StreakCelebrationPolicy.MAX_CELEBRATION_DURATION_MILLIS
    val config = PropTestConfig(iterations = 200)

    // Values in a range that comfortably straddles zero/negatives and covers many
    // multiples of 7, so both firing and non-firing cases are well represented.
    val streakValue: Arb<Int> = Arb.int(-14..70)
    val streakSequence: Arb<List<Int>> = Arb.list(streakValue, range = 0..40)

    "a celebration fires iff the value is an unseen positive multiple of 7, and duration <= 1500ms" {
        checkAll(config, streakSequence) { sequence ->
            // Reference model computed independently of the policy under test: track which
            // positive multiples of 7 have already been celebrated as we walk the sequence.
            val seenMilestones = mutableSetOf<Int>()
            var memory = MilestoneMemory.EMPTY

            for (value in sequence) {
                val expectedFire = value > 0 && value % interval == 0 && value !in seenMilestones

                val decision = StreakCelebrationPolicy.evaluate(value, memory)

                // Firing condition matches the independent reference model.
                decision.triggered shouldBe expectedFire

                // Every triggered celebration is bounded to <= 1500 ms; a non-fire has 0 ms.
                if (decision.triggered) {
                    (decision.durationMillis <= maxDuration) shouldBe true
                    (decision.durationMillis in 0..maxDuration) shouldBe true
                } else {
                    decision.durationMillis shouldBe 0
                }

                // Advance both the reference model and the policy's threaded memory.
                if (expectedFire) {
                    seenMilestones += value
                }
                memory = decision.updatedMemory
            }
        }
    }

    "each distinct milestone value fires at most once across a whole sequence" {
        checkAll(config, streakSequence) { sequence ->
            var memory = MilestoneMemory.EMPTY
            val fireCounts = mutableMapOf<Int, Int>()

            for (value in sequence) {
                val decision = StreakCelebrationPolicy.evaluate(value, memory)
                if (decision.triggered) {
                    fireCounts[value] = (fireCounts[value] ?: 0) + 1
                }
                memory = decision.updatedMemory
            }

            // No milestone value ever fired more than once.
            fireCounts.values.all { it <= 1 } shouldBe true
        }
    }
})
