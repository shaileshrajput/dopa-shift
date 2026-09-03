package com.dopashift.domain.creation

import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.LocalDate
import java.util.UUID

/**
 * Property 10: Habit Authoring Resolves to Exactly 30 Valid Checkpoints.
 *
 * For any [HabitAuthoring] mode (Template, Llm, Manual) with any usable input,
 * [HabitAuthoringResolver.resolve] SHALL produce a [com.dopashift.domain.entity.HabitTrack]
 * carrying exactly 30 checkpoints, each with a non-blank 1-200 character description and
 * ascending day numbers 1..30. When fewer than 30 descriptions are supplied, the remaining
 * days SHALL be auto-filled with the last usable description and reported via
 * [HabitResolution.autoFilledDays] (DQC-3.9); when more than 30 are supplied, the input is
 * truncated to 30 with no auto-fill.
 *
 * **Validates: Requirements 3.6, 3.9**
 *
 * Tags: Feature: dashboard-quick-create, Property 10 (also satisfies DQC-7.6)
 */
class HabitAuthoringCheckpointsPropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 10")
    )

    val config = PropTestConfig(iterations = 200)

    // --- Fake HabitTemplateProvider: always returns valid 30-checkpoint templates ---

    val templateId = "career-30"
    val category = "career"

    fun validTemplate(id: String): HabitTemplate =
        HabitTemplate(
            id = id,
            category = category,
            title = "Career Growth",
            checkpointDescriptions = (1..HabitTemplate.CHECKPOINT_COUNT).map { "Day $it: focused deep-work block" }
        )

    val fakeProvider = object : HabitTemplateProvider {
        override fun templatesForCategory(category: String): List<HabitTemplate> =
            listOf(validTemplate(templateId))

        override fun defaultTemplate(category: String): HabitTemplate =
            validTemplate("default-$category")
    }

    val resolver = HabitAuthoringResolver(fakeProvider)

    // --- Helpers mirroring the resolver's clamp/normalization contract ---

    fun clamp(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.length > HabitTemplate.DESCRIPTION_MAX) {
            trimmed.substring(0, HabitTemplate.DESCRIPTION_MAX)
        } else {
            trimmed
        }
    }

    /** Reproduce the expected 30 normalized descriptions from a raw sourced list. */
    fun expectedNormalized(sourced: List<String>): List<String> {
        val result = ArrayList<String>(HabitTemplate.CHECKPOINT_COUNT)
        var lastUsable: String? = null
        for (raw in sourced) {
            if (result.size == HabitTemplate.CHECKPOINT_COUNT) break
            val usable = clamp(raw) ?: lastUsable ?: continue
            result.add(usable)
            lastUsable = usable
        }
        val fill = lastUsable!!
        while (result.size < HabitTemplate.CHECKPOINT_COUNT) result.add(fill)
        return result
    }

    /** Assert the universal invariants that hold for every resolution. */
    fun assertValidTrack(resolution: HabitResolution) {
        val checkpoints = resolution.track.checkpoints
        checkpoints.size shouldBe HabitTemplate.CHECKPOINT_COUNT
        checkpoints.map { it.dayNumber } shouldContainExactly (1..30).toList()
        checkpoints.forEach { cp ->
            cp.description.isNotBlank() shouldBe true
            cp.description.length shouldBeGreaterThanOrEqual 1
            cp.description.length shouldBeLessThanOrEqual HabitTemplate.DESCRIPTION_MAX
        }
        resolution.track.currentDay shouldBe 1
    }

    // --- Generators for supplied descriptions with varied, adversarial content ---

    // A single description entry: usually valid, sometimes blank, sometimes over-length.
    val entryArb: Arb<String> = arbitrary { rs ->
        when (Arb.int(0..9).bind()) {
            0, 1 -> "   " // blank / whitespace-only
            2, 3 -> "x".repeat(Arb.int(201..400).bind()) // over 200 chars
            4 -> "  padded description  " // needs trimming
            else -> Arb.string(1..120).bind().ifBlank { "fallback task" }
        }
    }

    // A guaranteed-usable entry: non-blank after trim, within the 1-200 char bound.
    val usableEntryArb: Arb<String> = arbitrary { rs ->
        Arb.string(1..120).bind().ifBlank { "fallback task" }
    }

    // Supplied sizes span both auto-fill and truncation regimes plus boundaries.
    val sizeArb: Arb<Int> = Arb.of(1, 5, 15, 29, 30, 31, 40)

    // The resolver requires at least one usable description (it throws on all-blank input, and
    // that empty/all-blank case is covered by dedicated tests). To exercise the resolve-to-30
    // property over its valid input space, always seed the first slot with a usable entry; the
    // remaining slots use the adversarial [entryArb] (blank / over-length / needs-trimming).
    fun suppliedArb(size: Int): Arb<List<String>> = arbitrary { rs ->
        List(size) { index -> if (index == 0) usableEntryArb.bind() else entryArb.bind() }
    }

    // ---------------------------------------------------------------------------
    // Template mode: provider always supplies exactly 30 valid descriptions.
    // ---------------------------------------------------------------------------
    "Template authoring always resolves to exactly 30 valid checkpoints with no auto-fill" {
        checkAll(config, Arb.boolean(), Arb.boolean()) { existingActive, withReminder ->
            val resolution = resolver.resolve(
                authoring = HabitAuthoring.Template(templateId),
                category = category,
                goalId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                today = LocalDate.of(2025, 1, 1),
                existingActiveTrackPresent = existingActive,
                withReminder = withReminder
            )
            assertValidTrack(resolution)
            // Template supplies 30 usable descriptions => no auto-fill.
            resolution.autoFilledDays shouldBe 0
        }
    }

    // ---------------------------------------------------------------------------
    // Llm mode: varied generated lists across N in {1,5,15,29,30,31,40}.
    // ---------------------------------------------------------------------------
    "Llm authoring resolves to 30 valid checkpoints; auto-fills below 30, truncates above" {
        val genArb = sizeArb.let { sizes ->
            arbitrary { rs ->
                val size = sizes.bind()
                size to suppliedArb(size).bind()
            }
        }
        checkAll(config, genArb) { (size, generated) ->
            val resolution = resolver.resolve(
                authoring = HabitAuthoring.Llm(generated),
                category = category,
                goalId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                today = LocalDate.of(2025, 6, 15)
            )
            assertValidTrack(resolution)

            val expectedAutoFill = (HabitTemplate.CHECKPOINT_COUNT - size).coerceAtLeast(0)
            resolution.autoFilledDays shouldBe expectedAutoFill

            // Strong content assertion: the produced 30 descriptions match the resolver's
            // clamp + skip-blank + auto-fill-to-30 + truncate normalization exactly.
            val expected = expectedNormalized(generated)
            resolution.track.checkpoints.map { it.description } shouldContainExactly expected

            // The final day always carries the last usable description (auto-filled or authored).
            // Derived from expectedNormalized, so it is robust to blank entries that don't map 1:1
            // onto checkpoint slots.
            resolution.track.checkpoints.last().description shouldBe expected.last()

            if (size > 30) {
                // size > 30 => truncated, no auto-fill.
                resolution.autoFilledDays shouldBe 0
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Manual mode: authored size 1..40 with blanks / over-length entries.
    // ---------------------------------------------------------------------------
    "Manual authoring resolves to 30 valid checkpoints and auto-fills missing days with last usable" {
        val genArb = arbitrary { rs ->
            val size = Arb.int(1..40).bind()
            size to suppliedArb(size).bind()
        }
        checkAll(config, genArb) { (size, authored) ->
            val resolution = resolver.resolve(
                authoring = HabitAuthoring.Manual(authored),
                category = category,
                goalId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                today = LocalDate.of(2025, 3, 10)
            )
            assertValidTrack(resolution)

            // autoFilledDays counts against the raw supplied size (blanks included), per the resolver.
            val expectedAutoFill = (HabitTemplate.CHECKPOINT_COUNT - size).coerceAtLeast(0)
            resolution.autoFilledDays shouldBe expectedAutoFill
            resolution.requiresAutoFillDisclosure shouldBe (expectedAutoFill > 0)

            // Strong content assertion: matches clamp + skip-blank + auto-fill + truncate exactly.
            val expected = expectedNormalized(authored)
            resolution.track.checkpoints.map { it.description } shouldContainExactly expected

            // Final day always repeats the last usable description; derived from expectedNormalized
            // so it holds even when blank entries don't map 1:1 onto checkpoint slots.
            resolution.track.checkpoints.last().description shouldBe expected.last()
        }
    }

    // ---------------------------------------------------------------------------
    // Explicit boundary examples: 29 (one auto-fill), 30 (exact), 31 (truncate).
    // ---------------------------------------------------------------------------
    "Manual boundary: 29 authored descriptions auto-fill exactly one day" {
        val authored = (1..29).map { "Day $it plan" }
        val resolution = resolver.resolve(
            authoring = HabitAuthoring.Manual(authored),
            category = category,
            goalId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            today = LocalDate.of(2025, 1, 1)
        )
        assertValidTrack(resolution)
        resolution.autoFilledDays shouldBe 1
        resolution.track.checkpoints[29].description shouldBe authored.last()
        resolution.track.checkpoints[28].description shouldBe authored.last()
    }

    "Manual boundary: exactly 30 authored descriptions auto-fill nothing" {
        val authored = (1..30).map { "Day $it plan" }
        val resolution = resolver.resolve(
            authoring = HabitAuthoring.Manual(authored),
            category = category,
            goalId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            today = LocalDate.of(2025, 1, 1)
        )
        assertValidTrack(resolution)
        resolution.autoFilledDays shouldBe 0
        resolution.track.checkpoints.map { it.description } shouldContainExactly authored
    }

    "Manual boundary: 31 authored descriptions truncate to 30 with no auto-fill" {
        val authored = (1..31).map { "Day $it plan" }
        val resolution = resolver.resolve(
            authoring = HabitAuthoring.Manual(authored),
            category = category,
            goalId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            today = LocalDate.of(2025, 1, 1)
        )
        assertValidTrack(resolution)
        resolution.autoFilledDays shouldBe 0
        resolution.track.checkpoints.map { it.description } shouldContainExactly authored.take(30)
    }

    "Over-length and blank entries are clamped and never violate the checkpoint invariant" {
        val authored = listOf(
            "  leading and trailing spaces  ",
            "y".repeat(500),
            "   ", // blank -> falls back to last usable
            "valid entry"
        )
        val resolution = resolver.resolve(
            authoring = HabitAuthoring.Llm(authored),
            category = category,
            goalId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            today = LocalDate.of(2025, 1, 1)
        )
        assertValidTrack(resolution)
        resolution.track.checkpoints[0].description shouldBe "leading and trailing spaces"
        resolution.track.checkpoints[1].description shouldBe "y".repeat(HabitTemplate.DESCRIPTION_MAX)
        // Blank entry (index 2 of source) falls back to previous usable description.
        resolution.track.checkpoints[2].description shouldBe "y".repeat(HabitTemplate.DESCRIPTION_MAX)
        resolution.track.checkpoints[3].description shouldBe "valid entry"
    }
})
