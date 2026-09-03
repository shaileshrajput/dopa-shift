package com.dopashift.domain.creation

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.util.UUID

/**
 * Property-based test for Dashboard state resolution from entity counts.
 *
 * Feature: dashboard-quick-create, Property 16: Dashboard State Resolution From Counts
 * Validates: Requirements 5.3, 5.6 (also satisfies DQC-7.3)
 *
 * For any combination of active-goal, today-to-do, and active-habit counts:
 * - if all three are zero the Dashboard renders Zero_State (whole-screen prompts);
 * - if at least one is non-zero the Dashboard is never in Zero_State — each section
 *   renders POPULATED when its count is > 0 and INLINE_PROMPT otherwise, so a
 *   whole-screen empty state is never rendered when any entity type has data.
 *
 * Uses Kotest property testing (min 100 iterations) per the project tech stack,
 * matching the sibling `domain` property tests.
 */
@OptIn(ExperimentalKotest::class)
class DashboardStateResolutionPropertyTest : StringSpec({

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    // Non-negative counts, constrained to the input space the constructor accepts.
    // The upper bound is arbitrary but exercises zero, one, and many.
    val counts = Arb.int(min = 0, max = 10_000)
    val onboarding = Arb.enum<OnboardingStatus>()

    fun summary(goals: Int, todos: Int, habits: Int, status: OnboardingStatus) =
        DashboardSummary(
            userId = UUID.randomUUID(),
            activeGoalCount = goals,
            todayTodoCount = todos,
            activeHabitCount = habits,
            onboardingStatus = status
        )

    "Property 16: isZeroState is true iff all three counts are zero" {
        checkAll(config, counts, counts, counts, onboarding) { goals, todos, habits, status ->
            val expectedZero = goals == 0 && todos == 0 && habits == 0
            summary(goals, todos, habits, status).isZeroState shouldBe expectedZero
        }
    }

    "Property 16: each sectionState is POPULATED iff its count is > 0" {
        checkAll(config, counts, counts, counts, onboarding) { goals, todos, habits, status ->
            val s = summary(goals, todos, habits, status)

            s.sectionState(DashboardSection.GOALS) shouldBe
                if (goals > 0) SectionRenderState.POPULATED else SectionRenderState.INLINE_PROMPT
            s.sectionState(DashboardSection.TODOS) shouldBe
                if (todos > 0) SectionRenderState.POPULATED else SectionRenderState.INLINE_PROMPT
            s.sectionState(DashboardSection.HABITS) shouldBe
                if (habits > 0) SectionRenderState.POPULATED else SectionRenderState.INLINE_PROMPT
        }
    }

    "Property 16: whenever any count is non-zero, the Dashboard is never whole-screen empty" {
        checkAll(config, counts, counts, counts, onboarding) { goals, todos, habits, status ->
            if (goals > 0 || todos > 0 || habits > 0) {
                summary(goals, todos, habits, status).isZeroState shouldBe false
            }
        }
    }
})
