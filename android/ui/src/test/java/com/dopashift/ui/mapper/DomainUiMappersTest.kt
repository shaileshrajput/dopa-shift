package com.dopashift.ui.mapper

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.EfficiencyScore
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType as DomainLimitType
import com.dopashift.ui.render.DailyBarCategory
import com.dopashift.ui.render.continueEnabled
import com.dopashift.ui.render.roadmapFraction
import com.dopashift.ui.state.LimitType as UiLimitType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for the derivation-bearing domain → UI mappers (spec `android-ui-upgrade`, task 4.2).
 *
 * These focus on the mappers that make a render decision via the pure task-2.3 helpers —
 * [buildOverlayUiState] (continue gating), [HabitTrack.roadmapProgressFraction] /
 * [GoalProfile.toGoalDetailUiState] (roadmap fraction), and [EfficiencyScore.toDailyBarCategory]
 * (bar category). They assert the mappers stay in lockstep with [continueEnabled], [roadmapFraction]
 * and the [DailyBarCategory] mapping rather than re-deriving the rules.
 */
class DomainUiMappersTest : StringSpec({

    fun habitTrack(currentDay: Int, isFinished: Boolean = false, withCurrentCheckpoint: Boolean = false): HabitTrack {
        val trackId = UUID.randomUUID()
        val checkpoints = if (withCurrentCheckpoint) {
            listOf(
                HabitCheckpoint(
                    id = UUID.randomUUID(),
                    habitTrackId = trackId,
                    dayNumber = currentDay,
                    description = "Read 10 pages",
                    status = CheckpointStatus.COMPLETED,
                ),
            )
        } else {
            emptyList()
        }
        return HabitTrack(
            id = trackId,
            goalId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            startDate = LocalDate.of(2026, 1, 1),
            currentDay = currentDay,
            isFinished = isFinished,
            checkpoints = checkpoints,
        )
    }

    fun efficiencyScore(scorePercent: Int?): EfficiencyScore = EfficiencyScore(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        date = LocalDate.of(2026, 2, 3),
        productiveSeconds = 0,
        totalTrackedSeconds = 0,
        scorePercent = scorePercent,
        computedAt = Instant.EPOCH,
    )

    // --- LimitType mapping (AUI-3.3) ---

    "domain LimitType maps to the matching UI LimitType" {
        DomainLimitType.Once.toUi() shouldBe UiLimitType.ONCE
        DomainLimitType.Repetitive.toUi() shouldBe UiLimitType.REPETITIVE
    }

    // --- Overlay continue gating (Property 1, AUI-2.7) ---

    "buildOverlayUiState derives continueEnabled from the cooldown via the pure helper" {
        checkAll(Arb.int(-50, 600)) { cooldown ->
            val state = buildOverlayUiState(
                targetAppName = "Instagram",
                cooldownSeconds = cooldown,
                rescueHabit = null,
                pendingTasks = emptyList(),
            )
            state.continueEnabled shouldBe continueEnabled(cooldown)
        }
    }

    "buildOverlayUiState trims pending tasks to at most three" {
        val rows = (1..10).map {
            com.dopashift.ui.state.TodoRowUi(id = it.toString(), text = "t$it", done = false)
        }
        buildOverlayUiState("Reddit", 5, null, rows).pendingTasks.size shouldBe 3
    }

    // --- Roadmap fraction (Property 3, AUI-5.5) ---

    "roadmapProgressFraction equals roadmapFraction(currentDay) for all valid days" {
        checkAll(Arb.int(1, 30)) { day ->
            habitTrack(currentDay = day).roadmapProgressFraction() shouldBe roadmapFraction(day)
        }
    }

    "roadmapProgressFraction of a null track is zero" {
        (null as HabitTrack?).roadmapProgressFraction() shouldBe 0f
    }

    // --- Bar category (Property 4 input, AUI-6.4) ---

    "toDailyBarCategory maps null score to NO_DATA" {
        efficiencyScore(null).toDailyBarCategory() shouldBe DailyBarCategory.NO_DATA
    }

    "toDailyBarCategory maps scores at/above the threshold to PRODUCTIVE and below to DISTRACTING" {
        checkAll(Arb.int(0, 100)) { score ->
            val expected =
                if (score >= 50) DailyBarCategory.PRODUCTIVE else DailyBarCategory.DISTRACTING
            efficiencyScore(score).toDailyBarCategory(productiveThreshold = 50) shouldBe expected
        }
    }

    "efficiencyPercent clamps and defaults null to zero" {
        efficiencyScore(null).efficiencyPercent() shouldBe 0
        efficiencyScore(72).efficiencyPercent() shouldBe 72
        efficiencyScore(150).efficiencyPercent() shouldBe 100
    }

    // --- Rule row paused derivation (AUI-3.5) ---

    "toRuleRowUi derives paused from isPausedOn(today)" {
        val today = LocalDate.of(2026, 2, 3)
        val rule = InterceptionRule(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            appPackageName = "com.instagram.android",
            dailyLimitMinutes = 30,
            limitType = DomainLimitType.Once,
            pausedForDate = today,
            createdAt = Instant.EPOCH,
        )
        val row = rule.toRuleRowUi(appLabel = "Instagram", ruleTypeLabel = "Daily limit: 30m", today = today)
        row.paused shouldBe true
        row.packageName shouldBe "com.instagram.android"
    }
})
