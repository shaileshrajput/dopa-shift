package com.dopashift.domain.creation

// Feature: dashboard-quick-create, Property 11: Habit-Create Invariants

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.LocalDate

/**
 * Property 11: Habit-Create Invariants.
 *
 * Feature: dashboard-quick-create, Property 11
 * Validates: Requirements 3.10.
 *
 * Requirement 3.10 states that when a habit is created, its 30-day track starts "today" in
 * the user's configured time zone and its current-day pointer is 1. The subject under test is
 * the pure-Kotlin [HabitAuthoringResolver.resolve], which is the single convergence point for
 * all three authoring modes (Template / LLM / Manual).
 *
 * For any supplied `today`, `goalId`, `userId`, category, and authoring mode, the resolved
 * [HabitResolution.track] must satisfy the create-time invariants:
 * - `track.startDate == today` (today in the user's tz, supplied by the caller),
 * - `track.currentDay == 1`,
 * - `track.goalId == goalId` and `track.userId == userId` (ownership/linkage preserved),
 * - `track.isFinished == false` (a freshly created track is never finished).
 *
 * A fake [HabitTemplateProvider] returns valid 30-checkpoint templates for any category, so
 * Template mode always resolves; LLM and Manual modes carry at least one description each so
 * they always have usable input.
 *
 * Kotest property testing, 200 iterations (>= 100 per the design Testing Strategy).
 */
class HabitCreateInvariantsPropertyTest : StringSpec({

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    /**
     * A [HabitTemplateProvider] that always yields a valid 30-checkpoint template for any
     * category, so Template-mode resolution never fails for lack of a bundled template.
     */
    val fakeTemplateProvider = object : HabitTemplateProvider {
        private fun templateFor(category: String, id: String): HabitTemplate =
            HabitTemplate(
                id = id,
                category = category.ifBlank { "general" },
                title = "Template for ${category.ifBlank { "general" }}",
                checkpointDescriptions = (1..HabitTemplate.CHECKPOINT_COUNT).map { "Day $it micro-habit" }
            )

        override fun templatesForCategory(category: String): List<HabitTemplate> =
            listOf(templateFor(category, "tmpl-${category.ifBlank { "general" }}"))

        override fun defaultTemplate(category: String): HabitTemplate =
            templateFor(category, "default-${category.ifBlank { "general" }}")
    }

    val resolver = HabitAuthoringResolver(fakeTemplateProvider)

    // Wide range of dates spanning past/present/future to prove startDate is passed through verbatim.
    val today: Arb<LocalDate> =
        Arb.long(min = LocalDate.of(1970, 1, 1).toEpochDay(), max = LocalDate.of(2100, 12, 31).toEpochDay())
            .map { LocalDate.ofEpochDay(it) }

    // Category strings, including blank (the fake tolerates blank via ifBlank).
    val categories: Arb<String> = Arb.string(minSize = 0, maxSize = 40)

    // Non-blank descriptions within the checkpoint bound, so LLM/Manual input is always usable.
    val descriptions: Arb<String> =
        Arb.string(minSize = 1, maxSize = HabitTemplate.DESCRIPTION_MAX).map { raw ->
            raw.trim().ifBlank { "do the thing" }
        }

    /**
     * All three authoring modes, each guaranteed to carry usable input:
     * - Template with a non-blank template id (the fake falls back to default when unmatched),
     * - Llm with >= 1 generated description,
     * - Manual with >= 1 authored description.
     */
    val authorings: Arb<HabitAuthoring> = Arb.bind(
        Arb.int(0, 2),
        Arb.list(descriptions, 1..40),
        Arb.string(minSize = 1, maxSize = 12).map { "tmpl-$it" }
    ) { mode, descs, templateId ->
        when (mode) {
            0 -> HabitAuthoring.Template(templateId)
            1 -> HabitAuthoring.Llm(descs)
            else -> HabitAuthoring.Manual(descs)
        }
    }

    "resolved track carries startDate == today, currentDay == 1, and preserves ownership across all modes" {
        checkAll(
            config,
            today,
            Arb.uuid(),
            Arb.uuid(),
            categories,
            authorings
        ) { day, goalId, userId, category, authoring ->
            val resolution = resolver.resolve(
                authoring = authoring,
                category = category,
                goalId = goalId,
                userId = userId,
                today = day
            )

            val track = resolution.track
            track.startDate shouldBe day
            track.currentDay shouldBe 1
            track.goalId shouldBe goalId
            track.userId shouldBe userId
            track.isFinished shouldBe false
        }
    }

    "invariants hold regardless of existingActiveTrackPresent and withReminder flags" {
        checkAll(
            config,
            today,
            Arb.uuid(),
            Arb.uuid(),
            categories,
            authorings,
            Arb.of(true, false),
            Arb.of(true, false)
        ) { day, goalId, userId, category, authoring, existingActive, reminder ->
            val resolution = resolver.resolve(
                authoring = authoring,
                category = category,
                goalId = goalId,
                userId = userId,
                today = day,
                existingActiveTrackPresent = existingActive,
                withReminder = reminder
            )

            val track = resolution.track
            track.startDate shouldBe day
            track.currentDay shouldBe 1
            track.goalId shouldBe goalId
            track.userId shouldBe userId
            track.isFinished shouldBe false
        }
    }

    // Explicit example asserting today() semantics: an arbitrary "today" is threaded straight through.
    "example: an explicit today is used verbatim as the track start date with currentDay 1" {
        val day = LocalDate.of(2025, 3, 14)
        val goalId = java.util.UUID.randomUUID()
        val userId = java.util.UUID.randomUUID()

        val resolution = resolver.resolve(
            authoring = HabitAuthoring.Manual(listOf("stretch for five minutes")),
            category = "Fitness",
            goalId = goalId,
            userId = userId,
            today = day
        )

        resolution.track.startDate shouldBe day
        resolution.track.currentDay shouldBe 1
        resolution.track.goalId shouldBe goalId
        resolution.track.userId shouldBe userId
        resolution.track.isFinished shouldBe false
    }
})
