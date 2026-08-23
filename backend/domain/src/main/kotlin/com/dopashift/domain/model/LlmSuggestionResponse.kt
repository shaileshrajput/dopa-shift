package com.dopashift.domain.model

/**
 * Response from the LLM provider containing goal-related suggestions
 * (keywords, milestones, habit ideas, etc.).
 */
data class LlmSuggestionResponse(
    val suggestions: List<String>,
    val rawResponse: String
)
