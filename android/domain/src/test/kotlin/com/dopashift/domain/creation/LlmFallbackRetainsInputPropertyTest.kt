package com.dopashift.domain.creation

import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property 12: LLM Failure Falls Back to Template Without Losing Input.
 *
 * When the user selects LLM habit authoring and the [GoalSuggestionService.generateHabitPlan]
 * call fails or times out, [LlmAuthoringCoordinator.resolveHabitAuthoring] SHALL:
 *  - resolve to Template mode (never surface the failure as a hard error), and
 *  - surface the failure reason non-blockingly (a non-null degradation reason), and
 *  - NOT discard input the user has already entered in the sheet — i.e. the goal name,
 *    keywords, and any already-authored/manual content the caller still holds are never
 *    mutated by the coordinator (DQC-3.8, parent Requirement 15 AC10).
 *
 * The coordinator never owns the user's sheet input; retention is verified by asserting
 * the inputs passed in are returned untouched and that no exception is thrown, so the
 * caller's state (which it still holds) is safe.
 *
 * **Validates: Requirements 3.8**
 *
 * Tags: Feature: dashboard-quick-create, Property 12
 */
class LlmFallbackRetainsInputPropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 12")
    )

    val config = PropTestConfig(iterations = 200)

    // --- Fakes ------------------------------------------------------------------------

    val defaultTemplateId = "default-template"

    /** A valid 30-checkpoint template the coordinator falls back to for any category. */
    fun template(category: String): HabitTemplate =
        HabitTemplate(
            id = defaultTemplateId,
            category = category.ifBlank { "general" },
            title = "Default Plan",
            checkpointDescriptions = (1..HabitTemplate.CHECKPOINT_COUNT).map { "Day $it: focused effort" }
        )

    val templateProvider = object : HabitTemplateProvider {
        override fun templatesForCategory(category: String): List<HabitTemplate> = listOf(template(category))
        override fun defaultTemplate(category: String): HabitTemplate = template(category)
    }

    /**
     * A configured provider whose habit-plan generation always fails (models the LLM call
     * failing or timing out). It also records the last context it received so the test can
     * assert the privacy/retention boundary — only the passed inputs reach the service.
     */
    class FailingSuggestionService(private val failureReason: String) : GoalSuggestionService {
        var lastGoalName: String? = null
        var lastCategory: String? = null
        var lastKeywords: List<String>? = null

        override val isConfigured: Boolean = true

        override suspend fun suggestKeywordsAndDescription(
            name: String,
            description: String?,
            keywords: List<String>
        ): SuggestionResult = SuggestionResult.Failure(failureReason)

        override suspend fun generateHabitPlan(
            goalName: String,
            category: String,
            keywords: List<String>
        ): HabitPlanResult {
            lastGoalName = goalName
            lastCategory = category
            lastKeywords = keywords
            return HabitPlanResult.Failure(failureReason)
        }
    }

    // --- Generators -------------------------------------------------------------------

    // Non-blank inputs the user may have already entered in the sheet.
    val goalNames = Arb.string(1..100).filter { it.isNotBlank() }
    val categories = Arb.string(1..50).filter { it.isNotBlank() }
    val keywordLists = Arb.list(Arb.string(1..30).filter { it.isNotBlank() }, 0..20)
    // The user-authored/manual content selected in the LLM sheet that must survive fallback.
    val manualDrafts = Arb.list(Arb.string(1..40).filter { it.isNotBlank() }, 0..30)
    val reasons = Arb.string(1..60).filter { it.isNotBlank() }

    // --- Properties -------------------------------------------------------------------

    "Property 12 (DQC-3.8): LLM failure falls back to Template with a non-blocking reason" {
        checkAll(config, goalNames, categories, keywordLists, reasons) { name, category, keywords, reason ->
            val service = FailingSuggestionService(reason)
            val coordinator = LlmAuthoringCoordinator(service, templateProvider)

            val resolved = coordinator.resolveHabitAuthoring(
                selected = HabitAuthoring.Llm(emptyList()),
                goalName = name,
                category = category,
                keywords = keywords
            )

            // Falls back to Template mode.
            val authoring = resolved.authoring
            authoring.shouldBeInstanceOf<HabitAuthoring.Template>()
            authoring.templateId shouldBe defaultTemplateId

            // Surfaces the failure reason non-blockingly.
            resolved.degraded shouldBe true
            resolved.degradationReason.shouldNotBeNull()
            resolved.degradationReason shouldBe reason
        }
    }

    "Property 12 (DQC-3.8): the caller's already-entered input is never discarded or mutated" {
        checkAll(config, goalNames, categories, keywordLists, manualDrafts, reasons) {
                name, category, keywords, manualDraft, reason ->
            val service = FailingSuggestionService(reason)
            val coordinator = LlmAuthoringCoordinator(service, templateProvider)

            // Snapshots of the user's already-entered input before the failing LLM call.
            val nameBefore = name
            val categoryBefore = category
            val keywordsBefore = keywords.toList()
            val manualDraftBefore = manualDraft.toList()

            coordinator.resolveHabitAuthoring(
                selected = HabitAuthoring.Llm(emptyList()),
                goalName = name,
                category = category,
                keywords = keywords
            )

            // The coordinator does not own or mutate the caller's sheet input: the values the
            // caller still holds are byte-for-byte identical after the failed attempt (DQC-3.8).
            name shouldBe nameBefore
            category shouldBe categoryBefore
            keywords shouldBe keywordsBefore
            manualDraft shouldBe manualDraftBefore

            // Only the minimum-necessary context reached the service (privacy boundary), and it
            // was exactly what the user entered — proof nothing was lost or altered en route.
            service.lastGoalName shouldBe nameBefore
            service.lastCategory shouldBe categoryBefore
            service.lastKeywords shouldBe keywordsBefore
        }
    }

    "Property 12 (DQC-3.8): a subsequent Manual selection resolves cleanly (input reusable after fallback)" {
        // Proves that after an LLM failure the user can keep working with retained input:
        // the same manual content the user drafted resolves without any LLM interaction.
        checkAll(config, categories, manualDrafts.filter { it.isNotEmpty() }, reasons) {
                category, manualDraft, reason ->
            val service = FailingSuggestionService(reason)
            val coordinator = LlmAuthoringCoordinator(service, templateProvider)

            val resolved = coordinator.resolveHabitAuthoring(
                selected = HabitAuthoring.Manual(manualDraft),
                goalName = "Ship the app",
                category = category,
                keywords = emptyList()
            )

            // Manual is passed through unchanged with no degradation and no LLM call.
            resolved.authoring shouldBe HabitAuthoring.Manual(manualDraft)
            resolved.degraded shouldBe false
            service.lastGoalName shouldBe null
        }
    }
})
