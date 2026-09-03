package com.dopashift.domain.creation

/**
 * Coordinates optional BYO-LLM authoring for goals and habits with graceful
 * degradation (DQC-2.4, DQC-2.5, DQC-3.7, DQC-3.8).
 *
 * This is a pure-Kotlin domain coordinator over [GoalSuggestionService] and
 * [HabitTemplateProvider]. It centralises three behaviours so the UI and use cases
 * do not each re-implement them:
 *
 *  1. **Enablement** — [isLlmModeEnabled] delegates to [GoalSuggestionService.isConfigured]
 *     so callers disable the LLM affordance and default habit authoring to Template
 *     when no provider is configured (DQC-2.5, DQC-3.7).
 *  2. **Habit-plan degradation** — [resolveHabitAuthoring] calls
 *     [GoalSuggestionService.generateHabitPlan] when the user picked LLM mode; on
 *     success it returns [HabitAuthoring.Llm], and on failure/timeout it falls back to
 *     the category's [HabitTemplateProvider.defaultTemplate] while surfacing the reason
 *     non-blockingly and retaining any already-entered input (DQC-3.8).
 *  3. **Goal suggestions** — [suggestForGoal] calls
 *     [GoalSuggestionService.suggestKeywordsAndDescription] and returns the suggestions,
 *     or a non-blocking failure reason, without discarding the user's input (DQC-2.5).
 *
 * **Privacy boundary (DQC-2.4):** this coordinator only ever passes the goal name,
 * optional description, and user-supplied keywords (plus category/goalName for a habit
 * plan) to [GoalSuggestionService]. It never forwards raw telemetry, task history, or
 * any other field. Enforcing the boundary here keeps a single, auditable choke point.
 */
class LlmAuthoringCoordinator(
    private val suggestionService: GoalSuggestionService,
    private val templateProvider: HabitTemplateProvider
) {

    /**
     * Whether LLM mode should be offered to the user.
     *
     * When `false`, callers disable the "Suggest with AI" / LLM authoring affordance
     * and default habit authoring to Template rather than presenting an action that
     * errors on tap (DQC-2.5, DQC-3.7).
     */
    val isLlmModeEnabled: Boolean
        get() = suggestionService.isConfigured

    /**
     * Resolves the habit authoring mode the user selected into an authoring value the
     * habit-create path can persist, degrading gracefully when LLM mode fails.
     *
     * Behaviour by [selected] mode:
     *  - [HabitAuthoring.Template] / [HabitAuthoring.Manual]: passed through unchanged;
     *    no LLM call is made.
     *  - [HabitAuthoring.Llm]: if LLM mode is not enabled, immediately falls back to the
     *    category default template. Otherwise a plan is requested with only
     *    goal name/category/keywords (DQC-2.4). On
     *    [HabitPlanResult.Success] the generated descriptions are returned as
     *    [HabitAuthoring.Llm]; on [HabitPlanResult.Failure] the coordinator falls back to
     *    the category's [HabitTemplateProvider.defaultTemplate] and attaches the failure
     *    reason so the UI can surface it non-blockingly — without discarding the user's
     *    already-entered input, which the caller still holds (DQC-3.8).
     *
     * Note: this returns the resolved [HabitAuthoring] mode; conversion to exactly 30
     * checkpoints happens in the authoring-mode resolver before persistence.
     *
     * @param selected the authoring mode the user picked in the sheet.
     * @param goalName the goal name (sent to the LLM only for Llm mode).
     * @param category the goal category (used to look up the fallback template).
     * @param keywords the user-supplied keywords (sent to the LLM only for Llm mode).
     * @return the resolved authoring plus an optional non-blocking degradation reason.
     */
    suspend fun resolveHabitAuthoring(
        selected: HabitAuthoring,
        goalName: String,
        category: String,
        keywords: List<String>
    ): ResolvedHabitAuthoring {
        if (selected !is HabitAuthoring.Llm) {
            // Template and Manual work fully offline; no LLM call, no degradation.
            return ResolvedHabitAuthoring(authoring = selected, degradationReason = null)
        }

        if (!isLlmModeEnabled) {
            // No provider configured: default to Template (DQC-3.7). Selection was made
            // before enablement was known, so degrade silently to the default template.
            return fallbackToTemplate(category, reason = null)
        }

        // Privacy boundary (DQC-2.4): only goal name/category/keywords are sent.
        return when (val result = suggestionService.generateHabitPlan(goalName, category, keywords)) {
            is HabitPlanResult.Success ->
                ResolvedHabitAuthoring(
                    authoring = HabitAuthoring.Llm(result.checkpointDescriptions),
                    degradationReason = null
                )

            is HabitPlanResult.Failure ->
                // Fall back to Template, surface the reason non-blockingly, retain input (DQC-3.8).
                fallbackToTemplate(category, reason = result.reason)
        }
    }

    /**
     * Requests keyword/description suggestions for a goal, degrading gracefully.
     *
     * If LLM mode is not enabled the request is not attempted and a disabled outcome is
     * returned so the caller can keep the affordance hidden/disabled (DQC-2.5). When
     * enabled, only the goal name/description/keywords are sent (DQC-2.4). On failure the
     * reason is surfaced non-blockingly and the user's entered input is retained (the
     * caller still holds it — this coordinator never mutates or discards it).
     *
     * @param name the goal name.
     * @param description the optional goal description.
     * @param keywords the user-supplied keywords entered so far.
     * @return the suggestion outcome for the UI to apply or surface.
     */
    suspend fun suggestForGoal(
        name: String,
        description: String?,
        keywords: List<String>
    ): GoalSuggestionOutcome {
        if (!isLlmModeEnabled) {
            return GoalSuggestionOutcome.Disabled
        }

        // Privacy boundary (DQC-2.4): only name/description/keywords are sent.
        return when (val result = suggestionService.suggestKeywordsAndDescription(name, description, keywords)) {
            is SuggestionResult.Success ->
                GoalSuggestionOutcome.Suggested(
                    suggestedKeywords = result.suggestedKeywords,
                    suggestedDescription = result.suggestedDescription
                )

            is SuggestionResult.Failure ->
                GoalSuggestionOutcome.Degraded(reason = result.reason)
        }
    }

    /**
     * Builds a Template fallback for [category], attaching an optional non-blocking
     * [reason] the UI can surface. The already-entered input is retained by the caller.
     */
    private fun fallbackToTemplate(category: String, reason: String?): ResolvedHabitAuthoring {
        val template = templateProvider.defaultTemplate(category)
        return ResolvedHabitAuthoring(
            authoring = HabitAuthoring.Template(template.id),
            degradationReason = reason
        )
    }
}

