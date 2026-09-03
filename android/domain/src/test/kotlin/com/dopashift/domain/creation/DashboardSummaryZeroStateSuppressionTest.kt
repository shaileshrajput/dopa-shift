package com.dopashift.domain.creation

// Feature: dashboard-quick-create
// Tags: DQC-5.7

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

/**
 * Unit tests for [DashboardSummary.suppressesZeroStatePrompt] (tasks.md 11.2, DQC-5.7).
 *
 * When onboarding completed normally it produced a Goal_Profile, so the Dashboard SHALL NOT
 * re-present the Zero_State goal prompt (DQC-5.7). A user who skipped or never started onboarding
 * created nothing during it, so every empty-section prompt remains visible (DQC-5.2, DQC-5.6).
 */
class DashboardSummaryZeroStateSuppressionTest : StringSpec({

    val userId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000bb")

    fun summary(status: OnboardingStatus) =
        DashboardSummary(
            userId = userId,
            activeGoalCount = 0,
            todayTodoCount = 0,
            activeHabitCount = 0,
            onboardingStatus = status
        )

    "completed onboarding suppresses the Zero_State goal prompt" {
        summary(OnboardingStatus.COMPLETED)
            .suppressesZeroStatePrompt(DashboardSection.GOALS) shouldBe true
    }

    "completed onboarding does not suppress to-do or habit prompts" {
        val s = summary(OnboardingStatus.COMPLETED)
        s.suppressesZeroStatePrompt(DashboardSection.TODOS) shouldBe false
        s.suppressesZeroStatePrompt(DashboardSection.HABITS) shouldBe false
    }

    "skipped onboarding suppresses no prompt so every capability stays discoverable" {
        val s = summary(OnboardingStatus.SKIPPED)
        s.suppressesZeroStatePrompt(DashboardSection.GOALS) shouldBe false
        s.suppressesZeroStatePrompt(DashboardSection.TODOS) shouldBe false
        s.suppressesZeroStatePrompt(DashboardSection.HABITS) shouldBe false
    }

    "not-started onboarding suppresses no prompt" {
        val s = summary(OnboardingStatus.NOT_STARTED)
        DashboardSection.entries.forEach { section ->
            s.suppressesZeroStatePrompt(section) shouldBe false
        }
    }
})
