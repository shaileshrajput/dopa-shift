package com.dopashift.domain.service

/**
 * Provider-agnostic interface for LLM interactions.
 * Implementations live in the infrastructure/data layer.
 */
interface LlmProviderService {
    suspend fun requestVideoSuggestions(goalName: String, keywords: List<String>): LlmVideoResponse
    suspend fun validateApiKey(provider: LlmProviderType, apiKey: String): ValidationResult
}

data class LlmVideoResponse(
    val videoUrls: List<String>,
    val source: VideoSource
)

enum class VideoSource { LLM, YOUTUBE_SEARCH }

enum class LlmProviderType { OPENAI, GEMINI, CLAUDE }

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}