/**
 * The resolved habit authoring mode plus an optional user-facing degradation reason
 * (DQC-3.8).
 *
 * When LLM plan generation fails and the coordinator falls back to Template, the
 * [degradationReason] is non-null so the UI can surface it non-blockingly while the
 * user's already-entered input is retained. For a clean resolution (Template/Manual, or
 * a successful LLM plan) the reason is `null`.
 *
 * @property authoring the resolved authoring mode to persist.
 * @property degradationReason a non-blocking explanation when LLM mode degraded to
 *   Template; `null` when no degradation occurred.
 */
data class ResolvedHabitAuthoring(
    val authoring: HabitAuthoring,
    val degradationReason: String? = null
) {
    /** Whether resolution degraded from LLM to Template and the UI should surface a reason. */
    val degraded: Boolean get() = degradationReason != null
}

/**
 * The outcome of a goal keyword/description suggestion request, modelled so the UI can
 * apply suggestions, surface a non-blocking reason, or keep the affordance disabled —
 * always retaining the user's already-entered input (DQC-2.5).
 */
sealed interface GoalSuggestionOutcome {

    /**
     * LLM mode is not enabled; the affordance stays hidden/disabled and no request was
     * made (DQC-2.5, DQC-3.7).
     */
    data object Disabled : GoalSuggestionOutcome

    /**
     * The provider returned suggestions to pre-populate (each editable/removable).
     *
     * @property suggestedKeywords keywords to suggest.
     * @property suggestedDescription an optional description suggestion.
     */
    data class Suggested(
        val suggestedKeywords: List<String>,
        val suggestedDescription: String?
    ) : GoalSuggestionOutcome

    /**
     * The request failed or timed out; the caller keeps the user's input and surfaces
     * [reason] non-blockingly (DQC-2.5).
     *
     * @property reason a user-facing explanation of the failure.
     */
    data class Degraded(val reason: String) : GoalSuggestionOutcome {
        init {
            require(reason.isNotBlank()) { "GoalSuggestionOutcome.Degraded reason must not be blank" }
        }
    }
}
