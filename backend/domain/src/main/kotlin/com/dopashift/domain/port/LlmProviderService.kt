package com.dopashift.domain.port

import com.dopashift.domain.model.LlmProviderType
import com.dopashift.domain.model.LlmSuggestionResponse
import com.dopashift.domain.model.LlmVideoResponse
import com.dopashift.domain.model.ValidationResult

/**
 * Domain port for LLM-powered operations.
 * Orchestrates prompts, parses responses, and validates provider credentials.
 * Implementations live in the infrastructure layer.
 */
interface LlmProviderService {
    /**
     * Request video suggestions from the configured LLM for a given goal.
     */
    suspend fun requestVideoSuggestions(goalName: String, keywords: List<String>): LlmVideoResponse

    /**
     * Request goal-related suggestions (keywords, milestones, habit ideas) from the LLM.
     */
    suspend fun requestGoalSuggestions(
        goalName: String,
        description: String,
        keywords: List<String>
    ): LlmSuggestionResponse

    /**
     * Validate a user-supplied API key by making a test call to the provider.
     */
    suspend fun validateApiKey(provider: LlmProviderType, apiKey: String): ValidationResult
}
