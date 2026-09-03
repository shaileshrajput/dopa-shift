package com.dopashift.domain.creation

import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.UUID

/**
 * Property 13: LLM Context Privacy Boundary.
 *
 * For any AI suggestion request made from a Creation_Sheet, the request payload SHALL
 * contain only the goal name, goal description, and user-supplied keywords (plus the
 * goal category for a habit plan) — never raw telemetry, task history, or any other
 * field (DQC-2.4, parent Req 15 AC7, security rules: BYO-LLM calls send only the
 * minimum necessary context).
 *
 * [LlmAuthoringCoordinator] is the single, auditable choke point through which all AI
 * requests flow. This test drives that coordinator with a capturing fake
 * [GoalSuggestionService] that records exactly what it receives, then asserts the
 * captured payload is a subset of {name, description, keywords, category}. The
 * surrounding context deliberately carries telemetry- and task-history-shaped fields
 * (screen-time counts, distraction-app usage, prior task descriptions) that MUST NOT
 * appear anywhere in the captured payload.
 *
 * **Validates: Requirements 2.4**
 *
 * Tags: Feature: dashboard-quick-create, Property 13
 */
class LlmContextPrivacyBoundaryPropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 13")
    )

    val config = PropTestConfig(iterations = 200)

    // A single record of everything a suggestion request forwarded to the LLM provider.
    data class CapturedGoalSuggestion(
        val name: String,
        val description: String?,
        val keywords: List<String>
    )

    data class CapturedHabitPlan(
        val goalName: String,
        val category: String,
        val keywords: List<String>
    )

    /**
     * Capturing fake: records every argument it is handed and returns benign success.
     * It intentionally holds no reference to any telemetry/history source, so the ONLY
     * way privileged data could leak is if the coordinator forwarded it explicitly.
     */
    class CapturingSuggestionService(override val isConfigured: Boolean = true) : GoalSuggestionService {
        val goalCaptures = mutableListOf<CapturedGoalSuggestion>()
        val habitCaptures = mutableListOf<CapturedHabitPlan>()

        override suspend fun suggestKeywordsAndDescription(
            name: String,
            description: String?,
            keywords: List<String>
        ): SuggestionResult {
            goalCaptures += CapturedGoalSuggestion(name, description, keywords)
            return SuggestionResult.Success(
                suggestedKeywords = listOf("focus", "discipline"),
                suggestedDescription = "A concise plan."
            )
        }

        override suspend fun generateHabitPlan(
            goalName: String,
            category: String,
            keywords: List<String>
        ): HabitPlanResult {
            habitCaptures += CapturedHabitPlan(goalName, category, keywords)
            return HabitPlanResult.Success((1..30).map { "Day $it plan" })
        }
    }

    val fakeTemplateProvider = object : HabitTemplateProvider {
        override fun templatesForCategory(category: String): List<HabitTemplate> = listOf(defaultTemplate(category))
        override fun defaultTemplate(category: String): HabitTemplate =
            HabitTemplate(
                id = "default-$category",
                category = category.ifBlank { "general" },
                title = "Default plan",
                checkpointDescriptions = (1..HabitTemplate.CHECKPOINT_COUNT).map { "Day $it" }
            )
    }

    // --- Generators ---

    val nameArb: Arb<String> = Arb.string(1..100).map { it.ifBlank { "Ship product" } }
    val descriptionArb: Arb<String?> = Arb.string(0..200).orNull(nullProbability = 0.3)
    val keywordsArb: Arb<List<String>> =
        Arb.list(Arb.string(1..50).map { it.ifBlank { "kw" } }, 1..20)
    val categoryArb: Arb<String> = Arb.string(1..50).map { it.ifBlank { "career" } }

    // Telemetry- and task-history-shaped values the coordinator must NEVER forward.
    // These live only in the surrounding context, never handed to the coordinator's
    // AI-facing methods.
    val telemetryValues = listOf(
        "screen_time_minutes=482",
        "distraction_app=com.example.socialfeed",
        "unlock_count=173",
        "prior_task=call the dentist at 3pm",
        "task_history=[buy milk, finish taxes, reply to boss]",
        "location=lat:12.97,lng:77.59",
        "device_id=abc-123-secret"
    )

    /** True if [needle] (a telemetry token or fragment) appears anywhere in [haystack]. */
    fun leaks(haystack: String, needle: String): Boolean = haystack.contains(needle)

    "goal suggestion forwards only name, description, and keywords — never telemetry/history" {
        checkAll(config, nameArb, descriptionArb, keywordsArb, Arb.boolean()) { name, description, keywords, _ ->
            val service = CapturingSuggestionService(isConfigured = true)
            val coordinator = LlmAuthoringCoordinator(service, fakeTemplateProvider)

            val outcome = coordinator.suggestForGoal(name, description, keywords)
            outcome.shouldNotBeNull()

            // Exactly one request captured, and its fields equal the inputs verbatim.
            service.goalCaptures.size shouldBe 1
            val captured = service.goalCaptures.single()
            captured.name shouldBe name
            captured.description shouldBe description
            captured.keywords shouldContainExactly keywords

            // The captured payload's field set is a subset of {name, description, keywords}.
            // Confirm no telemetry/history token leaked into any captured string.
            val payloadText = buildString {
                append(captured.name)
                append('\u0000')
                append(captured.description ?: "")
                append('\u0000')
                captured.keywords.forEach { append(it); append('\u0000') }
            }
            telemetryValues.forEach { telemetry ->
                leaks(payloadText, telemetry).shouldBeFalse()
            }
        }
    }

    "habit plan forwards only goalName, category, and keywords — never telemetry/history" {
        val genArb = arbitrary {
            Triple(nameArb.bind(), categoryArb.bind(), keywordsArb.bind())
        }
        checkAll(config, genArb) { (goalName, category, keywords) ->
            val service = CapturingSuggestionService(isConfigured = true)
            val coordinator = LlmAuthoringCoordinator(service, fakeTemplateProvider)

            val resolved = coordinator.resolveHabitAuthoring(
                selected = HabitAuthoring.Llm(generated = emptyList()),
                goalName = goalName,
                category = category,
                keywords = keywords
            )
            resolved.shouldNotBeNull()

            service.habitCaptures.size shouldBe 1
            val captured = service.habitCaptures.single()
            captured.goalName shouldBe goalName
            captured.category shouldBe category
            captured.keywords shouldContainExactly keywords

            val payloadText = buildString {
                append(captured.goalName)
                append('\u0000')
                append(captured.category)
                append('\u0000')
                captured.keywords.forEach { append(it); append('\u0000') }
            }
            telemetryValues.forEach { telemetry ->
                leaks(payloadText, telemetry).shouldBeFalse()
            }
        }
    }

    // Adversarial: even when a CreateGoalCommand is populated with rich context and the
    // caller ALSO holds telemetry/task-history, the coordinator forwards only the three
    // permitted goal fields. This proves the boundary is enforced by the coordinator's
    // signature, not by callers happening to omit data.
    "coordinator never forwards fields beyond name/description/keywords even amid rich context" {
        val cmdArb = arbitrary {
            CreateGoalCommand(
                userId = UUID.randomUUID(),
                name = nameArb.bind(),
                category = categoryArb.bind(),
                keywords = keywordsArb.bind(),
                description = descriptionArb.bind(),
                correlationId = CorrelationId(UUID.randomUUID().toString()),
                origin = CreationOrigin.DASHBOARD
            )
        }
        checkAll(config, cmdArb) { command ->
            val service = CapturingSuggestionService(isConfigured = true)
            val coordinator = LlmAuthoringCoordinator(service, fakeTemplateProvider)

            // Local telemetry / task history that MUST stay on-device and never be sent.
            @Suppress("UNUSED_VARIABLE")
            val onDeviceTelemetry = telemetryValues

            coordinator.suggestForGoal(command.name, command.description, command.keywords)

            val captured = service.goalCaptures.single()
            // Only the three permitted goal fields are forwarded, verbatim.
            captured.name shouldBe command.name
            captured.description shouldBe command.description
            captured.keywords shouldContainExactly command.keywords

            // category, userId, correlationId, origin are NOT part of a goal-suggestion payload.
            val payloadText = buildString {
                append(captured.name)
                append('\u0000')
                append(captured.description ?: "")
                append('\u0000')
                captured.keywords.forEach { append(it); append('\u0000') }
            }
            // userId / correlationId / category must not leak into the goal-suggestion payload.
            leaks(payloadText, command.userId.toString()).shouldBeFalse()
            leaks(payloadText, command.correlationId.value).shouldBeFalse()
            telemetryValues.forEach { telemetry ->
                leaks(payloadText, telemetry).shouldBeFalse()
            }
        }
    }

    "disabled provider makes no request at all — nothing can leak" {
        checkAll(config, nameArb, descriptionArb, keywordsArb) { name, description, keywords ->
            val service = CapturingSuggestionService(isConfigured = false)
            val coordinator = LlmAuthoringCoordinator(service, fakeTemplateProvider)

            val outcome = coordinator.suggestForGoal(name, description, keywords)
            outcome shouldBe GoalSuggestionOutcome.Disabled
            service.goalCaptures.size shouldBe 0
        }
    }
})
