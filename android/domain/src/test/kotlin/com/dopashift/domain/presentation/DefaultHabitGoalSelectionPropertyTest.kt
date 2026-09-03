package com.dopashift.domain.presentation

import com.dopashift.domain.entity.GoalProfile
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Property-based test for the default habit goal selection rule.
 *
 * Feature: dashboard-quick-create, Property 15: Default Habit Goal Selection Is Most-Recently-Updated
 * Validates: Requirements 3.1
 *
 * For any non-empty set of active [GoalProfile]s, opening "New Habit" defaults the goal
 * selection to the goal with the most recent `updatedAt`. The subject under test is the pure
 * [DefaultHabitGoalSelector.selectDefault], which the `ui` habit sheet and the interception
 * overlay both call (no parallel implementation).
 *
 * The properties assert that when at least one active goal exists:
 * - the selection is non-null and is itself active,
 * - no other active goal has a strictly later `updatedAt` (it is a genuine maximum),
 * - selection is independent of the order goals are supplied in (deterministic).
 * And when no active goal exists, the selection is `null` (the sheet then begins with
 * Inline_Goal_Capture per DQC-3.2).
 *
 * Uses Kotest property testing (min 100 iterations) per the project tech stack, matching the
 * sibling `domain` property tests.
 */
class DefaultHabitGoalSelectionPropertyTest : StringSpec({

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    // A bounded epoch-second range so updatedAt values collide often enough to exercise ties,
    // while still spanning a wide window.
    val updatedAts: Arb<Instant> =
        Arb.long(min = 0L, max = 5_000L).map { Instant.ofEpochSecond(it) }

    // Valid goal fields; names/categories/keywords satisfy GoalProfile's init bounds.
    val names: Arb<String> = Arb.string(minSize = 1, maxSize = 40).map { it.ifBlank { "goal" } }
    val categories: Arb<String> = Arb.string(minSize = 1, maxSize = 30).map { it.ifBlank { "cat" } }

    val goals: Arb<GoalProfile> = Arb.bind(
        Arb.uuid(),
        names,
        categories,
        updatedAts,
        Arb.boolean()
    ) { id, name, category, updatedAt, isActive ->
        GoalProfile(
            id = id,
            userId = FIXED_USER,
            name = name,
            category = category,
            keywords = listOf("kw"),
            createdAt = Instant.EPOCH,
            updatedAt = updatedAt,
            isActive = isActive
        )
    }

    "Property 15: with >= 1 active goal, the default is active and has the maximum updatedAt" {
        // Build lists that always contain at least one active goal, so a default must exist.
        val nonEmptyActiveLists: Arb<List<GoalProfile>> = Arb.bind(
            goals.map { it.copy(isActive = true) }, // guaranteed active anchor
            Arb.list(goals, 0..8)
        ) { anchor, rest -> rest + anchor }

        checkAll(config, nonEmptyActiveLists) { input ->
            val selected = DefaultHabitGoalSelector.selectDefault(input)

            selected.shouldNotBeNull()
            selected.isActive shouldBe true

            val maxActiveUpdatedAt = input.filter { it.isActive }.maxOf { it.updatedAt }
            selected.updatedAt shouldBe maxActiveUpdatedAt
            // No active goal is strictly newer than the selected one.
            input.filter { it.isActive }.all { !it.updatedAt.isAfter(selected.updatedAt) } shouldBe true
        }
    }

    "Property 15: selection is independent of input ordering (deterministic)" {
        val nonEmptyActiveLists: Arb<List<GoalProfile>> = Arb.bind(
            goals.map { it.copy(isActive = true) },
            Arb.list(goals, 0..8)
        ) { anchor, rest -> rest + anchor }

        checkAll(config, nonEmptyActiveLists) { input ->
            val forward = DefaultHabitGoalSelector.selectDefault(input)
            val reversed = DefaultHabitGoalSelector.selectDefault(input.reversed())
            val shuffled = DefaultHabitGoalSelector.selectDefault(input.shuffled())

            forward shouldBe reversed
            forward shouldBe shuffled
        }
    }

    "Property 15: an inactive goal is never selected even when it is the newest overall" {
        checkAll(config, Arb.list(goals, 1..10)) { input ->
            val selected = DefaultHabitGoalSelector.selectDefault(input)
            val anyActive = input.any { it.isActive }

            if (anyActive) {
                selected.shouldNotBeNull()
                selected.isActive shouldBe true
            } else {
                selected shouldBe null
            }
        }
    }

    // Explicit example: the freshest active goal wins over an even-fresher inactive one.
    "example: newest active goal is chosen over a newer inactive goal" {
        val older = goalAt(Instant.ofEpochSecond(100), isActive = true)
        val newestActive = goalAt(Instant.ofEpochSecond(300), isActive = true)
        val newestOverallInactive = goalAt(Instant.ofEpochSecond(999), isActive = false)

        val selected = DefaultHabitGoalSelector.selectDefault(
            listOf(older, newestOverallInactive, newestActive)
        )

        selected shouldBe newestActive
    }

    "example: an all-inactive set yields no default" {
        val input = listOf(
            goalAt(Instant.ofEpochSecond(1), isActive = false),
            goalAt(Instant.ofEpochSecond(2), isActive = false)
        )
        DefaultHabitGoalSelector.selectDefault(input) shouldBe null
    }
})

private val FIXED_USER: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

private fun goalAt(updatedAt: Instant, isActive: Boolean): GoalProfile =
    GoalProfile(
        id = UUID.randomUUID(),
        userId = FIXED_USER,
        name = "goal-${updatedAt.epochSecond}",
        category = "cat",
        keywords = listOf("kw"),
        createdAt = Instant.EPOCH,
        updatedAt = updatedAt,
        isActive = isActive
    )
