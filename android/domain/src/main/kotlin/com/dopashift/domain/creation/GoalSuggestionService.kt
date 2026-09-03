package com.dopashift.domain.creation

/**
 * Optional BYO-LLM suggestion service for goals and habit plans (DQC-2.4, DQC-2.5, DQC-3.7, DQC-3.8).
 *
 * When no LLM provider is configured, [isConfigured] is `false` and callers disable
 * LLM affordances at the UI (defaulting habit authoring to Template) rather than
 * presenting actions that error on tap. On failure or timeout, callers fall back to
 * Template, surface the reason non-blockingly, and retain already-entered input.
 *
 * Privacy boundary (DQC-2.4, security rules): any request sends only the goal name,
 * optional goal description, and user-supplied keywords — never raw telemetry, task
 * history, or any other field.
 *
 * Declared in `domain` as a pure-Kotlin port; the provider-specific implementation
 * lives in the `data` module.
 */
interface GoalSuggestionService {

    /** Whether an LLM provider is configured; when `false`, LLM modes are disabled at the UI. */
    val isConfigured: Boolean

    /**
     * Requests keyword and description suggestions for a goal. Sends only [name],
     * [description], and [keywords] as context (DQC-2.4).
     *
     * @param name the goal name.
     * @param description the optional goal description.
     * @param keywords the user-supplied keywords entered so far.
     * @return the suggestion outcome.
     */
    suspend fun suggestKeywordsAndDescription(
        name: String,
        description: String?,
        keywords: List<String>
    ): SuggestionResult

    /**
     * Requests a 30-day habit plan for a goal. Sends only [goalName], [category], and
     * [keywords] as context (DQC-2.4). On failure/timeout the caller falls back to
     * Template mode without losing input (DQC-3.8).
     *
     * @param goalName the goal name.
     * @param category the goal category.
     * @param keywords the user-supplied keywords.
     * @return the habit-plan outcome.
     */
    suspend fun generateHabitPlan(
        goalName: String,
        category: String,
        keywords: List<String>
    ): HabitPlanResult
}

/**
 * The outcome of a keyword/description suggestion request (DQC-2.5, DQC-3.8).
 *
 * Failure is modelled explicitly so callers can degrade gracefully — surfacing the
 * reason non-blockingly and retaining the user's already-entered input.
 */
sealed interface SuggestionResult {

    /**
     * The provider returned suggestions.
     *
     * @property suggestedKeywords keywords to pre-populate (each editable/removable).
     * @property suggestedDescription an optional description suggestion.
     */
    data class Success(
        val suggestedKeywords: List<String>,
        val suggestedDescription: String?
    ) : SuggestionResult

    /**
     * The request failed or timed out; the caller keeps the user's input and surfaces
     * [reason] non-blockingly.
     *
     * @property reason a user-facing explanation of the failure.
     */
    data class Failure(val reason: String) : SuggestionResult {
        init {
            require(reason.isNotBlank()) { "SuggestionResult.Failure reason must not be blank" }
        }
    }
}

/**
 * The outcome of a 30-day habit-plan generation request (DQC-3.7, DQC-3.8).
 *
 * On [Failure] the caller falls back to Template mode without discarding input.
 */
sealed interface HabitPlanResult {

    /**
     * The provider returned a 30-day plan.
     *
     * @property checkpointDescriptions the generated day-by-day checkpoint descriptions
     *   (expected to carry 30 entries; resolution enforces exactly 30 before persistence).
     */
    data class Success(val checkpointDescriptions: List<String>) : HabitPlanResult

    /**
     * The request failed or timed out; the caller falls back to Template and surfaces
     * [reason] non-blockingly.
     *
     * @property reason a user-facing explanation of the failure.
     */
    data class Failure(val reason: String) : HabitPlanResult {
        init {
            require(reason.isNotBlank()) { "HabitPlanResult.Failure reason must not be blank" }
        }
    }
}
